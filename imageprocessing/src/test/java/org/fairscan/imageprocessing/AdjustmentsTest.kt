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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.fairscan.imageprocessing.AdjustmentControl.*
import org.junit.Test

class AdjustmentsTest {

    @Test
    fun default_instance_is_neutral() {
        assertThat(Adjustments().isNeutral).isTrue()
        assertThat(Adjustments.NEUTRAL).isEqualTo(Adjustments())
    }

    @Test
    fun any_change_makes_it_non_neutral() {
        for (control in AdjustmentControl.entries) {
            assertThat(Adjustments().with(control, 10).isNeutral).describedAs(control.name).isFalse()
        }
    }

    @Test
    fun hash_is_stable_and_has_8_hex_digits() {
        val a = Adjustments(brightness = 20, contrast = -5, detail = 40)
        assertThat(a.hash()).matches("[0-9a-f]{8}")
        assertThat(a.hash()).isEqualTo(Adjustments(brightness = 20, contrast = -5, detail = 40).hash())
        // Pinned value: the hash is part of processed file names, so it must never change.
        assertThat(Adjustments().canonical()).isEqualTo("b0,c0,s0,d0,r0,t0")
        assertThat(Adjustments().hash()).isEqualTo("705e44be")
    }

    @Test
    fun hash_differs_for_each_control() {
        val hashes = AdjustmentControl.entries.map { Adjustments().with(it, 10).hash() }
        assertThat(hashes.toSet()).hasSize(AdjustmentControl.entries.size)
        assertThat(hashes).doesNotContain(Adjustments().hash())
    }

    @Test
    fun hash_never_contains_a_dash() {
        // ImageRepository treats '-' in file names as a legacy rotation separator.
        for (b in -100..100 step 7) {
            assertThat(Adjustments(brightness = b, threshold = -b).hash()).doesNotContain("-")
        }
    }

    @Test
    fun out_of_range_values_are_rejected() {
        assertThatThrownBy { Adjustments(brightness = 101) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Adjustments(detail = -1) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun with_clamps_to_the_control_range() {
        assertThat(Adjustments().with(BRIGHTNESS, 500).brightness).isEqualTo(100)
        assertThat(Adjustments().with(DETAIL, -20).detail).isEqualTo(0)
    }

    @Test
    fun coerced_clamps_untrusted_values() {
        val a = Adjustments.coerced(brightness = 999, detail = -3, threshold = -999)
        assertThat(a).isEqualTo(Adjustments(brightness = 100, detail = 0, threshold = -100))
    }

    @Test
    fun for_mode_neutralizes_controls_the_mode_does_not_expose() {
        val a = Adjustments(brightness = 10, contrast = 20, saturation = 30, detail = 40,
            shadowRemoval = 50, threshold = 60)
        assertThat(a.forMode(ColorMode.BLACK_AND_WHITE)).isEqualTo(Adjustments(threshold = 60))
        assertThat(a.forMode(ColorMode.GRAYSCALE))
            .isEqualTo(Adjustments(brightness = 10, contrast = 20, detail = 40))
        assertThat(a.forMode(ColorMode.COLOR).threshold).isEqualTo(0)
        assertThat(a.forMode(ColorMode.COLOR).shadowRemoval).isEqualTo(50)
    }

    @Test
    fun only_tone_controls_can_be_applied_without_reprocessing() {
        val cheap = AdjustmentControl.entries.filter { !it.needsReprocessing }
        assertThat(cheap).containsExactlyInAnyOrder(BRIGHTNESS, CONTRAST, SATURATION)
        // The tone matrix must cover exactly the controls flagged as cheap.
        for (control in AdjustmentControl.entries) {
            assertThat(Adjustments().with(control, 10).hasTone).describedAs(control.name).isEqualTo(!control.needsReprocessing)
        }
    }

    @Test
    fun without_tone_keeps_only_the_reprocessing_controls() {
        val a = Adjustments(brightness = 10, contrast = 20, saturation = 30, detail = 40, shadowRemoval = 50, threshold = 60)
        assertThat(a.withoutTone()).isEqualTo(Adjustments(detail = 40, shadowRemoval = 50, threshold = 60))
        assertThat(a.withoutTone().hasTone).isFalse()
    }
}
