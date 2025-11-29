package com.example.wts.ui.label

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
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
import androidx.core.content.FileProvider
import com.example.wts.bluetooth.AppBluetoothManager
import com.example.wts.bluetooth.BluetoothDevicePickerActivity
import com.example.wts.permissions.PermissionUtils
import com.example.wts.ui.common.PrinterStatusIcon
import com.example.wts.utils.TsplUtils
import java.io.File

class AutoLabelPrintActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FIXED - always resolve URI properly
        val imageUriFromIntent = getImageUriFromDeepLink()

        val copiesFromIntent = intent.getIntExtra("copies", 1)

        setContent {
            AutoLabelPrintScreen(
                imageUriFromIntent = imageUriFromIntent,
                copiesFromIntent = copiesFromIntent
            )
        }
    }

    // ⭐ FIXED — Handle image both from intent.data and base64 fallback
    private fun getImageUriFromDeepLink(): Uri? {
        // 1) If FileProvider URI exists → use it
        intent.data?.let { return it }

        // 2) Else fallback to base64 extra
        val base64 = intent.getStringExtra("image_base64") ?: return null

        return try {
            val pure = base64.substringAfter(",")
            val bytes = Base64.decode(pure, Base64.DEFAULT)

            val file = File(cacheDir, "deeplink_label_img.png")
            file.writeBytes(bytes)

            FileProvider.getUriForFile(this, "com.example.wts.provider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoLabelPrintScreen(
    imageUriFromIntent: Uri?,
    copiesFromIntent: Int
) {
    val context = LocalContext.current
    val logs = remember { mutableStateListOf<String>() }

    // FIXED — dynamic image state
    var imageUri by remember { mutableStateOf(imageUriFromIntent) }

    // FIXED — preview loads from deep link immediately
    val imageBitmap by remember(imageUri) {
        derivedStateOf {
            imageUri?.let { uri ->
                try {
                    context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    var copies by remember { mutableStateOf(copiesFromIntent.toString()) }

    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            imageUri = uri
        }

    val printerPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.getParcelableExtra<BluetoothDevice>("device")?.let { device ->
                    AppBluetoothManager.connect(context, device)
                }
            }
        }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (PermissionUtils.hasBluetoothPermissions(context)) {
                printerPicker.launch(Intent(context, BluetoothDevicePickerActivity::class.java))
            } else {
                Toast.makeText(context, "Bluetooth permission required", Toast.LENGTH_SHORT).show()
            }
        }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Auto Label Print") },
            actions = { PrinterStatusIcon() }
        )
    }) {
        LazyColumn(
            modifier = Modifier
                .padding(it)
                .padding(16.dp)
                .fillMaxSize()
        ) {

            item {
                Button(onClick = {
                    permissionLauncher.launch(PermissionUtils.getRequiredBluetoothPermissions())
                }) {
                    Text("Select Printer")
                }
            }

            item {
                Button(onClick = { imagePicker.launch("image/*") }) {
                    Text("Select Image")
                }
            }

            // ⭐ PREVIEW NOW ALWAYS WORKS FROM DEEP LINK URI
            item {
                imageBitmap?.let {
                    Image(
                        it.asImageBitmap(),
                        "Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(vertical = 8.dp)
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = copies,
                    onValueChange = { copies = it },
                    label = { Text("Number of Copies") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val bmp = imageBitmap ?: return@Button
                        if (!AppBluetoothManager.isConnected.value) return@Button

                        val totalCopies = (copies.toIntOrNull() ?: 1).coerceAtLeast(1)
                        logs.add("Starting print job for $totalCopies labels...")

                        AppBluetoothManager.printerHelper.sendText(TsplUtils.getInitCommand()) { success ->
                            if (!success) {
                                logs.add("Error: Failed to initialize printer.")
                                return@sendText
                            }
                            logs.add("Printer initialized.")

                            val bitmapCmd = TsplUtils.createTsplBitmapCommand(bmp)
                            val printCmd = TsplUtils.getPrintCommand(1)

                            var printed = 0
                            fun printNext() {
                                if (printed >= totalCopies) {
                                    logs.add("Print job complete.")
                                    return
                                }

                                printed++
                                logs.add("Sending label #$printed...")

                                AppBluetoothManager.printerHelper.printImage(bitmapCmd, printCmd) { ok ->
                                    if (ok) {
                                        logs.add("Label #$printed sent.")
                                        printNext()
                                    } else {
                                        logs.add("Error printing label #$printed.")
                                    }
                                }
                            }

                            printNext()
                        }
                    }
                ) {
                    Text("Auto Print")
                }
            }

            item { Spacer(Modifier.height(24.dp)) }

            item {
                Text("Alignment & Test Tools", style = MaterialTheme.typography.titleMedium)
            }

            item {
                Row {
                    Button(onClick = {
                        AppBluetoothManager.printerHelper.sendText(TsplUtils.getGapDetectCommand()) {
                            logs.add("GAPDETECT sent.")
                        }
                    }) { Text("GAPDETECT") }

                    Spacer(Modifier.width(8.dp))

                    Button(onClick = {
                        AppBluetoothManager.printerHelper.sendText(TsplUtils.getFormFeedCommand()) {
                            logs.add("FORMFEED sent.")
                        }
                    }) { Text("FORMFEED") }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            item {
                Text("Logs", style = MaterialTheme.typography.titleMedium)
            }

            items(logs) { logEntry ->
                Text(logEntry, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
