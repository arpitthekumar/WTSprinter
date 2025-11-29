package com.example.wts.ui.receipt

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.wts.bluetooth.AppBluetoothManager
import com.example.wts.bluetooth.BluetoothDevicePickerActivity
import com.example.wts.permissions.PermissionUtils
import com.example.wts.ui.common.PrinterStatusIcon
import com.example.wts.ui.settings.PrinterControlPanelActivity
import com.example.wts.utils.EscPosUtils
import java.io.File

class ReceiptPrintActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔥 FIXED — always get URI (from setData() or base64)
        val imageUriFromIntent = getImageUriFromDeepLink()

        setContent {
            ReceiptPrintScreen(imageUriFromIntent)
        }
    }

    // 🔥 FUNCTION TO FIX DEEP LINK PREVIEW PROBLEM
    private fun getImageUriFromDeepLink(): Uri? {
        // 1) if URI already exists (FileProvider), use it
        intent.data?.let { return it }

        // 2) else fallback to base64
        val base64 = intent.getStringExtra("image_base64") ?: return null

        return try {
            val pure = base64.substringAfter(",")
            val bytes = Base64.decode(pure, Base64.DEFAULT)

            val file = File(cacheDir, "deeplink_receipt_img.png")
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
fun ReceiptPrintScreen(
    imageUriFromIntent: Uri?,
) {
    val context = LocalContext.current
    val logs = remember { mutableStateListOf<String>() }

    var imageUri by remember { mutableStateOf(imageUriFromIntent) }

    val imageBitmap by produceState<Bitmap?>(initialValue = null, imageUri) {
        value = imageUri?.let { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
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
            title = { Text("Print Receipt from Image") },
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
                Button(onClick = {
                    context.startActivity(Intent(context, PrinterControlPanelActivity::class.java))
                }) {
                    Text("Printer Control Panel")
                }
            }

            item {
                Button(onClick = { imagePicker.launch("image/*") }) {
                    Text("Select Image")
                }
            }

            // 🔥 FIXED — image now shows from deep link
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
                Button(onClick = {
                    val bmp = imageBitmap ?: return@Button
                    if (!AppBluetoothManager.isConnected.value) return@Button

                    val resizedBitmap = bmp.copy(Bitmap.Config.ARGB_8888, true)
                    val imageCmd = EscPosUtils.createImageCommand(resizedBitmap)

                    AppBluetoothManager.printerHelper.sendBytes(imageCmd) { success ->
                        logs.add(if (success) "Print job sent." else "Failed to print image.")
                    }
                }) {
                    Text("Print Receipt")
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Text("Logs")
            }

            items(logs) { logEntry ->
                Text(logEntry, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
