package com.example.payrajpal

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** A real, scannable QR code (replaces the decorative FakeQr). Keep it square. */
@Composable
fun RealQr(text: String, modifier: Modifier = Modifier, color: Color = Color.Black) {
    val matrix = remember(text) {
        QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
        )
    }
    Canvas(modifier) {
        val n = matrix.width
        val cell = size.width / n
        for (y in 0 until matrix.height) {
            for (x in 0 until n) {
                if (matrix.get(x, y)) {
                    drawRect(color, Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f))
                }
            }
        }
    }
}

/** The QR payload other SplitPay users scan to pay you. */
fun payQrPayload(name: String, email: String): String =
    "splitpay://pay?e=" + java.net.URLEncoder.encode(email, "UTF-8") + "&n=" + java.net.URLEncoder.encode(name, "UTF-8")