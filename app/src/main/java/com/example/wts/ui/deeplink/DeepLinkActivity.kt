package com.example.wts.ui.deeplink

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import com.example.wts.ui.label.AutoLabelPrintActivity
import com.example.wts.ui.receipt.EditableReceiptActivity
import com.example.wts.ui.receipt.ReceiptActivity
import com.example.wts.ui.receipt.ReceiptPrintActivity
import java.io.File
import java.net.URLDecoder

class DeepLinkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent.data?.let { handleDeepLink(it) }
        
        finish() // Close the dispatcher activity immediately
    }

    private fun handleDeepLink(data: Uri) {
        try {
            when (data.host) {
                "receipt" -> handleJsonReceipt(data)
                "print" -> handlePrint(data)
                else -> showToast("Unknown deep link host: ${data.host}")
            }
        } catch (e: Exception) {
            showToast("Error processing deep link: ${e.message}")
        }
    }

    private fun handleJsonReceipt(data: Uri) {
        val encodedJson = data.getQueryParameter("json") ?: run {
            showToast("JSON data is missing")
            return
        }

        val decodedJson = URLDecoder.decode(encodedJson, "UTF-8")
        val realJson = String(Base64.decode(decodedJson, Base64.DEFAULT))
        
        val receiptIntent = Intent(this, EditableReceiptActivity::class.java).apply {
            putExtra("receipt_json", realJson)
        }
        startActivity(receiptIntent)
    }

    private fun handlePrint(data: Uri) {
        val imageB64Encoded = data.getQueryParameter("image")

        if (imageB64Encoded != null) {

            // 🔥 DO NOT URL DECODE BASE64
            val imageB64 = imageB64Encoded

            val copies = data.getQueryParameter("copies")?.toIntOrNull() ?: 1
            val type = data.getQueryParameter("type")

            val pureBase64 = imageB64.substringAfter(",")
            val imageBytes = Base64.decode(pureBase64, Base64.DEFAULT)

            val imagesDir = File(cacheDir, "images").apply { mkdirs() }
            val file = File(imagesDir, "temp_print.png").apply {
                writeBytes(imageBytes)
            }

            val uri = FileProvider.getUriForFile(this, "com.example.wts.provider", file)

            val intent = if (type == "receipt") {
                Intent(this, ReceiptPrintActivity::class.java).apply {
                    setData(uri)
                }
            } else {
                Intent(this, AutoLabelPrintActivity::class.java).apply {
                    setData(uri)
                    putExtra("copies", copies)
                }
            }

// 🔥 NEW — make sure activity also gets Base64
            intent.putExtra("image_base64", imageB64)

            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)

        } else {
            val intent = Intent(this, ReceiptActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
