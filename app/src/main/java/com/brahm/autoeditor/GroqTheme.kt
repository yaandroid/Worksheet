package com.brahm.autoeditor

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** AI picks the best theme from the transcript (Groq chat). Falls back to default. */
object GroqTheme {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).build()

    fun pick(transcript: String, apiKey: String): String? {
        val key = apiKey.filterNot { it.isWhitespace() }
        if (key.isBlank() || transcript.isBlank()) return null
        val ids = Themes.ALL.joinToString(", ") { it.id }
        val sys = "You choose a short-video visual theme. Allowed ids: $ids. " +
            "Reply with ONLY one id, nothing else."
        return try {
            val body = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                put("temperature", 0)
                put("max_tokens", 12)
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", sys))
                    put(JSONObject().put("role", "user").put("content",
                        "Transcript: " + transcript.take(1500)))
                })
            }.toString()
            val req = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer $key")
                .post(body.toRequestBody("application/json".toMediaType())).build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return null
                val txt = JSONObject(r.body!!.string())
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content").trim().lowercase()
                Themes.ALL.firstOrNull { txt.contains(it.id) }?.id
            }
        } catch (e: Exception) { null }
    }
}
