package com.example.wts

import android.graphics.Bitmap

object TsplUtils {

    fun initPrinter(): String {
        return """SIZE 48 mm, 25 mm
GAP 3 mm, 0
DENSITY 6
SPEED 3
REFERENCE 0,0
DIRECTION 1
CLS
"""
    }

    fun bitmapToTsplBytes(bitmap: Bitmap, width: Int, height: Int, x: Int, y: Int): ByteArray {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)

        val widthBytes = (width + 7) / 8
        val header = "BITMAP $x,$y,$widthBytes,$height,0,"
        val bitmapData = ByteArray(widthBytes * height)
        for (j in 0 until height) {
            for (i in 0 until width) {
                val color = scaledBitmap.getPixel(i, j)
                val gray = (color shr 16 and 0xFF) * 0.3 + (color shr 8 and 0xFF) * 0.59 + (color and 0xFF) * 0.11
                if (gray < 128) {
                    val byteIndex = j * widthBytes + i / 8
                    val bitIndex = 7 - i % 8
                    bitmapData[byteIndex] = (bitmapData[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                }
            }
        }
        return header.toByteArray() + bitmapData
    }

    fun printBitmapCopies(bitmap: Bitmap, copies: Int): List<ByteArray> {
        val tsplBytes = bitmapToTsplBytes(bitmap, 384, bitmap.height, 0, 0)
        val printCommand = "PRINT 1,$copies\n".toByteArray()
        val formFeedCommand = "FORMFEED\n".toByteArray()
        return listOf(tsplBytes, printCommand, formFeedCommand)
    }

    fun density(density: Int): String {
        return "DENSITY $density\n"
    }

    fun speed(speed: Int): String {
        return "SPEED $speed\n"
    }

    fun direction(direction: Int): String {
        return "DIRECTION $direction\n"
    }

    fun reference(x: Int, y: Int): String {
        return "REFERENCE $x,$y\n"
    }

    fun cls(): String {
        return "CLS\n"
    }

    fun print(copies: Int): String {
        return "PRINT 1,$copies\n"
    }

    fun formFeed(): String {
        return "FORMFEED\n"
    }

    fun gapDetect(): String {
        return "GAPDETECT\n"
    }

    fun feed(dots: Int): String {
        return "FEED $dots\n"
    }

    fun selfTest(): String {
        return "SELFTEST\n"
    }

    fun status(): String {
        return "?STATUS\n"
    }

    fun version(): String {
        return "?VERSION\n"
    }
}
