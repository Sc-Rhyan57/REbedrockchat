package gg.sevenmc.voice.network

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class AudioCapture {

    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val FRAME_SIZE = 960

    private var recorder: AudioRecord? = null
    private var isCapturing = false
    private var isMuted = false
    private var amplification = 1.0f

    var onFrameCaptured: ((ByteArray) -> Unit)? = null

    fun start() {
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
            FRAME_SIZE * 2
        )
        recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        recorder?.startRecording()
        isCapturing = true
        Thread {
            val buffer = ShortArray(FRAME_SIZE)
            while (isCapturing) {
                val read = recorder?.read(buffer, 0, FRAME_SIZE) ?: break
                if (read > 0 && !isMuted) {
                    val amplified = applyAmplification(buffer, read)
                    onFrameCaptured?.invoke(shortsToBytes(amplified))
                }
            }
        }.also { it.isDaemon = true }.start()
    }

    fun stop() {
        isCapturing = false
        recorder?.stop()
        recorder?.release()
        recorder = null
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    fun setAmplification(amp: Float) {
        amplification = amp.coerceIn(0.5f, 4.0f)
    }

    fun isMuted() = isMuted

    private fun applyAmplification(samples: ShortArray, count: Int): ShortArray {
        if (amplification == 1.0f) return samples
        return ShortArray(count) { i ->
            (samples[i] * amplification).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun shortsToBytes(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        for (i in shorts.indices) {
            bytes[i * 2] = (shorts[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (shorts[i].toInt() shr 8 and 0xFF).toByte()
        }
        return bytes
    }
}
