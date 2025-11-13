package com.example.wts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice

class DeepLinkActivity : ComponentActivity() {
    private val printerHelper = BluetoothPrinterHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageString = intent.data?.getQueryParameter("image")
        val copies = intent.data?.getQueryParameter("copies")?.toIntOrNull() ?: 1
        val auto = intent.data?.getQueryParameter("auto")?.toBoolean() ?: false

        val image = imageString?.let {
            try {
                val decodedString = Base64.decode(it, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        setContent {
            if (image != null) {
                DeepLinkScreen(image = image, copies = copies, auto = auto, printerHelper = printerHelper) { printed ->
                    if (printed) {
                        finish()
                    }
                }
            } else {
                Text("Invalid Image in URL")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        printerHelper.disconnect()
    }
}

@Composable
fun DeepLinkScreen(image: Bitmap, copies: Int, auto: Boolean, printerHelper: BluetoothPrinterHelper, onPrintComplete: (Boolean) -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Waiting...") }
    val context = LocalContext.current

    LaunchedEffect(auto) {
        if (auto) {
            val lastDeviceAddress = getLastUsedPrinter(context)
            if (lastDeviceAddress == null) {
                Toast.makeText(context, "No last used printer found.", Toast.LENGTH_SHORT).show()
                onPrintComplete(false)
                return@LaunchedEffect
            }

            status = "Connecting..."
            val device: BluetoothDevice? = BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(lastDeviceAddress)

            if (device == null) {
                Toast.makeText(context, "Failed to find saved printer.", Toast.LENGTH_SHORT).show()
                onPrintComplete(false)
                return@LaunchedEffect
            }

            printerHelper.connect(device) { success, message ->
                coroutineScope.launch(Dispatchers.IO) {
                    if (success) {
                        withContext(Dispatchers.Main) { status = "Printing..." }
                        
                        printerHelper.sendCommand(TsplUtils.getInitCommand())
                        val bitmapCommand = TsplUtils.createTsplBitmapCommand(image)
                        
                        for (i in 1..copies) {
                            printerHelper.sendCommand(bitmapCommand)
                            printerHelper.sendCommand(TsplUtils.getPrintCommand())
                            printerHelper.sendCommand(TsplUtils.getFormFeedCommand())
                        }

                        withContext(Dispatchers.Main) {
                            status = "Print job sent."
                            onPrintComplete(true)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            onPrintComplete(false)
                        }
                    }
                }
            }
        }
    }

    Column {
        Text("Copies: $copies")
        Text("Auto Print: $auto")
        Text("Status: $status")
        Image(bitmap = image.asImageBitmap(), contentDescription = "Image Preview")
    }
}

private fun getLastUsedPrinter(context: Context): String? {
    val sharedPref = context.getSharedPreferences("PrinterPrefs", Context.MODE_PRIVATE)
    return sharedPref.getString("last_printer_address", null)
}
