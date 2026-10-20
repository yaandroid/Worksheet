package com.brahm.autoeditor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max

/** Simple multi-track timeline: Video / Captions / B-roll / Music as blocks.
 *  Horizontal drag to scroll, +/- to zoom, tap a block to edit it. */
class TimelineView @JvmOverloads constructor(ctx: Context, a: AttributeSet? = null, d: Int = 0)
    : View(ctx, a, d) {

    var captions: MutableList<Word> = mutableListOf()
    var broll: MutableList<Broll.Placed> = mutableListOf()
    var musicOn = false
    var totalSec = 0.0
    var pxPerSec = 80f
    var onSelect: ((track: Int, index: Int) -> Unit)? = null
    var onSeek: ((Double) -> Unit)? = null

    private var scrollX = 0f
    private val labelW = 120f
    private val playheadX get() = labelW + 140f
    fun currentTime(): Double = ((scrollX + (playheadX - labelW)) / pxPerSec).toDouble().coerceAtLeast(0.0)
    fun seekTo(t: Double) { scrollX = (t * pxPerSec - (playheadX - labelW)).toFloat().coerceAtLeast(0f); invalidate() }
    private val pHead = Paint().apply { color = Color.parseColor("#FF4D4D"); strokeWidth = 3f }
    private val rowH = 74f
    private val rows = listOf("Video", "Captions", "B-roll", "Music")

    private val pBg = Paint().apply { color = Color.parseColor("#0E1530") }
    private val pRow = Paint().apply { color = Color.parseColor("#141B30") }
    private val pLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#9AA6CC"); textSize = 26f }
    private val pTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 22f }
    private val pTick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2A3357"); textSize = 20f }
    private val blockPaints = listOf(
        Color.parseColor("#3E66B0"), Color.parseColor("#FFC400"),
        Color.parseColor("#27A36B"), Color.parseColor("#B05CC0"))

    fun setZoom(v: Float) { pxPerSec = v.coerceIn(20f, 400f); invalidate() }
    fun zoomIn() = setZoom(pxPerSec * 1.4f)
    fun zoomOut() = setZoom(pxPerSec / 1.4f)

    private fun x(t: Double) = labelW + (t * pxPerSec).toFloat() - scrollX

    override fun onDraw(c: Canvas) {
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pBg)
        // time ticks
        val secStep = max(1, (60 / pxPerSec).toInt())
        var s = 0
        while (s <= totalSec + 1) {
            val px = x(s.toDouble())
            if (px in labelW..width.toFloat()) {
                c.drawLine(px, 0f, px, height.toFloat(), pTick)
                c.drawText("${s}s", px + 4, 22f, pTick)
            }
            s += secStep
        }
        for ((ri, name) in rows.withIndex()) {
            val top = 30f + ri * rowH
            c.drawRect(labelW, top, width.toFloat(), top + rowH - 8, pRow)
            c.drawText(name, 8f, top + rowH / 2, pLabel)
            val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blockPaints[ri] }
            when (ri) {
                0 -> drawBlock(c, 0.0, totalSec, top, bp, "video")
                1 -> for (w in captions) drawBlock(c, w.s, w.e, top, bp, w.w, true)
                2 -> for (p in broll) drawBlock(c, p.start, p.end, top, bp, "img")
                3 -> if (musicOn) drawBlock(c, 0.0, totalSec, top, bp, "music")
            }
        }
        // fixed playhead
        c.drawLine(playheadX, 0f, playheadX, height.toFloat(), pHead)
    }

    private fun drawBlock(c: Canvas, a: Double, b: Double, top: Float, p: Paint, label: String, dark: Boolean = false) {
        val l = x(a); val r = x(b)
        if (r < labelW || l > width) return
        val rect = RectF(max(l, labelW), top + 6, r, top + rowH - 14)
        c.drawRoundRect(rect, 8f, 8f, p)
        if (rect.width() > 26) {
            pTxt.color = if (dark) Color.parseColor("#1A1300") else Color.WHITE
            c.save(); c.clipRect(rect)
            c.drawText(label, rect.left + 6, rect.centerY() + 7, pTxt); c.restore()
        }
    }

    private var downX = 0f; private var downY = 0f; private var dragging = false
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; dragging = false; return true }
            MotionEvent.ACTION_MOVE -> {
                if (abs(e.x - downX) > 12) { dragging = true
                    scrollX = (scrollX - (e.x - downX)).coerceAtLeast(0f)
                    downX = e.x; invalidate()
                    onSeek?.invoke(currentTime()) }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) handleTap(e.x, e.y)
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private fun handleTap(px: Float, py: Float) {
        val ri = ((py - 30f) / rowH).toInt()
        val t = ((px - labelW + scrollX) / pxPerSec).toDouble()
        when (ri) {
            1 -> captions.indexOfFirst { t >= it.s && t <= it.e }.takeIf { it >= 0 }?.let { onSelect?.invoke(1, it) }
            2 -> broll.indexOfFirst { t >= it.start && t <= it.end }.takeIf { it >= 0 }?.let { onSelect?.invoke(2, it) }
        }
    }
}
