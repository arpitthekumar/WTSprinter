package com.example.wts

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap

class SharePrintActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        val imageBitmap = imageUri?.let {
            contentResolver.openInputStream(it)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }

        setContent {
            SharePrintScreen(imageBitmap)
        }
    }
}

@Composable
fun SharePrintScreen(imageBitmap: Bitmap?) {
    Column {
        imageBitmap?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = "Shared Image")
        }
        Button(onClick = { /* TODO: Implement Print */ }) {
            Text("Print")
        }
    }
}
