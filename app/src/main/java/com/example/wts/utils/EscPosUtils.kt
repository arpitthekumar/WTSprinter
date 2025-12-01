package com.example.wts.utils

import android.graphics.Bitmap
import com.example.wts.model.ReceiptData
import com.example.wts.ui.receipt.ReceiptPreviewGenerator
import java.io.ByteArrayOutputStream

object EscPosUtils {
    // Command constants
    private const val ESC: Byte = 0x1B
    private const val GS: Byte = 0x1D

    fun getInitCommands(): ByteArray {
        return byteArrayOf(ESC, 0x40) // Initialize printer
    }

    fun getPrintAndFeed(lines: Int): ByteArray {
        return byteArrayOf(ESC, 0x64, lines.toByte()) // Print and feed n lines
    }

    fun getCutCommand(): ByteArray {
        return byteArrayOf(GS, 0x56, 0x41, 0x00) // Full cut
    }

    fun formatReceipt(receipt: ReceiptData): ByteArray {
        val stream = ByteArrayOutputStream()
        val receiptBitmap = ReceiptPreviewGenerator.generatePreview(receipt)

        // Initialize
        stream.write(getInitCommands())
        // Convert bitmap to printer commands
        stream.write(createImageCommand(receiptBitmap))
        // Feed and cut
        stream.write(getPrintAndFeed(2))
        stream.write(getCutCommand())

        return stream.toByteArray()
    }

    fun createImageCommand(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val widthInBytes = (width + 7) / 8
        val command = byteArrayOf(0x1D, 0x76, 0x30, 0x00, (widthInBytes % 256).toByte(), (widthInBytes / 256).toByte(), (height % 256).toByte(), (height / 256).toByte())

        val stream = ByteArrayOutputStream()
        stream.write(command)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (xByte in 0 until widthInBytes) {
                var slice: Byte = 0
                for (bit in 0..7) {
                    val x = xByte * 8 + bit
                    if (x < width) {
                        val pixel = pixels[y * width + x]
                        val luminance = (0.299 * (pixel shr 16 and 0xff) + 0.587 * (pixel shr 8 and 0xff) + 0.114 * (pixel and 0xff)).toInt()
                        if (luminance < 128) {
                            slice = (slice.toInt() or (1 shl (7 - bit))).toByte()
                        }
                    }
                }
                stream.write(slice.toInt())
            }
        }

        return stream.toByteArray()
    }
}
