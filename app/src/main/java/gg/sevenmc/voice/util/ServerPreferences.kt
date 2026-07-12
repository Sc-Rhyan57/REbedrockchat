package gg.sevenmc.voice.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class ServerEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 24454,
    val voicePort: Int = 24454,
    val secret: String = ""
)

class ServerPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("seven_servers", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val SERVERS_KEY = "servers"
    private val LAST_SERVER_KEY = "last_server_id"

    fun getServers(): List<ServerEntry> {
        val json = prefs.getString(SERVERS_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<ServerEntry>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun saveServer(entry: ServerEntry) {
        val list = getServers().toMutableList()
        val idx = list.indexOfFirst { it.id == entry.id }
        if (idx >= 0) list[idx] = entry else list.add(entry)
        prefs.edit().putString(SERVERS_KEY, gson.toJson(list)).apply()
    }

    fun deleteServer(id: String) {
        val list = getServers().filter { it.id != id }
        prefs.edit().putString(SERVERS_KEY, gson.toJson(list)).apply()
    }

    fun setLastServer(id: String) {
        prefs.edit().putString(LAST_SERVER_KEY, id).apply()
    }

    fun getLastServer(): ServerEntry? {
        val id = prefs.getString(LAST_SERVER_KEY, null) ?: return null
        return getServers().find { it.id == id }
    }

    fun getVoiceSettings(): VoiceSettings {
        return VoiceSettings(
            micVolume = prefs.getFloat("mic_volume", 1.0f),
            speakerVolume = prefs.getFloat("speaker_volume", 1.0f),
            pushToTalk = prefs.getBoolean("push_to_talk", false),
            noiseSuppress = prefs.getBoolean("noise_suppress", true),
            voiceActivationThreshold = prefs.getFloat("vad_threshold", 0.02f)
        )
    }

    fun saveVoiceSettings(s: VoiceSettings) {
        prefs.edit()
            .putFloat("mic_volume", s.micVolume)
            .putFloat("speaker_volume", s.speakerVolume)
            .putBoolean("push_to_talk", s.pushToTalk)
            .putBoolean("noise_suppress", s.noiseSuppress)
            .putFloat("vad_threshold", s.voiceActivationThreshold)
            .apply()
    }
}

data class VoiceSettings(
    val micVolume: Float = 1.0f,
    val speakerVolume: Float = 1.0f,
    val pushToTalk: Boolean = false,
    val noiseSuppress: Boolean = true,
    val voiceActivationThreshold: Float = 0.02f
)
