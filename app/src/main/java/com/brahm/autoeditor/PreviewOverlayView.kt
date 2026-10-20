package com.brahm.autoeditor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/** Live preview overlay — draws captions (active word highlighted), keyword pop,
 *  branding and a b-roll placeholder for the current time t, over the video frame.
 *  Approximate (not pixel-identical to export) but real-time, no FFmpeg. */
class PreviewOverlayView @JvmOverloads constructor(ctx: Context, a: AttributeSet? = null, d: Int = 0)
    : View(ctx, a, d) {

    var words: List<Word> = emptyList()
    var keywords: List<Keywords.Hit> = emptyList()
    var theme: Theme = Themes.ALL[0]
    var brand = "BRAHM"; var tag = "HOMEOPATHY"
    private var t = 0.0
    fun setTime(x: Double) { t = x; invalidate() }

    private val pCap = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val pOut = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE }
    private val pBrand = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT; color = Color.WHITE }
    private val pPop = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val pCircle = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private fun col(hex: String): Int = try { Color.parseColor(hex) } catch (e: Exception) { Color.WHITE }

    override fun onDraw(c: Canvas) {
        val sx = width / 1080f
        val cs = theme.caption

        // b-roll placeholder (active keyword) — upper circle
        keywords.firstOrNull { t >= it.start && t <= it.end }?.let { k ->
            val cx = width / 2f; val cy = 250 * sx + 300 * sx; val r = 300 * sx
            pCircle.color = col(cs.highlight); pCircle.alpha = 60
            c.drawCircle(cx, cy, r, pCircle)
            pPop.color = Color.WHITE; pPop.textSize = 44 * sx
            c.drawText("[${k.keyword}]", cx, cy, pPop)
        }

        // keyword pop-text (top)
        keywords.firstOrNull { t >= it.start && t <= it.start + 1.4 }?.let { k ->
            pPop.color = col(cs.highlight); pPop.textSize = 110 * sx
            c.drawText(k.keyword, width / 2f, 460 * sx, pPop)
        }

        // caption line (bottom) with active word highlighted
        val visible = words.filter { it.e >= t - 0.05 && it.s <= t + 1.1 }.take(4)
        if (visible.isNotEmpty()) {
            val y = height - cs.marginV * sx
            val size = cs.size * sx
            pCap.textSize = size; pOut.textSize = size; pOut.strokeWidth = cs.outlineW * sx
            pOut.color = col(cs.outline)
            // measure total width
            val widths = visible.map { pCap.measureText(it.w + " ") }
            val totalW = widths.sum()
            var x = width / 2f - totalW / 2f
            for ((i, w) in visible.withIndex()) {
                val active = t >= w.s && t <= w.e
                pCap.color = if (active) col(cs.highlight) else col(cs.primary)
                val wtxt = w.w + " "
                val cx = x + widths[i] / 2f
                c.drawText(wtxt, cx, y, pOut)
                c.drawText(wtxt, cx, y, pCap)
                x += widths[i]
            }
        }

        // branding (top-right)
        pBrand.textSize = 46 * sx
        c.drawText(brand, width - 46 * sx, 54 * sx + 40 * sx, pBrand)
        pBrand.textSize = 20 * sx; pBrand.alpha = 230
        c.drawText(tag, width - 46 * sx, 108 * sx + 20 * sx, pBrand); pBrand.alpha = 255
    }
}
