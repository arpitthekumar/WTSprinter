package com.example.wts.ui.receipt

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wts.bluetooth.AppBluetoothManager
import com.example.wts.bluetooth.BluetoothDevicePickerActivity
import com.example.wts.model.ItemData
import com.example.wts.model.ReceiptData
import com.example.wts.permissions.PermissionUtils
import com.example.wts.ui.common.PrinterStatusIcon
import com.example.wts.utils.EscPosUtils
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
class EditableReceiptActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val json = intent.getStringExtra("receipt_json")
        if (json == null) {
            finish()
            return
        }

        val receipt = Json.decodeFromString<ReceiptData>(json)

        setContent {
            EditableReceiptScreen(receipt)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditableReceiptScreen(initialReceipt: ReceiptData) {
    val context = LocalContext.current

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

    var receipt by remember { mutableStateOf(initialReceipt) }
    val items = remember { receipt.items.toMutableStateList() }

    val subtotal by derivedStateOf { items.sumOf { safeAmount(it.total) } }
    val discount by derivedStateOf { safeAmount(receipt.discount) }
    val total by derivedStateOf { (subtotal - discount).coerceAtLeast(0.0) }

    LaunchedEffect(subtotal, discount) {
        receipt = receipt.copy(
            subtotal = formatAmount(subtotal),
            total = formatAmount(total)
        )
    }

    val receiptBitmap: Bitmap = remember(receipt, items.toList()) {
        ReceiptPreviewGenerator.generatePreview(receipt.copy(items = items))
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Editable Receipt") }, actions = { PrinterStatusIcon() }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF6F6F6))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            item {
                Image(
                    bitmap = receiptBitmap.asImageBitmap(),
                    contentDescription = "Receipt Preview",
                    modifier = Modifier
                        .border(BorderStroke(2.dp, Color.Black), RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))

                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (PermissionUtils.hasBluetoothPermissions(context)) {
                                    printerPicker.launch(Intent(context, BluetoothDevicePickerActivity::class.java))
                                } else {
                                    permissionLauncher.launch(PermissionUtils.getRequiredBluetoothPermissions())
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Select Printer")
                        }

                        Button(
                            onClick = {
                                val finalReceipt = receipt.copy(items = items)
                                val commands = EscPosUtils.formatReceipt(finalReceipt)
                                AppBluetoothManager.printerHelper.sendBytes(commands)
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text("Print Receipt", fontSize = 16.sp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val commands = EscPosUtils.getPrintAndFeed(5)
                                    AppBluetoothManager.printerHelper.sendBytes(commands)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Feed Paper")
                            }
                            Button(
                                onClick = {
                                    val commands = EscPosUtils.getCutCommand()
                                    AppBluetoothManager.printerHelper.sendBytes(commands)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cut Paper")
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Editable fields
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Field(label = "Invoice Number", value = receipt.invoiceNumber) {
                        receipt = receipt.copy(invoiceNumber = it)
                    }
                    Field(label = "Customer Name", value = receipt.customerName) {
                        receipt = receipt.copy(customerName = it)
                    }
                    Field(label = "Customer Phone", value = receipt.customerPhone) {
                        receipt = receipt.copy(customerPhone = it)
                    }
                    Field(label = "Date", value = receipt.date) {
                        receipt = receipt.copy(date = it)
                    }
                    Field(label = "Time", value = receipt.time) {
                        receipt = receipt.copy(time = it)
                    }
                    Field(label = "Payment Method", value = receipt.paymentMethod) {
                        receipt = receipt.copy(paymentMethod = it)
                    }
                    Field(label = "Discount", value = receipt.discount) {
                        receipt = receipt.copy(discount = it)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                Text("Items", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            itemsIndexed(items) { index, item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(3.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Item ${index + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { items.removeAt(index) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }

                        Field(label = "Name", value = item.name) {
                            items[index] = item.copy(name = it)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FieldSmall(label = "Qty", value = item.qty.toString(), modifier = Modifier.weight(1f)) { newQty ->
                                val q = newQty.toIntOrNull() ?: 1
                                items[index] = item.copy(qty = q)
                            }
                            FieldSmall(label = "Rate", value = item.price, modifier = Modifier.weight(1f)) { newRate ->
                                items[index] = item.copy(price = newRate)
                            }
                            FieldSmall(label = "Amount", value = item.total, modifier = Modifier.weight(1f)) { newAmt ->
                                items[index] = item.copy(total = newAmt)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { 
                    items.add(ItemData(name = "New Item", qty = 1, price = "0", total = "0"))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add Item")
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }


            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}


@Composable
fun Field(label: String, value: String, onValue: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        OutlinedTextField(value = value, onValueChange = onValue, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun RowScope.FieldSmall(label: String, value: String, modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValue, singleLine = true, label = { Text(label) }, modifier = modifier)
}

@Composable
fun BoldRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(value, fontSize = 12.sp)
    }
}

private fun safeAmount(v: String): Double = v.replace("₹", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0

private fun formatAmount(v: Double): String {
    return "%.2f".format(v)
}

private fun currentDate(): String {
    val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return fmt.format(Date())
}

private fun currentTime(): String {
    val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    return fmt.format(Date())
}
