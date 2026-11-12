package com.example.payrajpal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class BankAccount(
    val id: String = UUID.randomUUID().toString(),
    val bankName: String,
    val accountNumberLast4: String,
    val balance: Double,
    val isPrimary: Boolean = false
)

@Serializable
data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String,
    val amount: Double,
    val timestamp: String,
    val isCredit: Boolean = false
)

@Serializable
data class ReceiptItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val price: Double,
    @SerialName("assigned_to") val assignedTo: List<String> = emptyList()
)

@Serializable
data class Member(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val contact: String,
    val email: String? = null,
    val status: PaymentStatus = PaymentStatus.PENDING
)

fun Member.resolvedEmail(): String {
    val e = email?.trim()
    if (!e.isNullOrBlank()) return e
    val c = contact.trim()
    if (c.contains("@")) return c
    return "sb-5myms53858328@personal.example.com"
}

@Serializable
enum class PaymentStatus { PENDING, PAID, OVERDUE }

@Serializable
data class BillSplitSession(
    @SerialName("bill_id") val billId: String = UUID.randomUUID().toString(),
    @SerialName("merchant_name") val merchantName: String = "Scanned Receipt",
    val items: List<ReceiptItem> = emptyList(),
    val tax: Double = 0.0,
    val service: Double = 0.0,
    val tip: Double = 0.0,
    val members: List<Member> = emptyList()
)