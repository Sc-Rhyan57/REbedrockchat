package gg.sevenmc.voice.mc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.geysermc.mcprotocollib.auth.GameProfile
import org.geysermc.mcprotocollib.network.Session
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory
import org.geysermc.mcprotocollib.network.packet.Packet
import org.geysermc.mcprotocollib.protocol.MinecraftConstants
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCustomPayloadPacket
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class VoiceChatSecret(
    val playerUuid: UUID,
    val secret: ByteArray,
    val voiceHost: String,
    val voicePort: Int
)

sealed class SessionResult {
    data class Success(val voiceSecret: VoiceChatSecret) : SessionResult()
    data class Failure(val reason: String) : SessionResult()
}

class MinecraftSession(
    private val host: String,
    private val port: Int,
    private val accessToken: String,
    private val gameProfile: GameProfile
) {

    private val VOICECHAT_CHANNEL = "voicechat:secret"
    private val REGISTER_CHANNEL = "minecraft:register"
    private val VOICECHAT_REQUEST_CHANNEL = "voicechat:request_secret"
    private val SECRET_TIMEOUT_SEC = 15L

    private var clientSession: org.geysermc.mcprotocollib.network.ClientSession? = null

    suspend fun connect(): SessionResult = withContext(Dispatchers.IO) {
        val latch = CountDownLatch(1)
        var result: SessionResult = SessionResult.Failure("Timeout ao aguardar secret do voice chat")
        var loggedIn = false

        val protocol = MinecraftProtocol(gameProfile, accessToken)

        val client = ClientNetworkSessionFactory.factory()
            .setRemoteSocketAddress(InetSocketAddress(host, port))
            .setProtocol(protocol)
            .create()

        clientSession = client

        client.addListener(object : SessionAdapter() {
            override fun packetReceived(session: Session, packet: Packet) {
                when (packet) {
                    is ClientboundLoginPacket -> {
                        loggedIn = true
                        registerVoiceChatChannel(session)
                        requestSecret(session)
                    }
                    is ClientboundCustomPayloadPacket -> {
                        val channelKey = packet.channel.asString()
                        if (channelKey == VOICECHAT_CHANNEL) {
                            val secret = parseVoiceChatSecret(packet.data, session, gameProfile.id)
                            if (secret != null) {
                                result = SessionResult.Success(secret)
                                latch.countDown()
                            }
                        }
                    }
                }
            }

            override fun disconnected(event: DisconnectedEvent) {
                if (!loggedIn) {
                    result = SessionResult.Failure("Desconectado: ${event.reason}")
                }
                latch.countDown()
            }
        })

        runCatching { client.connect() }.onFailure {
            return@withContext SessionResult.Failure("Falha ao conectar: ${it.message}")
        }

        latch.await(SECRET_TIMEOUT_SEC, TimeUnit.SECONDS)
        result
    }

    private fun registerVoiceChatChannel(session: Session) {
        val channelBytes = VOICECHAT_CHANNEL.toByteArray(Charsets.UTF_8)
        session.send(ServerboundCustomPayloadPacket(
            net.kyori.adventure.key.Key.key("minecraft", "register"),
            channelBytes
        ))
    }

    private fun requestSecret(session: Session) {
        val versionPayload = byteArrayOf(0x01)
        session.send(ServerboundCustomPayloadPacket(
            net.kyori.adventure.key.Key.key("voicechat", "request_secret"),
            versionPayload
        ))
    }

    private fun parseVoiceChatSecret(data: ByteArray, session: Session, playerUuid: UUID): VoiceChatSecret? {
        return runCatching {
            val buf = java.nio.ByteBuffer.wrap(data)

            val secretLength = buf.int
            val secret = ByteArray(secretLength)
            buf.get(secret)

            val portBytes = buf.short.toInt() and 0xFFFF

            val ipLength = buf.int
            val ipBytes = ByteArray(ipLength)
            buf.get(ipBytes)
            val voiceHost = String(ipBytes, Charsets.UTF_8).ifBlank { this.host }

            VoiceChatSecret(
                playerUuid = playerUuid,
                secret = secret,
                voiceHost = voiceHost,
                voicePort = portBytes
            )
        }.getOrNull()
    }

    fun keepAlive() {
    }

    fun disconnect() {
        clientSession?.disconnect(net.kyori.adventure.text.Component.text("SevenVoice desconectado"))
        clientSession = null
    }

    fun isConnected() = clientSession?.isConnected == true
}
