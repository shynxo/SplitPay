package com.example.payrajpal

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Shown to the user as-is. */
class ApiException(message: String) : Exception(message)

/**
 * Who is logged in. Lives for the whole app process; screens just read Session.name / Session.email
 * (they recompose by themselves when these change).
 */
object Session {
    private const val PREFS = "payrajpal_session"
    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var token by mutableStateOf<String?>(null)
        private set
    var name by mutableStateOf("")
        private set
    var email by mutableStateOf("")
        private set
    var loginError by mutableStateOf<String?>(null)

    val loggedIn: Boolean get() = token != null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        token = p.getString("token", null)
        name = p.getString("name", "") ?: ""
        email = p.getString("email", "") ?: ""
    }

    /** payrajpal://auth?token=…  (or ?error=…) — opened by the server after Log in with PayPal. */
    fun onAuthLink(uri: Uri) {
        val t = uri.getQueryParameter("token")
        if (t.isNullOrBlank()) {
            loginError = when (uri.getQueryParameter("error")) {
                "cancelled" -> "Login cancelled"
                "expired" -> "Login took too long. Try again."
                else -> "PayPal login failed. Try again."
            }
            return
        }
        loginError = null
        token = t
        prefs?.edit()?.putString("token", t)?.apply()
        scope.launch {
            try {
                val me = PayApi.me()
                name = me.optString("name")
                email = me.optString("email")
                prefs?.edit()?.putString("name", name)?.putString("email", email)?.apply()
            } catch (_: Exception) { /* PayApi already logs out on 401 */ }
        }
    }

    fun logout() {
        val old = token
        token = null
        name = ""
        email = ""
        prefs?.edit()?.clear()?.apply()
        if (old != null) scope.launch { try { PayApi.logout(old) } catch (_: Exception) { } }
    }
}

/** Calls to the PayRajpal server. All suspend + main-safe. */
object PayApi {
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)   // a sleeping free server needs a moment to wake
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private suspend fun call(method: String, path: String, body: JSONObject? = null, tokenOverride: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val t = tokenOverride ?: Session.token
            val req = Request.Builder()
                .url(AppConfig.SERVER_URL + path)
                .apply { if (t != null) header("Authorization", "Bearer $t") }
                .method(method, if (method == "GET") null else (body ?: JSONObject()).toString().toRequestBody(JSON))
                .build()
            try {
                http.newCall(req).execute().use { res ->
                    val text = res.body?.string().orEmpty()
                    val json = try { JSONObject(text) } catch (_: Exception) { JSONObject() }
                    if (res.code == 401 && tokenOverride == null) {
                        withContext(Dispatchers.Main) { Session.logout() }
                        throw ApiException("Please log in again")
                    }
                    if (!res.isSuccessful) throw ApiException(json.optString("error").ifBlank { "Server error (${res.code})" })
                    json
                }
            } catch (e: IOException) {
                throw ApiException("Can't reach the server. Check your internet connection.")
            }
        }

    suspend fun me(): JSONObject = call("GET", "/api/me")
    suspend fun logout(token: String) { call("POST", "/api/logout", tokenOverride = token) }

    /** Starts a PayPal payment. Returns (paymentId, approveUrl). */
    suspend fun createPayment(amountMinor: Long, email: String, name: String, note: String): Pair<String, String> {
        val r = call("POST", "/api/pay", JSONObject().put("amountMinor", amountMinor).put("recipientEmail", email).put("recipientName", name).put("note", note))
        return r.getString("paymentId") to r.getString("approveUrl")
    }

    /** Returns (status, error). */
    suspend fun paymentStatus(id: String): Pair<String, String?> {
        val r = call("GET", "/api/pay/$id")
        return r.getString("status") to r.optString("error").ifBlank { null }
    }

    /** A link anyone can open to pay you. */
    suspend fun createRequest(amountMinor: Long, note: String): String =
        call("POST", "/api/request", JSONObject().put("amountMinor", amountMinor).put("note", note)).getString("payUrl")

    suspend fun history(): List<Transaction> {
        val arr = call("GET", "/api/history").getJSONArray("transactions")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val ms = o.optLong("epochMillis")
            Transaction(
                id = o.getString("id"),
                title = o.getString("title"),
                subtitle = o.optString("subtitle"),
                amount = o.getDouble("amount"),
                timestamp = friendlyTime(ms),
                isCredit = o.optBoolean("isCredit"),
                epochMillis = ms,
                email = o.optString("email")
            )
        }
    }
}

/** Reloads the real payment history into the app. Safe to call any time. */
suspend fun refreshHistory(vm: SplitViewModel) {
    if (!Session.loggedIn) return
    try { vm.setTransactions(PayApi.history()) } catch (_: Exception) { /* keep what we have */ }
}

fun friendlyTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = epochMillis }
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(then.time)
    fun sameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameDay(now, then) -> "Today, $time"
        sameDay(yesterday, then) -> "Yesterday, $time"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(then.time) + ", " + time
    }
}
