package gg.sevenmc.voice.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import gg.sevenmc.voice.R
import gg.sevenmc.voice.auth.XboxAuthManager
import gg.sevenmc.voice.databinding.ActivityMainBinding
import gg.sevenmc.voice.network.ConnectionState
import gg.sevenmc.voice.service.VoiceChatService
import gg.sevenmc.voice.util.ServerEntry
import gg.sevenmc.voice.util.ServerPreferences

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: ServerPreferences
    private lateinit var authManager: XboxAuthManager
    private var voiceService: VoiceChatService? = null
    private var isBound = false
    private lateinit var serverAdapter: ServerAdapter

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            voiceService = (binder as VoiceChatService.LocalBinder).getService()
            isBound = true
            voiceService?.onStateChange = { state -> runOnUiThread { updateState(state) } }
            updateState(voiceService?.getState() ?: ConnectionState.DISCONNECTED)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            voiceService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = ServerPreferences(this)
        authManager = XboxAuthManager(this)

        setupRecyclerView()
        setupButtons()
        updateAccountUI()
        checkOverlayPermission()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, VoiceChatService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        refreshServerList()
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun setupRecyclerView() {
        serverAdapter = ServerAdapter(
            onConnect = { entry -> connectToServer(entry) },
            onEdit = { entry -> openServerEditor(entry) },
            onDelete = { entry -> confirmDelete(entry) }
        )
        binding.rvServers.layoutManager = LinearLayoutManager(this)
        binding.rvServers.adapter = serverAdapter
    }

    private fun setupButtons() {
        binding.btnAddServer.setOnClickListener { openServerEditor(null) }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnAccount.setOnClickListener {
            if (authManager.isLoggedIn()) {
                AlertDialog.Builder(this)
                    .setTitle("Conta Xbox")
                    .setMessage("Logado como: ${authManager.getSavedProfile()?.gamertag}")
                    .setNegativeButton("Sair") { _, _ ->
                        authManager.logout()
                        updateAccountUI()
                    }
                    .setPositiveButton("Fechar", null)
                    .show()
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }

        binding.btnDisconnect.setOnClickListener {
            val intent = Intent(this, VoiceChatService::class.java).apply {
                action = VoiceChatService.ACTION_DISCONNECT
            }
            startService(intent)
            updateState(ConnectionState.DISCONNECTED)
        }

        binding.tvDiscord.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dsc.gg/sevenmc7")))
        }

        binding.tvWatermark.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dsc.gg/sevenmc7")))
        }
    }

    private fun refreshServerList() {
        val servers = prefs.getServers()
        serverAdapter.setItems(servers)
        binding.tvEmptyServers.visibility = if (servers.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun connectToServer(entry: ServerEntry) {
        if (!checkOverlayPermission()) return
        prefs.setLastServer(entry.id)
        val intent = Intent(this, VoiceChatService::class.java).apply {
            action = VoiceChatService.ACTION_CONNECT
            putExtra(VoiceChatService.EXTRA_HOST, entry.host)
            putExtra(VoiceChatService.EXTRA_PORT, entry.port)
            putExtra(VoiceChatService.EXTRA_SERVER_NAME, entry.name)
            val profile = authManager.getSavedProfile()
            if (profile != null) {
                putExtra(VoiceChatService.EXTRA_MS_TOKEN, profile.accessToken)
            } else {
                putExtra(VoiceChatService.EXTRA_VOICE_PORT, entry.voicePort)
                putExtra(VoiceChatService.EXTRA_SECRET, entry.secret)
            }
        }
        startService(intent)
        bindService(Intent(this, VoiceChatService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        Toast.makeText(this, "Conectando a ${entry.name}...", Toast.LENGTH_SHORT).show()
        updateState(ConnectionState.CONNECTING)
    }

    private fun openServerEditor(entry: ServerEntry?) {
        val intent = Intent(this, ServerEditorActivity::class.java)
        entry?.let { intent.putExtra("server_id", it.id) }
        startActivity(intent)
    }

    private fun confirmDelete(entry: ServerEntry) {
        AlertDialog.Builder(this)
            .setTitle("Remover servidor")
            .setMessage("Remover \"${entry.name}\"?")
            .setPositiveButton("Remover") { _, _ ->
                prefs.deleteServer(entry.id)
                refreshServerList()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun updateAccountUI() {
        val profile = authManager.getSavedProfile()
        if (profile != null) {
            binding.btnAccount.text = profile.gamertag
            binding.ivAccountIcon.setImageResource(R.drawable.ic_xbox)
        } else {
            binding.btnAccount.text = getString(R.string.login_xbox)
            binding.ivAccountIcon.setImageResource(R.drawable.ic_xbox_grey)
        }
    }

    private fun updateState(state: ConnectionState) {
        val label = when (state) {
            ConnectionState.CONNECTED -> "● Conectado"
            ConnectionState.CONNECTING -> "◌ Conectando..."
            ConnectionState.AUTHENTICATING -> "◌ Autenticando..."
            ConnectionState.DISCONNECTED -> "○ Desconectado"
            ConnectionState.ERROR -> "✕ Erro"
        }
        binding.tvConnectionStatus.text = label
        binding.tvConnectionStatus.setTextColor(
            getColor(
                when (state) {
                    ConnectionState.CONNECTED -> R.color.green_400
                    ConnectionState.ERROR -> R.color.red_400
                    else -> R.color.gray_400
                }
            )
        )
        binding.btnDisconnect.visibility = if (
            state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING
        ) View.VISIBLE else View.GONE
    }

    private fun checkOverlayPermission(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Permissão necessária")
                .setMessage("O SevenVoice precisa de permissão para exibir a sobreposição de voz. Vá em Configurações e ative.")
                .setPositiveButton("Configurações") { _, _ ->
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
                .setNegativeButton("Cancelar", null)
                .show()
            return false
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        refreshServerList()
        updateAccountUI()
    }
}
