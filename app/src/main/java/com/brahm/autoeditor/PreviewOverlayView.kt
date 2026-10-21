package com.brahm.autoeditor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/** WYSIWYG live preview overlay — reflects the chosen font, highlight colour,
 *  text background box and caption position, mapped onto the 9:16 video area. */
class PreviewOverlayView @JvmOverloads constructor(ctx: Context, a: AttributeSet? = null, d: Int = 0)
    : View(ctx, a, d) {

    var words: List<Word> = emptyList()
    var keywords: List<Keywords.Hit> = emptyList()
    var theme: Theme = Themes.ALL[0]
    var brand = "BRAHM"; var tag = "HOMEOPATHY"
    var popText = false
    var captionMargin = 160
    var fontChoice = ""
    var lang = "en"
    var highlightOverride = ""
    var textBg = false
    var textBgColor = "#000000"
    private var t = 0.0
    fun setTime(x: Double) { t = x; invalidate() }

    private val pCap = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val pOut = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE }
    private val pBox = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pBrand = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT; color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
    private val pPop = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val tfCache = HashMap<String, Typeface?>()

    private fun col(hex: String, def: Int = Color.WHITE): Int = try { Color.parseColor(hex) } catch (e: Exception) { def }

    private fun typeface(): Typeface {
        val file = when {
            lang == "en" && fontChoice == "Anton" -> "Anton.ttf"
            lang == "en" && fontChoice == "Bebas Neue" -> "BebasNeue.ttf"
            lang == "hi" || lang == "mr" || lang == "ne" -> "NotoSansDevanagari-Bold.ttf"
            lang == "gu" -> "NotoSansGujarati-Bold.ttf"
            lang == "bn" -> "NotoSansBengali-Bold.ttf"
            lang == "ta" -> "NotoSansTamil-Bold.ttf"
            lang == "te" -> "NotoSansTelugu-Bold.ttf"
            lang == "kn" -> "NotoSansKannada-Bold.ttf"
            lang == "ml" -> "NotoSansMalayalam-Bold.ttf"
            lang == "pa" -> "NotoSansGurmukhi-Bold.ttf"
            else -> "Poppins-Bold.ttf"
        }
        return tfCache.getOrPut(file) {
            try { Typeface.createFromAsset(context.assets, "fonts/$file") } catch (e: Exception) { Typeface.DEFAULT_BOLD }
        } ?: Typeface.DEFAULT_BOLD
    }

    override fun onDraw(c: Canvas) {
        var vw = width.toFloat(); var vh = vw * 16f / 9f
        if (vh > height) { vh = height.toFloat(); vw = vh * 9f / 16f }
        val ox = (width - vw) / 2f; val oy = (height - vh) / 2f
        val sc = vw / 1080f
        val cs = theme.caption
        val tf = typeface()
        pCap.typeface = tf; pOut.typeface = tf
        val hiCol = col(if (highlightOverride.isNotBlank()) highlightOverride else cs.highlight, Color.YELLOW)
        val primCol = col(cs.primary)

        if (popText) keywords.firstOrNull { t >= it.start && t <= it.start + 1.4 }?.let { k ->
            pPop.typeface = tf; pPop.color = hiCol; pPop.textSize = 110 * sc
            c.drawText(k.keyword, ox + vw / 2f, oy + 460 * sc, pPop)
        }

        val visible = words.filter { it.e >= t - 0.05 && it.s <= t + 1.1 }.take(4)
        if (visible.isNotEmpty()) {
            val y = oy + vh - captionMargin * sc
            val size = cs.size * sc
            pCap.textSize = size; pOut.textSize = size; pOut.strokeWidth = (cs.outlineW + 1) * sc
            pOut.color = col(cs.outline)
            val widths = visible.map { pCap.measureText(it.w + " ") }
            val totalW = widths.sum()
            if (textBg) {
                pBox.color = col(textBgColor, Color.BLACK)
                val pad = 16 * sc
                c.drawRoundRect(RectF(ox + vw / 2f - totalW / 2f - pad, y - size, ox + vw / 2f + totalW / 2f + pad, y + size * 0.3f), 12 * sc, 12 * sc, pBox)
            }
            var x = ox + vw / 2f - totalW / 2f
            for ((i, w) in visible.withIndex()) {
                val active = t >= w.s && t <= w.e
                pCap.color = if (active) hiCol else primCol
                val cx = x + widths[i] / 2f
                if (!textBg) c.drawText(w.w + " ", cx, y, pOut)
                c.drawText(w.w + " ", cx, y, pCap)
                x += widths[i]
            }
        }

        pBrand.textSize = 46 * sc
        c.drawText(brand, ox + vw - 46 * sc, oy + 94 * sc, pBrand)
        pBrand.textSize = 20 * sc; pBrand.alpha = 230
        c.drawText(tag, ox + vw - 46 * sc, oy + 128 * sc, pBrand); pBrand.alpha = 255
    }
}
