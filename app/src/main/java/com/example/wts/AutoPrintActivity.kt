package com.example.wts

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class AutoPrintActivity : ComponentActivity() {
    private val printerHelper = BluetoothPrinterHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoPrintScreen(printerHelper)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        printerHelper.disconnect()
    }
}

@Composable
fun AutoPrintScreen(printerHelper: BluetoothPrinterHelper) {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val imagePicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        imageUri = uri
    }
    val context = LocalContext.current
    val imageBitmap by remember(imageUri) {
        derivedStateOf {
            imageUri?.let {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                bitmap
            }
        }
    }
    var copies by remember { mutableStateOf("1") }
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var connectionStatus by remember { mutableStateOf("Not Connected") }
    val coroutineScope = rememberCoroutineScope()

    val devicePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val device = result.data?.getParcelableExtra<BluetoothDevice>("device")
            if (device != null) {
                selectedDevice = device
                coroutineScope.launch {
                    connectionStatus = if (printerHelper.connect(device)) "Connected" else "Connection Failed"
                }
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = { devicePickerLauncher.launch(Intent(context, BluetoothDevicePickerActivity::class.java)) }) {
            Text("Select Printer")
        }
        Text("Connection Status: $connectionStatus")
        Button(onClick = { imagePicker.launch("image/*") }) {
            Text("Select Image")
        }
        imageBitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Image Preview",
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
        }
        TextField(
            value = copies,
            onValueChange = { copies = it },
            label = { Text("Number of Copies") }
        )
        Button(onClick = {
            if (imageBitmap != null && connectionStatus == "Connected") {
                coroutineScope.launch {
                    val commands = TsplUtils.printBitmapCopies(imageBitmap!!, copies.toIntOrNull() ?: 1)
                    commands.forEach { printerHelper.sendCommand(it) }
                }
            }
        }) {
            Text("Auto Print")
        }
        Row {
            Button(onClick = { if (connectionStatus == "Connected") coroutineScope.launch { printerHelper.sendCommand(TsplUtils.formFeed()) } }) {
                Text("FORMFEED")
            }
            Button(onClick = { if (connectionStatus == "Connected") coroutineScope.launch { printerHelper.sendCommand(TsplUtils.gapDetect()) } }) {
                Text("GAPDETECT")
            }
            Button(onClick = { if (connectionStatus == "Connected") coroutineScope.launch { printerHelper.sendCommand(TsplUtils.feed(10)) } }) {
                Text("FEED 10")
            }
            Button(onClick = { if (connectionStatus == "Connected") coroutineScope.launch { printerHelper.sendCommand(TsplUtils.cls()) } }) {
                Text("CLS")
            }
            Button(onClick = { if (connectionStatus == "Connected") coroutineScope.launch { printerHelper.sendCommand(TsplUtils.print(1)) } }) {
                Text("PRINT 1,1")
            }
        }
    }
}
