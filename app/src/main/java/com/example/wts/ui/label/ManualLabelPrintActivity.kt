package com.example.wts.ui.label

import android.app.Activity
import android.bluetooth.BluetoothDevice
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.wts.bluetooth.AppBluetoothManager
import com.example.wts.bluetooth.BluetoothDevicePickerActivity
import com.example.wts.permissions.PermissionUtils
import com.example.wts.ui.common.PrinterStatusIcon
import com.example.wts.ui.settings.PrinterControlPanelActivity
import com.example.wts.utils.TsplUtils

class ManualLabelPrintActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageUriFromIntent = intent.data

        setContent {
            ManualLabelPrintScreen(imageUriFromIntent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualLabelPrintScreen(
    imageUriFromIntent: Uri?,
) {
    val context = LocalContext.current
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

    var darkness by remember { mutableStateOf(6f) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> imageUri = uri }

    val printerPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getParcelableExtra<BluetoothDevice>("device")?.let { device ->
                AppBluetoothManager.connect(context, device)
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

    Scaffold(topBar = { TopAppBar(
        title = { Text("Manual Label Print") },
        actions = { PrinterStatusIcon() }
    ) }) {
        LazyColumn(modifier = Modifier.padding(it).padding(16.dp).fillMaxSize()) {
            item { Button(onClick = { permissionLauncher.launch(PermissionUtils.getRequiredBluetoothPermissions()) }) { Text("Select Printer") } }
            item { Button(onClick = { context.startActivity(Intent(context, PrinterControlPanelActivity::class.java)) }) { Text("Printer Control Panel") } }
            item { Button(onClick = { imagePicker.launch("image/*") }) { Text("Select Image") } }

            item { imageBitmap?.let { Image(it.asImageBitmap(), "Preview", modifier = Modifier.fillMaxWidth().height(200.dp).padding(vertical=8.dp)) } }

            item { Text("Darkness: ${darkness.toInt()}"); Slider(value = darkness, onValueChange = { darkness = it }, valueRange = 0f..15f) }

            item { Button(onClick = {
                val bmp = imageBitmap ?: return@Button
                if (!AppBluetoothManager.isConnected.value) return@Button
                
                val initCmd = TsplUtils.getInitCommand(density = darkness.toInt())
                val bitmapCmd = TsplUtils.createTsplBitmapCommand(bmp)
                val printCmd = TsplUtils.getPrintCommand(1)

                AppBluetoothManager.printerHelper.sendText(initCmd) { success ->
                    if (success) {
                        AppBluetoothManager.printerHelper.printImage(bitmapCmd, printCmd) { printSuccess ->
                            logs.add(if (printSuccess) "Print job sent." else "Failed to print image.")
                        }
                    } else {
                        logs.add("Failed to initialize printer.")
                    }
                }
            }) { Text("Print Single Label") } }

            item { Button(onClick = { AppBluetoothManager.printerHelper.sendText(TsplUtils.getFormFeedCommand()) { logs.add("FORMFEED sent.") } }) { Text("Feed Label") } }

            item { Spacer(Modifier.height(16.dp)); Text("Logs") }
            items(logs) { logEntry -> Text(logEntry, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
