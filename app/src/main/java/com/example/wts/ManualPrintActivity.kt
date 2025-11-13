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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class ManualPrintActivity : ComponentActivity() {
    private val printerHelper = BluetoothPrinterHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ManualPrintScreen(printerHelper)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        printerHelper.disconnect()
    }
}

@Composable
fun ManualPrintScreen(printerHelper: BluetoothPrinterHelper) {
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

    var width by remember { mutableStateOf(384f) }
    var height by remember { mutableStateOf(200f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var darkness by remember { mutableStateOf(6f) }
    var speed by remember { mutableStateOf(3f) }
    val debugLog = remember { mutableStateListOf<String>() }
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

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
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
        Button(onClick = {
            if (imageBitmap != null && connectionStatus == "Connected") {
                coroutineScope.launch {
                    val commands = listOf(
                        TsplUtils.density(darkness.toInt()),
                        TsplUtils.speed(speed.toInt()),
                    )
                    commands.forEach{
                        printerHelper.sendCommand(it)
                        debugLog.add(it)
                    }
                    val bitmapCommand = TsplUtils.bitmapToTsplBytes(imageBitmap!!, width.toInt(), height.toInt(), offsetX.toInt(), offsetY.toInt())
                    printerHelper.sendCommand(bitmapCommand)
                    debugLog.add("BITMAP command sent")

                    val printCommand = TsplUtils.print(1)
                    printerHelper.sendCommand(printCommand)
                    debugLog.add(printCommand)
                }
            }
        }) {
            Text("Print One Label")
        }
        Button(onClick = {
            if (connectionStatus == "Connected") {
                coroutineScope.launch {
                    val command = TsplUtils.gapDetect()
                    printerHelper.sendCommand(command)
                    debugLog.add(command)
                }
            }
        }) {
            Text("Feed (GAPDETECT)")
        }
        Slider(value = width, onValueChange = { width = it }, valueRange = 1f..384f)
        Text("Width: ${width.toInt()}")
        Slider(value = height, onValueChange = { height = it }, valueRange = 1f..1000f)
        Text("Height: ${height.toInt()}")
        Slider(value = offsetX, onValueChange = { offsetX = it }, valueRange = 0f..500f)
        Text("Offset X: ${offsetX.toInt()}")
        Slider(value = offsetY, onValueChange = { offsetY = it }, valueRange = 0f..500f)
        Text("Offset Y: ${offsetY.toInt()}")
        Slider(value = darkness, onValueChange = { darkness = it }, valueRange = 0f..15f)
        Text("Darkness: ${darkness.toInt()}")
        Slider(value = speed, onValueChange = { speed = it }, valueRange = 1f..5f)
        Text("Speed: ${speed.toInt()}")
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(debugLog) { log ->
                Text(log)
            }
        }
    }
}