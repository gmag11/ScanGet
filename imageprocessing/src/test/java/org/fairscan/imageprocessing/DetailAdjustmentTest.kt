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

import org.assertj.core.api.Assertions.assertThat
import org.junit.BeforeClass
import org.junit.Test
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class DetailAdjustmentTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadOpenCv() {
            nu.pattern.OpenCV.loadLocally()
        }
    }

    // Dark left half, light right half, softened so that the edge is as wide as a text stroke.
    private fun softEdge(width: Int = 1200, height: Int = 200, edgeSigma: Double = 1.0): Mat {
        val mat = Mat(height, width, CvType.CV_8UC3, Scalar.all(100.0))
        mat.submat(Rect(width / 2, 0, width / 2, height)).setTo(Scalar.all(160.0))
        val soft = Mat()
        Imgproc.GaussianBlur(mat, soft, Size(0.0, 0.0), edgeSigma)
        return soft
    }

    // Steepest step between neighbouring pixels along the middle row (first channel).
    private fun maxGradient(mat: Mat): Double {
        val row = DoubleArray(mat.cols()) { mat.get(mat.rows() / 2, it)[0] }
        return (1 until row.size).maxOf { Math.abs(row[it] - row[it - 1]) }
    }

    @Test
    fun detail_zero_is_a_no_op() {
        val src = softEdge()
        val out = applyDetail(src, 0)
        assertThat(TestPages.checksum(out)).isEqualTo(TestPages.checksum(src))
        assertThat(out.nativeObjAddr).isNotEqualTo(src.nativeObjAddr)
    }

    @Test
    fun detail_increases_edge_contrast() {
        val src = softEdge()
        val sharp = applyDetail(src, 60)
        assertThat(maxGradient(sharp)).isGreaterThan(maxGradient(src) * 1.1)
        // Far from the edge nothing changes.
        assertThat(sharp.get(100, 20)[0]).isEqualTo(100.0)
        assertThat(sharp.get(100, 1180)[0]).isEqualTo(160.0)
    }

    @Test
    fun more_detail_means_more_sharpening() {
        val src = softEdge()
        assertThat(maxGradient(applyDetail(src, 100))).isGreaterThan(maxGradient(applyDetail(src, 30)))
    }

    @Test
    fun sharpening_is_comparable_at_different_resolutions() {
        // The same page at two sizes (what the preview and the final render are) must be sharpened
        // by a similar relative amount.
        val small = softEdge(width = 1200, height = 100, edgeSigma = 1.0)
        val big = softEdge(width = 2400, height = 200, edgeSigma = 2.0)
        val smallGain = maxGradient(applyDetail(small, 60)) / maxGradient(small)
        val bigGain = maxGradient(applyDetail(big, 60)) / maxGradient(big)
        assertThat(smallGain).isGreaterThan(1.1)
        assertThat(bigGain).isGreaterThan(1.1)
        assertThat(Math.abs(smallGain - bigGain)).isLessThan(0.25)
    }

    @Test
    fun detail_control_is_applied_by_the_filters_that_expose_it() {
        val page = TestPages.colorPage()
        for (mode in listOf(ColorMode.ORIGINAL, ColorMode.COLOR, ColorMode.GRAYSCALE, ColorMode.NEGATIVE)) {
            val plain = enhanceCapturedImage(page, mode)
            val sharp = enhanceCapturedImage(page, mode, 0L, Adjustments(detail = 70))
            assertThat(TestPages.checksum(sharp)).describedAs(mode.name).isNotEqualTo(TestPages.checksum(plain))
        }
    }

    @Test
    fun black_and_white_ignores_detail() {
        val page = TestPages.colorPage()
        val plain = enhanceCapturedImage(page, ColorMode.BLACK_AND_WHITE)
        val sharp = enhanceCapturedImage(page, ColorMode.BLACK_AND_WHITE, 0L, Adjustments(detail = 70))
        assertThat(TestPages.checksum(sharp)).isEqualTo(TestPages.checksum(plain))
    }
}
