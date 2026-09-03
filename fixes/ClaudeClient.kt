package com.aria.companion.network

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object ClaudeClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val MODEL = "claude-sonnet-5"

    fun send(
        apiKey: String,
        systemPrompt: String,
        userMessage: String,
        onResult: (String?) -> Unit
    ) {
        val body = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", 300)
            put("system", systemPrompt)
            put("messages", JSONArray().put(
                JSONObject().put("role", "user").put("content", userMessage)
            ))
        }

        val req = Request.Builder()
            .url(ENDPOINT)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult("NETERR: " + e.javaClass.simpleName + ": " + e.message)
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use { resp ->
                    val raw = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        onResult("NETERR: HTTP " + resp.code + " - " + raw.take(300))
                        return
                    }
                    val json = JSONObject(raw)
                    val content = json.optJSONArray("content") ?: JSONArray()
                    val text = StringBuilder()
                    for (i in 0 until content.length()) {
                        val block = content.getJSONObject(i)
                        if (block.optString("type") == "text") {
                            text.append(block.optString("text"))
                        }
                    }
                    onResult(text.toString().ifBlank { "NETERR: empty response body" })
                }
            }
        })
    }
}
