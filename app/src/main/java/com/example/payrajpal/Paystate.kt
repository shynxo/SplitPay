package com.example.payrajpal

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/* ───────────────────────────── ROUTES ───────────────────────────── */

sealed interface Route {
    object Home : Route
    object History : Route
    object Autopays : Route
    object Wallet : Route
    object AddCard : Route
    object TapToPay : Route
    object Split : Route
    object Security : Route
    object Scan : Route
    data class Pay(val kind: PayKind, val recipient: String = "", val email: String = "") : Route
}

/** Tiny back-stack. Lives in [PayUiViewModel] so it survives rotation. */
@Stable
class Navigator {
    val stack = mutableStateListOf<Route>(Route.Home)

    var forward by mutableStateOf(true)
        private set

    val current: Route get() = stack.last()

    fun push(route: Route) {
        forward = true
        stack.add(route)
    }

    fun replace(route: Route) {
        forward = true
        stack[stack.lastIndex] = route
    }

    fun pop() {
        if (stack.size > 1) {
            forward = false
            stack.removeAt(stack.lastIndex)
        }
    }
}

/* ───────────────────────────── MODELS ───────────────────────────── */

enum class PayKind(val title: String, val cta: String, val isRequest: Boolean = false) {
    PAY("Pay contact", "Pay"),
    REQUEST("Request money", "Request", true),
    BANK("Bank transfer", "Transfer"),
    RECHARGE("Mobile recharge", "Recharge"),
    BILLS("Pay bills", "Pay bill")
}

data class Contact(val name: String, val phone: String)

/** Only the last 4 digits are ever kept. The full card number must be tokenised by PayPal. */
data class PayCard(
    val id: String,
    val network: String,
    val last4: String,
    val holder: String,
    val expiry: String,
    val isCredit: Boolean,
    val isDefault: Boolean = false,
    val frozen: Boolean = false,
    val tapEnabled: Boolean = true
)

data class Autopay(
    val id: String,
    val name: String,
    val category: String,
    val amount: Double,
    val frequency: String,
    val nextDate: String,
    val source: String,
    val active: Boolean = true
)

/* ───────────────────────────── UI STATE (mock until the PayPal backend lands) ───────────────────────────── */

class PayUiViewModel : ViewModel() {

    val nav = Navigator()

    val cards = mutableStateListOf(
        PayCard("c1", "VISA", "4242", "SHREYAN", "08/29", isCredit = false, isDefault = true),
        PayCard("c2", "MASTERCARD", "8891", "SHREYAN", "02/28", isCredit = true)
    )

    val autopays = mutableStateListOf(
        Autopay("a1", "Netflix", "Entertainment", 649.0, "Monthly", "12 Nov", "HDFC Bank"),
        Autopay("a2", "Jio Prepaid", "Mobile", 299.0, "Every 28 days", "18 Nov", "HDFC Bank"),
        Autopay("a3", "Electricity · BSES", "Electricity", 1420.0, "Monthly", "5 Dec", "HDFC Bank"),
        Autopay("a4", "Rent · Mr. Verma", "Rent", 15000.0, "Monthly", "1 Dec", "HDFC Bank")
    )

    // Tap to pay
    var tapEnabled by mutableStateOf(true)
    var requireUnlock by mutableStateOf(true)

    // Security
    var appLock by mutableStateOf(true)
    var notifications by mutableStateOf(true)
    var dailyLimit by mutableIntStateOf(50000)
    var lockAfter by mutableIntStateOf(1) // 0 = immediately, 1 = 1 min, 2 = 5 min

    /* ── cards ── */

    fun addCard(card: PayCard) {
        val makeDefault = card.isDefault || cards.isEmpty()
        if (makeDefault) {
            for (i in cards.indices) cards[i] = cards[i].copy(isDefault = false)
        }
        cards.add(0, card.copy(isDefault = makeDefault))
    }

    fun setDefaultCard(id: String) {
        for (i in cards.indices) cards[i] = cards[i].copy(isDefault = cards[i].id == id)
    }

    fun toggleFreeze(id: String) {
        val i = cards.indexOfFirst { it.id == id }
        if (i >= 0) cards[i] = cards[i].copy(frozen = !cards[i].frozen)
    }

    fun toggleCardTap(id: String) {
        val i = cards.indexOfFirst { it.id == id }
        if (i >= 0) cards[i] = cards[i].copy(tapEnabled = !cards[i].tapEnabled)
    }

    fun removeCard(id: String) {
        val wasDefault = cards.firstOrNull { it.id == id }?.isDefault == true
        cards.removeAll { it.id == id }
        if (wasDefault && cards.isNotEmpty()) cards[0] = cards[0].copy(isDefault = true)
    }

    /* ── autopay ── */

    fun addAutopay(name: String, amount: Double, frequency: String, source: String) {
        autopays.add(
            0,
            Autopay(
                id = UUID.randomUUID().toString(),
                name = name,
                category = guessCategory(name),
                amount = amount,
                frequency = frequency,
                nextDate = nextDebitDate(frequency),
                source = source
            )
        )
    }

    fun toggleAutopay(id: String) {
        val i = autopays.indexOfFirst { it.id == id }
        if (i >= 0) autopays[i] = autopays[i].copy(active = !autopays[i].active)
    }

    fun cancelAutopay(id: String) {
        autopays.removeAll { it.id == id }
    }
}

/* ───────────────────────────── HELPERS ───────────────────────────── */

fun guessCategory(name: String): String {
    val n = name.lowercase(Locale.ROOT)
    return when {
        listOf("netflix", "spotify", "prime", "hotstar", "youtube").any { n.contains(it) } -> "Entertainment"
        listOf("jio", "airtel", "vi ", "bsnl", "recharge", "mobile").any { n.contains(it) } -> "Mobile"
        listOf("electric", "bses", "power", "energy").any { n.contains(it) } -> "Electricity"
        listOf("rent", "landlord").any { n.contains(it) } -> "Rent"
        listOf("sip", "mutual", "fund", "invest").any { n.contains(it) } -> "Investment"
        else -> "Bill"
    }
}

fun nextDebitDate(frequency: String): String {
    val cal = Calendar.getInstance()
    when (frequency) {
        "Weekly" -> cal.add(Calendar.DAY_OF_YEAR, 7)
        "Yearly" -> cal.add(Calendar.YEAR, 1)
        else -> cal.add(Calendar.MONTH, 1)
    }
    return SimpleDateFormat("d MMM", Locale.getDefault()).format(cal.time)
}

/** Rough per-month figure so the autopay summary can add different frequencies together. */
fun Autopay.monthlyEquivalent(): Double = when (frequency) {
    "Weekly" -> amount * 4.33
    "Yearly" -> amount / 12.0
    "Every 28 days" -> amount * 1.086
    else -> amount
}