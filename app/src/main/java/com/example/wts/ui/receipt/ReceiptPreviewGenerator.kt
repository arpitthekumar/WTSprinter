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
    private const val MARGIN = 10       // Horizontal margin in pixels

    fun generatePreview(receipt: ReceiptData): Bitmap {
        val contentWidth = PREVIEW_WIDTH - (2 * MARGIN)

        // Define different paints for different text styles
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 22f // Title
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 14f // Sub-header font
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 13f // Smaller body font to fix wrapping
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        val bodyBoldPaint = TextPaint(bodyPaint).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        // Create a bitmap with a large height; we'll crop it later.
        val tempBitmap = Bitmap.createBitmap(PREVIEW_WIDTH, 4000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tempBitmap)
        canvas.drawColor(Color.WHITE)

        var yPos = MARGIN.toFloat() // Start with a top margin

        fun drawText(text: String, paint: TextPaint, alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) {
            val textLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
                .setAlignment(alignment)
                .setLineSpacing(0f, 1.0f)
                .setIncludePad(false)
                .build()

            canvas.save()
            canvas.translate(MARGIN.toFloat(), yPos) // Apply left margin
            textLayout.draw(canvas)
            canvas.restore()
            yPos += textLayout.height
        }

        fun drawSpacer(height: Int) {
            yPos += height
        }

        // --- Start Drawing Receipt --- //

        drawText("Bhootiya Fabric", titlePaint, Layout.Alignment.ALIGN_CENTER)
        drawText("Collection", headerPaint, Layout.Alignment.ALIGN_CENTER)
        drawText("Moti Ganj, Bakebar Road, Bharthana", headerPaint, Layout.Alignment.ALIGN_CENTER)
        drawText("Ph: +91 82736 89065", headerPaint, Layout.Alignment.ALIGN_CENTER)
        drawSpacer(10)

        val date = if (receipt.date.isNotBlank()) receipt.date else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val time = if (receipt.time.isNotBlank()) receipt.time else SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

        drawText("Invoice: ${receipt.invoiceNumber}", bodyPaint)
        drawText("Customer: ${receipt.customerName}", bodyPaint)
        drawText("Phone: ${receipt.customerPhone}", bodyPaint)
        drawText("Date: $date Time: $time", bodyPaint)

        drawText("--------------------------------------------", bodyPaint, Layout.Alignment.ALIGN_CENTER)

        // Items header - Final corrected formatting
        val headerLine = String.format("%-20s %4s %8s %8s", "Item", "Qty", "Rate", "Amt")
        drawText(headerLine, bodyBoldPaint)
        drawText("--------------------------------------------", bodyPaint, Layout.Alignment.ALIGN_CENTER)

        // Item rows - Final corrected formatting
        receipt.items.forEach { item ->
            val itemName = if (item.name.length > 20) item.name.substring(0, 20) else item.name
            // Using %d for quantity (integer)
            val line = String.format("%-20s %4d %8s %8s", itemName, item.qty, "₹${item.price}", "₹${item.total}")
            drawText(line, bodyPaint)
        }

        drawText("--------------------------------------------", bodyPaint, Layout.Alignment.ALIGN_CENTER)

        // Totals - Final corrected formatting for right alignment
        val subtotalLine = String.format("%29s %11s", "Subtotal:", "₹${receipt.subtotal}")
        drawText(subtotalLine, bodyPaint)

        if ((receipt.discount.toDoubleOrNull() ?: 0.0) > 0.0) {
            val discountLine = String.format("%29s %11s", "Discount:", "₹${receipt.discount}")
            drawText(discountLine, bodyPaint)
        }
        val totalLine = String.format("%29s %11s", "Total:", "₹${receipt.total}")
        drawText(totalLine, bodyBoldPaint)
        drawSpacer(10)

        drawText("Payment: ${receipt.paymentMethod}", bodyPaint)
        drawSpacer(15)

        // Barcode
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

        // --- End Drawing, Crop and Return --- //
        yPos += MARGIN // Add bottom margin
        val finalHeight = yPos.toInt()
        val finalBitmap = Bitmap.createBitmap(tempBitmap, 0, 0, PREVIEW_WIDTH, finalHeight)
        tempBitmap.recycle()

        return finalBitmap
    }
}
