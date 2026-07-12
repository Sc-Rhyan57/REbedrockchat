package gg.sevenmc.voice.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import gg.sevenmc.voice.databinding.ActivityServerEditorBinding
import gg.sevenmc.voice.util.ServerEntry
import gg.sevenmc.voice.util.ServerPreferences

class ServerEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServerEditorBinding
    private lateinit var prefs: ServerPreferences
    private var existingEntry: ServerEntry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = ServerPreferences(this)
        val serverId = intent.getStringExtra("server_id")
        if (serverId != null) {
            existingEntry = prefs.getServers().find { it.id == serverId }
            populateFields(existingEntry)
            binding.tvTitle.text = "Editar Servidor"
        } else {
            binding.tvTitle.text = "Adicionar Servidor"
        }

        binding.btnSave.setOnClickListener { save() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    private fun populateFields(entry: ServerEntry?) {
        entry ?: return
        binding.etServerName.setText(entry.name)
        binding.etHost.setText(entry.host)
        binding.etPort.setText(entry.port.toString())
        binding.etVoicePort.setText(entry.voicePort.toString())
        binding.etSecret.setText(entry.secret)
    }

    private fun save() {
        val name = binding.etServerName.text.toString().trim()
        val host = binding.etHost.text.toString().trim()
        val portStr = binding.etPort.text.toString().trim()
        val voicePortStr = binding.etVoicePort.text.toString().trim()
        val secret = binding.etSecret.text.toString().trim()

        if (name.isEmpty() || host.isEmpty()) {
            Toast.makeText(this, "Preencha o nome e o host.", Toast.LENGTH_SHORT).show()
            return
        }

        val port = portStr.toIntOrNull() ?: 25565
        val voicePort = voicePortStr.toIntOrNull() ?: 24454

        val entry = (existingEntry ?: ServerEntry(name = name, host = host)).copy(
            name = name,
            host = host,
            port = port,
            voicePort = voicePort,
            secret = secret
        )
        prefs.saveServer(entry)
        Toast.makeText(this, "Servidor salvo!", Toast.LENGTH_SHORT).show()
        finish()
    }
}
