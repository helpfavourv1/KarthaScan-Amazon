package com.zdmgold.katharscan.autoscan

import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * OpenCV-based document edge detector.
 *
 * Runs per frame on the Y (luminance) plane of a CameraX ImageProxy.
 * Returns the four corner points of the most likely document quadrilateral,
 * or null if no valid quadrilateral is found.
 *
 * The critical difference from naive "largest contour" detection:
 * every contour is checked for convexity AND minimum area before being
 * considered. This prevents the wrong-quad problem on multi-region photos
 * (flyers, receipts on desks, multiple cards in one shot).
 */
class DocumentEdgeDetector {

    companion object {
        private const val CANNY_LOW = 75.0
        private const val CANNY_HIGH = 200.0
        private const val GAUSSIAN_KERNEL = 5
        private const val DILATE_KERNEL = 5
        private const val APPROX_EPSILON_RATIO = 0.02
        private const val MIN_AREA_RATIO = 0.15
    }

    private val gray = Mat()
    private val blurred = Mat()
    private val edges = Mat()
    private val dilated = Mat()
    private val hierarchy = Mat()

    /**
     * @param yPlane single-channel luminance Mat from a CameraX ImageProxy Y-plane
     * @return four corners in image coordinates, or null if none found
     */
    fun detect(yPlane: Mat): MatOfPoint2f? {
        if (yPlane.channels() != 1) {
            Imgproc.cvtColor(yPlane, gray, Imgproc.COLOR_YUV2GRAY_420)
        } else {
            yPlane.copyTo(gray)
        }

        Imgproc.GaussianBlur(
            gray, blurred,
            Size(GAUSSIAN_KERNEL.toDouble(), GAUSSIAN_KERNEL.toDouble()),
            0.0
        )

        Imgproc.Canny(blurred, edges, CANNY_LOW, CANNY_HIGH)

        val dilateKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(DILATE_KERNEL.toDouble(), DILATE_KERNEL.toDouble())
        )
        Imgproc.dilate(edges, dilated, dilateKernel)
        dilateKernel.release()

        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(
            dilated, contours, hierarchy,
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
        )

        val frameArea = yPlane.width().toDouble() * yPlane.height().toDouble()
        val minArea = frameArea * MIN_AREA_RATIO

        var bestQuad: MatOfPoint2f? = null
        var bestArea = 0.0

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < minArea) {
                contour.release()
                continue
            }

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

        return bestQuad
    }

    fun release() {
        gray.release()
        blurred.release()
        edges.release()
        dilated.release()
        hierarchy.release()
    }
}
