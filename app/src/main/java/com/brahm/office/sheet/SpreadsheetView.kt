package com.brahm.office.sheet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.OverScroller
import kotlin.math.max
import kotlin.math.min

/** Virtualized spreadsheet grid. Draws only visible cells for speed at 702×10000. */
class SpreadsheetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var sheet: Sheet = Sheet()
        set(v) { field = v; invalidate() }

    var onSelect: ((Int, Int) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private var cellW = 92f * d
    private var cellH = 34f * d
    private val rowHeadW = 44f * d
    private val colHeadH = 30f * d

    var selRow = 0; private set
    var selCol = 0; private set

    private var scrollX = 0f
    private var scrollY = 0f

    private val scroller = OverScroller(context)

    private val gridPaint = Paint().apply { color = Color.parseColor("#D9DEE6"); strokeWidth = 1f }
    private val headBg = Paint().apply { color = Color.parseColor("#16233F") }
    private val headText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 12f * d; textAlign = Paint.Align.CENTER }
    private val cellText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#16233F"); textSize = 13f * d }
    private val cellBg = Paint().apply { color = Color.WHITE }
    private val altBg = Paint().apply { color = Color.parseColor("#FAFBFC") }
    private val selPaint = Paint().apply { color = Color.parseColor("#331F9D63") }
    private val selBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F9D63"); style = Paint.Style.STROKE; strokeWidth = 2.5f * d }
    private val selHeadBg = Paint().apply { color = Color.parseColor("#E8A33D") }

    private val gesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean { scroller.forceFinished(true); return true }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            scrollX = clampX(scrollX + dx); scrollY = clampY(scrollY + dy); invalidate(); return true
        }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            scroller.fling(scrollX.toInt(), scrollY.toInt(), -vx.toInt(), -vy.toInt(),
                0, maxScrollX().toInt(), 0, maxScrollY().toInt())
            postInvalidateOnAnimation(); return true
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (e.x < rowHeadW || e.y < colHeadH) return false
            val col = ((scrollX + e.x - rowHeadW) / cellW).toInt()
            val row = ((scrollY + e.y - colHeadH) / cellH).toInt()
            if (row in 0 until CellRef.MAX_ROWS && col in 0 until CellRef.MAX_COLS) {
                selRow = row; selCol = col; onSelect?.invoke(row, col); invalidate()
            }
            return true
        }
    })

    private val scaleGesture = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(dg: ScaleGestureDetector): Boolean {
            val f = dg.scaleFactor
            cellW = (cellW * f).coerceIn(40f * d, 240f * d)
            cellH = (cellH * f).coerceIn(22f * d, 80f * d)
            cellText.textSize = (cellText.textSize * f).coerceIn(9f * d, 22f * d)
            scrollX = clampX(scrollX); scrollY = clampY(scrollY)
            invalidate(); return true
        }
    })

    private fun maxScrollX() = max(0f, CellRef.MAX_COLS * cellW - (width - rowHeadW))
    private fun maxScrollY() = max(0f, CellRef.MAX_ROWS * cellH - (height - colHeadH))
    private fun clampX(x: Float) = x.coerceIn(0f, maxScrollX())
    private fun clampY(y: Float) = y.coerceIn(0f, maxScrollY())

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollX = clampX(scroller.currX.toFloat()); scrollY = clampY(scroller.currY.toFloat())
            postInvalidateOnAnimation()
        }
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaleGesture.onTouchEvent(e)
        gesture.onTouchEvent(e)
        return true
    }

    /** Move selection programmatically (e.g. after Enter). */
    fun selectCell(r: Int, c: Int) {
        selRow = r.coerceIn(0, CellRef.MAX_ROWS - 1)
        selCol = c.coerceIn(0, CellRef.MAX_COLS - 1)
        ensureVisible(); onSelect?.invoke(selRow, selCol); invalidate()
    }

    private fun ensureVisible() {
        val cellLeft = selCol * cellW; val cellTop = selRow * cellH
        val viewW = width - rowHeadW; val viewH = height - colHeadH
        if (cellLeft < scrollX) scrollX = cellLeft
        else if (cellLeft + cellW > scrollX + viewW) scrollX = cellLeft + cellW - viewW
        if (cellTop < scrollY) scrollY = cellTop
        else if (cellTop + cellH > scrollY + viewH) scrollY = cellTop + cellH - viewH
        scrollX = clampX(scrollX); scrollY = clampY(scrollY)
    }

    override fun onDraw(canvas: Canvas) {
        val viewW = width.toFloat(); val viewH = height.toFloat()
        val firstCol = (scrollX / cellW).toInt().coerceAtLeast(0)
        val lastCol = ((scrollX + viewW - rowHeadW) / cellW).toInt().coerceAtMost(CellRef.MAX_COLS - 1)
        val firstRow = (scrollY / cellH).toInt().coerceAtLeast(0)
        val lastRow = ((scrollY + viewH - colHeadH) / cellH).toInt().coerceAtMost(CellRef.MAX_ROWS - 1)

        // cells
        for (r in firstRow..lastRow) {
            val top = colHeadH + r * cellH - scrollY
            for (c in firstCol..lastCol) {
                val left = rowHeadW + c * cellW - scrollX
                canvas.drawRect(left, top, left + cellW, top + cellH, if (r % 2 == 0) cellBg else altBg)
                if (r == selRow && c == selCol) canvas.drawRect(left, top, left + cellW, top + cellH, selPaint)
                canvas.drawLine(left, top, left + cellW, top, gridPaint)
                canvas.drawLine(left, top, left, top + cellH, gridPaint)
                val text = sheet.display(r, c)
                if (text.isNotEmpty()) {
                    val clip = canvas.save()
                    canvas.clipRect(left, top, left + cellW, top + cellH)
                    val isNum = sheet.value(r, c) is Value.Num
                    if (isNum) {
                        cellText.textAlign = Paint.Align.RIGHT
                        canvas.drawText(text, left + cellW - 6 * d, top + cellH * 0.68f, cellText)
                    } else {
                        cellText.textAlign = Paint.Align.LEFT
                        canvas.drawText(text, left + 6 * d, top + cellH * 0.68f, cellText)
                    }
                    canvas.restoreToCount(clip)
                }
            }
        }
        // selection border
        if (selRow in firstRow..lastRow && selCol in firstCol..lastCol) {
            val l = rowHeadW + selCol * cellW - scrollX
            val t = colHeadH + selRow * cellH - scrollY
            canvas.drawRect(l, t, l + cellW, t + cellH, selBorder)
        }

        // column headers
        canvas.drawRect(0f, 0f, viewW, colHeadH, headBg)
        headText.textAlign = Paint.Align.CENTER
        for (c in firstCol..lastCol) {
            val left = rowHeadW + c * cellW - scrollX
            if (c == selCol) canvas.drawRect(left, 0f, left + cellW, colHeadH, selHeadBg)
            canvas.drawText(CellRef.colName(c), left + cellW / 2, colHeadH * 0.68f, headText)
        }
        // row headers
        canvas.drawRect(0f, 0f, rowHeadW, viewH, headBg)
        for (r in firstRow..lastRow) {
            val top = colHeadH + r * cellH - scrollY
            if (r == selRow) canvas.drawRect(0f, top, rowHeadW, top + cellH, selHeadBg)
            canvas.drawText("${r + 1}", rowHeadW / 2, top + cellH * 0.68f, headText)
        }
        // corner
        canvas.drawRect(0f, 0f, rowHeadW, colHeadH, headBg)
    }
}
