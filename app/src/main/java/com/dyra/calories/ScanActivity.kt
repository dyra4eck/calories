package com.dyra.calories

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Вертикальный экран сканирования штрих-кода: тёмная тема, зелёная рамка,
 * кнопка фонарика. Результат возвращается через стандартный ScanContract.
 */
class ScanActivity : AppCompatActivity(), DecoratedBarcodeView.TorchListener {

    private lateinit var capture: CaptureManager
    private lateinit var barcodeView: DecoratedBarcodeView
    private lateinit var torchButton: ImageButton
    private var torchOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        barcodeView = findViewById(R.id.scannerView)
        barcodeView.setTorchListener(this)

        torchButton = findViewById(R.id.torchButton)
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            torchButton.visibility = View.GONE
        }
        torchButton.setOnClickListener {
            if (torchOn) barcodeView.setTorchOff() else barcodeView.setTorchOn()
        }

        findViewById<ImageButton>(R.id.closeButton).setOnClickListener { finish() }

        capture = CaptureManager(this, barcodeView)
        capture.initializeFromIntent(intent, savedInstanceState)
        capture.decode()
    }

    override fun onResume() {
        super.onResume()
        capture.onResume()
    }

    override fun onPause() {
        super.onPause()
        capture.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        capture.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        capture.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        capture.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onTorchOn() {
        torchOn = true
        torchButton.setImageResource(R.drawable.ic_flash_off)
    }

    override fun onTorchOff() {
        torchOn = false
        torchButton.setImageResource(R.drawable.ic_flash_on)
    }

    companion object {
        /** Единые настройки запуска сканера для всего приложения. */
        fun options(): ScanOptions = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            .setCaptureActivity(ScanActivity::class.java)
            .setOrientationLocked(true)
            .setBeepEnabled(false)
    }
}
