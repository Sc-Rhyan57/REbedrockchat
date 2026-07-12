package gg.sevenmc.voice.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    ERROR
}

data class VoiceServerConfig(
    val host: String,
    val port: Int,
    val secret: ByteArray,
    val playerUuid: UUID
)

class VoiceChatConnection(private val config: VoiceServerConfig) {

    private var socket: DatagramSocket? = null
    private var state = ConnectionState.DISCONNECTED
    private val PACKET_MAGIC: Byte = 0x01
    private val TYPE_AUTH: Byte = 0x01
    private val TYPE_PING: Byte = 0x02
    private val TYPE_VOICE: Byte = 0x03
    private val TYPE_AUTH_ACK: Byte = 0x04
    private val PING_INTERVAL_MS = 5000L
    private var lastPingTime = 0L

    var onStateChange: ((ConnectionState) -> Unit)? = null
    var onVoiceReceived: ((ByteArray, UUID) -> Unit)? = null

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            state = ConnectionState.CONNECTING
            onStateChange?.invoke(state)
            socket = DatagramSocket()
            socket?.soTimeout = 10000
            val address = InetAddress.getByName(config.host)
            socket?.connect(address, config.port)
            state = ConnectionState.AUTHENTICATING
            onStateChange?.invoke(state)
            sendAuthPacket()
            awaitAuthAck()
        }.isSuccess
    }

    private fun sendAuthPacket() {
        val uuidBytes = uuidToBytes(config.playerUuid)
        val payload = ByteArray(1 + 16)
        payload[0] = TYPE_AUTH
        System.arraycopy(uuidBytes, 0, payload, 1, 16)
        val encrypted = encryptPayload(payload)
        sendRaw(encrypted)
    }

    private fun awaitAuthAck(): Boolean {
        val buf = ByteArray(512)
        val packet = DatagramPacket(buf, buf.size)
        return try {
            socket?.receive(packet)
            val data = packet.data.copyOf(packet.length)
            val decrypted = decryptPayload(data)
            decrypted.isNotEmpty() && decrypted[0] == TYPE_AUTH_ACK
        } catch (e: Exception) {
            false
        }.also { success ->
            state = if (success) ConnectionState.CONNECTED else ConnectionState.ERROR
            onStateChange?.invoke(state)
        }
    }

    fun sendVoiceData(pcmData: ByteArray) {
        if (state != ConnectionState.CONNECTED) return
        val now = System.currentTimeMillis()
        if (now - lastPingTime > PING_INTERVAL_MS) {
            sendPing()
            lastPingTime = now
        }
        val payload = ByteArray(1 + pcmData.size)
        payload[0] = TYPE_VOICE
        System.arraycopy(pcmData, 0, payload, 1, pcmData.size)
        val encrypted = encryptPayload(payload)
        sendRaw(encrypted)
    }

    fun startReceiving() {
        Thread {
            val buf = ByteArray(4096)
            val packet = DatagramPacket(buf, buf.size)
            while (state == ConnectionState.CONNECTED) {
                try {
                    socket?.soTimeout = 3000
                    socket?.receive(packet)
                    val data = packet.data.copyOf(packet.length)
                    val decrypted = decryptPayload(data)
                    if (decrypted.isNotEmpty() && decrypted[0] == TYPE_VOICE) {
                        val uuidBytes = decrypted.copyOfRange(1, 17)
                        val uuid = bytesToUuid(uuidBytes)
                        val voice = decrypted.copyOfRange(17, decrypted.size)
                        onVoiceReceived?.invoke(voice, uuid)
                    }
                } catch (_: Exception) {}
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun sendPing() {
        val payload = byteArrayOf(TYPE_PING)
        val encrypted = encryptPayload(payload)
        sendRaw(encrypted)
    }

    private fun sendRaw(data: ByteArray) {
        try {
            val packet = DatagramPacket(data, data.size)
            socket?.send(packet)
        } catch (_: Exception) {}
    }

    fun disconnect() {
        state = ConnectionState.DISCONNECTED
        onStateChange?.invoke(state)
        socket?.close()
        socket = null
    }

    fun getState() = state

    private fun encryptPayload(data: ByteArray): ByteArray {
        return try {
            val key = config.secret.copyOf(16)
            val iv = ByteArray(16)
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            cipher.doFinal(data)
        } catch (e: Exception) {
            data
        }
    }

    private fun decryptPayload(data: ByteArray): ByteArray {
        return try {
            val key = config.secret.copyOf(16)
            val iv = ByteArray(16)
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            cipher.doFinal(data)
        } catch (e: Exception) {
            ByteArray(0)
        }
    }

    private fun uuidToBytes(uuid: UUID): ByteArray {
        val buffer = ByteBuffer.wrap(ByteArray(16))
        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)
        return buffer.array()
    }

    private fun bytesToUuid(bytes: ByteArray): UUID {
        val buffer = ByteBuffer.wrap(bytes)
        val most = buffer.long
        val least = buffer.long
        return UUID(most, least)
    }
}
