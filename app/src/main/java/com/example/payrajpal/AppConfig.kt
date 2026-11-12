package com.example.payrajpal

/**
 * The address of the PayRajpal server (the one that does Log in with PayPal, payments, QR payments and history).
 * Put your Render address here, e.g. "https://payrajpal-server-xxxx.onrender.com" (no trailing slash).
 *
 * Bill scanning / splitting keeps using your friend's server (BackendService in backend.kt).
 */
object AppConfig {
    const val SERVER_URL = "https://splitpay-backend-bzkh.onrender.com"
}
