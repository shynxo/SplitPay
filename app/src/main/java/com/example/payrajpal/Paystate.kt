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
    object Split : Route
    object Security : Route
    object ChangePin : Route
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
    REQUEST("Request money", "Request", true)
}

/** [phone] holds the PayPal email (kept as "phone" so old call sites compile). */
data class Contact(val name: String, val phone: String)

/* ───────────────────────────── UI STATE (mock until the PayPal backend lands) ───────────────────────────── */

class PayUiViewModel : ViewModel() {
    val nav = Navigator()
}
