package com.example.wts

import android.content.Intent
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class SharePrintActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedImageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

        setContent {
            SharePrintScreen(sharedImageUri)
        }
    }
}

@Composable
fun SharePrintScreen(imageUri: Uri?) {
    val context = LocalContext.current

    // safely decode bitmap
    val bitmap by remember(imageUri) {
        derivedStateOf {
            imageUri?.let {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text("Image Ready to Print", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))

        // preview
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Shared Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
        } else {
            Text("Unable to load image.")
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("Choose Print Mode", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // Buttons
        Row {
            Button(
                onClick = {
                    val i = Intent(context, ManualPrintActivity::class.java)
                    i.putExtra(Intent.EXTRA_STREAM, imageUri)
                    context.startActivity(i)
                }
            ) {
                Text("Manual Print")
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = {
                    val i = Intent(context, AutoPrintActivity::class.java)
                    i.putExtra(Intent.EXTRA_STREAM, imageUri)
                    context.startActivity(i)
                }
            ) {
                Text("Auto Print")
            }
        }
    }
}
