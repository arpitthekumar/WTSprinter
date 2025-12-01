package com.example.wts.ui.receipt

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
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
import androidx.compose.ui.text.style.TextAlign
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

    val barcodeBitmap: Bitmap? = remember(receipt.barcode) {
        try {
            if (receipt.barcode.isNotBlank()) {
                val pure = receipt.barcode.substringAfter(",")
                val bytes = Base64.decode(pure, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editable Receipt") },
                actions = { PrinterStatusIcon() }
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF6F6F6))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // PREVIEW
            item {
                ReceiptPreview(receipt.copy(items = items), barcodeBitmap)
            }

            item { Spacer(Modifier.height(16.dp)) }

            // BUTTON NODES
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        // SELECT PRINTER
                        Button(
                            onClick = {
                                if (PermissionUtils.hasBluetoothPermissions(context)) {
                                    printerPicker.launch(Intent(context, BluetoothDevicePickerActivity::class.java))
                                } else {
                                    permissionLauncher.launch(PermissionUtils.getRequiredBluetoothPermissions())
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Select Printer") }

                        // -------------------------------
                        // THREE PRINT BUTTONS FOR TESTING
                        // -------------------------------


                        Button(
                            onClick = {
                                val finalReceipt = receipt.copy(items = items)
                                val bytes = EscPosUtils.formatReceiptB(finalReceipt)
                                AppBluetoothManager.printerHelper.sendBytes(bytes)
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) { Text("Print Style B (Compact)") }


                        // EXTRA COMMANDS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    AppBluetoothManager.printerHelper.sendBytes(EscPosUtils.getPrintAndFeed(5))
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Feed") }

                            Button(
                                onClick = {
                                    AppBluetoothManager.printerHelper.sendBytes(EscPosUtils.getCutCommand())
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Cut") }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }

            // Editable fields
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Field("Invoice Number", receipt.invoiceNumber) {
                        receipt = receipt.copy(invoiceNumber = it)
                    }
                    Field("Customer Name", receipt.customerName) {
                        receipt = receipt.copy(customerName = it)
                    }
                    Field("Customer Phone", receipt.customerPhone) {
                        receipt = receipt.copy(customerPhone = it)
                    }
                    Field("Date", receipt.date) {
                        receipt = receipt.copy(date = it)
                    }
                    Field("Time", receipt.time) {
                        receipt = receipt.copy(time = it)
                    }
                    Field("Payment Method", receipt.paymentMethod) {
                        receipt = receipt.copy(paymentMethod = it)
                    }
                    Field("Discount", receipt.discount) {
                        receipt = receipt.copy(discount = it)
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            // Items List
            item {
                Text("Items", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                            Text(
                                "Item ${index + 1}",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { items.removeAt(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }

                        Field("Name", item.name) {
                            items[index] = item.copy(name = it)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FieldSmall("Qty", item.qty.toString(), Modifier.weight(1f)) { newQty ->
                                val q = newQty.toIntOrNull() ?: 1
                                items[index] = item.copy(qty = q)
                            }
                            FieldSmall("Rate", item.price, Modifier.weight(1f)) { newRate ->
                                items[index] = item.copy(price = newRate)
                            }
                            FieldSmall("Amount", item.total, Modifier.weight(1f)) { newAmt ->
                                items[index] = item.copy(total = newAmt)
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        items.add(ItemData(name = "New Item", qty = 1, price = "0", total = "0"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add Item") }
            }

            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

@Composable
fun ReceiptPreview(receipt: ReceiptData, barcodeBitmap: Bitmap?) {
    Box(
        modifier = Modifier
            .widthIn(min = 280.dp, max = 360.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(BorderStroke(2.dp, Color.Black), RoundedCornerShape(6.dp))
            .background(Color.White)
            .padding(14.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Text("Bhootiya Fabric", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("Collection", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Moti Ganj, Bakebar Road, Bharthana", fontSize = 10.sp)
            Text("Ph: +91 82736 89065", fontSize = 10.sp)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            BoldRow("Invoice:", receipt.invoiceNumber)
            BoldRow("Customer:", receipt.customerName)
            BoldRow("Phone:", receipt.customerPhone)

            Row(Modifier.fillMaxWidth()) {
                Text("Date:", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(receipt.date, fontSize = 12.sp)
                Spacer(Modifier.width(12.dp))
                Text("Time:", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(receipt.time, fontSize = 12.sp)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Row(Modifier.fillMaxWidth()) {
                Text("Item", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("Qty", Modifier.width(40.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
                Text("Rate", Modifier.width(60.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
                Text("Amt", Modifier.width(60.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(Modifier.padding(vertical = 6.dp))

            receipt.items.forEach {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(it.name, Modifier.weight(1f), fontSize = 12.sp)
                    Text(it.qty.toString(), Modifier.width(40.dp), textAlign = TextAlign.End, fontSize = 12.sp)
                    Text("₹${it.price}", Modifier.width(60.dp), textAlign = TextAlign.End, fontSize = 12.sp)
                    Text("₹${it.total}", Modifier.width(60.dp), textAlign = TextAlign.End, fontSize = 12.sp)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            BoldRow("Subtotal:", "₹${receipt.subtotal}")
            if (safeAmount(receipt.discount) > 0) {
                BoldRow("Discount:", "₹${receipt.discount}")
            }
            BoldRow("Total:", "₹${receipt.total}")
            BoldRow("Payment:", receipt.paymentMethod)

            barcodeBitmap?.let { bmp ->
                Spacer(Modifier.height(10.dp))
                Image(
                    bmp.asImageBitmap(),
                    contentDescription = "Barcode",
                    modifier = Modifier.fillMaxWidth().height(80.dp)
                )
            }

            Spacer(Modifier.height(10.dp))
            Text("Thank you, Visit Again!", fontWeight = FontWeight.Bold)
        }
    }
}


@Composable
fun Field(label: String, value: String, onValue: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun RowScope.FieldSmall(label: String, value: String, modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        label = { Text(label) },
        modifier = modifier
    )
}

@Composable
fun BoldRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Text(value, fontSize = 12.sp)
    }
}

private fun safeAmount(v: String): Double =
    v.replace("₹", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0

private fun formatAmount(v: Double): String = "%.2f".format(v)

private fun currentDate(): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

private fun currentTime(): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
