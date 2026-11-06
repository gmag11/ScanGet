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
import kotlin.random.Random

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

    private val legacy = DocumentInfo("legacy-id", "Scan legacy", createdAt = 10, updatedAt = 20)

    @Test
    fun version_1_gets_the_legacy_identity_and_pages_from_file_names() {
        val text = """{"version":1,"pages":[{"file":"111-90.jpg"},{"file":"222.jpg"}]}"""

        val loaded = parseDocumentMetadata(text) { legacy }

        assertThat(loaded.info).isEqualTo(legacy)
        assertThat(loaded.pages.map { it.id }).containsExactly("111", "222")
    }

    @Test
    fun version_2_gets_the_legacy_identity_and_keeps_its_pages() {
        val page = PageV2("1", manualRotationDegrees = 90, quadVersion = 2)
        val text = json.encodeToString(DocumentMetadataV2(pages = listOf(page)))

        val loaded = parseDocumentMetadata(text) { legacy }

        assertThat(loaded.info).isEqualTo(legacy)
        assertThat(loaded.pages).containsExactly(page)
    }

    @Test
    fun a_file_without_version_is_version_1() {
        val loaded = parseDocumentMetadata("""{"pages":[{"file":"5.jpg"}]}""") { legacy }

        assertThat(loaded.pages.map { it.id }).containsExactly("5")
    }

    @Test
    fun version_3_keeps_its_own_identity_and_does_not_ask_for_a_legacy_one() {
        val info = DocumentInfo("20261012-203300-ab12", "Invoice", createdAt = 1000, updatedAt = 2000)
        val page = PageV2("1", adjustments = Adjustments(brightness = 10).toSerializable())
        val text = json.encodeToString(info.toMetadata(listOf(page)))

        val loaded = parseDocumentMetadata(text) { error("no legacy identity needed") }

        assertThat(loaded.info).isEqualTo(info)
        assertThat(loaded.pages).containsExactly(page)
    }

    @Test
    fun an_unknown_version_is_rejected() {
        val result = runCatching { parseDocumentMetadata("""{"version":99,"pages":[]}""") { legacy } }

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun document_ids_are_sortable_timestamps_with_a_random_suffix() {
        val now = java.time.LocalDateTime.of(2026, 10, 12, 20, 33, 5)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        val id = newDocumentId(now, Random(1))

        assertThat(id).matches("20261012-203305-[0-9a-f]{4}")
        assertThat(newDocumentId(now, Random(2))).isNotEqualTo(id)
    }
}
