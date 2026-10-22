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
    var clips: MutableList<Clip> = mutableListOf()
    var selectedClip = -1
    var musicOn = false
    var totalSec = 0.0

    // ---- clip thumbnail cache ----
    private val thumbs = HashMap<String, android.graphics.Bitmap>()
    private val thumbLoading = HashSet<String>()
    private val pClipBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.parseColor("#2A3357") }
    private val pSel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5f; color = Color.parseColor("#FFC400") }
    private val pHandle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFC400") }

    private fun ensureThumb(cl: Clip) {
        val key = cl.path + ":" + cl.srcIn
        if (thumbs.containsKey(key) || thumbLoading.contains(key)) return
        thumbLoading.add(key)
        Thread {
            val bmp: android.graphics.Bitmap? = try {
                if (cl.isImage) {
                    val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                    android.graphics.BitmapFactory.decodeFile(cl.path, o)
                } else {
                    val r = android.media.MediaMetadataRetriever(); r.setDataSource(cl.path)
                    val f = r.getFrameAtTime((cl.srcIn * 1_000_000).toLong(),
                        android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC); r.release(); f
                }
            } catch (e: Exception) { null }
            if (bmp != null) {
                val h = (rowH - 20).toInt().coerceAtLeast(16)
                val w = (h * bmp.width / bmp.height.coerceAtLeast(1)).coerceIn(16, 400)
                val s = try { android.graphics.Bitmap.createScaledBitmap(bmp, w, h, true) } catch (e: Exception) { bmp }
                post { thumbs[key] = s; thumbLoading.remove(key); invalidate() }
            } else post { thumbLoading.remove(key) }
        }.start()
    }

    private fun drawClipBlock(c: Canvas, a: Double, b: Double, top: Float, cl: Clip, idx: Int) {
        val l = x(a); val r = x(b)
        if (r < labelW || l > width) return
        val rect = RectF(max(l, labelW), top + 6, r, top + rowH - 14)
        ensureThumb(cl)
        val bmp = thumbs[cl.path + ":" + cl.srcIn]
        c.save()
        val clip = android.graphics.Path().apply { addRoundRect(rect, 8f, 8f, android.graphics.Path.Direction.CW) }
        c.clipPath(clip)
        if (bmp != null) {
            var xx = rect.left
            while (xx < rect.right) { c.drawBitmap(bmp, xx, rect.top, null); xx += bmp.width.toFloat() }
        } else {
            c.drawRect(rect, blockPaints[0].let { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = it } })
        }
        c.restore()
        c.drawRoundRect(rect, 8f, 8f, if (idx == selectedClip) pSel else pClipBorder)
        if (idx == selectedClip) {
            // white trim handles (drag to trim)
            val hw = 10f
            c.drawRoundRect(RectF(rect.left, rect.top, rect.left + hw, rect.bottom), 4f, 4f, pHandle)
            c.drawRoundRect(RectF(rect.right - hw, rect.top, rect.right, rect.bottom), 4f, 4f, pHandle)
        }
        if (rect.width() > 26) {
            val tag = (if (cl.isImage) "🖼" else "▶") + "${idx + 1}" + (if (cl.speed != 1.0) " ${cl.speed}x" else "")
            pTxt.color = Color.WHITE
            c.save(); c.clipRect(rect)
            c.drawText(tag, rect.left + 16, rect.top + 22, pTxt); c.restore()
        }
    }
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
                0 -> {
                    if (clips.isEmpty()) drawBlock(c, 0.0, totalSec, top, bp, "video")
                    else {
                        var st = 0.0
                        for ((ci, cl) in clips.withIndex()) {
                            val d = cl.outDur()
                            drawClipBlock(c, st, st + d, top, cl, ci)
                            st += d
                        }
                    }
                }
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

    var onEdited: (() -> Unit)? = null
    var onEditBegin: (() -> Unit)? = null
    var onClipTrimmed: (() -> Unit)? = null
    private var downX = 0f; private var lastX = 0f; private var dragging = false
    private var mode = 0           // 0 scroll, 1 move block, 2 trim-left, 3 trim-right
    private var blockIdx = -1
    private var clipMode = 0       // 0 none, 2 trim selected clip left, 3 trim right
    private var editBegun = false

    /** start x (sec) of a clip on the composed timeline */
    private fun clipStart(idx: Int): Double { var s = 0.0; for (i in 0 until idx) s += clips[i].outDur(); return s }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; lastX = e.x; dragging = false; mode = 0; blockIdx = -1; clipMode = 0
                val ri = ((e.y - 30f) / rowH).toInt()
                if (ri == 0 && selectedClip in clips.indices) {
                    val st = clipStart(selectedClip); val d = clips[selectedClip].outDur()
                    val lx = x(st); val rx = x(st + d)
                    if (abs(e.x - lx) < 42 && e.x > labelW) clipMode = 2
                    else if (abs(e.x - rx) < 42) clipMode = 3
                }
                if (ri == 1) {
                    val t = ((e.x - labelW + scrollX) / pxPerSec).toDouble()
                    blockIdx = captions.indexOfFirst { t >= it.s && t <= it.e }
                    if (blockIdx >= 0) {
                        val w = captions[blockIdx]
                        mode = when {
                            abs(e.x - x(w.s)) < 30 -> 2
                            abs(e.x - x(w.e)) < 30 -> 3
                            else -> 1
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - lastX; lastX = e.x
                if (abs(e.x - downX) > 10) dragging = true
                if (clipMode > 0 && selectedClip in clips.indices) {
                    if (!editBegun) { onEditBegin?.invoke(); editBegun = true }
                    val cl = clips[selectedClip]
                    val dtDisp = dx / pxPerSec
                    if (cl.isImage) {
                        if (clipMode == 3) cl.imageDur = (cl.imageDur + dtDisp).coerceIn(0.3, 30.0)
                    } else {
                        val dtSrc = dtDisp * cl.speed
                        if (clipMode == 2) cl.srcIn = (cl.srcIn + dtSrc).coerceIn(0.0, cl.srcOut - 0.2)
                        else {
                            val hi = if (cl.srcDur > 0) cl.srcDur else cl.srcOut + dtSrc
                            cl.srcOut = (cl.srcOut + dtSrc).coerceIn(cl.srcIn + 0.2, hi)
                        }
                    }
                    invalidate(); onEdited?.invoke()
                } else if (mode > 0 && blockIdx in captions.indices) {
                    if (!editBegun) { onEditBegin?.invoke(); editBegun = true }
                    val dt = dx / pxPerSec
                    val w = captions[blockIdx]
                    captions[blockIdx] = when (mode) {
                        2 -> Word((w.s + dt).coerceIn(0.0, w.e - 0.1), w.e, w.w)
                        3 -> Word(w.s, (w.e + dt).coerceAtLeast(w.s + 0.1), w.w)
                        else -> Word((w.s + dt).coerceAtLeast(0.0), (w.e + dt).coerceAtLeast(0.1), w.w)
                    }
                    invalidate(); onEdited?.invoke()
                } else {
                    scrollX = (scrollX - dx).coerceAtLeast(0f); invalidate(); onSeek?.invoke(currentTime())
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val wasClipTrim = clipMode > 0 && dragging
                if (!dragging) {
                    if (blockIdx >= 0) onSelect?.invoke(1, blockIdx)
                    else handleTap(e.x, e.y)
                }
                mode = 0; blockIdx = -1; clipMode = 0; editBegun = false
                if (wasClipTrim) onClipTrimmed?.invoke()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private fun handleTap(px: Float, py: Float) {
        val ri = ((py - 30f) / rowH).toInt()
        val t = ((px - labelW + scrollX) / pxPerSec).toDouble()
        if (ri == 0 && clips.isNotEmpty()) {
            var st = 0.0
            for ((ci, cl) in clips.withIndex()) {
                val d = cl.outDur()
                if (t >= st && t <= st + d) { onSelect?.invoke(0, ci); return }
                st += d
            }
        }
        if (ri == 2) broll.indexOfFirst { t >= it.start && t <= it.end }.takeIf { it >= 0 }?.let { onSelect?.invoke(2, it) }
    }
}
