package com.dyra.calories

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EntryAdapter(
    private val entries: List<Entry>,
    private val onLongClick: (Int) -> Unit
) : RecyclerView.Adapter<EntryAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.entryName)
        val time: TextView = view.findViewById(R.id.entryTime)
        val kcal: TextView = view.findViewById(R.id.entryKcal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_entry, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = entries[position]
        holder.name.text = entry.name
        holder.time.text = entry.time
        holder.kcal.text = holder.itemView.context.getString(R.string.kcal_value, entry.kcal)
        holder.itemView.setOnLongClickListener {
            onLongClick(holder.bindingAdapterPosition)
            true
        }
    }

    override fun getItemCount() = entries.size
}
