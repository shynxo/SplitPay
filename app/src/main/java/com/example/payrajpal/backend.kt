package com.example.payrajpal

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ParsedReceipt(
    val id: String,
    val merchantName: String,
    val currency: String,
    val items: List<ReceiptItem>,
    val tax: Double,
    val service: Double,
    val tip: Double,
    val total: Double
)

object BackendService {
    private const val BASE_URL = "https://splitpay-server-duh3.onrender.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private fun mainThread(action: () -> Unit) {
        Handler(Looper.getMainLooper()).post(action)
    }

    private fun showToast(context: Context?, message: String) {
        if (context == null) return
        mainThread {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun callApi(
        endpoint: String,
        json: String,
        context: Context? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        postJson(endpoint, json, context, onResult)
    }

    fun postJson(
        endpoint: String,
        jsonString: String,
        context: Context? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        val cleanEndpoint = endpoint.removePrefix("/")
        val url = "$BASE_URL/$cleanEndpoint"

        Log.d("BACKEND", "REQUEST [POST] $url - Body: $jsonString")

        val body = jsonString.toRequestBody(JSON)
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("BACKEND", "FAILURE [POST] $url: ${e.message}", e)
                showToast(context, e.message ?: "Network error")
                mainThread { onResult(false, e.localizedMessage ?: "Network error") }
            }

            override fun onResponse(call: Call, response: Response) {
                val resBody = response.body?.string() ?: ""
                Log.d("BACKEND", "RESPONSE [${response.code}] $url - Body: $resBody")
                if (!response.isSuccessful) {
                    showToast(context, "Server error (${response.code}): $resBody")
                }
                mainThread { onResult(response.isSuccessful, resBody) }
            }
        })
    }

    fun getJson(
        endpoint: String,
        context: Context? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        val cleanEndpoint = endpoint.removePrefix("/")
        val url = "$BASE_URL/$cleanEndpoint"

        Log.d("BACKEND", "REQUEST [GET] $url")

        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("BACKEND", "FAILURE [GET] $url: ${e.message}", e)
                showToast(context, e.message ?: "Network error")
                mainThread { onResult(false, e.localizedMessage ?: "Network error") }
            }

            override fun onResponse(call: Call, response: Response) {
                val resBody = response.body?.string() ?: ""
                Log.d("BACKEND", "RESPONSE [${response.code}] $url - Body: $resBody")
                if (!response.isSuccessful) {
                    showToast(context, "Server error (${response.code}): $resBody")
                }
                mainThread { onResult(response.isSuccessful, resBody) }
            }
        })
    }

    fun postReceipt(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        context: Context? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        val url = "$BASE_URL/api/receipts"
        Log.d("BACKEND", "REQUEST [POST] $url (image ${imageBytes.size} bytes, $mimeType)")

        val fileName = if (mimeType.contains("png")) "receipt.png" else "receipt.jpg"
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                fileName,
                imageBytes.toRequestBody(mimeType.toMediaType())
            )
            .build()

        val request = Request.Builder().url(url).post(requestBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("BACKEND", "FAILURE [POST] $url: ${e.message}", e)
                showToast(context, e.message ?: "Network error")
                mainThread { onResult(false, e.localizedMessage ?: "Network error") }
            }

            override fun onResponse(call: Call, response: Response) {
                val resBody = response.body?.string() ?: ""
                Log.d("BACKEND", "RESPONSE [${response.code}] $url - Body: $resBody")
                if (!response.isSuccessful) {
                    showToast(context, "Receipt error (${response.code}): $resBody")
                }
                mainThread { onResult(response.isSuccessful, resBody) }
            }
        })
    }
}

fun parseReceiptResponse(jsonStr: String): ParsedReceipt? {
    return try {
        val obj = JSONObject(jsonStr)
        val id = obj.optString("id", UUID.randomUUID().toString())
        val merchant = obj.optString("merchant", "Scanned Receipt")
        val currency = obj.optString("currency", "INR")
        val taxCents = obj.optDouble("taxCents", 0.0)
        val serviceCents = obj.optDouble("serviceCents", 0.0)
        val tipCents = obj.optDouble("tipCents", 0.0)
        val totalCents = obj.optDouble("totalCents", 0.0)

        val itemsArr = obj.optJSONArray("items") ?: JSONArray()
        val itemsList = mutableListOf<ReceiptItem>()
        for (i in 0 until itemsArr.length()) {
            val itemObj = itemsArr.getJSONObject(i)
            val itemId = itemObj.optString("id", UUID.randomUUID().toString())
            val baseName = itemObj.optString("name", "Item")
            val qty = itemObj.optInt("qty", 1).coerceAtLeast(1)
            val priceCents = itemObj.optDouble("priceCents", 0.0)
            // Server price is per unit. The app has no quantity, so show the line total.
            val name = if (qty > 1) "$baseName x$qty" else baseName
            itemsList.add(
                ReceiptItem(
                    id = itemId,
                    name = name,
                    price = (priceCents * qty) / 100.0,
                    assignedTo = emptyList()
                )
            )
        }
        ParsedReceipt(
            id = id,
            merchantName = merchant,
            currency = currency,
            items = itemsList,
            tax = taxCents / 100.0,
            service = serviceCents / 100.0,
            tip = tipCents / 100.0,
            total = totalCents / 100.0
        )
    } catch (e: Exception) {
        Log.e("BACKEND", "Failed to parse receipt response", e)
        null
    }
}