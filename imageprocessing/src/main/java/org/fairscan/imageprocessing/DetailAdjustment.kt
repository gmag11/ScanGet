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
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

// Detail is an unsharp mask. Its radius is proportional to the longest side of the page so that a
// preview at screen size and the full-resolution page sharpen at the same scale.
// A 1 MP preview (about 1150 px) already gets a radius above the minimum, so it sharpens at the
// same relative scale as the full-resolution page.
private const val DETAIL_SIGMA_PER_PIXEL = 0.0007
private const val DETAIL_MIN_SIGMA = 0.5

// Sharpening amount reached at detail = 100.
private const val DETAIL_MAX_AMOUNT = 1.5

/**
 * Sharpens edges and text. [detail] is in 0..100. Returns a new Mat that the caller owns; it is a
 * plain copy when [detail] is 0.
 */
fun applyDetail(bgr: Mat, detail: Int): Mat {
    val result = Mat()
    if (detail <= 0) {
        bgr.copyTo(result)
        return result
    }
    val amount = DETAIL_MAX_AMOUNT * detail / 100.0
    val sigma = max(DETAIL_MIN_SIGMA, DETAIL_SIGMA_PER_PIXEL * max(bgr.cols(), bgr.rows()))
    val blurred = Mat()
    Imgproc.GaussianBlur(bgr, blurred, Size(0.0, 0.0), sigma)
    Core.addWeighted(bgr, 1.0 + amount, blurred, -amount, 0.0, result)
    blurred.release()
    return result
}
