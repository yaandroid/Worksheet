package com.brahm.autoeditor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/** Live preview overlay — maps the 1080x1920 design onto the letterboxed 9:16
 *  video area, so what you see matches the export. Draws captions (active word
 *  highlighted), optional keyword pop, and branding for the current time t. */
class PreviewOverlayView @JvmOverloads constructor(ctx: Context, a: AttributeSet? = null, d: Int = 0)
    : View(ctx, a, d) {

    var words: List<Word> = emptyList()
    var keywords: List<Keywords.Hit> = emptyList()
    var theme: Theme = Themes.ALL[0]
    var brand = "BRAHM"; var tag = "HOMEOPATHY"
    var popText = false
    var captionMargin = 430
    private var t = 0.0
    fun setTime(x: Double) { t = x; invalidate() }

    private val pCap = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val pOut = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE }
    private val pBrand = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT; color = Color.WHITE }
    private val pPop = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    private fun col(hex: String): Int = try { Color.parseColor(hex) } catch (e: Exception) { Color.WHITE }

    override fun onDraw(c: Canvas) {
        // compute the 9:16 video rect centred in this view
        var vw = width.toFloat(); var vh = vw * 16f / 9f
        if (vh > height) { vh = height.toFloat(); vw = vh * 9f / 16f }
        val ox = (width - vw) / 2f; val oy = (height - vh) / 2f
        val sc = vw / 1080f
        val cs = theme.caption

        // keyword pop-text (optional)
        if (popText) keywords.firstOrNull { t >= it.start && t <= it.start + 1.4 }?.let { k ->
            pPop.color = if (cs.highlight.isNotBlank()) col(cs.highlight) else Color.YELLOW
            pPop.textSize = 110 * sc
            c.drawText(k.keyword, ox + vw / 2f, oy + 460 * sc, pPop)
        }

        // caption line with active word highlighted
        val visible = words.filter { it.e >= t - 0.05 && it.s <= t + 1.1 }.take(4)
        if (visible.isNotEmpty()) {
            val y = oy + vh - captionMargin * sc
            val size = cs.size * sc
            pCap.textSize = size; pOut.textSize = size; pOut.strokeWidth = (cs.outlineW + 1) * sc
            pOut.color = col(cs.outline)
            val widths = visible.map { pCap.measureText(it.w + " ") }
            var x = ox + vw / 2f - widths.sum() / 2f
            for ((i, w) in visible.withIndex()) {
                val active = t >= w.s && t <= w.e
                pCap.color = if (active) col(cs.highlight) else col(cs.primary)
                val cx = x + widths[i] / 2f
                c.drawText(w.w + " ", cx, y, pOut)
                c.drawText(w.w + " ", cx, y, pCap)
                x += widths[i]
            }
        }

        // branding (top-right of the video area)
        pBrand.textSize = 46 * sc
        c.drawText(brand, ox + vw - 46 * sc, oy + 94 * sc, pBrand)
        pBrand.textSize = 20 * sc; pBrand.alpha = 230
        c.drawText(tag, ox + vw - 46 * sc, oy + 128 * sc, pBrand); pBrand.alpha = 255
    }
}
