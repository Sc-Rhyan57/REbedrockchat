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
import gg.sevenmc.voice.network.AudioCapture
import gg.sevenmc.voice.network.AudioPlayback
import gg.sevenmc.voice.network.ConnectionState
import gg.sevenmc.voice.network.VoiceChatConnection
import gg.sevenmc.voice.network.VoiceServerConfig
import gg.sevenmc.voice.overlay.OverlayService
import gg.sevenmc.voice.ui.MainActivity
import gg.sevenmc.voice.util.ServerEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

class VoiceChatService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): VoiceChatService = this@VoiceChatService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var connection: VoiceChatConnection? = null
    private val capture = AudioCapture()
    private val playback = AudioPlayback()
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
        const val EXTRA_VOICE_PORT = "voice_port"
        const val EXTRA_SECRET = "secret"
        const val EXTRA_PLAYER_UUID = "player_uuid"
        const val EXTRA_SERVER_NAME = "server_name"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val host = intent.getStringExtra(EXTRA_HOST) ?: return START_NOT_STICKY
                val voicePort = intent.getIntExtra(EXTRA_VOICE_PORT, 24454)
                val secretStr = intent.getStringExtra(EXTRA_SECRET) ?: ""
                val uuidStr = intent.getStringExtra(EXTRA_PLAYER_UUID) ?: UUID.randomUUID().toString()
                val serverName = intent.getStringExtra(EXTRA_SERVER_NAME) ?: "Server"
                val secret = secretStr.toByteArray(Charsets.UTF_8).copyOf(32)
                val playerUuid = runCatching { UUID.fromString(uuidStr) }.getOrElse { UUID.randomUUID() }
                startForeground(NOTIF_ID, buildNotification(serverName, "Conectando..."))
                connect(host, voicePort, secret, playerUuid, serverName)
            }
            ACTION_DISCONNECT -> {
                disconnect()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun connect(host: String, port: Int, secret: ByteArray, uuid: UUID, serverName: String) {
        val config = VoiceServerConfig(host, port, secret, uuid)
        connection = VoiceChatConnection(config).also { conn ->
            conn.onStateChange = { state ->
                onStateChange?.invoke(state)
                updateNotification(serverName, stateLabel(state))
                if (state == ConnectionState.CONNECTED) {
                    startAudio(conn)
                    startOverlay()
                }
                if (state == ConnectionState.DISCONNECTED || state == ConnectionState.ERROR) {
                    stopAudio()
                }
            }
            conn.onVoiceReceived = { pcm, _ ->
                if (!isDeafened) playback.playFrame(pcm)
            }
        }
        scope.launch(Dispatchers.IO) {
            connection?.connect()
        }
    }

    private fun startAudio(conn: VoiceChatConnection) {
        playback.start()
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
        val intent = Intent(this, OverlayService::class.java)
        startService(intent)
    }

    fun disconnect() {
        connection?.disconnect()
        connection = null
        stopAudio()
        stopService(Intent(this, OverlayService::class.java))
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
    fun getState() = connection?.getState() ?: ConnectionState.DISCONNECTED

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
