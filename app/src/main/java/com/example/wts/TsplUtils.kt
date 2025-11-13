package com.example.wts

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.roundToInt

object TsplUtils {

    /**
     * Returns the base TSPL initialization command string.
     * Ensures the printer is in a known state before printing.
     */
    fun getInitCommand(density: Int = 6, speed: Int = 3): String {
        return """
            SIZE 48 mm, 25 mm
            GAP 3 mm, 0
            DENSITY $density
            SPEED $speed
            REFERENCE 0,0
            DIRECTION 1
            CLS
        """.trimIndent() + "\n"
    }

    /**
     * High-quality image-to-TSPL BITMAP command converter.
     * 1. Flattens image on a white background to handle transparency.
     * 2. Scales to 384px width, maintaining aspect ratio.
     * 3. Converts to 1-bit monochrome using Floyd-Steinberg dithering.
     * 4. Packs 8 pixels into each byte.
     * 5. Prepends the correct TSPL BITMAP header.
     *
     * @return A ByteArray ready to be sent to the printer.
     */
    fun createTsplBitmapCommand(bitmap: Bitmap): ByteArray {
        // --- 1. Flatten image on a white background ---
        val flattenedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flattenedBitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        // --- 2. Scale image to 384px width ---
        val printWidth = 384
        val aspectRatio = flattenedBitmap.width.toFloat() / flattenedBitmap.height.toFloat()
        val printHeight = (printWidth / aspectRatio).roundToInt().coerceAtMost(220) // Cap height
        val scaledBitmap = Bitmap.createScaledBitmap(flattenedBitmap, printWidth, printHeight, true)

        // --- 3. Convert to dithered monochrome ---
        val width = scaledBitmap.width
        val height = scaledBitmap.height
        val pixels = IntArray(width * height)
        scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val grayPixels = FloatArray(width * height)
        for (i in pixels.indices) {
            val color = pixels[i]
            // Standard NTSC conversion to grayscale
            grayPixels[i] = (Color.red(color) * 0.299f + Color.green(color) * 0.587f + Color.blue(color) * 0.114f)
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val oldPixel = grayPixels[index]
                val newPixel = if (oldPixel < 128) 0f else 255f
                pixels[index] = if (newPixel == 0f) Color.BLACK else Color.WHITE
                val quantError = oldPixel - newPixel

                if (x + 1 < width) grayPixels[index + 1] += quantError * 7 / 16
                if (x > 0 && y + 1 < height) grayPixels[index - 1 + width] += quantError * 3 / 16
                if (y + 1 < height) grayPixels[index + width] += quantError * 5 / 16
                if (x + 1 < width && y + 1 < height) grayPixels[index + 1 + width] += quantError * 1 / 16
            }
        }

        // --- 4. Pack 8 pixels into each byte ---
        val widthBytes = (width + 7) / 8
        val bitmapData = ByteArray(widthBytes * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixelColor = pixels[y * width + x]
                if (pixelColor == Color.BLACK) { // If pixel is black, set the bit
                    val byteIndex = y * widthBytes + x / 8
                    val bitIndex = 7 - (x % 8)
                    bitmapData[byteIndex] = (bitmapData[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                }
            }
        }
        
        // --- 5. Prepend header and return ---
        val commandHeader = "BITMAP 0,0,$widthBytes,$height,0,".toByteArray(Charsets.US_ASCII)
        return commandHeader + bitmapData
    }

    // --- Standard TSPL Commands ---
    fun getPrintCommand(copies: Int = 1): String = "PRINT 1,$copies\n"
    fun getFormFeedCommand(): String = "FORMFEED\n"
    fun getGapDetectCommand(): String = "GAPDETECT\n"
    fun getClsCommand(): String = "CLS\n"
    fun getFeedCommand(dots: Int): String = "FEED $dots\n"
    fun getDensityCommand(density: Int): String = "DENSITY $density\n"
    fun getSpeedCommand(speed: Int): String = "SPEED $speed\n"
    fun getDirectionCommand(direction: Int): String = "DIRECTION $direction\n"
    fun getReferenceCommand(x: Int, y: Int): String = "REFERENCE $x,$y\n"
    fun getSelfTestCommand(): String = "SELFTEST\n"
    fun getStatusCommand(): String = "?STATUS\n"
    fun getVersionCommand(): String = "?VERSION\n"
}
