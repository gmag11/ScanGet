/*
 * Copyright 2025-2026 The FairScan authors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.fairscan.imageprocessing

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.pow

// Filters that keep the colors of the page. Constants are a first guess and are meant to be tuned
// on real photos with the filter evaluator.

// Paper level that Lighten pushes the page to, and the gamma that lifts mid-tones afterwards.
private const val LIGHTEN_PAPER_TARGET = 250.0
private const val LIGHTEN_GAMMA = 0.85
// The black point never goes above this fraction of the paper level.
private const val LIGHTEN_MAX_BLACK_POINT = 0.6

// Tuned on the public FairScan dataset: clip 2.0 turned plain paper into a blotchy texture, so
// the CLAHE result is mixed with the untouched lightness instead of replacing it.
private const val ENHANCE_CLAHE_CLIP = 1.2
private const val ENHANCE_CLAHE_MIX = 0.35
private const val ENHANCE_CLAHE_TILES = 8.0
private const val ENHANCE_SATURATION = 15

// No shadows: how strongly the page is divided by its illumination when shadowRemoval is neutral,
// and the parameters of the illumination estimate (computed on an image of at most this size).
internal const val NO_SHADOWS_STRENGTH = 0.9
private const val ILLUMINATION_MAX_DIM = 256.0
// Thin dark strokes (text, ruled lines) up to this fraction of the page are erased first.
private const val ILLUMINATION_STROKE_FRACTION = 0.03
// Pixels darker than this fraction of the paper level are ink, not shadow, whatever their size.
private const val ILLUMINATION_INK_FRACTION = 0.5
private const val ILLUMINATION_PAPER_PERCENTILE = 0.9
// Blur radii, as fractions of the longest side, from coarse to fine. A finer estimate replaces a
// coarser one wherever enough paper pixels fall inside its window.
private val ILLUMINATION_SCALES = listOf(0.12, 0.04, 0.012)
private const val ILLUMINATION_MIN_COVERAGE = 0.3
private const val ILLUMINATION_MIN_LEVEL = 30.0

/** Grayscale page with its colors inverted: light ink on a dark background. */
fun negativeImage(bgr: Mat): Mat {
    val gray = enhanceGrayscaleImage(bgr)
    val inverted = Mat()
    Core.bitwise_not(gray, inverted)
    gray.release()
    return inverted
}

/**
 * White balance on the paper, then a linear stretch that puts the paper level near white, then a
 * gentle gamma. No illumination flattening, so gradients and shadows stay.
 */
fun lightenImage(bgr: Mat): Mat {
    val balanced = whiteBalanceOnPaper(bgr)
    val gray = Mat()
    Imgproc.cvtColor(balanced, gray, Imgproc.COLOR_BGR2GRAY)
    val paper = paperLevel(gray)
    // On an almost blank page the darkest percentile is the paper itself: cap the black point so
    // the stretch cannot explode.
    val low = minOf(percentileL(gray, 0.01), paper * LIGHTEN_MAX_BLACK_POINT)
    gray.release()

    val span = max(paper - low, 1.0)
    val lut = Mat(1, 256, CvType.CV_8U)
    val values = ByteArray(256) { i ->
        val stretched = ((i - low) * LIGHTEN_PAPER_TARGET / span).coerceIn(0.0, 255.0)
        val lifted = 255.0 * (stretched / 255.0).pow(LIGHTEN_GAMMA)
        lifted.toInt().coerceIn(0, 255).toByte()
    }
    lut.put(0, 0, values)
    val result = Mat()
    Core.LUT(balanced, lut, result)
    lut.release()
    balanced.release()
    return result
}

/**
 * Lighten (white balance, white paper), then local contrast (CLAHE on L) and a mild saturation
 * boost. CLAHE alone leaves the paper a dull gray, hence the white point first.
 */
fun enhanceColorImage(bgr: Mat): Mat {
    val lightened = lightenImage(bgr)

    val lab = Mat()
    Imgproc.cvtColor(lightened, lab, Imgproc.COLOR_BGR2Lab)
    lightened.release()
    val channels = ArrayList<Mat>(3)
    Core.split(lab, channels)
    val clahe = Imgproc.createCLAHE(ENHANCE_CLAHE_CLIP, Size(ENHANCE_CLAHE_TILES, ENHANCE_CLAHE_TILES))
    val equalized = Mat()
    clahe.apply(channels[0], equalized)
    Core.addWeighted(equalized, ENHANCE_CLAHE_MIX, channels[0], 1.0 - ENHANCE_CLAHE_MIX, 0.0, channels[0])
    equalized.release()
    Core.merge(channels, lab)
    channels.forEach { it.release() }

    val contrasted = Mat()
    Imgproc.cvtColor(lab, contrasted, Imgproc.COLOR_Lab2BGR)
    lab.release()

    val result = applyTone(contrasted, Adjustments(saturation = ENHANCE_SATURATION))
    contrasted.release()
    return result
}

/**
 * Divides each channel by an estimate of the illumination, so shadows and lighting gradients on
 * the paper disappear and the paper turns white, while colored content keeps its color.
 * [strength] in 0..1 blends from the original (0) to the fully flattened page (1).
 */
fun removeShadows(bgr: Mat, strength: Double = NO_SHADOWS_STRENGTH): Mat {
    val illumination = estimateIllumination(bgr)

    val src = Mat()
    bgr.convertTo(src, CvType.CV_32FC3)
    val flattened = Mat()
    Core.divide(src, illumination, flattened, 255.0)
    illumination.release()

    val blended = Mat()
    Core.addWeighted(src, 1.0 - strength, flattened, strength, 0.0, blended)
    src.release()
    flattened.release()

    val result = Mat()
    blended.convertTo(result, CvType.CV_8UC3) // saturates to 0..255
    blended.release()
    return result
}

