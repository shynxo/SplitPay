package com.example.payrajpal

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

object QrDecoder {

    private fun createScanner(): BarcodeScanner {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        return BarcodeScanning.getClient(options)
    }

    fun decodeFromUri(context: Context, uri: Uri, onResult: (String?) -> Unit) {
        try {
            val inputImage = InputImage.fromFilePath(context, uri)
            val scanner = createScanner()
            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    val qrCode = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                    onResult(qrCode)
                }
                .addOnFailureListener { e ->
                    Log.e("QrDecoder", "Failed to decode QR from URI", e)
                    onResult(null)
                }
        } catch (e: Exception) {
            Log.e("QrDecoder", "Error reading image URI for QR decoding", e)
            onResult(null)
        }
    }

    fun createImageAnalyzer(onQrDetected: (String) -> Unit): ImageAnalysis.Analyzer {
        return QrImageAnalyzer(onQrDetected)
    }

    private class QrImageAnalyzer(
        private val onQrDetected: (String) -> Unit
    ) : ImageAnalysis.Analyzer {

        private val scanner = createScanner()
        private val isQrFound = AtomicBoolean(false)

        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            if (isQrFound.get()) {
                imageProxy.close()
                return
            }

            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return
            }

            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val qrCode = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                    if (qrCode != null && isQrFound.compareAndSet(false, true)) {
                        onQrDetected(qrCode)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("QrDecoder", "Live frame QR analysis failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }
}