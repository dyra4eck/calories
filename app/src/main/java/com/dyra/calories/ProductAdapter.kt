package com.dyra.calories

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ProductAdapter(
    private val products: List<Product>,
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit
) : RecyclerView.Adapter<ProductAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.productName)
        val info: TextView = view.findViewById(R.id.productInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val product = products[position]
        holder.name.text = product.name
        val context = holder.itemView.context
        var info = context.getString(
            R.string.product_info,
            fmt(product.kcal100),
            fmt(product.protein100),
            fmt(product.fat100),
            fmt(product.carbs100)
        )
        product.barcode?.let {
            info += "\n" + context.getString(R.string.product_barcode_line, it)
        }
        holder.info.text = info
        holder.itemView.setOnClickListener { onClick(holder.bindingAdapterPosition) }
        holder.itemView.setOnLongClickListener {
            onLongClick(holder.bindingAdapterPosition)
            true
        }
    }

    override fun getItemCount() = products.size
}
