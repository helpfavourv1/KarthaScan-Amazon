package com.zdmgold.katharscan.autoscan

import android.graphics.PointF

class QuadStabilizer(
    private val bufferSize: Int = 8,
    private val stabilityThreshold: Double = 25.0,
    private val requiredStableFrames: Int = 5,
    private val cooldownMs: Long = 2000L
) {

    private val buffer = ArrayDeque<List<PointF>>()
    private var stableCount = 0
    private var lastFireMs = 0L

    fun push(corners: List<PointF>): Boolean {
        if (corners.size != 4) {
            reset()
            return false
        }

        buffer.addLast(corners)
        if (buffer.size > bufferSize) buffer.removeFirst()
        if (buffer.size < 2) return false

        val prev = buffer[buffer.size - 2]
        val curr = buffer.last()
        val delta = cornerDelta(prev, curr)

        if (delta < stabilityThreshold) {
            stableCount++
        } else {
            stableCount = 0
        }

        val now = System.currentTimeMillis()
        if (stableCount >= requiredStableFrames && (now - lastFireMs) > cooldownMs) {
            lastFireMs = now
            stableCount = 0
            buffer.clear()
            return true
        }

        return false
    }

    fun reset() {
        buffer.clear()
        stableCount = 0
    }

    private fun cornerDelta(a: List<PointF>, b: List<PointF>): Double {
        var sum = 0.0
        for (i in 0 until 4) {
            val dx = (a[i].x - b[i].x).toDouble()
            val dy = (a[i].y - b[i].y).toDouble()
            sum += Math.sqrt(dx * dx + dy * dy)
        }
        return sum / 4.0
    }
}
