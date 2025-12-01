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

    // Standard 384 dot width for 58mm printers (48mm printable area)
    private const val PREVIEW_WIDTH = 384
    private const val MARGIN = 10

    fun generatePreview(receipt: ReceiptData): Bitmap {
        val contentWidth = PREVIEW_WIDTH - (2 * MARGIN)

        // --- Paints to mimic ESC/POS Font B (~42 chars) ---
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 15f // Carefully chosen to mimic 42 chars/line
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        val bodyBoldPaint = TextPaint(bodyPaint).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        val titlePaint = TextPaint(bodyBoldPaint).apply {
            textSize = 22f
        }

        // --- Dynamic Height Canvas ---
        // We draw on a large bitmap first, then crop it to the actual used height.
        val tempBitmap = Bitmap.createBitmap(PREVIEW_WIDTH, 8000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tempBitmap)
        canvas.drawColor(Color.WHITE)

        var yPos = MARGIN.toFloat()

        fun drawText(text: String, paint: TextPaint, alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) {
            val textLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
                .setAlignment(alignment)
                .setLineSpacing(0f, 1.0f)
                .setIncludePad(false)
                .build()
            canvas.save()
            canvas.translate(MARGIN.toFloat(), yPos)
            textLayout.draw(canvas)
            canvas.restore()
            yPos += textLayout.height
        }

        fun drawSpacer(height: Int) {
            yPos += height
        }

        // --- Start Drawing Receipt --- //

        drawText("Bhootiya Fabric", titlePaint, Layout.Alignment.ALIGN_CENTER)
        drawText("Collection", bodyPaint, Layout.Alignment.ALIGN_CENTER)
        drawText("Moti Ganj, Bakebar Road, Bharthana", bodyPaint, Layout.Alignment.ALIGN_CENTER)
        drawText("Ph: +91 82736 89065", bodyPaint, Layout.Alignment.ALIGN_CENTER)
        drawSpacer(10)

        val date = if (receipt.date.isNotBlank()) receipt.date else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val time = if (receipt.time.isNotBlank()) receipt.time else SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

        drawText("Invoice: ${receipt.invoiceNumber}", bodyPaint)
        drawText("Customer: ${receipt.customerName}", bodyPaint)
        drawText("Phone: ${receipt.customerPhone}", bodyPaint)
        drawText("Date: $date Time: $time", bodyPaint)

        val separator = "__________________________________________"
        drawText(separator, bodyPaint, Layout.Alignment.ALIGN_CENTER)

        // 42 chars total: Item (22) Qty (4) Rate (8) Amt (8)
        val headerLine = String.format("%-22s%4s%8s%8s", "Item", "Qty", "Rate", "Amt")
        drawText(headerLine, bodyBoldPaint)
        drawText(separator, bodyPaint, Layout.Alignment.ALIGN_CENTER)

        receipt.items.forEach { item ->
            val itemName = if (item.name.length > 22) item.name.substring(0, 22) else item.name
            val line = String.format("%-22s%4d%8s%8s", itemName, item.qty, "₹${item.price}", "₹${item.total}")
            drawText(line, bodyPaint)
        }

        drawText(separator, bodyPaint, Layout.Alignment.ALIGN_CENTER)

        // Right-aligned totals
        drawText(String.format("Subtotal: %12s", "₹${receipt.subtotal}"), bodyPaint, Layout.Alignment.ALIGN_OPPOSITE)
        if ((receipt.discount.toDoubleOrNull() ?: 0.0) > 0.0) {
            drawText(String.format("Discount: %12s", "₹${receipt.discount}"), bodyPaint, Layout.Alignment.ALIGN_OPPOSITE)
        }
        drawText(String.format("Total: %12s", "₹${receipt.total}"), bodyBoldPaint, Layout.Alignment.ALIGN_OPPOSITE)
        drawSpacer(10)

        drawText("Payment: ${receipt.paymentMethod}", bodyPaint)
        drawSpacer(15)

        if (receipt.barcode.isNotBlank()) {
            try {
                val pure = receipt.barcode.substringAfter(",")
                val bytes = Base64.decode(pure, Base64.DEFAULT)
                val barcodeBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (barcodeBitmap != null) {
                    val ratio = barcodeBitmap.height.toFloat() / barcodeBitmap.width.toFloat()
                    val scaledHeight = (contentWidth * ratio).toInt()
                    val scaledBarcode = Bitmap.createScaledBitmap(barcodeBitmap, contentWidth, scaledHeight, true)
                    canvas.drawBitmap(scaledBarcode, MARGIN.toFloat(), yPos, null)
                    yPos += scaledBarcode.height
                    drawSpacer(5)
                    drawText(receipt.invoiceNumber, bodyPaint, Layout.Alignment.ALIGN_CENTER)
                }
            } catch (e: Exception) { /* Fails silently */ }
        }

        drawSpacer(15)
        drawText("Thank you, Visit Again!", bodyBoldPaint, Layout.Alignment.ALIGN_CENTER)

        // --- Crop bitmap to the actual height used ---
        yPos += MARGIN // Add bottom margin
        val finalHeight = yPos.toInt()
        val finalBitmap = Bitmap.createBitmap(tempBitmap, 0, 0, PREVIEW_WIDTH, finalHeight)
        tempBitmap.recycle()

        return finalBitmap
    }
}
