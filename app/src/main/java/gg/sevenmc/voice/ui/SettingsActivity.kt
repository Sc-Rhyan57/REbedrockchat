package gg.sevenmc.voice.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import gg.sevenmc.voice.databinding.ActivitySettingsBinding
import gg.sevenmc.voice.util.ServerPreferences
import gg.sevenmc.voice.util.VoiceSettings

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: ServerPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = ServerPreferences(this)
        val settings = prefs.getVoiceSettings()

        binding.sliderMicVolume.value = settings.micVolume
        binding.sliderSpeakerVolume.value = settings.speakerVolume
        binding.switchPushToTalk.isChecked = settings.pushToTalk
        binding.switchNoiseSuppress.isChecked = settings.noiseSuppress
        binding.sliderVadThreshold.value = (settings.voiceActivationThreshold * 100).toInt().toFloat()

        binding.tvMicVolumeValue.text = "${(settings.micVolume * 100).toInt()}%"
        binding.tvSpeakerVolumeValue.text = "${(settings.speakerVolume * 100).toInt()}%"
        binding.tvVadValue.text = "${(settings.voiceActivationThreshold * 100).toInt()}%"

        binding.sliderMicVolume.addOnChangeListener { _, value, _ ->
            binding.tvMicVolumeValue.text = "${(value * 100).toInt()}%"
        }
        binding.sliderSpeakerVolume.addOnChangeListener { _, value, _ ->
            binding.tvSpeakerVolumeValue.text = "${(value * 100).toInt()}%"
        }
        binding.sliderVadThreshold.addOnChangeListener { _, value, _ ->
            binding.tvVadValue.text = "${value.toInt()}%"
        }

        binding.btnSaveSettings.setOnClickListener {
            val newSettings = VoiceSettings(
                micVolume = binding.sliderMicVolume.value,
                speakerVolume = binding.sliderSpeakerVolume.value,
                pushToTalk = binding.switchPushToTalk.isChecked,
                noiseSuppress = binding.switchNoiseSuppress.isChecked,
                voiceActivationThreshold = binding.sliderVadThreshold.value / 100f
            )
            prefs.saveVoiceSettings(newSettings)
            finish()
        }

        binding.btnBack.setOnClickListener { finish() }
    }
}
