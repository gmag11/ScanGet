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

class ColorFiltersTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadOpenCv() {
            nu.pattern.OpenCV.loadLocally()
        }
    }

    private fun solid(width: Int, height: Int, b: Double, g: Double, r: Double) =
        Mat(height, width, CvType.CV_8UC3, Scalar(b, g, r))

    /** Mean of the three channels (as one value) inside [rect]. */
    private fun mean(mat: Mat, rect: Rect) = Core.mean(mat.submat(rect)).`val`.take(3).average()

    private fun channelMeans(mat: Mat, rect: Rect) = Core.mean(mat.submat(rect)).`val`

    /** White paper with a dark shadow over its left third and some text bars. */
    private fun shadowedPage(): Mat {
        val mat = solid(300, 400, 225.0, 225.0, 225.0)
        mat.submat(Rect(0, 0, 100, 400)).setTo(Scalar(130.0, 130.0, 130.0))
        for (row in 0 until 10) {
            mat.submat(Rect(20, 30 + row * 36, 250, 6)).setTo(Scalar(40.0, 40.0, 40.0))
        }
        return mat
    }

    @Test
    fun negative_is_255_minus_grayscale() {
        val page = TestPages.colorPage()
        val negative = enhanceCapturedImage(page, ColorMode.NEGATIVE)
        val gray = enhanceCapturedImage(page, ColorMode.GRAYSCALE)
        val inverted = Mat()
        Core.subtract(Mat(gray.size(), gray.type(), Scalar.all(255.0)), gray, inverted)
        val diff = Mat()
        Core.absdiff(negative, inverted, diff)
        assertThat(Core.norm(diff, Core.NORM_INF)).isEqualTo(0.0)
    }

    @Test
    fun negative_turns_dark_text_light_and_paper_dark() {
        val out = enhanceCapturedImage(shadowedPage(), ColorMode.NEGATIVE)
        val paper = mean(out, Rect(150, 5, 100, 20))
        val text = mean(out, Rect(60, 31, 150, 3))
        assertThat(paper).isLessThan(60.0)
        assertThat(text).isGreaterThan(paper + 80)
    }

    @Test
    fun negative_is_gray() {
        val out = enhanceCapturedImage(TestPages.colorPage(), ColorMode.NEGATIVE)
        val channels = ArrayList<Mat>()
        Core.split(out, channels)
        val diff = Mat()
        Core.absdiff(channels[0], channels[2], diff)
        assertThat(Core.norm(diff, Core.NORM_INF)).isEqualTo(0.0)
    }

    @Test
    fun no_shadows_evens_out_shaded_and_unshaded_paper() {
        val page = shadowedPage()
        val shaded = Rect(5, 5, 60, 20)    // inside the shadow, away from text
        val lit = Rect(200, 5, 60, 20)
        val before = mean(page, lit) - mean(page, shaded)
        val out = enhanceCapturedImage(page, ColorMode.NO_SHADOWS)
        val after = mean(out, lit) - mean(out, shaded)
        assertThat(before).isGreaterThan(80.0)
        assertThat(after).isLessThan(before / 4)
    }

    @Test
    fun no_shadows_keeps_color_and_text() {
        val page = TestPages.colorPage()
        val out = enhanceCapturedImage(page, ColorMode.NO_SHADOWS)
        val red = channelMeans(out, Rect(205, 65, 50, 50)) // B, G, R
        assertThat(red[2]).isGreaterThan(red[0] + 60)
        // The text bars stay much darker than the paper next to them.
        val text = mean(out, Rect(35, 42, 100, 3))
        val paper = mean(out, Rect(35, 52, 100, 8))
        assertThat(text).isLessThan(paper - 80)
    }

    @Test
    fun no_shadows_leaves_no_halo_around_a_saturated_color_block() {
        // Paper with a lighting gradient and a saturated blue block that is bright in the blue
        // channel only. A per-channel illumination estimate tints the paper around it.
        val page = solid(600, 400, 215.0, 225.0, 235.0)
        for (x in 0 until 600) {
            page.submat(Rect(x, 0, 1, 400)).setTo(Scalar.all(0.0).let { Scalar(215.0 - x / 8.0, 225.0 - x / 8.0, 235.0 - x / 8.0) })
        }
        page.submat(Rect(250, 180, 120, 40)).setTo(Scalar(190.0, 80.0, 40.0))
        val out = enhanceCapturedImage(page, ColorMode.NO_SHADOWS)
        for (ring in listOf(Rect(238, 190, 8, 20), Rect(374, 190, 8, 20), Rect(280, 168, 40, 8), Rect(280, 224, 40, 8))) {
            val m = channelMeans(out, ring) // B, G, R
            assertThat(Math.abs(m[0] - m[2])).describedAs("tint next to the block at $ring").isLessThan(8.0)
            assertThat(m[0]).describedAs("brightness next to the block at $ring").isGreaterThan(235.0)
        }
    }

    @Test
    fun no_shadows_strength_zero_leaves_the_page_untouched() {
        val page = shadowedPage()
        val out = removeShadows(page, strength = 0.0)
        val diff = Mat()
        Core.absdiff(page, out, diff)
        assertThat(Core.norm(diff, Core.NORM_INF)).isLessThanOrEqualTo(1.0)
    }

    @Test
    fun no_shadows_shadow_removal_control_changes_the_result() {
        val page = shadowedPage()
        val shaded = Rect(5, 5, 60, 20)
        val neutral = mean(enhanceCapturedImage(page, ColorMode.NO_SHADOWS), shaded)
        val weak = mean(enhanceCapturedImage(page, ColorMode.NO_SHADOWS, 0L, Adjustments(shadowRemoval = -80)), shaded)
        assertThat(weak).isLessThan(neutral)
    }

    @Test
    fun enhance_brings_yellow_paper_closer_to_neutral() {
        // Yellowish paper: blue is much lower than red.
        val page = solid(200, 200, 150.0, 195.0, 230.0)
        val paperBefore = channelMeans(page, Rect(20, 20, 100, 100))
        val out = enhanceCapturedImage(page, ColorMode.ENHANCE)
        val paperAfter = channelMeans(out, Rect(20, 20, 100, 100))
        val castBefore = paperBefore[2] - paperBefore[0]
        val castAfter = Math.abs(paperAfter[2] - paperAfter[0])
        assertThat(castAfter).isLessThan(castBefore / 3)
    }

    @Test
    fun enhance_keeps_colored_content_colored() {
        val out = enhanceCapturedImage(TestPages.colorPage(), ColorMode.ENHANCE)
        val blue = channelMeans(out, Rect(205, 205, 50, 50)) // B, G, R
        assertThat(blue[0]).isGreaterThan(blue[2] + 50)
    }

    @Test
    fun lighten_raises_the_paper_level() {
        val page = solid(200, 200, 175.0, 180.0, 185.0)
        val out = enhanceCapturedImage(page, ColorMode.LIGHTEN)
        assertThat(mean(out, Rect(20, 20, 100, 100))).isGreaterThan(mean(page, Rect(20, 20, 100, 100)) + 40)
    }

    @Test
    fun lighten_keeps_text_dark_and_color() {
        val page = TestPages.colorPage()
        val out = enhanceCapturedImage(page, ColorMode.LIGHTEN)
        val text = mean(out, Rect(35, 42, 100, 3))
        val paper = mean(out, Rect(35, 52, 100, 8))
        assertThat(text).isLessThan(paper - 100)
        val red = channelMeans(out, Rect(205, 65, 50, 50))
        assertThat(red[2]).isGreaterThan(red[0] + 60)
    }

    @Test
    fun new_filters_return_a_three_channel_image_of_the_same_size() {
        val page = TestPages.colorPage()
        for (mode in listOf(ColorMode.ORIGINAL, ColorMode.LIGHTEN, ColorMode.ENHANCE, ColorMode.NO_SHADOWS, ColorMode.NEGATIVE)) {
            val out = enhanceCapturedImage(page, mode)
            assertThat(out.size()).describedAs(mode.name).isEqualTo(page.size())
            assertThat(out.type()).describedAs(mode.name).isEqualTo(CvType.CV_8UC3)
        }
    }

    @Test
    fun filters_do_not_modify_their_input() {
        for (mode in ColorMode.entries) {
            val page = TestPages.colorPage()
            val before = TestPages.checksum(page)
            enhanceCapturedImage(page, mode)
            assertThat(TestPages.checksum(page)).describedAs(mode.name).isEqualTo(before)
        }
    }
}
