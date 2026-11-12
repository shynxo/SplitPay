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
        PinStore.clear()
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
                    if (!res.isSuccessful) {
                        val msg = json.optString("error")
                        if (msg.isNotBlank()) throw ApiException(msg)
                        // Render answers 404/502/503/504 with a plain page while the free server wakes up or redeploys.
                        if (res.code in listOf(404, 502, 503, 504)) throw ApiException("The server is waking up. Wait a minute and try again.")
                        throw ApiException("Server error (${res.code})")
                    }
                    json
                }
            } catch (e: IOException) {
                throw ApiException("Can't reach the server. Check your internet connection.")
            }
        }

    /** Wakes the free server up (it sleeps when idle) so the first real tap isn't slow. */
    fun warmUp() {
        try {
            val req = Request.Builder().url(AppConfig.SERVER_URL + "/health").build()
            http.newCall(req).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {}
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.close() }
            })
        } catch (_: Exception) { }
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
    /** One payment link per friend. shares = (memberId, name, amountMinor). Returns (splitId, memberId -> link). */
    suspend fun createSplit(title: String, shares: List<Triple<String, String, Long>>): Pair<String, Map<String, String>> {
        val arr = org.json.JSONArray()
        shares.forEach { (id, name, amt) -> arr.put(JSONObject().put("memberId", id).put("name", name).put("amountMinor", amt)) }
        val r = call("POST", "/api/split", JSONObject().put("title", title).put("shares", arr))
        val ms = r.getJSONArray("members")
        val links = (0 until ms.length()).associate { ms.getJSONObject(it).getString("memberId") to ms.getJSONObject(it).getString("payUrl") }
        return r.getString("splitId") to links
    }

    /** Who has paid so far: memberId -> paid, and the total collected in rupees. */
    suspend fun splitStatus(id: String): Pair<Map<String, Boolean>, Double> {
        val r = call("GET", "/api/split/$id")
        val ms = r.getJSONArray("members")
        val paid = (0 until ms.length()).associate { ms.getJSONObject(it).getString("memberId") to (ms.getJSONObject(it).getString("status") == "PAID") }
        return paid to r.optLong("collectedMinor") / 100.0
    }

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

/** Keeps the payment list fresh while a screen is showing (picks up money people paid you). */
@androidx.compose.runtime.Composable
fun AutoRefreshHistory(vm: SplitViewModel, everyMs: Long = 12_000L) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            refreshHistory(vm)
            kotlinx.coroutines.delay(everyMs)
        }
    }
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
