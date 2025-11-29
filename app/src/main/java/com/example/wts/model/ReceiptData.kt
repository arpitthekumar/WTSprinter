package com.example.wts.model

import kotlinx.serialization.Serializable

@Serializable
data class ReceiptData(
    val invoiceNumber: String,
    val customerName: String,
    val customerPhone: String,
    val date: String,
    val time: String,
    val paymentMethod: String,
    val items: List<ItemData>,
    val subtotal: String,
    val discount: String,
    val total: String,
    val barcode: String
)

@Serializable
data class ItemData(
    val name: String,
    val qty: Int,
    val price: String,
    val total: String
)
