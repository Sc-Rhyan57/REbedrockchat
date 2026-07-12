package gg.sevenmc.voice.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import gg.sevenmc.voice.databinding.ItemServerBinding
import gg.sevenmc.voice.util.ServerEntry

class ServerAdapter(
    private val onConnect: (ServerEntry) -> Unit,
    private val onEdit: (ServerEntry) -> Unit,
    private val onDelete: (ServerEntry) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ViewHolder>() {

    private val items = mutableListOf<ServerEntry>()

    fun setItems(list: List<ServerEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemServerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: ServerEntry) {
            binding.tvServerName.text = entry.name
            binding.tvServerHost.text = "${entry.host}:${entry.voicePort}"
            binding.btnConnect.setOnClickListener { onConnect(entry) }
            binding.btnEdit.setOnClickListener { onEdit(entry) }
            binding.btnDelete.setOnClickListener { onDelete(entry) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemServerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size
}
