package com.example.payrajpal

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface PayPhase {
    object Idle : PayPhase
    object Creating : PayPhase      // asking the server for a PayPal order / link
    object InBrowser : PayPhase     // user is approving in PayPal
    object Confirming : PayPhase    // back in the app, checking the result
    object Done : PayPhase
    data class Failed(val message: String) : PayPhase
}

/**
 * One real PayPal payment (or money-request link) at a time.
 * The server captures the money and forwards it to the recipient; the app only opens PayPal and listens
 * for splitpay://paypal/return.
 */
object PayPalPay {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pendingId: String? = null
    private var job: Job? = null

    var phase by mutableStateOf<PayPhase>(PayPhase.Idle)
        private set

    /** The most recent request link, kept so the success screen can show Share / Copy. */
    var lastLink by mutableStateOf<String?>(null)
        private set

    fun fail(message: String) { phase = PayPhase.Failed(message) }
    fun reset() { job?.cancel(); pendingId = null; phase = PayPhase.Idle }

    fun start(context: Context, amountMinor: Long, email: String, name: String, note: String) {
        if (phase == PayPhase.Creating || phase == PayPhase.InBrowser || phase == PayPhase.Confirming) return
        phase = PayPhase.Creating
        job = scope.launch {
            try {
                val (id, url) = PayApi.createPayment(amountMinor, email, name, note)
                pendingId = id
                phase = PayPhase.InBrowser
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                phase = PayPhase.Failed(e.message ?: "Couldn't start the payment")
            }
        }
    }

    /** "Request money": makes a link and opens the share sheet. */
    fun request(context: Context, amountMinor: Long, note: String, message: (String) -> String) {
        if (phase == PayPhase.Creating) return
        phase = PayPhase.Creating
        lastLink = null
        job = scope.launch {
            try {
                val url = PayApi.createRequest(amountMinor, note)
                lastLink = url
                shareText(context, message(url))
                phase = PayPhase.Done
            } catch (e: Exception) {
                phase = PayPhase.Failed(e.message ?: "Couldn't create the link")
            }
        }
    }

    /** splitpay://paypal/return?pid=…&status=… */
    fun onReturnLink(uri: Uri) {
        val pid = uri.getQueryParameter("pid") ?: return
        pendingId = pid
        if (uri.getQueryParameter("status") == "cancelled") phase = PayPhase.Failed("Payment cancelled. No money was taken.")
        else confirm(pid)
    }

    /** Back in the app after PayPal's page: check the result without making the user tap anything. */
    fun onResume() { if (phase == PayPhase.InBrowser) check() }

    /** The "I've paid — check status" button. */
    fun check() { pendingId?.let { confirm(it) } }

    private fun confirm(pid: String) {
        job?.cancel()
        phase = PayPhase.Confirming
        job = scope.launch {
            repeat(15) {
                try {
                    val (status, error) = PayApi.paymentStatus(pid)
                    when (status) {
                        "delivered" -> { phase = PayPhase.Done; return@launch }
                        "capture_failed", "payout_failed" -> { phase = PayPhase.Failed(error ?: "PayPal couldn't complete this payment"); return@launch }
                        "cancelled" -> { phase = PayPhase.Failed("Payment cancelled. No money was taken."); return@launch }
                        "created" -> { phase = PayPhase.InBrowser; return@launch }
                    }
                } catch (e: ApiException) {
                    if (!Session.loggedIn) { phase = PayPhase.Failed("Please log in again"); return@launch }
                }
                delay(1500)
            }
            phase = PayPhase.Failed("PayPal is taking longer than usual. Check your PayPal activity before trying again.")
        }
    }
}