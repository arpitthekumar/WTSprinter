package com.example.wts

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.items
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoPrintScreen(
    printerHelper: BluetoothPrinterHelper,
    imageUriFromIntent: Uri?
) {
    val context = LocalContext.current
    var connectionStatus by remember { mutableStateOf("Not Connected") }
    val logs = remember { mutableStateListOf<String>() }
    
    var imageUri by remember { mutableStateOf(imageUriFromIntent) }
    val imageBitmap by remember(imageUri) {
        derivedStateOf {
            imageUri?.let { uri ->
                try {
                    context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                } catch (_: Exception) { null }
            }
        }
    }
    var copies by remember { mutableStateOf("1") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> imageUri = uri }

    val printerPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getParcelableExtra<BluetoothDevice>("device")?.let { device ->
                connectionStatus = "Connecting..."
                printerHelper.connect(context, device) { success, msg ->
                    connectionStatus = msg
                    logs.add(msg)
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { 
        if (PermissionUtils.hasBluetoothPermissions(context)) {
            printerPicker.launch(Intent(context, BluetoothDevicePickerActivity::class.java))
        } else {
            Toast.makeText(context, "Bluetooth permission required", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Automatic Print") }) }) {
        LazyColumn(modifier = Modifier.padding(it).padding(16.dp).fillMaxSize()) {
            item { Button(onClick = { permissionLauncher.launch(PermissionUtils.getRequiredBluetoothPermissions()) }) { Text("Select Printer") } }
            item { Text("Status: $connectionStatus") }
            item { Button(onClick = { imagePicker.launch("image/*") }) { Text("Select Image") } }

            item { imageBitmap?.let { Image(it.asImageBitmap(), "Preview", modifier = Modifier.fillMaxWidth().height(200.dp).padding(vertical=8.dp)) } }

            item { OutlinedTextField(value = copies, onValueChange = { copies = it }, label = { Text("Number of Copies") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }

            item { Button(modifier = Modifier.fillMaxWidth(), onClick = {
                val bmp = imageBitmap ?: return@Button
                if (!printerHelper.isConnected) return@Button
                val totalCopies = (copies.toIntOrNull() ?: 1).coerceAtLeast(1)
                logs.add("Starting print job for $totalCopies labels...")

                printerHelper.sendText(TsplUtils.getInitCommand()) { success ->
                    if (!success) {
                        logs.add("Error: Failed to initialize printer.")
                        return@sendText
                    }
                    logs.add("Printer initialized.")

                    val bitmapCmd = TsplUtils.createTsplBitmapCommand(bmp)
                    val printCmd = TsplUtils.getPrintCommand(1)

                    var copiesPrinted = 0
                    fun printNextCopy() {
                        if (copiesPrinted >= totalCopies) {
                            logs.add("Print job complete.")
                            return
                        }
                        copiesPrinted++
                        logs.add("Sending label #${copiesPrinted}...")
                        printerHelper.printImage(bitmapCmd, printCmd) { printSuccess ->
                             if(printSuccess) {
                                logs.add("Label #${copiesPrinted} sent successfully.")
                                printNextCopy()
                             } else {
                                logs.add("Error printing label #${copiesPrinted}.")
                             }
                        }
                    }
                    printNextCopy()
                }
            }) { Text("Auto Print") } }

            item { Spacer(Modifier.height(24.dp)); Text("Alignment & Test Tools", style = MaterialTheme.typography.titleMedium) }
            item { Row {
                Button(onClick = { printerHelper.sendText(TsplUtils.getGapDetectCommand()) { logs.add("GAPDETECT sent.") } }) { Text("GAPDETECT") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { printerHelper.sendText(TsplUtils.getFormFeedCommand()) { logs.add("FORMFEED sent.") } }) { Text("FORMFEED") }
            } }

            item { Spacer(Modifier.height(16.dp)); Text("Logs", style = MaterialTheme.typography.titleMedium) }
            items(logs) { logEntry -> Text(logEntry, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
