package com.example.wts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap

class DeepLinkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageString = intent.data?.getQueryParameter("image")
        val copies = intent.data?.getQueryParameter("copies")?.toIntOrNull() ?: 1
        val auto = intent.data?.getQueryParameter("auto")?.toBoolean() ?: false

        val image = imageString?.let {
            val decodedString = Base64.decode(it, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
        }

        setContent {
            if (image != null) {
                DeepLinkScreen(image = image, copies = copies, auto = auto)
            } else {
                Text("Invalid Image")
            }
        }
    }
}

@Composable
fun DeepLinkScreen(image: Bitmap, copies: Int, auto: Boolean) {
    Column {
        Text("Copies: $copies")
        Text("Auto Print: $auto")
        Image(bitmap = image.asImageBitmap(), contentDescription = "•	Image Preview")
    }
}
