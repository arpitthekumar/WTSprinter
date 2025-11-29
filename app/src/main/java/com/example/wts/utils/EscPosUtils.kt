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

    fun createImageCommand(bitmap: Bitmap): ByteArray {
        val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
        val targetHeight = (MAX_DOTS_PER_LINE * aspectRatio).roundToInt()
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, MAX_DOTS_PER_LINE, targetHeight, true)
        return convertToMonochromeCommand(scaledBitmap)
    }

    private fun convertToMonochromeCommand(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val width = bitmap.width
        val height = bitmap.height
        val widthInBytes = (width + 7) / 8

        stream.write(byteArrayOf(GS, 0x76, 0x30, 0, widthInBytes.toByte(), (widthInBytes ushr 8).toByte(), height.toByte(), (height ushr 8).toByte()))

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

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

    fun formatReceipt(receipt: ReceiptData): ByteArray {
        val stream = ByteArrayOutputStream()

        // Initialize
        stream.write(getInitCommands())

        // Header
        stream.write(getSetJustification(1)) // Center
        stream.write(getSetTextSize(1, 1))   // Double size
        stream.write("Bhootiya Fabric Collection\n".toByteArray())
        stream.write(getSetTextSize(0, 0))   // Normal size
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
        stream.write("--------------------------------\n".toByteArray())

        // Items
        val header = String.format("%-16s%4s%6s%6s", "Item", "Qty", "Rate", "Amt")
        stream.write(header.toByteArray())
        stream.write("\n".toByteArray())
        stream.write("--------------------------------\n".toByteArray())

        receipt.items.forEach { item ->
            val namePart = item.name.padEnd(16, ' ').substring(0, 16)
            val qtyPart = item.qty.toString().padStart(4, ' ')
            val ratePart = item.price.padStart(6, ' ')
            val amtPart = item.total.padStart(6, ' ')
            val itemLine = "$namePart$qtyPart$ratePart$amtPart\n"
            stream.write(itemLine.toByteArray())
        }
        stream.write("--------------------------------\n".toByteArray())

        // Totals
        stream.write(getSetJustification(2)) // Right
        stream.write(String.format("%16s %15s\n", "Subtotal:", receipt.subtotal).toByteArray())
        stream.write(String.format("%16s %15s\n", "Discount:", receipt.discount).toByteArray())
        stream.write(getSetTextSize(0, 1)) // Double height for bold effect
        stream.write(String.format("%16s %15s\n", "Total:", receipt.total).toByteArray())
        stream.write(getSetTextSize(0, 0)) // Reset to normal size
        stream.write("\n".toByteArray())

        // Payment Method
        stream.write(getSetJustification(0)) // Left
        stream.write("Payment: ${receipt.paymentMethod}\n\n".toByteArray())

        // Barcode
        stream.write(getSetJustification(1)) // Center
        val pureBase64 = receipt.barcode.substringAfter(",")
        val bytes = Base64.decode(pureBase64, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        stream.write(createImageCommand(bitmap))
        stream.write("\n".toByteArray())
        stream.write("${receipt.invoiceNumber}\n".toByteArray())
        stream.write("\n".toByteArray())

        // Footer
        stream.write(getSetJustification(1)) // Center
        stream.write("Thank you, Visit Again!\n".toByteArray())

        // Feed and cut
        stream.write(getPrintAndFeed(5))
        stream.write(getCutCommand())

        return stream.toByteArray()
    }
}
