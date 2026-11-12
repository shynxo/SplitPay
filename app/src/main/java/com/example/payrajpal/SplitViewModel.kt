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

    private val _splitId = MutableStateFlow<String?>(null)
    val splitId: StateFlow<String?> = _splitId.asStateFlow()

    private val _serverBreakdowns = MutableStateFlow<Map<String, Double>>(emptyMap())
    val serverBreakdowns: StateFlow<Map<String, Double>> = _serverBreakdowns.asStateFlow()

    private val _collectedAmount = MutableStateFlow<Double>(0.0)
    val collectedAmount: StateFlow<Double> = _collectedAmount.asStateFlow()

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

        // Fresh bill: only "You" is in the list. Friends are added with the + button, receipt items come from the scan.
        val m1 = Member(
            id = "m1",
            name = "You",
            contact = "you@payrajpal",
            email = "sb-5myms53858328@personal.example.com",
            status = PaymentStatus.PAID
        )

        _session.value = BillSplitSession(
            merchantName = "Scanned Receipt",
            tax = 0.0,
            service = 0.0,
            tip = 0.0,
            members = listOf(m1),
            items = emptyList()
        )
        _selectedMemberId.value = m1.id
    }

    /** Forget any previously sent split so old amounts/statuses never leak into a new request. */
    private fun resetSendState() {
        _splitId.value = null
        _serverBreakdowns.value = emptyMap()
        _collectedAmount.value = 0.0
        _session.update { cur ->
            cur.copy(
                members = cur.members.map {
                    it.copy(status = if (it.id == "m1") PaymentStatus.PAID else PaymentStatus.PENDING)
                }
            )
        }
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

    fun setSplitId(id: String?) {
        _splitId.value = id
    }

    fun setServerBreakdowns(map: Map<String, Double>) {
        _serverBreakdowns.value = map
    }

    fun setCollectedAmount(amount: Double) {
        _collectedAmount.value = amount
    }

    fun addMember(name: String, contact: String, email: String? = null) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        val trimmedContact = contact.trim()
        val trimmedEmail = email?.trim()?.ifBlank { null }

        val finalContact = trimmedContact.ifBlank { trimmedEmail ?: "" }
        val finalEmail = trimmedEmail ?: if (trimmedContact.contains("@")) trimmedContact else "sb-5myms53858328@personal.example.com"

        val newMember = Member(
            id = java.util.UUID.randomUUID().toString(),
            name = trimmedName,
            contact = finalContact,
            email = finalEmail
        )
        resetSendState()
        _session.update { it.copy(members = it.members + newMember) }
        _selectedMemberId.value = newMember.id
    }

    fun updateReceiptFromBackend(
        billId: String,
        merchantName: String,
        items: List<ReceiptItem>,
        tax: Double,
        service: Double,
        tip: Double
    ) {
        resetSendState()
        _session.update { current ->
            current.copy(
                billId = if (billId.isNotBlank()) billId else current.billId,
                merchantName = if (merchantName.isNotBlank()) merchantName else current.merchantName,
                items = items,
                tax = tax,
                service = service,
                tip = tip
            )
        }
    }

    fun updateMemberStatuses(statuses: Map<String, PaymentStatus>) {
        _session.update { current ->
            val updatedMembers = current.members.map { m ->
                val newStatus = statuses[m.id]
                if (newStatus != null) m.copy(status = newStatus) else m
            }
            current.copy(members = updatedMembers)
        }
    }

    fun toggleItemAssignment(itemId: String) {
        val activeMember = _selectedMemberId.value ?: return
        resetSendState()
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
        resetSendState()
        _session.update { current ->
            val updatedItems = current.items.map {
                if (it.id == itemId) it.copy(name = newName, price = newPrice) else it
            }
            current.copy(items = updatedItems)
        }
    }

    fun calculateMemberShare(memberId: String): Double {
        val serverShare = _serverBreakdowns.value[memberId]
        if (serverShare != null) return serverShare

        val s = _session.value
        val itemsSubtotal = s.items.sumOf { it.price }
        if (itemsSubtotal == 0.0) return 0.0

        val memberSubtotal = s.items.sumOf { item ->
            if (memberId in item.assignedTo && item.assignedTo.isNotEmpty()) {
                item.price / item.assignedTo.size
            } else 0.0
        }

        val proportion = memberSubtotal / itemsSubtotal
        val total = memberSubtotal + ((s.tax + s.service + s.tip) * proportion)
        return (total * 100.0).roundToInt() / 100.0
    }
}