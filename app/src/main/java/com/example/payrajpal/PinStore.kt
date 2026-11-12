package com.example.payrajpal

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The one PIN used to open the app and to confirm every payment.
 * Only a salted SHA-256 hash is stored on the phone, never the PIN itself.
 * 5 wrong tries in a row lock PIN entry for 30 seconds.
 */
object PinStore {
    private const val FILE = "payrajpal_pin"
    private const val MAX_TRIES = 5
    private const val LOCK_MS = 30_000L

    private var prefs: SharedPreferences? = null
    private var failures = 0
    private var lockedUntil = 0L

    /** True once the user has created a PIN. Compose reads this, so screens react when it changes. */
    var hasPin by mutableStateOf(false)
        private set

    /** "Ask for my PIN when the app opens" switch (payments always ask regardless). */
    var lockOnOpen by mutableStateOf(true)
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        hasPin = p.contains("hash")
        lockOnOpen = p.getBoolean("lock_on_open", true)
    }

    private fun hash(pin: String, salt: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest((salt + ":" + pin).toByteArray())
        return Base64.encodeToString(d, Base64.NO_WRAP)
    }

    /** Seconds left on the lockout, 0 when entry is allowed. */
    fun lockedSeconds(): Int {
        val left = lockedUntil - System.currentTimeMillis()
        return if (left > 0) ((left + 999) / 1000).toInt() else 0
    }

    fun verify(pin: String): Boolean {
        val p = prefs ?: return false
        if (lockedSeconds() > 0) return false
        val salt = p.getString("salt", null) ?: return false
        val ok = hash(pin, salt) == p.getString("hash", null)
        if (ok) {
            failures = 0
        } else {
            failures += 1
            if (failures >= MAX_TRIES) {
                failures = 0
                lockedUntil = System.currentTimeMillis() + LOCK_MS
            }
        }
        return ok
    }

    /** Text to show after a wrong PIN. */
    fun failureMessage(): String {
        val s = lockedSeconds()
        return if (s > 0) "Too many tries. Wait $s seconds." else "Wrong PIN. Try again."
    }

    fun set(pin: String) {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val salt = Base64.encodeToString(bytes, Base64.NO_WRAP)
        prefs?.edit()?.putString("salt", salt)?.putString("hash", hash(pin, salt))?.apply()
        failures = 0
        lockedUntil = 0L
        hasPin = true
    }

    fun changeLockOnOpen(on: Boolean) {
        lockOnOpen = on
        prefs?.edit()?.putBoolean("lock_on_open", on)?.apply()
    }

    /** Called on log out so the next person to sign in on this phone makes their own PIN. */
    fun clear() {
        prefs?.edit()?.remove("hash")?.remove("salt")?.apply()
        failures = 0
        lockedUntil = 0L
        hasPin = false
    }
}