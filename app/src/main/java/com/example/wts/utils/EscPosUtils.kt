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

    private const val ESC: Byte = 0x1B
    private const val GS: Byte = 0x1D

    private const val RECEIPT_WIDTH_DOTS = 384 // Full 58mm thermal width

    // ===========================
    // BASIC COMMANDS
    // ===========================

    fun getInitCommands(): ByteArray {
        return byteArrayOf(ESC, 0x40)
    }

    fun getPrintAndFeed(lines: Int): ByteArray {
        return byteArrayOf(ESC, 0x64, lines.toByte())
    }

    fun getCutCommand(): ByteArray {
        return byteArrayOf(GS, 0x56, 0x41, 0x00)
    }

    fun getSetJustification(align: Byte): ByteArray {
        return byteArrayOf(ESC, 0x61, align)
    }

    fun getSelectFont(font: Byte): ByteArray {
        return byteArrayOf(ESC, 0x4D, font)
    }

    fun getSetTextSize(w: Byte, h: Byte): ByteArray {
        val size = ((w.toInt() and 0x0F) shl 4) or (h.toInt() and 0x0F)
        return byteArrayOf(GS, 0x21, size.toByte())
    }

    fun getSetBold(on: Boolean): ByteArray {
        return byteArrayOf(ESC, 0x45, if (on) 1 else 0)
    }

    // ===========================
    // IMAGE PRINTING
    // ===========================

    fun createImageCommand(bitmap: Bitmap): ByteArray {
        val ratio = bitmap.height.toFloat() / bitmap.width.toFloat()
        val targetHeight = (RECEIPT_WIDTH_DOTS * ratio).roundToInt()

        val scaled = Bitmap.createScaledBitmap(bitmap, RECEIPT_WIDTH_DOTS, targetHeight, true)

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

                        if (luminance < 140) {
                            slice = slice or (1 shl (7 - bit))
                        }
                    }
                }

                baos.write(slice)
            }
        }

        return baos.toByteArray()
    }

    // ===========================
    // MONEY FORMATTER
    // ===========================

    private fun formatMoney(value: String): String {
        val clean = value.replace("₹", "").replace(",", "").trim()
        val num = clean.toDoubleOrNull() ?: return value
        return "₹" + "%, .0f".format(num).trim()
    }

    // ===========================
    // DEFAULT RECEIPT
    // (Your original untouched method)
    // ===========================

    fun formatReceipt(receipt: ReceiptData): ByteArray {
        val stream = ByteArrayOutputStream()

        stream.write(getInitCommands())

        stream.write(getSetJustification(1))
        stream.write("Bhootiyа Fabric\n".toByteArray())
        stream.write("Collection\n".toByteArray())
        stream.write("Moti Ganj, Bakebar Road, Bharthana\n".toByteArray())
        stream.write("Ph: +91 82736 89065\n".toByteArray())
        stream.write("------------------------------------------\n".toByteArray())

        stream.write(getSetJustification(0))
        stream.write("Invoice: ${receipt.invoiceNumber}\n".toByteArray())
        stream.write("Customer: ${receipt.customerName}\n".toByteArray())
        stream.write("Phone: ${receipt.customerPhone}\n".toByteArray())

        if (receipt.date != "Invalid Date") {
            stream.write("Date: ${receipt.date}   Time: ${receipt.time}\n".toByteArray())
        }

        stream.write("------------------------------------------\n".toByteArray())

        val itemHeader = String.format("%-20s %4s %7s %7s", "Item", "Qty", "Rate", "Amt")
        stream.write(itemHeader.toByteArray())
        stream.write("\n".toByteArray())
        stream.write("------------------------------------------\n".toByteArray())

        receipt.items.forEach { item ->
            val name = item.name.take(20).padEnd(20, ' ')
            val qty = item.qty.toString().padStart(4, ' ')
            val rate = formatMoney(item.price).padStart(7, ' ')
            val amt = formatMoney(item.total).padStart(7, ' ')
            stream.write("$name $qty $rate $amt\n".toByteArray())
        }

        stream.write("------------------------------------------\n".toByteArray())

        stream.write(getSetJustification(0))
        stream.write("Subtotal: ${formatMoney(receipt.subtotal)}\n".toByteArray())

        if ((receipt.discount.toDoubleOrNull() ?: 0.0) > 0.0) {
            stream.write("Discount: ₹${receipt.discount}\n".toByteArray())
        }

        stream.write(getSetBold(true))
        stream.write("Total: ${formatMoney(receipt.total)}\n".toByteArray())
        stream.write(getSetBold(false))

        stream.write("Payment: ${receipt.paymentMethod}\n".toByteArray())

        // Barcode
        if (receipt.barcode.isNotBlank()) {
            try {
                val pure = receipt.barcode.substringAfter(",")
                val bytes = Base64.decode(pure, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    stream.write(getSetJustification(1))
                    stream.write(createImageCommand(bitmap))
                    stream.write("${receipt.invoiceNumber}\n".toByteArray())
                }
            } catch (_: Exception) { }
        }

        stream.write("\n".toByteArray())
        stream.write(getSetJustification(1))
        stream.write(getSetTextSize(1, 1))
        stream.write("Thank you, Visit Again!\n".toByteArray())

        stream.write(getCutCommand())
        return stream.toByteArray()
    }

    // =======================================================
    // ** NEW STYLE A — IMAGE-LIKE (BIG CLEAN SPACED) **
    // =======================================================

    fun formatReceiptA(receipt: ReceiptData): ByteArray {
        val s = ByteArrayOutputStream()

        s.write(getInitCommands())

        s.write(getSetJustification(1))
        s.write(getSetTextSize(1, 1))
        s.write("Bhootiya Fabric\n".toByteArray())
        s.write("Collection\n".toByteArray())

        s.write(getSetTextSize(0, 0))
        s.write("Moti Ganj, Bakebar Road, Bharthana\n".toByteArray())
        s.write("Ph: +91 82736 89065\n".toByteArray())

        s.write("────────────────────────────────────────\n".toByteArray())

        s.write(getSetJustification(0))
        s.write("Invoice: ${receipt.invoiceNumber}\n".toByteArray())
        s.write("Customer: ${receipt.customerName}\n".toByteArray())
        s.write("Phone: ${receipt.customerPhone}\n".toByteArray())
        s.write("Date: ${receipt.date}  Time: ${receipt.time}\n".toByteArray())

        s.write("────────────────────────────────────────\n".toByteArray())

        val header = String.format("%-20s %4s %7s %7s", "Item", "Qty", "Rate", "Amt")
        s.write(header.toByteArray())
        s.write("\n".toByteArray())

        s.write("────────────────────────────────────────\n".toByteArray())

        receipt.items.forEach {
            val name = it.name.take(20).padEnd(20, ' ')
            val qty = it.qty.toString().padStart(4, ' ')
            val rate = formatMoney(it.price).padStart(7, ' ')
            val amt = formatMoney(it.total).padStart(7, ' ')
            s.write("$name $qty $rate $amt\n".toByteArray())
        }

        s.write("────────────────────────────────────────\n".toByteArray())

        s.write("Subtotal: ${formatMoney(receipt.subtotal)}\n".toByteArray())
        if ((receipt.discount.toDoubleOrNull() ?: 0.0) > 0.0) {
            s.write("Discount: ₹${receipt.discount}\n".toByteArray())
        }

        s.write(getSetBold(true))
        s.write("Total: ${formatMoney(receipt.total)}\n".toByteArray())
        s.write(getSetBold(false))

        s.write("Payment: ${receipt.paymentMethod}\n".toByteArray())

        s.write(getSetJustification(1))
        s.write(getSetTextSize(1, 1))
        s.write("Thank you, Visit Again!\n".toByteArray())

        s.write(getCutCommand())
        return s.toByteArray()
    }

    // =======================================================
    // ** NEW STYLE B — COMPACT SHOP (SMALL + TIGHT) **
    // =======================================================

    fun formatReceiptB(receipt: ReceiptData): ByteArray {
        val s = ByteArrayOutputStream()
        s.write(getInitCommands())

        s.write(getSetJustification(1))
        s.write("Bhootiya Fabric\n".toByteArray())

        s.write(getSetJustification(0))
        s.write("Invoice:${receipt.invoiceNumber}\n".toByteArray())
        s.write("Name:${receipt.customerName}\n".toByteArray())
        s.write("Phone:${receipt.customerPhone}\n".toByteArray())
        s.write("Date:${receipt.date} ${receipt.time}\n".toByteArray())

        s.write("--------------------------------\n".toByteArray())
        s.write("Item         QTY  RT   AMT\n".toByteArray())
        s.write("--------------------------------\n".toByteArray())

        receipt.items.forEach {
            val name = it.name.take(12).padEnd(12, ' ')
            val qty = it.qty.toString().padStart(3, ' ')
            val rate = formatMoney(it.price).padStart(5, ' ')
            val amt = formatMoney(it.total).padStart(6, ' ')
            s.write("$name $qty $rate $amt\n".toByteArray())
        }

        s.write("--------------------------------\n".toByteArray())

        s.write("Subtotal: ${formatMoney(receipt.subtotal)}\n".toByteArray())
        s.write("Total: ${formatMoney(receipt.total)}\n".toByteArray())

        s.write(getSetJustification(1))
        s.write("Thank you!\n".toByteArray())

        s.write(getCutCommand())
        return s.toByteArray()
    }

    // =======================================================
    // ** NEW STYLE C — MODERN CLEAN + BOLD LABELS **
    // =======================================================

    fun formatReceiptC(receipt: ReceiptData): ByteArray {
        val s = ByteArrayOutputStream()
        s.write(getInitCommands())

        s.write(getSetJustification(1))
        s.write(getSetBold(true))
        s.write("Bhootiya Fabric\n".toByteArray())
        s.write(getSetBold(false))
        s.write("Collection\n".toByteArray())

        s.write(getSetJustification(0))
        s.write("------------------------------------------------\n".toByteArray())
        s.write("Invoice:  ${receipt.invoiceNumber}\n".toByteArray())
        s.write("Customer: ${receipt.customerName}\n".toByteArray())
        s.write("Phone:    ${receipt.customerPhone}\n".toByteArray())
        s.write("Date:     ${receipt.date}   ${receipt.time}\n".toByteArray())
        s.write("------------------------------------------------\n".toByteArray())

        s.write("Item                Qty   Rate   Amt\n".toByteArray())
        s.write("------------------------------------------------\n".toByteArray())

        receipt.items.forEach {
            val name = it.name.take(16).padEnd(16, ' ')
            val qty = it.qty.toString().padStart(3)
            val rate = formatMoney(it.price).padStart(6)
            val amt = formatMoney(it.total).padStart(6)
            s.write("$name $qty  $rate  $amt\n".toByteArray())
        }

        s.write("------------------------------------------------\n".toByteArray())

        s.write("Subtotal: ${formatMoney(receipt.subtotal)}\n".toByteArray())
        s.write("Total:    ${formatMoney(receipt.total)}\n".toByteArray())
        s.write("Payment:  ${receipt.paymentMethod}\n".toByteArray())

        s.write(getSetJustification(1))
        s.write("Thank you, Visit Again!\n".toByteArray())

        s.write(getCutCommand())
        return s.toByteArray()
    }
}
