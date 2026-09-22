package com.zdmgold.katharscan.autoscan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CornerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B998B")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B998B")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val path = Path()
    private val corners = mutableListOf<PointF>()
    private var draggedIndex = -1

    var onCornersChanged: (() -> Unit)? = null

    fun setCorners(newCorners: List<PointF>) {
        if (newCorners.size != 4) return
        corners.clear()
        corners.addAll(newCorners)
        invalidate()
    }

    fun getCorners(): List<PointF> = corners.toList()

    fun clear() {
        corners.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (corners.size != 4) return

        path.reset()
        path.moveTo(corners[0].x, corners[0].y)
        for (i in 1 until corners.size) {
            path.lineTo(corners[i].x, corners[i].y)
        }
        path.close()
        canvas.drawPath(path, strokePaint)

        for (corner in corners) {
            canvas.drawCircle(corner.x, corner.y, 20f, handleFillPaint)
            canvas.drawCircle(corner.x, corner.y, 20f, handleStrokePaint)
        }
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
                    corners[draggedIndex].set(event.x, event.y)
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

    private fun findNearestCorner(x: Float, y: Float): Int {
        var best = -1
        var bestDist = 80f * 80f
        for (i in corners.indices) {
            val dx = corners[i].x - x
            val dy = corners[i].y - y
            val d = dx * dx + dy * dy
            if (d < bestDist) { bestDist = d; best = i }
        }
        return best
    }
}
