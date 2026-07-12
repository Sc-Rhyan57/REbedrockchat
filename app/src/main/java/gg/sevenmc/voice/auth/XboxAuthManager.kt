package gg.sevenmc.voice.auth

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class XboxProfile(
    val xuid: String,
    val gamertag: String,
    val accessToken: String,
    val xstsToken: String,
    val userHash: String
)

data class DeviceCodeResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Int,
    val interval: Int
)

class XboxAuthManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("seven_auth", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val CLIENT_ID = "000000004C12AE6F"
    private val MS_AUTH_URL = "https://login.live.com/oauth20_token.srf"
    private val XBL_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate"
    private val XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize"
    private val DEVICE_CODE_URL = "https://login.live.com/oauth20_connect.srf"

    suspend fun requestDeviceCode(): Result<DeviceCodeResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("scope", "service::user.auth.xboxlive.com::MBI_SSL")
                .add("response_type", "device_code")
                .build()
            val request = Request.Builder()
                .url(DEVICE_CODE_URL)
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            val json = gson.fromJson(response.body?.string(), JsonObject::class.java)
            DeviceCodeResponse(
                deviceCode = json.get("device_code").asString,
                userCode = json.get("user_code").asString,
                verificationUri = json.get("verification_uri").asString,
                expiresIn = json.get("expires_in").asInt,
                interval = json.get("interval").asInt
            )
        }
    }

    suspend fun pollForToken(deviceCode: String): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("device_code", deviceCode)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .build()
            val request = Request.Builder()
                .url(MS_AUTH_URL)
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            val json = gson.fromJson(response.body?.string(), JsonObject::class.java)
            if (json.has("access_token")) {
                json.get("access_token").asString
            } else {
                null
            }
        }
    }

    suspend fun authenticateWithXbox(msAccessToken: String): Result<XboxProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val xblToken = getXblToken(msAccessToken)
            val (xstsToken, userHash) = getXstsToken(xblToken)
            val profile = getXboxProfile(xstsToken, userHash)
            val xboxProfile = XboxProfile(
                xuid = profile.first,
                gamertag = profile.second,
                accessToken = msAccessToken,
                xstsToken = xstsToken,
                userHash = userHash
            )
            saveProfile(xboxProfile)
            xboxProfile
        }
    }

    private fun getXblToken(msToken: String): String {
        val payload = """
            {
                "Properties": {
                    "AuthMethod": "RPS",
                    "SiteName": "user.auth.xboxlive.com",
                    "RpsTicket": "d=$msToken"
                },
                "RelyingParty": "http://auth.xboxlive.com",
                "TokenType": "JWT"
            }
        """.trimIndent()
        val request = Request.Builder()
            .url(XBL_AUTH_URL)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        val response = client.newCall(request).execute()
        val json = gson.fromJson(response.body?.string(), JsonObject::class.java)
        return json.get("Token").asString
    }

    private fun getXstsToken(xblToken: String): Pair<String, String> {
        val payload = """
            {
                "Properties": {
                    "SandboxId": "RETAIL",
                    "UserTokens": ["$xblToken"]
                },
                "RelyingParty": "http://xboxlive.com",
                "TokenType": "JWT"
            }
        """.trimIndent()
        val request = Request.Builder()
            .url(XSTS_AUTH_URL)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        val response = client.newCall(request).execute()
        val json = gson.fromJson(response.body?.string(), JsonObject::class.java)
        val token = json.get("Token").asString
        val uhs = json.getAsJsonObject("DisplayClaims")
            .getAsJsonArray("xui")
            .get(0).asJsonObject
            .get("uhs").asString
        return Pair(token, uhs)
    }

    private fun getXboxProfile(xstsToken: String, userHash: String): Pair<String, String> {
        val auth = "XBL3.0 x=$userHash;$xstsToken"
        val request = Request.Builder()
            .url("https://profile.xboxlive.com/users/me/profile/settings?settings=Gamertag")
            .addHeader("Authorization", auth)
            .addHeader("x-xbl-contract-version", "2")
            .get()
            .build()
        val response = client.newCall(request).execute()
        val json = gson.fromJson(response.body?.string(), JsonObject::class.java)
        val settings = json.getAsJsonObject("profileUsers")
            .getAsJsonArray("profileUsers")
        val user = settings.get(0).asJsonObject
        val xuid = user.get("id").asString
        val gamertag = user.getAsJsonArray("settings")
            .get(0).asJsonObject
            .get("value").asString
        return Pair(xuid, gamertag)
    }

    private fun saveProfile(profile: XboxProfile) {
        prefs.edit()
            .putString("xuid", profile.xuid)
            .putString("gamertag", profile.gamertag)
            .putString("access_token", profile.accessToken)
            .putString("xsts_token", profile.xstsToken)
            .putString("user_hash", profile.userHash)
            .apply()
    }

    fun getSavedProfile(): XboxProfile? {
        val xuid = prefs.getString("xuid", null) ?: return null
        val gamertag = prefs.getString("gamertag", null) ?: return null
        val accessToken = prefs.getString("access_token", null) ?: return null
        val xstsToken = prefs.getString("xsts_token", null) ?: return null
        val userHash = prefs.getString("user_hash", null) ?: return null
        return XboxProfile(xuid, gamertag, accessToken, xstsToken, userHash)
    }

    fun isLoggedIn(): Boolean = prefs.contains("xuid")

    fun logout() {
        prefs.edit().clear().apply()
    }
}
