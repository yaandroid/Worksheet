package com.brahm.autoeditor

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Resolves keyword -> image for b-roll, cascading:
 *   1) local library  (getExternalFilesDir("broll")/<keyword>.jpg|png  — user drops images here)
 *   2) Pexels online   (needs pexelsKey)
 *   3) AI-generated    (Pollinations.ai — free, no key)
 */
object Broll {
    data class Placed(val imagePath: String, val start: Double, val end: Double)

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    fun libraryDir(ctx: Context): File {
        val d = File(ctx.getExternalFilesDir(null), "broll"); if (!d.exists()) d.mkdirs(); return d
    }

    private fun localImage(ctx: Context, key: String): File? {
        val dir = libraryDir(ctx)
        for (ext in listOf("jpg", "jpeg", "png", "webp")) {
            val f = File(dir, "${key.lowercase()}.$ext")
            if (f.exists()) return f
        }
        return null
    }

    private fun download(url: String, out: File, headers: Map<String, String> = emptyMap()): Boolean {
        return try {
            val b = Request.Builder().url(url)
            headers.forEach { (k, v) -> b.addHeader(k, v) }
            http.newCall(b.build()).execute().use { r ->
                if (!r.isSuccessful) return false
                r.body?.byteStream()?.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            }
            out.length() > 1000
        } catch (e: Exception) { false }
    }

    private fun pexels(key: String, query: String, out: File): Boolean {
        return try {
            val url = "https://api.pexels.com/v1/search?query=" +
                java.net.URLEncoder.encode(query, "UTF-8") + "&per_page=1&orientation=portrait"
            http.newCall(Request.Builder().url(url).addHeader("Authorization", key.trim()).build())
                .execute().use { r ->
                    if (!r.isSuccessful) return false
                    val j = JSONObject(r.body!!.string())
                    val photos = j.optJSONArray("photos") ?: return false
                    if (photos.length() == 0) return false
                    val src = photos.getJSONObject(0).getJSONObject("src").getString("portrait")
                    download(src, out)
                }
        } catch (e: Exception) { false }
    }

    private fun pollinations(query: String, out: File): Boolean {
        val url = "https://image.pollinations.ai/prompt/" +
            java.net.URLEncoder.encode("$query, clean medical editorial photo, soft light", "UTF-8") +
            "?width=720&height=720&nologo=true"
        return download(url, out)
    }

    /** Blocking — call on a background thread. */
    fun resolve(ctx: Context, hits: List<Keywords.Hit>, pexelsKey: String?, useAi: Boolean): List<Placed> {
        val cache = File(ctx.cacheDir, "broll"); cache.mkdirs()
        val out = ArrayList<Placed>()
        for (h in hits) {
            val target = File(cache, "${h.keyword.lowercase()}.jpg")
            var ok = false
            val local = localImage(ctx, h.keyword)
            if (local != null) { out.add(Placed(local.absolutePath, h.start, h.end)); continue }
            if (!pexelsKey.isNullOrBlank()) ok = pexels(pexelsKey, h.keyword, target)
            if (!ok && useAi) ok = pollinations(h.keyword, target)
            if (ok) out.add(Placed(target.absolutePath, h.start, h.end))
        }
        return out
    }
}
