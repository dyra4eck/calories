package com.dyra.calories

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EntryAdapter(
    private val entries: List<Entry>,
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit
) : RecyclerView.Adapter<EntryAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.entryName)
        val time: TextView = view.findViewById(R.id.entryTime)
        val macros: TextView = view.findViewById(R.id.entryMacros)
        val kcal: TextView = view.findViewById(R.id.entryKcal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_entry, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = entries[position]
        val context = holder.itemView.context
        holder.name.text = entry.name
        holder.time.text = entry.time
        holder.kcal.text = context.getString(R.string.kcal_value, entry.kcal)

        val hasMacros = entry.protein > 0 || entry.fat > 0 || entry.carbs > 0
        holder.macros.visibility = if (hasMacros) View.VISIBLE else View.GONE
        if (hasMacros) {
            holder.macros.text = context.getString(
                R.string.entry_macros,
                fmt(entry.protein),
                fmt(entry.fat),
                fmt(entry.carbs)
            )
        }

        holder.itemView.setOnClickListener { onClick(holder.bindingAdapterPosition) }
        holder.itemView.setOnLongClickListener {
            onLongClick(holder.bindingAdapterPosition)
            true
        }
    }

    override fun getItemCount() = entries.size
}
