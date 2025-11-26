package com.example.wts.ui.main

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import com.example.wts.ui.choice.PrintChoiceActivity
import com.example.wts.ui.label.AutoLabelPrintActivity
import com.example.wts.ui.receipt.ReceiptPrintActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Check if the app was launched from our custom deep link
            if (intent.action == Intent.ACTION_VIEW && intent.data?.scheme == "wts") {
                handleDeepLink()
            } 
            // Check if the app was launched from a standard share intent
            else if (Intent.ACTION_SEND == intent.action && intent.type?.startsWith("image/") == true) {
                handleShareIntent()
            } 
            // Otherwise, it's a regular app launch
            else {
                launchChoiceScreen(null)
            }
        } catch (e: Exception) {
            // If anything goes wrong, just launch the normal way
            e.printStackTrace()
            launchChoiceScreen(null)
        }
        
        finish() // This activity is just a router, so we finish it.
    }

    private fun handleDeepLink() {
        val data = intent.data ?: return
        val imageB64 = data.getQueryParameter("image")
        val type = data.getQueryParameter("type") ?: "label" // Default to label if not specified

        if (imageB64 != null) {
            val imageBytes = Base64.decode(imageB64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            val imageUri = saveBitmapToCache(this, bitmap)

            when (type) {
                "receipt" -> {
                    val receiptIntent = Intent(this, ReceiptPrintActivity::class.java).apply {
                        this.data = imageUri
                    }
                    startActivity(receiptIntent)
                }
                else -> { // Default to "label"
                    val copies = data.getQueryParameter("copies")?.toIntOrNull() ?: 1
                    val autoPrintIntent = Intent(this, AutoLabelPrintActivity::class.java).apply {
                        this.data = imageUri
                        putExtra("copies", copies)
                    }
                    startActivity(autoPrintIntent)
                }
            }
        } else {
            // Deep link without image? Fallback to choice screen.
            launchChoiceScreen(null)
        }
    }

    private fun handleShareIntent() {
        val receivedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }

        if (receivedUri != null) {
            contentResolver.openInputStream(receivedUri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val finalImageUri = saveBitmapToCache(this, bitmap)
                launchChoiceScreen(finalImageUri)
            }
        } else {
            launchChoiceScreen(null)
        }
    }

    private fun launchChoiceScreen(imageUri: Uri?) {
        val choiceIntent = Intent(this, PrintChoiceActivity::class.java).apply {
            data = imageUri
        }
        startActivity(choiceIntent)
    }

    private fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri {
        val imagesFolder = File(context.cacheDir, "images")
        imagesFolder.mkdirs()
        val file = File(imagesFolder, "shared_image.png")
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }
}