// Illumination of the page as a CV_32FC3 image of the size of [bgr], with the color of the paper.
//
// It is estimated on the luminance, where any ink is dark, from the paper pixels only: ink is
// everything well below the paper level, so a large dark or colored block is not mistaken for a
// shadow, while soft shadows (which keep most of the paper's brightness) are followed closely.
// Holes left by the ink are filled from the surrounding paper.
private fun estimateIllumination(bgr: Mat): Mat {
    val scale = minOf(1.0, ILLUMINATION_MAX_DIM / max(bgr.cols(), bgr.rows()))
    val small = Mat()
    Imgproc.resize(bgr, small, Size(bgr.cols() * scale, bgr.rows() * scale), 0.0, 0.0, Imgproc.INTER_AREA)
    val maxDim = max(small.cols(), small.rows()).toDouble()

    val gray = Mat()
    Imgproc.cvtColor(small, gray, Imgproc.COLOR_BGR2GRAY)
    val strokeSize = (maxDim * ILLUMINATION_STROKE_FRACTION).toInt().coerceAtLeast(3) or 1
    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(strokeSize.toDouble(), strokeSize.toDouble()))
    val closed = Mat()
    Imgproc.morphologyEx(gray, closed, Imgproc.MORPH_CLOSE, kernel)
    kernel.release()

    val paper = percentileL(closed, ILLUMINATION_PAPER_PERCENTILE)
    val paperMask = Mat()
    Core.compare(closed, Scalar(paper * ILLUMINATION_INK_FRACTION), paperMask, Core.CMP_GE)

    val luma = Mat()
    closed.convertTo(luma, CvType.CV_32F)
    val weights = Mat()
    paperMask.convertTo(weights, CvType.CV_32F, 1.0 / 255.0)
    val weightedLuma = Mat()
    Core.multiply(luma, weights, weightedLuma)

    val estimate = Mat(luma.size(), CvType.CV_32F, Scalar(paper))
    val num = Mat()
    val den = Mat()
    val quotient = Mat()
    val reliable = Mat()
    for (fraction in ILLUMINATION_SCALES) {
        val sigma = max(1.0, fraction * maxDim)
        Imgproc.GaussianBlur(weightedLuma, num, Size(0.0, 0.0), sigma)
        Imgproc.GaussianBlur(weights, den, Size(0.0, 0.0), sigma)
        Core.compare(den, Scalar(ILLUMINATION_MIN_COVERAGE), reliable, Core.CMP_GE)
        Core.max(den, Scalar(1e-6), den)
        Core.divide(num, den, quotient)
        quotient.copyTo(estimate, reliable)
    }

    // Give the estimate the color of the paper, so dividing also balances the whites.
    val paperMeanBgr = Core.mean(small, paperMask).`val`
    val paperMeanLuma = Core.mean(gray, paperMask).`val`[0]
    val ratio = if (paperMeanLuma < 1.0) Scalar.all(1.0)
        else Scalar(paperMeanBgr[0] / paperMeanLuma, paperMeanBgr[1] / paperMeanLuma, paperMeanBgr[2] / paperMeanLuma)
    val estimate3 = Mat()
    Core.merge(listOf(estimate, estimate, estimate), estimate3)
    Core.multiply(estimate3, ratio, estimate3)

    val full = Mat()
    Imgproc.resize(estimate3, full, bgr.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
    Core.max(full, Scalar.all(ILLUMINATION_MIN_LEVEL), full)

    listOf(small, gray, closed, paperMask, luma, weights, weightedLuma, estimate, num, den, quotient, reliable, estimate3)
        .forEach { it.release() }
    return full
}

// Scales each channel so that the brighter half of the page, taken as paper, becomes neutral.
// Unlike the gray-world step of the color detection, the scaling applies to the whole image.
internal fun whiteBalanceOnPaper(bgr: Mat): Mat {
    val gray = Mat()
    Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
    val median = percentileL(gray, 0.5)
    val paperMask = Mat()
    Core.compare(gray, Scalar(median), paperMask, Core.CMP_GE)
    gray.release()

    val mean = Core.mean(bgr, paperMask).`val`
    paperMask.release()
    val eps = 1e-6
    val b = max(mean[0], eps)
    val g = max(mean[1], eps)
    val r = max(mean[2], eps)
    val target = (b + g + r) / 3.0

    val scaled = Mat()
    bgr.convertTo(scaled, CvType.CV_32FC3)
    Core.multiply(scaled, Scalar(target / b, target / g, target / r), scaled)
    val result = Mat()
    scaled.convertTo(result, CvType.CV_8UC3)
    scaled.release()
    return result
}

// The most frequent bright value, as an estimate of the paper level; falls back to a high
// percentile when the page has no bright pixels at all.
private fun paperLevel(gray: Mat): Double {
    val hist = Mat()
    Imgproc.calcHist(listOf(gray), MatOfInt(0), Mat(), hist, MatOfInt(256), MatOfFloat(0f, 256f))
    var mode = -1
    var modeCount = 0.0
    for (i in 128 until 256) {
        val count = hist.get(i, 0)[0]
        if (count > modeCount) {
            modeCount = count
            mode = i
        }
    }
    hist.release()
    return if (mode >= 0) mode.toDouble() else percentileL(gray, 0.95)
}
