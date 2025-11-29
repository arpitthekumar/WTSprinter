package com.example.wts.model

import kotlinx.serialization.Serializable

@Serializable
data class FastReceipt(
    val shopName: String,
    val addressLines: List<String>,
    val invoiceInfo: String,
    val customerName: String,
    val customerPhone: String,
    val date: String,
    val time: String,
    val items: List<ReceiptItem>,
    val subtotal: String,
    val discount: String,
    val total: String,
    val paymentMethod: String,
    val barcode: String,
    val footer: String
)

@Serializable
data class ReceiptItem(
    val name: String,
    val quantity: Int,
    val price: String
)
