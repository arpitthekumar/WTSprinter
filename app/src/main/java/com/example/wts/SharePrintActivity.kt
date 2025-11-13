package com.example.wts

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class SharePrintActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

        setContent {
            SharePrintScreen(imageUri)
        }
    }
}

@Composable
fun SharePrintScreen(imageUri: Uri?) {
    val context = LocalContext.current

    val imageBitmap: Bitmap? = imageUri?.let {
        context.contentResolver.openInputStream(it)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Image Ready to Print", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))

        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap.asImageBitmap(),
                contentDescription = "Shared Image Preview",
                modifier = Modifier.height(250.dp)
            )
        } else {
            Text("Could not load image.")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Choose a print mode:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(onClick = {
                val intent = Intent(context, ManualPrintActivity::class.java).apply {
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                }
                context.startActivity(intent)
            }) {
                Text("Manual Print")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = {
                val intent = Intent(context, AutoPrintActivity::class.java).apply {
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                }
                context.startActivity(intent)
            }) {
                Text("Auto Print")
            }
        }
    }
}
