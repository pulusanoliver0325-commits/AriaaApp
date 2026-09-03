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

    fun send(
        apiKey: String,
        systemPrompt: String,
        userMessage: String,
        onResult: (String?) -> Unit
    ) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + apiKey

        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
            ))
        }

        val req = Request.Builder()
            .url(url)
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
                    try {
                        val json = JSONObject(raw)
                        val text = json.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                        onResult(text.ifBlank { "NETERR: empty response body" })
                    } catch (e: Exception) {
                        onResult("NETERR: parse failure - " + raw.take(300))
                    }
                }
            }
        })
    }
}
