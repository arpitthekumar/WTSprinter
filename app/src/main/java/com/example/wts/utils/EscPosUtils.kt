package com.example.wts.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import com.example.wts.model.ReceiptData
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

object EscPosUtils {
    // Command constants
    private const val ESC: Byte = 0x1B
    private const val GS: Byte = 0x1D

    // Standard receipt paper width is 58mm, but we use 48mm for content to leave margins.
    // Most thermal printers are 203 DPI (8 dots/mm).
    private const val RECEIPT_WIDTH_MM = 48
    private const val DOTS_PER_MM = 8
    private const val MAX_DOTS_PER_LINE = RECEIPT_WIDTH_MM * DOTS_PER_MM

    fun getInitCommands(): ByteArray {
        return byteArrayOf(ESC, 0x40) // Initialize printer
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

    fun getSelectFont(font: Byte): ByteArray {
        return byteArrayOf(ESC, 0x4D, font)
    }
    private const val RECEIPT_WIDTH_DOTS = 384 // fixed width for 58mm printers

    fun createImageCommand(bitmap: Bitmap): ByteArray {
        // scale with correct aspect ratio
        val ratio = bitmap.height.toFloat() / bitmap.width.toFloat()
        val targetHeight = (RECEIPT_WIDTH_DOTS * ratio).roundToInt()

        val scaled = Bitmap.createScaledBitmap(bitmap, RECEIPT_WIDTH_DOTS, targetHeight, true)

        // convert to pure white background always
        val cleanBitmap = Bitmap.createBitmap(RECEIPT_WIDTH_DOTS, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cleanBitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(scaled, 0f, 0f, null)

        return convertToMonochromeCommand(cleanBitmap)
    }

    private fun convertToMonochromeCommand(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8

        val baos = ByteArrayOutputStream()

        // ESC/POS Header for raster printing
        baos.write(
            byteArrayOf(
                0x1D, 0x76, 0x30, 0x00,
                (widthBytes and 0xFF).toByte(),
                ((widthBytes shr 8) and 0xFF).toByte(),
                (height and 0xFF).toByte(),
                ((height shr 8) and 0xFF).toByte()
            )
        )

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (xByte in 0 until widthBytes) {
                var slice = 0

                for (bit in 0 until 8) {
                    val x = xByte * 8 + bit
                    if (x < width) {
                        val pixel = pixels[y * width + x]

                        val luminance =
                            (0.3 * Color.red(pixel) +
                                    0.59 * Color.green(pixel) +
                                    0.11 * Color.blue(pixel))

                        if (luminance < 140) {   // BETTER THRESHOLD
                            slice = slice or (1 shl (7 - bit))
                        }
                    }
                }

                baos.write(slice)
            }
        }

        return baos.toByteArray()
    }

    fun formatReceipt(receipt: ReceiptData): ByteArray {
        val stream = ByteArrayOutputStream()

        // Initialize
        stream.write(getInitCommands())
        stream.write(getSelectFont(1)) // Select Font B (smaller)

        // Header
        stream.write(getSetJustification(1)) // Center
        stream.write("Bhootiya Fabric\n".toByteArray())
        stream.write("Collection\n".toByteArray())
        stream.write("Moti Ganj, Bakebar Road, Bharthana\n".toByteArray())
        stream.write("Ph: +91 82736 89065\n".toByteArray())
        stream.write(getPrintAndFeed(1))

        // Customer Info
        stream.write(getSetJustification(0)) // Left
        stream.write("Invoice: ${receipt.invoiceNumber}\n".toByteArray())
        stream.write("Customer: ${receipt.customerName}\n".toByteArray())
        stream.write("Phone: ${receipt.customerPhone}\n".toByteArray())
        if (receipt.date != "Invalid Date" && receipt.time != "Invalid Date") {
            stream.write("Date: ${receipt.date} Time: ${receipt.time}\n".toByteArray())
        }
        stream.write("------------------------------------------\n".toByteArray())

        // Items
        val header = String.format("%-24s%6s%6s%6s", "Item", "Qty", "Rate", "Amt")
        stream.write(header.toByteArray())
        stream.write("\n".toByteArray())
        stream.write("------------------------------------------\n".toByteArray())

        receipt.items.forEach { item ->
            val namePart = item.name.padEnd(24, ' ').take(24)
            val qtyPart = item.qty.toString().padStart(6, ' ')
            val ratePart = item.price.padStart(6, ' ')
            val amtPart = item.total.padStart(6, ' ')
            val itemLine = "$namePart$qtyPart$ratePart$amtPart\n"
            stream.write(itemLine.toByteArray())
        }
        stream.write("------------------------------------------\n".toByteArray())

        // Totals
        stream.write(getSetJustification(2)) // Right
        stream.write(String.format("%26s %15s\n", "Subtotal:", receipt.subtotal).toByteArray())
        if (safeAmount(receipt.discount) > 0.0) {
            stream.write(String.format("%26s %15s\n", "Discount:", receipt.discount).toByteArray())
        }
        stream.write(String.format("%26s %15s\n", "Total:", receipt.total).toByteArray())
        stream.write("\n".toByteArray())

        // Payment Method
        stream.write(getSetJustification(0)) // Left
        stream.write("Payment: ${receipt.paymentMethod}\n\n".toByteArray())

        // Barcode
        if (receipt.barcode.isNotBlank()) {
            stream.write(getSetJustification(1)) // Center
            val pureBase64 = receipt.barcode.substringAfter(",")
            val bytes = Base64.decode(pureBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                stream.write(createImageCommand(bitmap))
                stream.write("${receipt.invoiceNumber}\n".toByteArray()) // Invoice number with one newline
            }
        }

        stream.write("\n".toByteArray()) // One blank line for spacing

        // Footer
        stream.write(getSetJustification(1)) // Center
        stream.write("Thank you, Visit Again!\n".toByteArray())

        // Reset font and cut
        stream.write(getSelectFont(0)) // Reset to Font A
        stream.write(getPrintAndFeed(1)) // Feed only ONE line
        stream.write(getCutCommand())

        return stream.toByteArray()
    }

    private fun safeAmount(v: String): Double = v.replace("₹", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0
}
