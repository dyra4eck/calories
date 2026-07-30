package com.dyra.calories

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Список записей дня, сгруппированный по приёмам пищи с подытогами калорий.
 * Колбэки получают индекс записи в исходном списке entries.
 */
class EntryAdapter(
    private val entries: List<Entry>,
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Row {
        class Header(val meal: Int, val kcal: Int) : Row()
        class Item(val index: Int) : Row()
    }

    private val rows = mutableListOf<Row>()

    init {
        rebuildRows()
    }

    /** Пересобрать список после любого изменения записей. */
    fun rebuild() {
        rebuildRows()
        notifyDataSetChanged()
    }

    private fun rebuildRows() {
        rows.clear()
        for (meal in 0 until Entry.MEAL_COUNT) {
            val indices = entries.indices.filter { entries[it].meal == meal }
            if (indices.isEmpty()) continue
            rows.add(Row.Header(meal, indices.sumOf { entries[it].kcal }))
            indices.mapTo(rows) { Row.Item(it) }
        }
    }

    /** Индекс записи для позиции списка; null — это заголовок секции. */
    fun entryIndexAt(position: Int): Int? = (rows.getOrNull(position) as? Row.Item)?.index

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.mealTitle)
        val kcal: TextView = view.findViewById(R.id.mealKcal)
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.entryName)
        val time: TextView = view.findViewById(R.id.entryTime)
        val macros: TextView = view.findViewById(R.id.entryMacros)
        val kcal: TextView = view.findViewById(R.id.entryKcal)
    }

    override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            HeaderHolder(inflater.inflate(R.layout.item_meal_header, parent, false))
        } else {
            Holder(inflater.inflate(R.layout.item_entry, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val context = holder.itemView.context
        when (val row = rows[position]) {
            is Row.Header -> {
                holder as HeaderHolder
                holder.title.text =
                    context.resources.getStringArray(R.array.meals)[row.meal]
                holder.kcal.text = context.getString(R.string.kcal_value, row.kcal)
            }
            is Row.Item -> {
                holder as Holder
                val entry = entries[row.index]
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

                holder.itemView.setOnClickListener {
                    entryIndexAt(holder.bindingAdapterPosition)?.let(onClick)
                }
                holder.itemView.setOnLongClickListener {
                    entryIndexAt(holder.bindingAdapterPosition)?.let(onLongClick)
                    true
                }
            }
        }
    }

    override fun getItemCount() = rows.size
}
