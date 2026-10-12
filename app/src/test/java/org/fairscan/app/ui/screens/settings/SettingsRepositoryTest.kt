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
package org.fairscan.app.ui.screens.settings

import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.app.ui.screens.settings.DefaultFilenameStyle.SCAN_DATE_TIME
import org.fairscan.app.ui.screens.settings.DefaultFilenameStyle.DATE
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsRepositoryTest {

    @Test
    fun default_filename_styles() {
        val dec31 = SimpleDateFormat("yyyy_MM_dd HH:mm:ss", Locale.ENGLISH)
            .parse("2025_12_31 15:59:03")
        requireNotNull(dec31)
        assertThat(SCAN_DATE_TIME.filename()).isEqualTo(SCAN_DATE_TIME.filename(Date()))
        assertThat(SCAN_DATE_TIME.filename(dec31)).isEqualTo("Scan 2025-12-31 15.59.03")
        assertThat(DATE.filename()).isEqualTo(DATE.filename(Date()))
        assertThat(DATE.filename(dec31)).isEqualTo("2025-12-31")
    }

    @Test
    fun every_filter_can_be_the_default_filter() {
        val filters = DefaultColorMode.entries.mapNotNull { it.colorMode }
        assertThat(filters).containsExactlyInAnyOrderElementsOf(ColorMode.entries)
        assertThat(DefaultColorMode.entries.count { it.colorMode == null }).isEqualTo(1)
        assertThat(DefaultColorMode.AUTO.colorMode).isNull()
    }

    @Test
    fun default_filter_entries_have_distinct_labels() {
        val labels = DefaultColorMode.entries.map { it.labelResource }
        assertThat(labels.toSet()).hasSameSizeAs(labels)
    }

    @Test
    fun stored_default_filter_names_are_read_back() {
        for (mode in DefaultColorMode.entries) {
            assertThat(parseDefaultColorMode(mode.name)).isEqualTo(mode)
        }
        // Values stored by earlier versions.
        assertThat(parseDefaultColorMode("AUTO")).isEqualTo(DefaultColorMode.AUTO)
        assertThat(parseDefaultColorMode("COLOR")).isEqualTo(DefaultColorMode.COLOR)
        assertThat(parseDefaultColorMode("GRAYSCALE")).isEqualTo(DefaultColorMode.GRAYSCALE)
        assertThat(parseDefaultColorMode("BLACK_AND_WHITE")).isEqualTo(DefaultColorMode.BLACK_AND_WHITE)
    }

    @Test
    fun unknown_or_missing_default_filter_falls_back_to_automatic() {
        assertThat(parseDefaultColorMode(null)).isEqualTo(DefaultColorMode.AUTO)
        assertThat(parseDefaultColorMode("")).isEqualTo(DefaultColorMode.AUTO)
        assertThat(parseDefaultColorMode("SEPIA")).isEqualTo(DefaultColorMode.AUTO)
    }

    @Test
    fun torch_always_on_is_off_by_default_and_persists() = runTest {
        val file = java.io.File.createTempFile("settings", ".preferences_pb").apply { delete() }
        val scope = TestScope(UnconfinedTestDispatcher())
        val dataStore = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { file }
        val repo = SettingsRepository(ContextWrapper(null), dataStore, scope.backgroundScope)

        assertThat(repo.torchAlwaysOn.first()).isFalse()
        repo.setTorchAlwaysOn(true)
        assertThat(repo.torchAlwaysOn.first()).isTrue()
        repo.setTorchAlwaysOn(false)
        assertThat(repo.torchAlwaysOn.first()).isFalse()
        file.delete()
    }
}
