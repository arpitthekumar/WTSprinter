package com.example.wts.ui.receipt

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import com.example.wts.model.ReceiptData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReceiptPreviewGenerator {

    private const val PREVIEW_WIDTH = 384 // For 58mm thermal paper

    fun generatePreview(receipt: ReceiptData): Bitmap {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        // Create a bitmap with a large height; we'll crop it later.
        val tempBitmap = Bitmap.createBitmap(PREVIEW_WIDTH, 4000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tempBitmap)
        canvas.drawColor(Color.WHITE)

        var yPos = 0f

        fun drawText(text: String, alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, isBold: Boolean = false) {
            paint.isFakeBoldText = isBold
            val textLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, PREVIEW_WIDTH)
                .setAlignment(alignment)
                .setLineSpacing(0f, 1.0f)
                .setIncludePad(false)
                .build()

            canvas.save()
            canvas.translate(0f, yPos)
            textLayout.draw(canvas)
            canvas.restore()
            yPos += textLayout.height
        }

        fun drawSpacer(height: Int) {
            yPos += height
        }

        // --- Start Drawing Receipt --- //

        drawText("Bhootiya Fabric", Layout.Alignment.ALIGN_CENTER, true)
        drawText("Collection", Layout.Alignment.ALIGN_CENTER)
        drawText("Moti Ganj, Bakebar Road, Bharthana", Layout.Alignment.ALIGN_CENTER)
        drawText("Ph: +91 82736 89065", Layout.Alignment.ALIGN_CENTER)
        drawSpacer(10)

        val date = if (receipt.date.isNotBlank()) receipt.date else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val time = if (receipt.time.isNotBlank()) receipt.time else SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

        drawText("Invoice: ${receipt.invoiceNumber}")
        drawText("Customer: ${receipt.customerName}")
        drawText("Phone: ${receipt.customerPhone}")
        drawText("Date: $date Time: $time")

        drawText("- - - - - - - - - - - - - - - - - - - -", Layout.Alignment.ALIGN_CENTER)

        // Items header
        drawText(String.format("%-21s %4s %7s %7s", "Item", "Qty", "Rate", "Amt"), isBold = true)
        drawText("- - - - - - - - - - - - - - - - - - - -", Layout.Alignment.ALIGN_CENTER)

        receipt.items.forEach { item ->
            val itemName = if (item.name.length > 21) item.name.substring(0, 21) else item.name
            val line = String.format("%-21s %4s %7s %7s", itemName, item.qty, "₹${item.price}", "₹${item.total}")
            drawText(line)
        }

        drawText("- - - - - - - - - - - - - - - - - - - -", Layout.Alignment.ALIGN_CENTER)

        // Totals
        drawText(String.format("%33s %7s", "Subtotal:", "₹${receipt.subtotal}"))
        if ((receipt.discount.toDoubleOrNull() ?: 0.0) > 0.0) {
            drawText(String.format("%33s %7s", "Discount:", "₹${receipt.discount}"))
        }
        drawText(String.format("%33s %7s", "Total:", "₹${receipt.total}"), isBold = true)
        drawSpacer(10)

        drawText("Payment: ${receipt.paymentMethod}")
        drawSpacer(15)

        // Barcode
        if (receipt.barcode.isNotBlank()) {
            try {
                val pure = receipt.barcode.substringAfter(",")
                val bytes = Base64.decode(pure, Base64.DEFAULT)
                val barcodeBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (barcodeBitmap != null) {
                    val ratio = barcodeBitmap.height.toFloat() / barcodeBitmap.width.toFloat()
                    val scaledHeight = (PREVIEW_WIDTH * ratio).toInt()
                    val scaledBarcode = Bitmap.createScaledBitmap(barcodeBitmap, PREVIEW_WIDTH, scaledHeight, true)
                    canvas.drawBitmap(scaledBarcode, 0f, yPos, null)
                    yPos += scaledBarcode.height
                    drawSpacer(5)
                    drawText(receipt.invoiceNumber, Layout.Alignment.ALIGN_CENTER)
                }
            } catch (e: Exception) { /* Fails silently */ }
        }

        drawSpacer(15)
        drawText("Thank you, Visit Again!", Layout.Alignment.ALIGN_CENTER, true)

        // --- End Drawing, Crop and Return --- //
        val finalHeight = yPos.toInt()
        val finalBitmap = Bitmap.createBitmap(tempBitmap, 0, 0, PREVIEW_WIDTH, finalHeight)
        tempBitmap.recycle()

        return finalBitmap
    }
}