package com.example.wts

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

class AutoPrintActivity : ComponentActivity() {
    private val printerHelper = BluetoothPrinterHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageUriFromIntent = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        setContent {
            AutoPrintScreen(printerHelper, imageUriFromIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        printerHelper.disconnect()
    }
}

@Composable
fun AutoPrintScreen(printerHelper: BluetoothPrinterHelper, imageUriFromIntent: Uri?) {
    val context = LocalContext.current
    var connectionStatus by remember { mutableStateOf("Not Connected") }

    var imageUri by remember { mutableStateOf(imageUriFromIntent) }
    val imageBitmap by remember(imageUri) {
        derivedStateOf {
            imageUri?.let { context.contentResolver.openInputStream(it)?.use(BitmapFactory::decodeStream) }
        }
    }
    var copies by remember { mutableStateOf("1") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> imageUri = uri }

    val devicePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getParcelableExtra<BluetoothDevice>("device")?.let { device ->
                connectionStatus = "Connecting..."
                printerHelper.connect(device) { success, message -> connectionStatus = message }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { 
        if (PermissionUtils.hasBluetoothPermissions(context)) {
             devicePickerLauncher.launch(Intent(context, BluetoothDevicePickerActivity::class.java))
        } else {
             Toast.makeText(context, "Bluetooth permissions are required.", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        item { Button(onClick = { permissionLauncher.launch(PermissionUtils.getRequiredBluetoothPermissions()) }) { Text("Select Printer") } }
        item { Text("Status: $connectionStatus") }
        item { Button(onClick = { imagePicker.launch("image/*") }) { Text("Select Image") } }

        item { imageBitmap?.let { Image(bitmap = it.asImageBitmap(), "Preview", modifier = Modifier.fillMaxWidth().height(200.dp)) } }

        item { OutlinedTextField(value = copies, onValueChange = { copies = it }, label = { Text("Number of Copies") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }

        item { Button(onClick = {
            if (imageBitmap == null || !printerHelper.isConnected) return@Button
            val numCopies = copies.toIntOrNull() ?: 1
            val bitmapCommand = TsplUtils.createTsplBitmapCommand(imageBitmap!!)

            // Loop for each copy with the correct command sequence
            for (i in 1..numCopies) {
                printerHelper.sendCommand(TsplUtils.getInitCommand()) // Includes CLS
                printerHelper.sendCommand(bitmapCommand)
                printerHelper.sendCommand(TsplUtils.getPrintCommand())
                printerHelper.sendCommand(TsplUtils.getFormFeedCommand())
            }
        }) { Text("Auto Print") } }

        item { Spacer(modifier = Modifier.height(16.dp)); Text("Test Commands", style = MaterialTheme.typography.titleMedium) }
        item { Row { 
            Button(onClick = { printerHelper.sendCommand(TsplUtils.getFormFeedCommand()) }) { Text("FORMFEED") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { printerHelper.sendCommand(TsplUtils.getGapDetectCommand()) }) { Text("GAPDETECT") }
        } }
    }
}