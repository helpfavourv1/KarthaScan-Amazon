package com.zdmgold.katharscan.autoscan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CornerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B998B")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B998B")
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(18f, 14f), 0f)
    }

    private val guideTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 44f
        textAlign = Paint.Align.CENTER
        setShadowLayer(8f, 0f, 0f, Color.BLACK)
    }

    private val path = Path()
    private val guideRect = RectF()
    private val corners = mutableListOf<PointF>()
    private var draggedIndex = -1
    private var guideMode = false
    private var statusText: String? = null
    private val bracketLength = 60f

    var onCornersChanged: (() -> Unit)? = null

    fun setCorners(newCorners: List<PointF>) {
        if (newCorners.size != 4) return
        corners.clear()
        corners.addAll(newCorners)
        guideMode = false
        invalidate()
    }

    fun getCorners(): List<PointF> = corners.toList()

    fun setGuideMode(enabled: Boolean) {
        if (guideMode == enabled) return
        guideMode = enabled
        invalidate()
    }

    fun setStatusText(text: String?) {
        if (statusText == text) return
        statusText = text
        invalidate()
    }

    fun clear() {
        corners.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (guideMode && corners.isEmpty()) {
            drawGuide(canvas)
        } else if (corners.size == 4) {
            path.reset()
            path.moveTo(corners[0].x, corners[0].y)
            path.lineTo(corners[1].x, corners[1].y)
            path.lineTo(corners[2].x, corners[2].y)
            path.lineTo(corners[3].x, corners[3].y)
            path.close()
            canvas.drawPath(path, linePaint)

            for (i in corners.indices) {
                drawBracket(canvas, corners[i], i)
            }
        }

        statusText?.let {
            canvas.drawText(it, width / 2f, 120f, statusPaint)
        }
    }

    private fun drawBracket(canvas: Canvas, corner: PointF, index: Int) {
        val cx = corner.x
        val cy = corner.y
        val len = bracketLength

        when (index) {
            0 -> {
                canvas.drawLine(cx, cy, cx + len, cy, bracketPaint)
                canvas.drawLine(cx, cy, cx, cy + len, bracketPaint)
            }
            1 -> {
                canvas.drawLine(cx, cy, cx - len, cy, bracketPaint)
                canvas.drawLine(cx, cy, cx, cy + len, bracketPaint)
            }
            2 -> {
                canvas.drawLine(cx, cy, cx - len, cy, bracketPaint)
                canvas.drawLine(cx, cy, cx, cy - len, bracketPaint)
            }
            3 -> {
                canvas.drawLine(cx, cy, cx + len, cy, bracketPaint)
                canvas.drawLine(cx, cy, cx, cy - len, bracketPaint)
            }
        }
    }

    private fun drawGuide(canvas: Canvas) {
        val margin = width * 0.08f
        guideRect.set(margin, margin, width - margin, height - margin)
        canvas.drawRoundRect(guideRect, 24f, 24f, guidePaint)
        canvas.drawText("Point at a document", width / 2f, height / 2f, guideTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (corners.size != 4) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                draggedIndex = findNearestCorner(event.x, event.y)
                return draggedIndex >= 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggedIndex in corners.indices) {
                    val c = applyQuadrantConstraint(draggedIndex, event.x, event.y)
                    corners[draggedIndex].set(c.first, c.second)
                    onCornersChanged?.invoke()
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggedIndex = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun applyQuadrantConstraint(index: Int, x: Float, y: Float): Pair<Float, Float> {
        val centerX = width / 2f
        val centerY = height / 2f
        val margin = 20f
        var cx = x.coerceIn(margin, width - margin)
        var cy = y.coerceIn(margin, height - margin)

        when (index) {
            0 -> {
                cx = cx.coerceAtMost(centerX - margin)
                cy = cy.coerceAtMost(centerY - margin)
            }
            1 -> {
                cx = cx.coerceAtLeast(centerX + margin)
                cy = cy.coerceAtMost(centerY - margin)
            }
            2 -> {
                cx = cx.coerceAtLeast(centerX + margin)
                cy = cy.coerceAtLeast(centerY + margin)
            }
            3 -> {
                cx = cx.coerceAtMost(centerX - margin)
                cy = cy.coerceAtLeast(centerY + margin)
            }
        }
        return Pair(cx, cy)
    }

    private fun findNearestCorner(x: Float, y: Float): Int {
        var best = -1
        var bestDist = 100f * 100f
        for (i in corners.indices) {
            val dx = corners[i].x - x
            val dy = corners[i].y - y
            val d = dx * dx + dy * dy
            if (d < bestDist) { bestDist = d; best = i }
        }
        return best
    }
}
