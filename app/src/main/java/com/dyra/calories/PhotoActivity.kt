package com.dyra.calories

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Фото прогресса: снимки хранятся в приватной папке приложения,
 * имя файла — дата съёмки. Есть сравнение первого и последнего фото.
 */
class PhotoActivity : AppCompatActivity() {

    private lateinit var list: RecyclerView
    private lateinit var emptyText: TextView
    private val photos = mutableListOf<File>()

    private val fileNameFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")
    private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))

    private val pickLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { importPhoto(it) }
        }

    private val photosDir: File
        get() = File(filesDir, "progress_photos").apply { mkdirs() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photos)

        emptyText = findViewById(R.id.photosEmptyText)
        list = findViewById(R.id.photoGrid)
        list.layoutManager = GridLayoutManager(this, 3)
        list.adapter = PhotoAdapter()

        findViewById<Button>(R.id.addPhotoButton).setOnClickListener {
            pickLauncher.launch(arrayOf("image/*"))
        }
        findViewById<Button>(R.id.comparePhotosButton).setOnClickListener { showCompare() }

        reload()
    }

    private fun reload() {
        photos.clear()
        photosDir.listFiles { file -> file.extension == "jpg" }
            ?.sortedBy { it.name }
            ?.let { photos.addAll(it) }
        list.adapter?.notifyDataSetChanged()
        emptyText.visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun importPhoto(uri: Uri) {
        try {
            val name = LocalDateTime.now().format(fileNameFormat) + ".jpg"
            contentResolver.openInputStream(uri)?.use { input ->
                File(photosDir, name).outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException()
            reload()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.photo_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun photoDate(file: File): String {
        val raw = file.name.removeSuffix(".jpg").substringBefore('_')
        return try {
            LocalDate.parse(raw).format(dateFormat)
        } catch (e: Exception) {
            raw
        }
    }

    /** Уменьшенная загрузка, чтобы не держать огромные снимки в памяти. */
    private fun decodeScaled(file: File, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPx) sample *= 2
        return BitmapFactory.decodeFile(
            file.path,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    private fun showPhoto(file: File) {
        val view = ImageView(this).apply {
            adjustViewBounds = true
            setPadding(0, 16, 0, 0)
            setImageBitmap(decodeScaled(file, 1080))
        }
        AlertDialog.Builder(this)
            .setTitle(photoDate(file))
            .setView(view)
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.photo_delete_confirm)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        file.delete()
                        reload()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .show()
    }

    /** Первое и последнее фото рядом: было/стало. */
    private fun showCompare() {
        if (photos.size < 2) {
            Toast.makeText(this, R.string.photo_need_two, Toast.LENGTH_SHORT).show()
            return
        }
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_compare, null)
        val first = photos.first()
        val last = photos.last()
        view.findViewById<ImageView>(R.id.compareBefore).setImageBitmap(decodeScaled(first, 540))
        view.findViewById<ImageView>(R.id.compareAfter).setImageBitmap(decodeScaled(last, 540))
        view.findViewById<TextView>(R.id.compareBeforeDate).text = photoDate(first)
        view.findViewById<TextView>(R.id.compareAfterDate).text = photoDate(last)
        AlertDialog.Builder(this)
            .setTitle(R.string.photo_compare_title)
            .setView(view)
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private inner class PhotoAdapter : RecyclerView.Adapter<PhotoAdapter.Holder>() {

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val image: ImageView = view.findViewById(R.id.photoImage)
            val date: TextView = view.findViewById(R.id.photoDate)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_photo, parent, false)
            )

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val file = photos[position]
            holder.image.setImageBitmap(decodeScaled(file, 256))
            holder.date.text = photoDate(file)
            holder.itemView.setOnClickListener { showPhoto(file) }
        }

        override fun getItemCount() = photos.size
    }
}
