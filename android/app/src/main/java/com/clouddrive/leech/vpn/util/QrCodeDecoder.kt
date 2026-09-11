package com.clouddrive.leech.vpn.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.EnumMap

/**
 * Offline QR Code & Barcode Decoder using ZXing.
 * Decodes configurations from camera frames, files, and Base64 image streams.
 */
object QrCodeDecoder {

    fun decodeFromBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                put(DecodeHintType.TRY_HARDER, java.lang.Boolean.TRUE)
                put(DecodeHintType.CHARACTER_SET, "UTF-8")
            }

            val reader = MultiFormatReader()
            val result = reader.decode(binaryBitmap, hints)
            result.text
        } catch (_: Exception) {
            null
        }
    }

    fun decodeFromBase64(base64Image: String): String? {
        return try {
            val clean = if (base64Image.contains(",")) {
                base64Image.substringAfter(",")
            } else {
                base64Image
            }
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            decodeFromBitmap(bitmap)
        } catch (_: Exception) {
            null
        }
    }
}
