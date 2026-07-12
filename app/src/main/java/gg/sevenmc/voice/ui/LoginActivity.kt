package gg.sevenmc.voice.ui

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import gg.sevenmc.voice.auth.AuthWebView
import gg.sevenmc.voice.auth.XboxDeviceInfo
import gg.sevenmc.voice.auth.fetchIdentityToken
import gg.sevenmc.voice.constructors.AccountManager
import kotlin.concurrent.thread

class LoginActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var webView: AuthWebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("seven_auth", Context.MODE_PRIVATE)
        AccountManager.init(this)

        webView = AuthWebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            deviceInfo = XboxDeviceInfo.ANDROID
            callback = { success ->
                if (success) {
                    val account = AccountManager.selectedAccount
                    if (account != null) {
                        runOnUiThread { webView?.showLoadingPage("Autenticando com Xbox...") }
                        thread {
                            try {
                                val (accessToken, newRefreshToken) = account.deviceInfo.refreshToken(
                                    account.refreshToken,
                                    isAuthCode = false
                                )

                                val identityToken = fetchIdentityToken(accessToken, account.deviceInfo)

                                val gamertag = extractGamertagFromToken(identityToken.token)
                                    ?: account.remark

                                prefs.edit()
                                    .putString("gamertag", gamertag)
                                    .putString("identity_token", identityToken.token)
                                    .putString("refresh_token", newRefreshToken)
                                    .putString("user_hash", extractUhsFromToken(identityToken.token))
                                    .putLong("expires_at", identityToken.notAfter * 1000L)
                                    .apply()

                                runOnUiThread {
                                    Toast.makeText(
                                        this@LoginActivity,
                                        "Bem-vindo, $gamertag!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    setResult(Activity.RESULT_OK)
                                    finish()
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    webView?.loadErrorPage("Falha ao autenticar: ${e.message}")
                                }
                            }
                        }
                    } else {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                } else {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
        }

        setContentView(FrameLayout(this).apply { addView(webView) })
        webView?.addAccount()
    }

    private fun extractUhsFromToken(identityToken: String): String {
        return try {
            identityToken.substringAfter("x=").substringBefore(";")
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractGamertagFromToken(identityToken: String): String? {
        return try {
            val xstsToken = identityToken.substringAfter(";")
            val parts = xstsToken.split(".")
            if (parts.size < 2) return null
            val decoded = gg.sevenmc.voice.auth.base64Decode(parts[1]).toString(Charsets.UTF_8)
            val json = org.json.JSONObject(decoded)
            val xui = json.optJSONObject("DisplayClaims")
                ?.optJSONArray("xui")
                ?.optJSONObject(0)
            xui?.optString("gtg")?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    @Deprecated("Use OnBackPressedDispatcher")
    override fun onBackPressed() {
        setResult(Activity.RESULT_CANCELED)
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }
}
