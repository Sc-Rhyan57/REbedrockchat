package gg.sevenmc.voice.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import gg.sevenmc.voice.R
import gg.sevenmc.voice.mc.VoiceChatConnector
import gg.sevenmc.voice.network.AudioCapture
import gg.sevenmc.voice.network.AudioPlayback
import gg.sevenmc.voice.network.ConnectionState
import gg.sevenmc.voice.network.VoiceChatConnection
import gg.sevenmc.voice.overlay.OverlayService
import gg.sevenmc.voice.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class VoiceChatService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): VoiceChatService = this@VoiceChatService
    }

    private val binder = LocalBinder()
    private var connector: VoiceChatConnector? = null
    private val capture = AudioCapture()
    private val playback = AudioPlayback()
    private var currentState = ConnectionState.DISCONNECTED
    private var isMuted = false
    private var isDeafened = false

    var onStateChange: ((ConnectionState) -> Unit)? = null
    var onSpeakingChanged: ((Boolean) -> Unit)? = null

    companion object {
        const val CHANNEL_ID = "seven_voice_channel"
        const val NOTIF_ID = 1001
        const val ACTION_CONNECT = "gg.sevenmc.voice.CONNECT"
        const val ACTION_DISCONNECT = "gg.sevenmc.voice.DISCONNECT"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_SERVER_NAME = "server_name"
        const val EXTRA_MS_TOKEN = "ms_token"
        const val EXTRA_PLAYER_UUID = "player_uuid"
        const val EXTRA_VOICE_PORT = "voice_port"
        const val EXTRA_SECRET = "secret"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val host = intent.getStringExtra(EXTRA_HOST) ?: return START_NOT_STICKY
                val port = intent.getIntExtra(EXTRA_PORT, 25565)
                val serverName = intent.getStringExtra(EXTRA_SERVER_NAME) ?: "Server"
                val msToken = intent.getStringExtra(EXTRA_MS_TOKEN)

                startForeground(NOTIF_ID, buildNotification(serverName, "Conectando..."))

                if (msToken != null) {
                    connectWithJavaLogin(host, port, serverName, msToken)
                } else {
                    val voicePort = intent.getIntExtra(EXTRA_VOICE_PORT, 24454)
                    val secretStr = intent.getStringExtra(EXTRA_SECRET) ?: ""
                    val uuidStr = intent.getStringExtra(EXTRA_PLAYER_UUID) ?: ""
                    connectDirectUDP(host, voicePort, secretStr, uuidStr, serverName)
                }
            }
            ACTION_DISCONNECT -> {
                disconnect()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun connectWithJavaLogin(
        host: String,
        port: Int,
        serverName: String,
        msToken: String
    ) {
        val entry = gg.sevenmc.voice.util.ServerEntry(
            name = serverName,
            host = host,
            port = port
        )

        val conn = VoiceChatConnector(entry, msToken)
        connector = conn

        conn.onStateChange = { state, label ->
            currentState = state
            onStateChange?.invoke(state)
            updateNotification(serverName, label)
        }

        conn.onVoiceReady = { voice ->
            startAudio(voice)
            startOverlay()
        }

        conn.connect()
    }

    private fun connectDirectUDP(
        host: String,
        voicePort: Int,
        secretStr: String,
        uuidStr: String,
        serverName: String
    ) {
        val secret = secretStr.toByteArray(Charsets.UTF_8).copyOf(32)
        val uuid = runCatching { java.util.UUID.fromString(uuidStr) }.getOrElse { java.util.UUID.randomUUID() }

        val config = gg.sevenmc.voice.network.VoiceServerConfig(host, voicePort, secret, uuid)
        val voice = gg.sevenmc.voice.network.VoiceChatConnection(config)

        voice.onStateChange = { state ->
            currentState = state
            onStateChange?.invoke(state)
            updateNotification(serverName, stateLabel(state))
            if (state == ConnectionState.CONNECTED) {
                startAudio(voice)
                startOverlay()
            }
            if (state == ConnectionState.DISCONNECTED || state == ConnectionState.ERROR) {
                stopAudio()
            }
        }
        voice.onVoiceReceived = { pcm, _ ->
            if (!isDeafened) playback.playFrame(pcm)
        }

        GlobalScope.launch(Dispatchers.IO) {
            voice.connect()
        }
    }

    private fun startAudio(conn: VoiceChatConnection) {
        playback.start()
        conn.onVoiceReceived = { pcm, _ ->
            if (!isDeafened) playback.playFrame(pcm)
        }
        capture.onFrameCaptured = { frame ->
            if (!isMuted) {
                conn.sendVoiceData(frame)
                onSpeakingChanged?.invoke(true)
            }
        }
        capture.start()
        conn.startReceiving()
    }

    private fun stopAudio() {
        capture.stop()
        playback.stop()
    }

    private fun startOverlay() {
        startService(Intent(this, OverlayService::class.java))
    }

    fun disconnect() {
        connector?.disconnect()
        connector = null
        stopAudio()
        stopService(Intent(this, OverlayService::class.java))
        currentState = ConnectionState.DISCONNECTED
    }

    fun toggleMute() {
        isMuted = !isMuted
        capture.setMuted(isMuted)
    }

    fun toggleDeafen() {
        isDeafened = !isDeafened
        if (isDeafened && !isMuted) {
            isMuted = true
            capture.setMuted(true)
        }
    }

    fun isMuted() = isMuted
    fun isDeafened() = isDeafened
    fun getState() = currentState

    private fun stateLabel(state: ConnectionState) = when (state) {
        ConnectionState.CONNECTED -> "Conectado"
        ConnectionState.CONNECTING -> "Conectando..."
        ConnectionState.AUTHENTICATING -> "Autenticando..."
        ConnectionState.DISCONNECTED -> "Desconectado"
        ConnectionState.ERROR -> "Erro de conexão"
    }

    private fun buildNotification(server: String, status: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SevenVoice • $server")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(server: String, status: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(server, status))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Voice Chat",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "SevenVoice foreground service" }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }
}
