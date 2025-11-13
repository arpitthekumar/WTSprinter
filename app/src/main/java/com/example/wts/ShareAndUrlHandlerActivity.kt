package com.example.wts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ShareAndUrlHandlerActivity : ComponentActivity() {

    private val printerHelper = BluetoothPrinterHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent.action
        val type = intent.type

        var imageUri: Uri? = null
        var copies = 1
        var autoPrint = false

        if (Intent.ACTION_SEND == action && type?.startsWith("image/") == true) {
            imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        } else if (Intent.ACTION_VIEW == action) {
            val data = intent.data
            val imageB64 = data?.getQueryParameter("image")
            copies = data?.getQueryParameter("copies")?.toIntOrNull() ?: 1
            autoPrint = data?.getQueryParameter("auto")?.toBoolean() == true
            
            imageUri = imageB64?.let { b64 ->
                val imageBytes = Base64.decode(b64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                saveBitmapToCache(this, bitmap)
            }
        }

        if (imageUri == null) {
            Toast.makeText(this, "Failed to load image.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (autoPrint) {
            // Handle auto-print and close
            setContent {
                AutoPrintAndCloseScreen(printerHelper, imageUri, copies) { finish() }
            }
        } else {
            // Show manual/auto choice screen
            setContent {
                ShareChoiceScreen(imageUri)
            }
        }
    }
    
    private fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri {
        val imagesFolder = File(context.cacheDir, "images")
        imagesFolder.mkdirs()
        val file = File(imagesFolder, "shared_image.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.flush()
        stream.close()
        return androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }
}

@Composable
fun ShareChoiceScreen(imageUri: Uri?) {
    val context = LocalContext.current
    val imageBitmap by remember(imageUri) {
        derivedStateOf {
            imageUri?.let { context.contentResolver.openInputStream(it)?.use(BitmapFactory::decodeStream) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Image Ready", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        imageBitmap?.let { Image(it.asImageBitmap(), "Preview", modifier = Modifier.height(200.dp)) }
        Spacer(Modifier.height(24.dp))
        Text("Choose a print mode:")
        Spacer(Modifier.height(16.dp))
        Row {
            Button(onClick = { 
                val intent = Intent(context, ManualPrintActivity::class.java).apply { putExtra(Intent.EXTRA_STREAM, imageUri) }
                context.startActivity(intent)
                (context as? Activity)?.finish()
            }) { Text("Manual Print") }
            Spacer(Modifier.width(16.dp))
            Button(onClick = { 
                val intent = Intent(context, AutoPrintActivity::class.java).apply { putExtra(Intent.EXTRA_STREAM, imageUri) }
                context.startActivity(intent)
                (context as? Activity)?.finish()
            }) { Text("Auto Print") }
        }
    }
}

@Composable
fun AutoPrintAndCloseScreen(printerHelper: BluetoothPrinterHelper, imageUri: Uri, copies: Int, onComplete: () -> Unit) {
    var status by remember { mutableStateOf("Initializing...") }
    val context = LocalContext.current
    
    val imageBitmap by remember(imageUri) {
        derivedStateOf {
            imageUri.let { context.contentResolver.openInputStream(it)?.use(BitmapFactory::decodeStream) }
        }
    }

    LaunchedEffect(key1 = true) {
        val lastPrinterAddress = context.getSharedPreferences("PrinterPrefs", Context.MODE_PRIVATE).getString("last_printer_address", null)
        if (lastPrinterAddress == null) {
            status = "No saved printer."
            Toast.makeText(context, status, Toast.LENGTH_LONG).show()
            onComplete()
            return@LaunchedEffect
        }

        if (imageBitmap == null) {
            status = "Failed to load image."
            Toast.makeText(context, status, Toast.LENGTH_LONG).show()
            onComplete()
            return@LaunchedEffect
        }
        
        status = "Connecting to printer..."
        val device = android.bluetooth.BluetoothAdapter.getDefaultAdapter().getRemoteDevice(lastPrinterAddress)
        printerHelper.connect(device) { success, message ->
            if (success) {
                status = "Printing $copies copies..."
                CoroutineScope(Dispatchers.IO).launch {
                    val bitmapCommand = TsplUtils.createTsplBitmapCommand(imageBitmap!!)
                    for (i in 1..copies) {
                        printerHelper.sendCommand(TsplUtils.getInitCommand()) // Includes CLS
                        printerHelper.sendCommand(bitmapCommand)
                        printerHelper.sendCommand(TsplUtils.getPrintCommand())
                        printerHelper.sendCommand(TsplUtils.getFormFeedCommand())
                    }
                    printerHelper.disconnect()
                    onComplete()
                }
            } else {
                status = message
                Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                onComplete()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(status)
        }
    }
}
