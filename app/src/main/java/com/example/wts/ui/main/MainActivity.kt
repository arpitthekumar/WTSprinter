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
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var finalImageUri: Uri? = null

        try {
            if (Intent.ACTION_SEND == intent.action && intent.type?.startsWith("image/") == true) {
                val receivedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }

                if (receivedUri != null) {
                    // For shared content, we must read it immediately and save it to our own cache
                    // to avoid losing permission to the URI.
                    contentResolver.openInputStream(receivedUri)?.use { inputStream ->
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        finalImageUri = saveBitmapToCache(this, bitmap)
                    }
                }
            } else if (Intent.ACTION_VIEW == intent.action) {
                // For deep links with base64 data, we decode and save to cache.
                val imageB64 = intent.data?.getQueryParameter("image")
                if (imageB64 != null) {
                    val imageBytes = Base64.decode(imageB64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    finalImageUri = saveBitmapToCache(this, bitmap)
                }
            }
        } catch (e: Exception) {
            // If anything goes wrong, we just proceed without an image.
            e.printStackTrace()
        }

        // Launch the choice screen, passing our reliable cache URI if it exists.
        val choiceIntent = Intent(this, PrintChoiceActivity::class.java).apply {
            data = finalImageUri
        }
        startActivity(choiceIntent)
        finish() // This activity is just a router, so we finish it.
    }

    /**
     * Saves a bitmap to the app's private cache directory and returns a reliable, shareable Uri.
     */
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
