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
            if (image != null && auto) {
                DeepLinkScreen(image = image, copies = copies, printerHelper = printerHelper) { 
                    finish()
                }
            } else {
                Text("Invalid or non-auto print request.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        printerHelper.disconnect()
    }
}

@Composable
fun DeepLinkScreen(image: Bitmap, copies: Int, printerHelper: BluetoothPrinterHelper, onPrintComplete: (Boolean) -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Waiting...") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val lastDeviceAddress = (context.getSharedPreferences("PrinterPrefs", Context.MODE_PRIVATE)).getString("last_printer_address", null)
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

        printerHelper.connect(context, device) { success, message ->
            if (success) {
                coroutineScope.launch(Dispatchers.Main) { status = "Printing..." }
                
                val bitmapCmd = TsplUtils.createTsplBitmapCommand(image)
                val printCmd = TsplUtils.getPrintCommand(1)

                var copiesPrinted = 0
                fun printNext() {
                    if (copiesPrinted >= copies) {
                        printerHelper.disconnect()
                        onPrintComplete(true)
                        return
                    }
                    copiesPrinted++
                    printerHelper.sendText(TsplUtils.getInitCommand()) { initSuccess -> 
                        if(initSuccess) {
                            printerHelper.printImage(bitmapCmd, printCmd) { printSuccess ->
                                if(printSuccess) printNext() else onPrintComplete(false)
                            }
                        } else {
                            onPrintComplete(false)
                        }
                    }
                }
                printNext()
            } else {
                 coroutineScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    onPrintComplete(false)
                 }
            }
        }
    }

    Column {
        Text("Copies: $copies")
        Text("Status: $status")
        Image(bitmap = image.asImageBitmap(), contentDescription = "Image Preview")
    }
}
