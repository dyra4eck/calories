package com.dyra.calories

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/** Экран управления базой продуктов (КБЖУ на 100 г). */
class ProductsActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var adapter: ProductAdapter
    private lateinit var emptyText: TextView
    private val products = mutableListOf<Product>()

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { onBarcodeScanned(it) }
    }

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
        findViewById<Button>(R.id.scanProductButton).setOnClickListener {
            scanLauncher.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                    .setPrompt(getString(R.string.scan_prompt))
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
            )
        }

        refreshEmpty()
    }

    /**
     * Скан из базы: знакомый код открывает продукт на редактирование,
     * новый — создание продукта с привязанным кодом.
     */
    private fun onBarcodeScanned(code: String) {
        val index = products.indexOfFirst { it.barcode == code }
        if (index >= 0) {
            Toast.makeText(this, R.string.barcode_known, Toast.LENGTH_SHORT).show()
            showEditDialog(index)
        } else {
            ProductDialog.show(this, R.string.add_product, null, code) { product ->
                addOrUpdate(null, product)
            }
        }
    }

    /** position == null — создание нового продукта. */
    private fun showEditDialog(position: Int?) {
        ProductDialog.show(
            this,
            if (position == null) R.string.add_product else R.string.edit_product,
            position?.let { products[it] }
        ) { product ->
            addOrUpdate(position, product)
        }
    }

    private fun addOrUpdate(position: Int?, product: Product) {
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
