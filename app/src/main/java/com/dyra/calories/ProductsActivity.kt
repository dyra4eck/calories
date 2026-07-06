package com.dyra.calories

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Экран управления базой продуктов (КБЖУ на 100 г). */
class ProductsActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var adapter: ProductAdapter
    private lateinit var emptyText: TextView
    private val products = mutableListOf<Product>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_products)

        store = Store(this)
        products.addAll(store.products())

        emptyText = findViewById(R.id.productsEmptyText)

        val list = findViewById<RecyclerView>(R.id.productList)
        adapter = ProductAdapter(
            products,
            onClick = { position -> showEditDialog(position) },
            onLongClick = { position -> confirmDelete(position) }
        )
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<Button>(R.id.addProductButton).setOnClickListener { showEditDialog(null) }

        refreshEmpty()
    }

    /** position == null — создание нового продукта. */
    private fun showEditDialog(position: Int?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_food, null)
        view.findViewById<TextView>(R.id.foodSubtitle).setText(R.string.per_100g)

        val nameInput = view.findViewById<EditText>(R.id.foodName)
        val kcalInput = view.findViewById<EditText>(R.id.foodKcal)
        val proteinInput = view.findViewById<EditText>(R.id.foodProtein)
        val fatInput = view.findViewById<EditText>(R.id.foodFat)
        val carbsInput = view.findViewById<EditText>(R.id.foodCarbs)

        if (position != null) {
            val product = products[position]
            nameInput.setText(product.name)
            kcalInput.setText(fmt(product.kcal100))
            proteinInput.setText(fmt(product.protein100))
            fatInput.setText(fmt(product.fat100))
            carbsInput.setText(fmt(product.carbs100))
        }

        AlertDialog.Builder(this)
            .setTitle(if (position == null) R.string.add_product else R.string.edit_product)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameInput.text.toString().trim()
                val kcal = parseNum(kcalInput.text.toString())
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.name_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (kcal == null || kcal < 0) {
                    Toast.makeText(this, R.string.enter_kcal, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val product = Product(
                    name = name,
                    kcal100 = kcal,
                    protein100 = parseNum(proteinInput.text.toString()) ?: 0.0,
                    fat100 = parseNum(fatInput.text.toString()) ?: 0.0,
                    carbs100 = parseNum(carbsInput.text.toString()) ?: 0.0
                )
                if (position == null) {
                    products.add(product)
                    adapter.notifyItemInserted(products.size - 1)
                } else {
                    products[position] = product
                    adapter.notifyItemChanged(position)
                }
                store.saveProducts(products)
                refreshEmpty()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(position: Int) {
        if (position !in products.indices) return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_product_title)
            .setMessage(products[position].name)
            .setPositiveButton(R.string.delete) { _, _ ->
                products.removeAt(position)
                store.saveProducts(products)
                adapter.notifyItemRemoved(position)
                refreshEmpty()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshEmpty() {
        emptyText.visibility = if (products.isEmpty()) View.VISIBLE else View.GONE
    }
}
