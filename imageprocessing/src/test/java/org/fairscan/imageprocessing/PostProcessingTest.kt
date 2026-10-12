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
import org.opencv.core.Mat

class PostProcessingTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadOpenCv() {
            nu.pattern.OpenCV.loadLocally()
        }

        // CRC32 of the output of the filters on TestPages.colorPage(), recorded from the code as
        // it was before adjustments existed. A neutral render must still match them. They depend
        // on OpenCV's floating-point code paths: if they change after an OpenCV upgrade, compare
        // the images before updating the numbers.
        private const val GOLDEN_COLOR = 22813363L
        private const val GOLDEN_GRAYSCALE = 2778139882L
        private const val GOLDEN_BLACK_AND_WHITE = 3035612855L
        private const val GOLDEN_BLACK_AND_WHITE_UPSCALED = 2188896231L
    }

    private val page = TestPages.colorPage()

    private fun render(mode: ColorMode, adjustments: Adjustments = Adjustments.NEUTRAL, maxPixels: Long = 0L) =
        enhanceCapturedImage(page, mode, maxPixels, adjustments)

    private fun checksum(mode: ColorMode, adjustments: Adjustments = Adjustments.NEUTRAL) =
        TestPages.checksum(render(mode, adjustments))

    private fun meanValue(mat: Mat) = Core.mean(mat).`val`.take(3).average()

    private fun blackPixels(mat: Mat) = mat.total() - Core.countNonZero(mat)

    // --- Neutral adjustments keep the previous output ---

    @Test
    fun neutral_color_matches_previous_output() {
        assertThat(checksum(ColorMode.COLOR)).isEqualTo(GOLDEN_COLOR)
    }

    @Test
    fun neutral_grayscale_matches_previous_output() {
        assertThat(checksum(ColorMode.GRAYSCALE)).isEqualTo(GOLDEN_GRAYSCALE)
    }

    @Test
    fun neutral_black_and_white_matches_previous_output() {
        assertThat(checksum(ColorMode.BLACK_AND_WHITE)).isEqualTo(GOLDEN_BLACK_AND_WHITE)
        val upscaled = render(ColorMode.BLACK_AND_WHITE, maxPixels = 400_000L)
        assertThat(TestPages.checksum(upscaled)).isEqualTo(GOLDEN_BLACK_AND_WHITE_UPSCALED)
    }

    @Test
    fun original_returns_the_input_unchanged() {
        assertThat(TestPages.checksum(render(ColorMode.ORIGINAL))).isEqualTo(TestPages.checksum(page))
    }

    // --- Controls a filter does not expose are ignored ---

    @Test
    fun black_and_white_ignores_tone_controls() {
        val noisy = Adjustments(brightness = 50, contrast = 30, saturation = 40, shadowRemoval = 20)
        assertThat(checksum(ColorMode.BLACK_AND_WHITE, noisy)).isEqualTo(GOLDEN_BLACK_AND_WHITE)
    }

    @Test
    fun grayscale_ignores_saturation_and_threshold() {
        assertThat(checksum(ColorMode.GRAYSCALE, Adjustments(saturation = 80, threshold = 50)))
            .isEqualTo(GOLDEN_GRAYSCALE)
    }

    @Test
    fun color_ignores_threshold() {
        assertThat(checksum(ColorMode.COLOR, Adjustments(threshold = 70))).isEqualTo(GOLDEN_COLOR)
    }

    // --- Controls have an effect ---

    @Test
    fun brightness_changes_the_rendering() {
        val neutral = meanValue(render(ColorMode.COLOR))
        assertThat(meanValue(render(ColorMode.COLOR, Adjustments(brightness = 40)))).isGreaterThan(neutral)
        assertThat(meanValue(render(ColorMode.COLOR, Adjustments(brightness = -40)))).isLessThan(neutral)
    }

    @Test
    fun saturation_minimum_makes_the_page_gray() {
        val out = render(ColorMode.COLOR, Adjustments(saturation = -100))
        val channels = ArrayList<Mat>()
        Core.split(out, channels)
        val diff = Mat()
        Core.absdiff(channels[0], channels[2], diff)
        assertThat(Core.norm(diff, Core.NORM_INF)).isLessThanOrEqualTo(1.0)
    }

    @Test
    fun shadow_removal_changes_the_color_rendering() {
        assertThat(checksum(ColorMode.COLOR, Adjustments(shadowRemoval = 60))).isNotEqualTo(GOLDEN_COLOR)
        assertThat(checksum(ColorMode.COLOR, Adjustments(shadowRemoval = -60))).isNotEqualTo(GOLDEN_COLOR)
    }

    @Test
    fun raising_the_threshold_blackens_more_of_the_page() {
        val neutral = blackPixels(render(ColorMode.BLACK_AND_WHITE))
        val higher = blackPixels(render(ColorMode.BLACK_AND_WHITE, Adjustments(threshold = 80)))
        val lower = blackPixels(render(ColorMode.BLACK_AND_WHITE, Adjustments(threshold = -80)))
        assertThat(higher).isGreaterThan(neutral)
        assertThat(lower).isLessThan(neutral)
    }

    // --- Mapping helpers ---

    @Test
    fun strength_is_the_base_when_neutral_and_reaches_both_ends() {
        assertThat(strengthAround(0.6, 0)).isEqualTo(0.6)
        assertThat(strengthAround(0.6, 100)).isEqualTo(1.0)
        assertThat(strengthAround(0.6, -100)).isEqualTo(0.0)
        assertThat(strengthAround(0.6, 50)).isBetween(0.6, 1.0)
        assertThat(strengthAround(0.6, -50)).isBetween(0.0, 0.6)
    }

    @Test
    fun sauvola_k_is_the_previous_constant_when_neutral_and_decreases_with_threshold() {
        assertThat(sauvolaK(0)).isEqualTo(0.25)
        assertThat(sauvolaK(100)).isLessThan(sauvolaK(0))
        assertThat(sauvolaK(-100)).isGreaterThan(sauvolaK(0))
    }
}
