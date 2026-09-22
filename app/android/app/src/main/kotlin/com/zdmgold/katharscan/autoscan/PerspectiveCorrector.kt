package com.zdmgold.katharscan.autoscan

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * OpenCV perspective correction and filtering.
 *
 * Given a source bitmap and four document corners (in bitmap coordinates),
 * produces a flattened, filtered result bitmap suitable for saving.
 *
 * Filter.ENHANCE mirrors what ML Kit Document Scanner produces by default:
 * adaptive threshold — a "scanned" look with white background and black text.
 */
class PerspectiveCorrector {

    enum class Filter {
        NONE,
        GRAYSCALE,
        ENHANCE
    }

    fun correct(
        source: Bitmap,
        corners: MatOfPoint2f,
        filter: Filter = Filter.ENHANCE
    ): Bitmap {
        val srcMat = Mat()
        Utils.bitmapToMat(source, srcMat)

        val points = corners.toArray()
        if (points.size != 4) {
            srcMat.release()
            return source
        }

        val p0 = points[0]
        val p1 = points[1]
        val p2 = points[2]
        val p3 = points[3]

        val widthTop = distance(p0, p1)
        val widthBottom = distance(p3, p2)
        val heightLeft = distance(p0, p3)
        val heightRight = distance(p1, p2)

        val outWidth = maxOf(widthTop, widthBottom).toInt().coerceAtLeast(1)
        val outHeight = maxOf(heightLeft, heightRight).toInt().coerceAtLeast(1)

        val srcQuad = MatOfPoint2f(p0, p1, p2, p3)
        val dstQuad = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(outWidth.toDouble(), 0.0),
            Point(outWidth.toDouble(), outHeight.toDouble()),
            Point(0.0, outHeight.toDouble())
        )

        val transform = Imgproc.getPerspectiveTransform(srcQuad, dstQuad)
        val warped = Mat()
        Imgproc.warpPerspective(
            srcMat, warped, transform,
            Size(outWidth.toDouble(), outHeight.toDouble())
        )

        val filtered = applyFilter(warped, filter)
        warped.release()

        val outBitmap = matToBitmap(filtered)
        filtered.release()

        srcMat.release()
        srcQuad.release()
        dstQuad.release()
        transform.release()

        return outBitmap
    }

    private fun applyFilter(input: Mat, filter: Filter): Mat {
        return when (filter) {
            Filter.NONE -> input.clone()
            Filter.GRAYSCALE -> {
                val out = Mat()
                Imgproc.cvtColor(input, out, Imgproc.COLOR_RGBA2GRAY)
                out
            }
            Filter.ENHANCE -> {
                val gray = Mat()
                Imgproc.cvtColor(input, gray, Imgproc.COLOR_RGBA2GRAY)
                val out = Mat()
                Imgproc.adaptiveThreshold(
                    gray, out, 255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY,
                    15, 10.0
                )
                gray.release()
                out
            }
        }
    }

    private fun matToBitmap(mat: Mat): Bitmap {
        val out = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        if (mat.channels() == 1) {
            val rgba = Mat()
            Imgproc.cvtColor(mat, rgba, Imgproc.COLOR_GRAY2RGBA)
            Utils.matToBitmap(rgba, out)
            rgba.release()
        } else {
            Utils.matToBitmap(mat, out)
        }
        return out
    }

    private fun distance(a: Point, b: Point): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return Math.sqrt(dx * dx + dy * dy)
    }
}
