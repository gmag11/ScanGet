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
package org.fairscan.app.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.fairscan.imageprocessing.Adjustments
import org.junit.Test

class DocumentMetadataTest {

    private val json = Json { prettyPrint = false; encodeDefaults = true }

    @Test
    fun neutral_adjustments_are_stored_as_null() {
        assertThat(Adjustments.NEUTRAL.toSerializable()).isNull()
        assertThat(Adjustments().toSerializable()).isNull()
    }

    @Test
    fun missing_or_null_adjustments_load_as_neutral() {
        assertThat((null as AdjustmentsV1?).toAdjustments()).isEqualTo(Adjustments.NEUTRAL)
        val page = Json.decodeFromString<PageV2>("""{"id":"1","quadVersion":0}""")
        assertThat(page.adjustments).isNull()
        assertThat(page.adjustments.toAdjustments().isNeutral).isTrue()
    }

    @Test
    fun adjustments_round_trip_through_json() {
        val adjustments = Adjustments(brightness = 20, contrast = -35, saturation = 10, detail = 40,
            shadowRemoval = -60, threshold = 75)
        val page = PageV2("1", adjustments = adjustments.toSerializable())

        val decoded = json.decodeFromString<PageV2>(json.encodeToString(page))

        assertThat(decoded).isEqualTo(page)
        assertThat(decoded.adjustments.toAdjustments()).isEqualTo(adjustments)
    }

    @Test
    fun a_neutral_page_serializes_with_no_adjustments_value() {
        val text = json.encodeToString(PageV2("1", adjustments = Adjustments.NEUTRAL.toSerializable()))
        assertThat(text).contains("\"adjustments\":null")
    }

    @Test
    fun partially_stored_adjustments_default_the_other_controls_to_neutral() {
        val page = Json.decodeFromString<PageV2>("""{"id":"1","adjustments":{"contrast":30}}""")
        assertThat(page.adjustments.toAdjustments()).isEqualTo(Adjustments(contrast = 30))
    }

    @Test
    fun out_of_range_stored_values_are_clamped() {
        val stored = AdjustmentsV1(brightness = 5000, detail = -7, threshold = -5000)
        assertThat(stored.toAdjustments()).isEqualTo(Adjustments(brightness = 100, detail = 0, threshold = -100))
    }
}
