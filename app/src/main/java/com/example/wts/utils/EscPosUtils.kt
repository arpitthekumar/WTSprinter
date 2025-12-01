package com.example.wts.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.wts.model.ReceiptData
import java.io.ByteArrayOutputStream

object EscPosUtils {
    private const val ESC: Byte = 0x1B
    private const val GS: Byte = 0x1D

    fun getInitCommands(): ByteArray = byteArrayOf(ESC, 0x40)
    fun getPrintAndFeed(lines: Int): ByteArray = byteArrayOf(ESC, 0x64, lines.toByte())
    fun getCutCommand(): ByteArray = byteArrayOf(GS, 0x56, 0x41, 0x00)
    fun getSetJustification(j: Byte): ByteArray = byteArrayOf(ESC, 0x61, j)
    fun getSelectFont(font: Byte): ByteArray = byteArrayOf(ESC, 0x4D, font) // 0=A, 1=B

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

    fun formatReceipt(receipt: ReceiptData): ByteArray {
        val stream = ByteArrayOutputStream()
        // Font B on a 58mm printer is ~42 characters wide.
        val separator = "__________________________________________\n"

        // Initialize printer and select Font B
        stream.write(getInitCommands())
        stream.write(getSelectFont(1))

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
        if (receipt.date.isNotBlank() && receipt.time.isNotBlank()) {
            stream.write("Date: ${receipt.date} Time: ${receipt.time}\n".toByteArray())
        }
        stream.write(separator.toByteArray())

        // Items Header - 42 chars total: Item (22) Qty (4) Rate (8) Amt (8)
        val itemHeader = String.format("%-22s%4s%8s%8s\n", "Item", "Qty", "Rate", "Amt")
        stream.write(itemHeader.toByteArray())
        stream.write(separator.toByteArray())

        // Items
        receipt.items.forEach { item ->
            val itemName = if (item.name.length > 22) item.name.substring(0, 22) else item.name
            val itemLine = String.format("%-22s%4d%8s%8s\n", itemName, item.qty, "₹${item.price}", "₹${item.total}")
            stream.write(itemLine.toByteArray())
        }
        stream.write(separator.toByteArray())

        // Totals - Right aligned
        stream.write(getSetJustification(2)) // Right
        stream.write(String.format("Subtotal: %12s\n", "₹${receipt.subtotal}").toByteArray())
        if ((receipt.discount.toDoubleOrNull() ?: 0.0) > 0.0) {
            stream.write(String.format("Discount: %12s\n", "₹${receipt.discount}").toByteArray())
        }
        stream.write(String.format("Total: %12s\n", "₹${receipt.total}").toByteArray())
        stream.write(getPrintAndFeed(1))

        // Payment Method
        stream.write(getSetJustification(0)) // Left
        stream.write("Payment: ${receipt.paymentMethod}\n\n".toByteArray())

        // Barcode (as image)
        if (receipt.barcode.isNotBlank()) {
            try {
                stream.write(getSetJustification(1)) // Center barcode
                val pure = receipt.barcode.substringAfter(",")
                val bytes = Base64.decode(pure, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    stream.write(createImageCommand(bitmap))
                    stream.write("${receipt.invoiceNumber}\n".toByteArray())
                }
            } catch (e: Exception) { /* Fails Silently */ }
        }
        stream.write(getPrintAndFeed(1))

        // Footer
        stream.write(getSetJustification(1)) // Center
        stream.write("Thank you, Visit Again!\n".toByteArray())

        // Reset font, feed, and cut
        stream.write(getSelectFont(0))
        stream.write(getPrintAndFeed(2))
        stream.write(getCutCommand())

        return stream.toByteArray()
    }
}
