package com.example.wts

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.roundToInt

object TsplUtils {

    /**
     * Default physical label settings (can be overridden by callers).
     * Using DPI (dots per inch) lets us compute pixel sizes that match the printer.
     *
     * Common thermal printers are 203 DPI. If your printer is 300 DPI change dpi accordingly.
     */
    private const val DEFAULT_DPI = 203

    /**
     * Build an init command that sets the label size and printer defaults.
     * labelWidthMm / labelHeightMm defaults match the values you used originally (48 x 25 mm).
     */
    fun getInitCommand(
        labelWidthMm: Float = 48f,
        labelHeightMm: Float = 25f,
        density: Int = 6,
        speed: Int = 3,
        dpi: Int = DEFAULT_DPI
    ): String {
        // Use supplied mm values to construct SIZE command
        return buildString {
            append("SIZE ${labelWidthMm} mm, ${labelHeightMm} mm\n")
            append("GAP 3 mm, 0\n") // keep gap as before (adjust if your labels use different gap)
            append("DENSITY $density\n")
            append("SPEED $speed\n")
            append("REFERENCE 0,0\n")
            append("DIRECTION 1\n")
            append("CLS\n")
        }
    }

    /**
     * Corrected PRINT command. TSPL expects "PRINT <copies>\n".
     * (Older code using "PRINT 1,<copies>" can behave incorrectly on some models.)
     */
    fun getPrintCommand(copies: Int = 1): String = "PRINT $copies\n"

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

    /**
     * Convert an Android Bitmap into a TSPL BITMAP command (binary payload).
     *
     * Improvements and behavior:
     * - DPI-aware scaling from label physical size (mm) to pixels.
     * - Maintains aspect ratio, fits within label width and label height.
     * - Flattens transparency on white background to avoid unexpected black areas.
     * - Uses Floyd–Steinberg dithering for good 1-bit results.
     * - Packs bits MSB-first in each byte (x=0 goes to bit 7).
     * - Packs WHITE pixels as bit=1 and BLACK pixels as bit=0 (TSPL expects 0 = dot/black).
     * - Returns header (ASCII) + binary bitmap data in a single ByteArray.
     *
     * @param labelWidthMm physical label width used for computing the pixel width (default 48mm)
     * @param labelHeightMm physical label height used for computing the pixel height (default 25mm)
     * @param dpi printer dots-per-inch (default 203)
     */
    fun createTsplBitmapCommand(
        source: Bitmap,
        labelWidthMm: Float = 48f,
        labelHeightMm: Float = 25f,
        dpi: Int = DEFAULT_DPI
    ): ByteArray {
        // --- Calculate target pixel dimensions based on DPI and label mm dimensions ---
        val targetWidthPx = ((labelWidthMm / 25.4f) * dpi).roundToInt()
        val targetMaxHeightPx = ((labelHeightMm / 25.4f) * dpi).roundToInt()

        // --- Flatten alpha onto white background to avoid transparent issues ---
        val flattened = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flattened)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, 0f, 0f, null)

        // --- Scale while preserving aspect ratio and ensuring we don't exceed label height ---
        val aspectRatio = flattened.width.toFloat() / flattened.height.toFloat()
        var scaledWidth = targetWidthPx
        var scaledHeight = (scaledWidth / aspectRatio).roundToInt()

        if (scaledHeight > targetMaxHeightPx) {
            // If height exceeds label height, scale by height instead
            scaledHeight = targetMaxHeightPx
            scaledWidth = (scaledHeight * aspectRatio).roundToInt()
        }

        val scaled = Bitmap.createScaledBitmap(flattened, scaledWidth, scaledHeight, true)

        // --- Convert to grayscale float array for dithering ---
        val width = scaled.width
        val height = scaled.height
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = FloatArray(width * height)
        for (i in pixels.indices) {
            val c = pixels[i]
            gray[i] = (Color.red(c) * 0.299f + Color.green(c) * 0.587f + Color.blue(c) * 0.114f)
        }

        // --- Floyd–Steinberg dithering (in-place on gray[]) ---
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val oldPixel = gray[idx]
                val newPixel = if (oldPixel < 128f) 0f else 255f // 0 => black, 255 => white
                gray[idx] = newPixel
                val quantError = oldPixel - newPixel

                // distribute the error
                if (x + 1 < width) gray[idx + 1] = gray[idx + 1] + quantError * 7f / 16f
                if (x > 0 && y + 1 < height) gray[idx - 1 + width] = gray[idx - 1 + width] + quantError * 3f / 16f
                if (y + 1 < height) gray[idx + width] = gray[idx + width] + quantError * 5f / 16f
                if (x + 1 < width && y + 1 < height) gray[idx + 1 + width] = gray[idx + 1 + width] + quantError * 1f / 16f
            }
        }

        // --- Create final monochrome pixel array (Color.BLACK or Color.WHITE) ---
        for (i in 0 until width * height) {
            pixels[i] = if (gray[i] < 128f) Color.BLACK else Color.WHITE
        }

        // --- Pack bits into bytes: widthBytes = ceil(width / 8) ---
        val widthBytes = (width + 7) / 8
        val bitmapData = ByteArray(widthBytes * height) // initialized to 0x00 (all zeros)

        /*
         * IMPORTANT: TSPL interprets a 0 bit as a printed dot (black).
         * So we want BLACK pixels to remain as 0 in the data.
         * We set bits to 1 for WHITE pixels (no dot).
         *
         * Packing scheme: MSB-first per byte:
         * x % 8 == 0 -> bit 7, x % 8 == 7 -> bit 0
         */
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = pixels[y * width + x]
                if (color == Color.WHITE) {
                    val byteIndex = y * widthBytes + x / 8
                    val bitIndex = 7 - (x % 8)
                    // set bit to 1 for white
                    bitmapData[byteIndex] = (bitmapData[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                }
                // note: black pixels are left as 0 bit (printer will print them)
            }
        }

        // --- Construct TSPL BITMAP header ---
        // Format: BITMAP x,y,widthBytes,height,mode,<binary data>
        // Many TSPL implementations expect the header then raw binary data immediately.
        val header = "BITMAP 0,0,$widthBytes,$height,0,".toByteArray(Charsets.US_ASCII)

        // Combine header + binary payload
        val out = ByteArray(header.size + bitmapData.size)
        System.arraycopy(header, 0, out, 0, header.size)
        System.arraycopy(bitmapData, 0, out, header.size, bitmapData.size)

        return out
    }
}
