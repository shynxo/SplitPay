package com.example.payrajpal

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object BackendService {
    private const val BASE_URL = "http://127.0.0.1:3000"

    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun callApi(endpoint: String, json: String, onResult: (Boolean, String) -> Unit) {
        val cleanEndpoint = endpoint.removePrefix("/")
        val url = "$BASE_URL/$cleanEndpoint"

        val body = json.toRequestBody(JSON)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                println(">>> BACKEND NETWORK ERROR: ${e.message}")
                onResult(false, e.localizedMessage ?: "Network error")
            }

            override fun onResponse(call: Call, response: Response) {
                val resBody = response.body?.string() ?: ""
                println(">>> BACKEND SUCCESS (${response.code}): $resBody")
                onResult(response.isSuccessful, resBody)
            }
        })
    }
}