package com.example.wts.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

object EscPosUtils {
    // Command constants
    private const val ESC: Byte = 0x1B
    private const val GS: Byte = 0x1D

    // Standard receipt paper width is 58mm, but we use 50mm for content to leave margins.
    // Most thermal printers are 203 DPI (8 dots/mm).
    private const val RECEIPT_WIDTH_MM = 50
    private const val DOTS_PER_MM = 8
    private const val MAX_DOTS_PER_LINE = RECEIPT_WIDTH_MM * DOTS_PER_MM

    fun getInitCommands(): ByteArray {
        return byteArrayOf(
            ESC, 0x40 // Initialize printer
        )
    }

    fun getPrintAndFeed(lines: Int): ByteArray {
        return byteArrayOf(ESC, 0x64, lines.toByte()) // Print and feed n lines
    }

    fun getCutCommand(): ByteArray {
        return byteArrayOf(GS, 0x56, 0x41, 0x00) // Full cut
    }

    fun getSetJustification(justification: Byte): ByteArray {
        return byteArrayOf(ESC, 0x61, justification)
    }

    fun getSetTextSize(width: Byte, height: Byte): ByteArray {
        val size = ((width.toInt() and 0x0F) shl 4) or (height.toInt() and 0x0F)
        return byteArrayOf(GS, 0x21, size.toByte())
    }

    fun getBarcodeCommand(code: String, type: Byte = 0x45, height: Int = 100): ByteArray {
        val codeBytes = code.toByteArray(Charsets.US_ASCII)
        return byteArrayOf(
            GS, 0x68, height.toByte(), // Set barcode height
            GS, 0x77, 2, // Set barcode width
            GS, 0x6B, type, codeBytes.size.toByte(),
            *codeBytes
        )
    }

    /**
     * Creates the ESC/POS command to print a bitmap, scaled to fit a 50mm width.
     */
    fun createImageCommand(bitmap: Bitmap): ByteArray {
        // Scale the bitmap to the target width while maintaining aspect ratio.
        val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
        val targetHeight = (MAX_DOTS_PER_LINE * aspectRatio).roundToInt()
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, MAX_DOTS_PER_LINE, targetHeight, true)

        // Convert to monochrome and get the command bytes.
        return convertToMonochromeCommand(scaledBitmap)
    }

    /**
     * Converts a bitmap into the ESC/POS commands for printing a monochrome image.
     * This uses the "GS v 0" command, which is common for modern printers.
     */
    private fun convertToMonochromeCommand(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()

        // Flatten transparency to white
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val width = bitmap.width
        val height = bitmap.height
        val widthInBytes = (width + 7) / 8

        // Command header: GS v 0
        // p1 = 0 (mode)
        // p2, p3 = width in bytes
        // p4, p5 = height in dots
        stream.write(byteArrayOf(GS, 0x76, 0x30, 0, widthInBytes.toByte(), (widthInBytes / 256).toByte(), height.toByte(), (height / 256).toByte()))

        // Get the pixel data
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Generate the byte array for the image data
        for (y in 0 until height) {
            for (x in 0 until widthInBytes) {
                var slice: Byte = 0
                for (b in 0..7) {
                    val pixelX = x * 8 + b
                    if (pixelX < width) {
                        val pixel = pixels[y * width + pixelX]
                        val luminance = (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)).toInt()
                        if (luminance < 128) {
                            slice = (slice.toInt() or (1 shl (7 - b))).toByte()
                        }
                    }
                }
                stream.write(slice.toInt())
            }
        }
        return stream.toByteArray()
    }
}
