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
import org.opencv.core.CvType
import org.opencv.core.Mat
import kotlin.random.Random

class ToneAdjustmentTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadOpenCv() {
            nu.pattern.OpenCV.loadLocally()
        }
    }

    // Deterministic image with saturated, dark, bright and colored pixels.
    private fun syntheticImage(): Mat {
        val rnd = Random(42)
        val mat = Mat(24, 24, CvType.CV_8UC3)
        val data = ByteArray(24 * 24 * 3) { rnd.nextInt(256).toByte() }
        mat.put(0, 0, data)
        return mat
    }

    private fun bgrAt(mat: Mat, row: Int, col: Int): Triple<Int, Int, Int> {
        val px = mat.get(row, col)
        return Triple(px[2].toInt(), px[1].toInt(), px[0].toInt()) // as RGB
    }

    @Test
    fun neutral_matrix_is_the_identity() {
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        assertThat(toneMatrix(Adjustments())).isEqualTo(identity)
    }

    @Test
    fun neutral_tone_returns_an_identical_copy() {
        val src = syntheticImage()
        val out = applyTone(src, Adjustments())
        val diff = Mat()
        org.opencv.core.Core.absdiff(src, out, diff)
        assertThat(org.opencv.core.Core.norm(diff, org.opencv.core.Core.NORM_INF)).isEqualTo(0.0)
        assertThat(out.nativeObjAddr).isNotEqualTo(src.nativeObjAddr)
    }

    @Test
    fun minimum_saturation_removes_all_color() {
        val out = applyTone(syntheticImage(), Adjustments(saturation = -100))
        for (row in 0 until out.rows()) for (col in 0 until out.cols()) {
            val (r, g, b) = bgrAt(out, row, col)
            assertThat(Math.abs(r - g)).describedAs("R-G at $row,$col").isLessThanOrEqualTo(1)
            assertThat(Math.abs(g - b)).isLessThanOrEqualTo(1)
        }
    }

    @Test
    fun brightness_lightens_and_darkens() {
        val gray = Mat(2, 2, CvType.CV_8UC3, org.opencv.core.Scalar(100.0, 100.0, 100.0))
        val lighter = applyTone(gray, Adjustments(brightness = 50))
        val darker = applyTone(gray, Adjustments(brightness = -50))
        assertThat(lighter.get(0, 0)[0]).isGreaterThan(100.0)
        assertThat(darker.get(0, 0)[0]).isLessThan(100.0)
    }

    @Test
    fun contrast_moves_values_away_from_or_toward_mid_gray() {
        val dark = Mat(2, 2, CvType.CV_8UC3, org.opencv.core.Scalar(60.0, 60.0, 60.0))
        assertThat(applyTone(dark, Adjustments(contrast = 50)).get(0, 0)[0]).isLessThan(60.0)
        assertThat(applyTone(dark, Adjustments(contrast = -50)).get(0, 0)[0]).isGreaterThan(60.0)
        val mid = Mat(2, 2, CvType.CV_8UC3, org.opencv.core.Scalar(128.0, 128.0, 128.0))
        assertThat(applyTone(mid, Adjustments(contrast = 80)).get(0, 0)[0]).isEqualTo(128.0)
    }

    @Test
    fun kotlin_per_pixel_math_matches_opencv() {
        val src = syntheticImage()
        val cases = listOf(
            Adjustments(brightness = 30),
            Adjustments(contrast = -60),
            Adjustments(saturation = 80),
            Adjustments(brightness = -20, contrast = 40, saturation = -30),
            Adjustments(brightness = 100, contrast = 100, saturation = 100),
            Adjustments(brightness = -100, contrast = -100, saturation = -100),
        )
        for (adj in cases) {
            val out = applyTone(src, adj)
            val matrix = toneMatrix(adj)
            for (row in 0 until src.rows()) for (col in 0 until src.cols()) {
                val px = src.get(row, col)
                val expected = applyToneToPixel(matrix, px[2].toInt(), px[1].toInt(), px[0].toInt())
                val actual = out.get(row, col)
                assertThat(Math.abs(actual[2] - expected.first)).describedAs("$adj R at $row,$col")
                    .isLessThanOrEqualTo(1.0)
                assertThat(Math.abs(actual[1] - expected.second)).describedAs("$adj G at $row,$col")
                    .isLessThanOrEqualTo(1.0)
                assertThat(Math.abs(actual[0] - expected.third)).describedAs("$adj B at $row,$col")
                    .isLessThanOrEqualTo(1.0)
            }
        }
    }
}
