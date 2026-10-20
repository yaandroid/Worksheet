package com.brahm.autoeditor

import android.content.Context
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Groq Whisper transcription with word timestamps. The AI key comes from the
 *  app's settings field. Falls back cleanly with a clear error if missing. */
class GroqTranscriber(private val ctx: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private fun extractWav(input: String): File {
        val wav = File(ctx.cacheDir, "audio_${System.currentTimeMillis()}.wav")
        val s = FFmpegKit.execute("-y -i \"$input\" -vn -ac 1 -ar 16000 \"${wav.absolutePath}\"")
        if (!ReturnCode.isSuccess(s.returnCode)) throw RuntimeException("audio extract failed")
        return wav
    }

    /** Blocking — call from a background thread/coroutine. */
    fun transcribe(input: String, apiKey: String, language: String?): List<Word> {
        if (apiKey.isBlank()) throw RuntimeException("Groq API key missing — settings me key daalo ya transcript paste karo.")
        val wav = extractWav(input)
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "audio.wav", wav.asRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("model", "whisper-large-v3")
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("timestamp_granularities[]", "word")
        if (!language.isNullOrBlank()) bodyBuilder.addFormDataPart("language", language)
        val req = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(bodyBuilder.build())
            .build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw RuntimeException("Groq error ${resp.code}: $txt")
            val words = ArrayList<Word>()
            val arr = JSONObject(txt).optJSONArray("words") ?: return emptyList()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val w = o.optString("word").trim()
                if (w.isNotEmpty()) words.add(Word(o.optDouble("start"), o.optDouble("end"), w))
            }
            wav.delete()
            return words
        }
    }

    /** Rough even distribution when no AI/transcript timing is available. */
    fun manual(text: String, totalSec: Double): List<Word> {
        val toks = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (toks.isEmpty()) return emptyList()
        val d = (if (totalSec > 0) totalSec else 15.0) / toks.size
        return toks.mapIndexed { i, t -> Word(i * d, (i + 1) * d - 0.02, t) }
    }
}
