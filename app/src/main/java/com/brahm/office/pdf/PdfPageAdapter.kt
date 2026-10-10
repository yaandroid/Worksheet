package com.brahm.office.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.brahm.office.R
import java.util.concurrent.Executors

/** Renders PDF pages lazily into a vertically scrolling list. */
class PdfPageAdapter(
    private val renderer: PdfRenderer,
    private var scale: Float,
    private val viewWidthPx: () -> Int
) : RecyclerView.Adapter<PdfPageAdapter.VH>() {

    private val io = Executors.newSingleThreadExecutor()
    private val lock = Any()
    // cache keyed by "page@scaleBucket"
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 4).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val image: ImageView = v.findViewById(R.id.pageImage)
    }

    fun setScale(s: Float) {
        scale = s
        cache.evictAll()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false)
        return VH(v)
    }

    override fun getItemCount() = renderer.pageCount

    override fun onBindViewHolder(h: VH, position: Int) {
        val w = (viewWidthPx() * scale).toInt().coerceAtLeast(200)
        val key = "$position@$w"
        val cached = cache.get(key)
        if (cached != null) { h.image.setImageBitmap(cached); return }
        h.image.setImageDrawable(null)
        io.execute {
            val bmp = renderPage(position, w) ?: return@execute
            cache.put(key, bmp)
            h.image.post {
                if (h.bindingAdapterPosition == position) h.image.setImageBitmap(bmp)
            }
        }
    }

    private fun renderPage(index: Int, targetW: Int): Bitmap? {
        synchronized(lock) {
            return try {
                renderer.openPage(index).use { page ->
                    val ratio = page.height.toFloat() / page.width.toFloat()
                    val w = targetW
                    val hgt = (w * ratio).toInt().coerceAtLeast(200)
                    val bmp = Bitmap.createBitmap(w, hgt, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            } catch (e: Exception) { null }
        }
    }

    fun shutdown() { io.shutdownNow() }
}
