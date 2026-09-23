package com.zdmgold.katharscan.autoscan

import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class DocumentEdgeDetector {

    companion object {
        private const val TARGET_WIDTH = 800.0
        private const val GAUSSIAN_KERNEL = 5
        private const val DILATE_KERNEL = 5
        private const val APPROX_EPSILON_RATIO = 0.04
        private const val MIN_AREA_RATIO = 0.03
        private const val CANNY_LOW = 40.0
        private const val CANNY_HIGH = 130.0
    }

    private val gray = Mat()
    private val small = Mat()
    private val blurred = Mat()
    private val edges = Mat()
    private val hierarchy = Mat()

    fun detect(source: Mat): MatOfPoint2f? {
        if (source.channels() != 1) {
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
        } else {
            source.copyTo(gray)
        }

        val originalWidth = gray.width().toDouble()
        val originalHeight = gray.height().toDouble()
        val scale = if (originalWidth > TARGET_WIDTH) TARGET_WIDTH / originalWidth else 1.0
        val smallWidth = (originalWidth * scale).toInt().coerceAtLeast(1)
        val smallHeight = (originalHeight * scale).toInt().coerceAtLeast(1)

        if (scale != 1.0) {
            Imgproc.resize(gray, small, Size(smallWidth.toDouble(), smallHeight.toDouble()))
        } else {
            gray.copyTo(small)
        }

        Imgproc.GaussianBlur(small, blurred,
            Size(GAUSSIAN_KERNEL.toDouble(), GAUSSIAN_KERNEL.toDouble()), 0.0)

        Imgproc.Canny(blurred, edges, CANNY_LOW, CANNY_HIGH)

        val dilateKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(DILATE_KERNEL.toDouble(), DILATE_KERNEL.toDouble()))
        Imgproc.dilate(edges, edges, dilateKernel)
        dilateKernel.release()

        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(edges, contours, hierarchy,
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val frameArea = smallWidth.toDouble() * smallHeight.toDouble()
        val minArea = frameArea * MIN_AREA_RATIO

        var bestQuad: MatOfPoint2f? = null
        var bestArea = 0.0

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < minArea) { contour.release(); continue }

            val contour2f = MatOfPoint2f(*contour.toArray())
            val approx = MatOfPoint2f()
            val peri = Imgproc.arcLength(contour2f, true)
            Imgproc.approxPolyDP(contour2f, approx, APPROX_EPSILON_RATIO * peri, true)

            val approxPoints = approx.toArray()
            val convexMat = MatOfPoint(*approxPoints)
            val isValid = approx.total() == 4L && Imgproc.isContourConvex(convexMat)

            if (isValid && area > bestArea) {
                bestQuad?.release()
                bestQuad = approx
                bestArea = area
            } else {
                approx.release()
            }
            convexMat.release()
            contour2f.release()
            contour.release()
        }

        if (bestQuad == null) return null

        if (scale != 1.0) {
            val scaledPoints = bestQuad.toArray().map { p ->
                Point(p.x / scale, p.y / scale)
            }.toTypedArray()
            bestQuad.release()
            bestQuad = MatOfPoint2f(*scaledPoints)
        }
        return bestQuad
    }

    fun release() {
        gray.release(); small.release(); blurred.release()
        edges.release(); hierarchy.release()
    }
}
