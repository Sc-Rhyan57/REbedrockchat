package gg.sevenmc.voice.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import gg.sevenmc.voice.auth.XboxAuthManager
import gg.sevenmc.voice.databinding.ActivityLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var authManager: XboxAuthManager
    private var polling = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        authManager = XboxAuthManager(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnStartLogin.setOnClickListener { startDeviceCodeFlow() }
        binding.tvUserCode.setOnClickListener {
            val code = binding.tvUserCode.text.toString()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Xbox Code", code))
            Toast.makeText(this, "Código copiado!", Toast.LENGTH_SHORT).show()
        }
        binding.btnOpenBrowser.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://microsoft.com/link")))
        }
    }

    private fun startDeviceCodeFlow() {
        binding.btnStartLogin.isEnabled = false
        binding.progressLogin.visibility = View.VISIBLE
        binding.layoutCodeInfo.visibility = View.GONE

        lifecycleScope.launch {
            val result = authManager.requestDeviceCode()
            result.onSuccess { deviceCode ->
                withContext(Dispatchers.Main) {
                    binding.tvUserCode.text = deviceCode.userCode
                    binding.tvVerificationUrl.text = deviceCode.verificationUri
                    binding.layoutCodeInfo.visibility = View.VISIBLE
                    binding.progressLogin.visibility = View.GONE
                    binding.tvStatusLogin.text = "Aguardando autorização..."
                }
                pollForToken(deviceCode.deviceCode, deviceCode.interval)
            }.onFailure {
                withContext(Dispatchers.Main) {
                    binding.progressLogin.visibility = View.GONE
                    binding.btnStartLogin.isEnabled = true
                    Toast.makeText(this@LoginActivity, "Erro ao iniciar login. Verifique sua conexão.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun pollForToken(deviceCode: String, intervalSec: Int) {
        polling = true
        lifecycleScope.launch(Dispatchers.IO) {
            while (polling) {
                delay(intervalSec * 1000L)
                val result = authManager.pollForToken(deviceCode)
                val token = result.getOrNull()
                if (token != null) {
                    val profileResult = authManager.authenticateWithXbox(token)
                    withContext(Dispatchers.Main) {
                        profileResult.onSuccess { profile ->
                            Toast.makeText(this@LoginActivity, "Bem-vindo, ${profile.gamertag}!", Toast.LENGTH_SHORT).show()
                            finish()
                        }.onFailure {
                            binding.tvStatusLogin.text = "Erro ao buscar perfil Xbox."
                            binding.btnStartLogin.isEnabled = true
                        }
                    }
                    polling = false
                }
            }
        }
    }

    override fun onDestroy() {
        polling = false
        super.onDestroy()
    }
}
