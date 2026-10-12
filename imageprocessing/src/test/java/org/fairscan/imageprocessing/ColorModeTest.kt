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
import org.fairscan.imageprocessing.AdjustmentControl.*
import org.fairscan.imageprocessing.ColorMode.*
import org.junit.Test

class ColorModeTest {

    @Test
    fun only_black_and_white_is_bitonal() {
        assertThat(ColorMode.entries.filter { it.isBitonal }).containsExactly(BLACK_AND_WHITE)
    }

    @Test
    fun color_preserving_filters_expose_tone_and_detail() {
        val expected = setOf(BRIGHTNESS, CONTRAST, SATURATION, DETAIL)
        assertThat(ORIGINAL.controls).isEqualTo(expected)
        assertThat(LIGHTEN.controls).isEqualTo(expected)
        assertThat(ENHANCE.controls).isEqualTo(expected)
    }

    @Test
    fun flattening_filters_also_expose_shadow_removal() {
        val expected = setOf(BRIGHTNESS, CONTRAST, SATURATION, DETAIL, SHADOW_REMOVAL)
        assertThat(ColorMode.COLOR.controls).isEqualTo(expected)
        assertThat(NO_SHADOWS.controls).isEqualTo(expected)
    }

    @Test
    fun grayscale_filters_have_no_saturation() {
        val expected = setOf(BRIGHTNESS, CONTRAST, DETAIL)
        assertThat(GRAYSCALE.controls).isEqualTo(expected)
        assertThat(NEGATIVE.controls).isEqualTo(expected)
    }

    @Test
    fun black_and_white_only_exposes_threshold() {
        assertThat(BLACK_AND_WHITE.controls).containsExactly(THRESHOLD)
    }

    @Test
    fun mode_names_are_stable_for_persistence() {
        // Names are stored in document metadata and in file names.
        assertThat(ColorMode.entries.map { it.name }).contains("COLOR", "GRAYSCALE", "BLACK_AND_WHITE")
    }
}
