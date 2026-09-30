package com.github.username.cardapp.ui.scan

import androidx.camera.core.ImageProxy

/**
 * Computer-vision helper for auto-scan mode.
 *
 * Instead of scanning on a timer, auto mode fingerprints each camera frame (a 16×16
 * grayscale grid sampled from the center of the Y/luminance plane) and reasons about
 * scene stability and content using cheap mean-difference / variance metrics. This
 * object owns those metrics and the thresholds that interpret them; [ScanViewModel]
 * drives the actual state machine on top of them.
 */
object FrameFingerprintAnalyzer {

    /** Consecutive stable frames required before firing OCR. */
    const val STABLE_FRAMES_REQUIRED = 5

    // Frame fingerprinting — 16×16 grayscale grid sampled from center of frame
    private const val FINGERPRINT_SIZE = 16
    private const val STABLE_THRESHOLD = 8.0            // max mean pixel diff for "stable"
    private const val CHANGE_THRESHOLD = 25.0           // min mean pixel diff for "scene changed"
    private const val CONTENT_VARIANCE_THRESHOLD = 200.0 // min variance to consider "has content"

    /** Sample a 16×16 grayscale fingerprint from the central half of the Y plane.
     *  Stability and content are judged on the middle of the camera frame rather than
     *  the on-screen reticle — OCR reads the whole frame, so the card only needs to be
     *  roughly centred. The screen-space centre is mapped to sensor coordinates using
     *  the image's rotation degrees. */
    fun computeFingerprint(imageProxy: ImageProxy): IntArray {
        val yPlane = imageProxy.planes[0]
        val yBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val imgW = imageProxy.width
        val imgH = imageProxy.height
        val rotation = imageProxy.imageInfo.rotationDegrees

        // Frame center in normalized screen coords (portrait)
        val screenCx = 0.5
        val screenCy = 0.5

        // Map screen coords to sensor coords based on rotation
        val sensorCxNorm: Double
        val sensorCyNorm: Double
        when (rotation) {
            90  -> { sensorCxNorm = 1.0 - screenCy; sensorCyNorm = screenCx }
            270 -> { sensorCxNorm = screenCy;        sensorCyNorm = 1.0 - screenCx }
            180 -> { sensorCxNorm = 1.0 - screenCx;  sensorCyNorm = 1.0 - screenCy }
            else -> { sensorCxNorm = screenCx;        sensorCyNorm = screenCy }
        }

        val cropW = imgW / 2
        val cropH = imgH / 2
        val centerX = (sensorCxNorm * imgW).toInt().coerceIn(cropW / 2, imgW - cropW / 2)
        val centerY = (sensorCyNorm * imgH).toInt().coerceIn(cropH / 2, imgH - cropH / 2)
        val startX = centerX - cropW / 2
        val startY = centerY - cropH / 2

        return IntArray(FINGERPRINT_SIZE * FINGERPRINT_SIZE) { i ->
            val fx = i % FINGERPRINT_SIZE
            val fy = i / FINGERPRINT_SIZE
            val srcX = startX + fx * cropW / FINGERPRINT_SIZE
            val srcY = startY + fy * cropH / FINGERPRINT_SIZE
            yBuffer[srcY * rowStride + srcX].toInt() and 0xFF
        }
    }

    /** Mean absolute difference between two fingerprints. */
    fun fingerprintDiff(a: IntArray, b: IntArray): Double {
        var sum = 0L
        for (i in a.indices) sum += kotlin.math.abs(a[i] - b[i])
        return sum.toDouble() / a.size
    }

    /** Variance of luminance values — low means uniform (empty surface), high means content. */
    fun fingerprintVariance(fp: IntArray): Double {
        val mean = fp.average()
        return fp.sumOf { (it - mean) * (it - mean) } / fp.size
    }

    /** True when two consecutive frames are similar enough to count as a still scene. */
    fun isStable(a: IntArray, b: IntArray): Boolean =
        fingerprintDiff(a, b) < STABLE_THRESHOLD

    /** True when a frame has diverged far enough from a reference to count as a scene change
     *  (card removed / swapped). */
    fun hasChanged(reference: IntArray, current: IntArray): Boolean =
        fingerprintDiff(reference, current) > CHANGE_THRESHOLD

    /** True when a frame has enough luminance variance to plausibly contain a card
     *  rather than an empty surface. */
    fun hasContent(fp: IntArray): Boolean =
        fingerprintVariance(fp) > CONTENT_VARIANCE_THRESHOLD
}
