package gg.sevenmc.voice.mc

import gg.sevenmc.voice.network.ConnectionState
import gg.sevenmc.voice.network.VoiceChatConnection
import gg.sevenmc.voice.network.VoiceServerConfig
import gg.sevenmc.voice.util.ServerEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VoiceChatConnector(
    private val entry: ServerEntry,
    private val msAccessToken: String
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val authBridge = MinecraftAuthBridge()
    private var mcSession: MinecraftSession? = null
    private var voiceConnection: VoiceChatConnection? = null

    var onStateChange: ((ConnectionState, String) -> Unit)? = null
    var onVoiceReady: ((VoiceChatConnection) -> Unit)? = null

    fun connect() {
        scope.launch {
            onStateChange?.invoke(ConnectionState.CONNECTING, "Autenticando conta Java...")

            val credsResult = authBridge.getCredentials(msAccessToken)
            val creds = credsResult.getOrElse {
                onStateChange?.invoke(ConnectionState.ERROR, "Falha na autenticação: ${it.message}")
                return@launch
            }

            onStateChange?.invoke(ConnectionState.CONNECTING, "Conectando ao servidor Minecraft...")

            val session = MinecraftSession(
                host = entry.host,
                port = entry.port,
                accessToken = creds.accessToken,
                gameProfile = creds.gameProfile
            )
            mcSession = session

            onStateChange?.invoke(ConnectionState.AUTHENTICATING, "Aguardando secret do voice chat...")

            val sessionResult = session.connect()

            when (sessionResult) {
                is SessionResult.Failure -> {
                    onStateChange?.invoke(ConnectionState.ERROR, sessionResult.reason)
                }
                is SessionResult.Success -> {
                    val secret = sessionResult.voiceSecret
                    onStateChange?.invoke(ConnectionState.CONNECTING, "Conectando ao voice chat (UDP)...")

                    val config = VoiceServerConfig(
                        host = secret.voiceHost,
                        port = secret.voicePort,
                        secret = secret.secret,
                        playerUuid = secret.playerUuid
                    )

                    val voice = VoiceChatConnection(config)
                    voiceConnection = voice

                    val udpConnected = voice.connect()
                    if (udpConnected) {
                        onStateChange?.invoke(ConnectionState.CONNECTED, "Conectado!")
                        onVoiceReady?.invoke(voice)
                    } else {
                        onStateChange?.invoke(ConnectionState.ERROR, "Falha na conexão UDP do voice chat")
                    }
                }
            }
        }
    }

    fun disconnect() {
        voiceConnection?.disconnect()
        mcSession?.disconnect()
        voiceConnection = null
        mcSession = null
        onStateChange?.invoke(ConnectionState.DISCONNECTED, "Desconectado")
    }

    fun getVoiceConnection() = voiceConnection
    fun isConnected() = voiceConnection?.getState() == ConnectionState.CONNECTED
}
