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
import kotlin.math.pow
import kotlin.math.roundToInt

// Brightness, contrast and saturation are one 4x5 color matrix, in the same layout as
// android.graphics.ColorMatrix and Compose's ColorMatrix: rows R, G, B, A, columns R, G, B, A, 1,
// 8-bit scale, no gamma. The screen preview applies it as a color filter and the final render
// applies it with OpenCV, so both give the same pixels.

private const val LUMA_R = 0.299f
private const val LUMA_G = 0.587f
private const val LUMA_B = 0.114f

// Contrast factor 2^(contrast / CONTRAST_STEPS): 0.35x at -100, 1x at 0, 2.9x at +100.
private const val CONTRAST_STEPS = 66.0
// Brightness 100 adds half of the 8-bit range.
private const val BRIGHTNESS_OFFSET_PER_UNIT = 1.275f

/** True when brightness, contrast and saturation have any effect. */
val Adjustments.hasTone: Boolean
    get() = brightness != 0 || contrast != 0 || saturation != 0

/**
 * 4x5 color matrix (20 floats, row-major, offsets on a 0..255 scale) for the brightness, contrast
 * and saturation of [adjustments]. Saturation runs first, then contrast around mid-gray, then
 * brightness. Neutral values give the identity.
 */
fun toneMatrix(adjustments: Adjustments): FloatArray {
    val s = 1f + adjustments.saturation / 100f
    val contrast = 2.0.pow(adjustments.contrast / CONTRAST_STEPS).toFloat()
    val offset = adjustments.brightness * BRIGHTNESS_OFFSET_PER_UNIT

    // Saturation: blend each channel with the luma by factor s.
    val sat = arrayOf(
        floatArrayOf(LUMA_R * (1 - s) + s, LUMA_G * (1 - s), LUMA_B * (1 - s)),
        floatArrayOf(LUMA_R * (1 - s), LUMA_G * (1 - s) + s, LUMA_B * (1 - s)),
        floatArrayOf(LUMA_R * (1 - s), LUMA_G * (1 - s), LUMA_B * (1 - s) + s),
    )
    // Contrast pivots on 128, then brightness adds the offset: out = contrast * (x - 128) + 128 + offset.
    val constant = 128f * (1 - contrast) + offset

    val m = FloatArray(20)
    for (row in 0 until 3) {
        for (col in 0 until 3) m[row * 5 + col] = contrast * sat[row][col]
        m[row * 5 + 4] = constant
    }
    m[3 * 5 + 3] = 1f // alpha is left alone
    return m
}

/** Applies [matrix] (from [toneMatrix]) to one pixel, rounding and clamping to 0..255. */
fun applyToneToPixel(matrix: FloatArray, r: Int, g: Int, b: Int): Triple<Int, Int, Int> {
    fun channel(row: Int): Int {
        val v = matrix[row * 5] * r + matrix[row * 5 + 1] * g + matrix[row * 5 + 2] * b + matrix[row * 5 + 4]
        return v.roundToInt().coerceIn(0, 255)
    }
    return Triple(channel(0), channel(1), channel(2))
}

/**
 * Applies brightness, contrast and saturation to a BGR image. Returns a new Mat that the caller
 * owns; it is a plain copy when the tone is neutral.
 */
fun applyTone(bgr: Mat, adjustments: Adjustments): Mat {
    require(bgr.type() == CvType.CV_8UC3) { "Expected a CV_8UC3 image" }
    val result = Mat()
    if (!adjustments.hasTone) {
        bgr.copyTo(result)
        return result
    }
    val m = toneMatrix(adjustments)
    // Core.transform wants a 3x4 matrix, output rows B, G, R and columns B, G, R, 1.
    val rowOf = intArrayOf(2, 1, 0) // BGR index -> RGB row/column
    val bgrMatrix = FloatArray(12)
    for (out in 0 until 3) {
        for (inp in 0 until 3) bgrMatrix[out * 4 + inp] = m[rowOf[out] * 5 + rowOf[inp]]
        bgrMatrix[out * 4 + 3] = m[rowOf[out] * 5 + 4]
    }
    val transform = Mat(3, 4, CvType.CV_32F)
    transform.put(0, 0, bgrMatrix)
    Core.transform(bgr, result, transform)
    transform.release()
    return result
}
