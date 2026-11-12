package com.example.payrajpal

/**
 * The address of the SplitPay server (the one that does Log in with PayPal, payments, QR payments and history).
 * Your Render address (no trailing slash).
 *
 * Bill scanning / splitting keeps using your friend's server (BackendService in backend.kt).
 */
object AppConfig {
    const val SERVER_URL = "https://splitpay-backend-bzkh.onrender.com"
}