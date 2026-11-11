package com.example.payrajpal

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

class SplitViewModel : ViewModel() {

    private val _banks = MutableStateFlow<List<BankAccount>>(emptyList())
    val banks: StateFlow<List<BankAccount>> = _banks.asStateFlow()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _session = MutableStateFlow(BillSplitSession())
    val session: StateFlow<BillSplitSession> = _session.asStateFlow()

    private val _receiptImageUri = MutableStateFlow<Uri?>(null)
    val receiptImageUri: StateFlow<Uri?> = _receiptImageUri.asStateFlow()

    private val _selectedMemberId = MutableStateFlow<String?>(null)
    val selectedMemberId: StateFlow<String?> = _selectedMemberId.asStateFlow()

    init {
        loadMockData()
    }

    private fun loadMockData() {
        _banks.value = listOf(
            BankAccount(bankName = "HDFC Bank •• 4821", accountNumberLast4 = "4821", balance = 24850.0, isPrimary = true),
            BankAccount(bankName = "SBI Savings •• 9012", accountNumberLast4 = "9012", balance = 8420.50, isPrimary = false)
        )

        _transactions.value = listOf(
            Transaction(title = "Aarav Sharma", subtitle = "Paid via PayPal / UPI", amount = 450.0, timestamp = "Today, 4:15 PM", isCredit = false),
            Transaction(title = "Riya Verma", subtitle = "Split Bill Settlement", amount = 620.0, timestamp = "Yesterday, 9:30 PM", isCredit = true),
            Transaction(title = "Third Wave Coffee", subtitle = "Scanned QR Payment", amount = 340.0, timestamp = "Nov 8, 11:20 AM", isCredit = false),
            Transaction(title = "Kabir Singh", subtitle = "Bank Transfer", amount = 1200.0, timestamp = "Nov 6, 6:05 PM", isCredit = true)
        )

        val m1 = Member(id = "m1", name = "You", contact = "shreyan@payrajpal", status = PaymentStatus.PAID)
        val m2 = Member(id = "m2", name = "Aarav", contact = "+91 9876543210")
        val m3 = Member(id = "m3", name = "Riya", contact = "riya@paypal.com")

        _session.value = BillSplitSession(
            merchantName = "Cyber Hub Bistro",
            tax = 145.0,
            tip = 100.0,
            members = listOf(m1, m2, m3),
            items = listOf(
                ReceiptItem(name = "Truffle Mushroom Pizza", price = 650.0, assignedTo = listOf("m1", "m2")),
                ReceiptItem(name = "Peri Peri Fries", price = 280.0, assignedTo = listOf("m1", "m2", "m3")),
                ReceiptItem(name = "Iced Matcha Latte", price = 320.0, assignedTo = listOf("m3")),
                ReceiptItem(name = "Classic Smash Burger", price = 490.0, assignedTo = listOf("m2"))
            )
        )
        _selectedMemberId.value = m2.id
    }

    fun addBankAccount(bankName: String, last4: String) {
        if (bankName.isBlank() || last4.length < 4) return
        val newBank = BankAccount(
            bankName = "$bankName •• ${last4.takeLast(4)}",
            accountNumberLast4 = last4.takeLast(4),
            balance = 15000.0,
            isPrimary = _banks.value.isEmpty()
        )
        _banks.update { it + newBank }
    }

    fun setPrimaryBank(id: String) {
        _banks.update { list -> list.map { it.copy(isPrimary = it.id == id) } }
    }

    fun sendDirectPayment(recipient: String, amount: Double, note: String) {
        if (recipient.isBlank() || amount <= 0.0) return
        val newTx = Transaction(
            title = recipient.trim(),
            subtitle = if (note.isBlank()) "Direct PayRajpal Transfer" else note.trim(),
            amount = amount,
            timestamp = "Just now",
            isCredit = false
        )
        _transactions.update { listOf(newTx) + it }
        _banks.update { list ->
            list.map { if (it.isPrimary) it.copy(balance = (it.balance - amount).coerceAtLeast(0.0)) else it }
        }
    }

    fun setReceiptImage(uri: Uri?) {
        _receiptImageUri.value = uri
    }

    fun selectMember(id: String) {
        _selectedMemberId.value = id
    }

    fun addMember(name: String, contact: String) {
        if (name.isBlank() || contact.isBlank()) return
        val newMember = Member(name = name.trim(), contact = contact.trim())
        _session.update { it.copy(members = it.members + newMember) }
        _selectedMemberId.value = newMember.id
    }

    fun toggleItemAssignment(itemId: String) {
        val activeMember = _selectedMemberId.value ?: return
        _session.update { current ->
            val updatedItems = current.items.map { item ->
                if (item.id == itemId) {
                    val updatedList = if (activeMember in item.assignedTo) item.assignedTo - activeMember else item.assignedTo + activeMember
                    item.copy(assignedTo = updatedList)
                } else item
            }
            current.copy(items = updatedItems)
        }
    }

    fun updateItem(itemId: String, newName: String, newPrice: Double) {
        _session.update { current ->
            val updatedItems = current.items.map {
                if (it.id == itemId) it.copy(name = newName, price = newPrice) else it
            }
            current.copy(items = updatedItems)
        }
    }

    fun calculateMemberShare(memberId: String): Double {
        val s = _session.value
        val itemsSubtotal = s.items.sumOf { it.price }
        if (itemsSubtotal == 0.0) return 0.0

        val memberSubtotal = s.items.sumOf { item ->
            if (memberId in item.assignedTo && item.assignedTo.isNotEmpty()) {
                item.price / item.assignedTo.size
            } else 0.0
        }

        val proportion = memberSubtotal / itemsSubtotal
        val total = memberSubtotal + ((s.tax + s.tip) * proportion)
        return (total * 100.0).roundToInt() / 100.0
    }
}