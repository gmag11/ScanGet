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
package org.fairscan.app.ui.screens.export

import org.assertj.core.api.Assertions.assertThat
import org.fairscan.app.data.DocumentInfo
import org.fairscan.app.domain.pagesSuffix
import org.fairscan.app.ui.screens.settings.DefaultFilenameStyle.DATE
import org.fairscan.app.ui.screens.settings.DefaultFilenameStyle.SCAN_DATE_TIME
import org.junit.Test
import java.util.Date

class DefaultFilenameTest {

    private val created = Date(1_760_300_000_000L)
    private val now = Date(1_760_400_000_000L)

    private fun info(name: String) = DocumentInfo("id", name, created.time, created.time)

    @Test
    fun `a renamed document gives its name`() {
        assertThat(defaultExportBaseName(info("Invoice March"), SCAN_DATE_TIME, now))
            .isEqualTo("Invoice March")
    }

    @Test
    fun `a document that was not renamed gives the name of the style, as before`() {
        val generated = info(SCAN_DATE_TIME.filename(created))

        assertThat(defaultExportBaseName(generated, SCAN_DATE_TIME, now)).isEqualTo(SCAN_DATE_TIME.filename(now))
        assertThat(defaultExportBaseName(generated, DATE, now)).isEqualTo(DATE.filename(now))
        assertThat(defaultExportBaseName(info(DATE.filename(created)), SCAN_DATE_TIME, now))
            .isEqualTo(SCAN_DATE_TIME.filename(now))
    }

    @Test
    fun `without a document or with a blank name the style is used`() {
        assertThat(defaultExportBaseName(null, DATE, now)).isEqualTo(DATE.filename(now))
        assertThat(defaultExportBaseName(info("   "), DATE, now)).isEqualTo(DATE.filename(now))
    }

    @Test
    fun `characters that cannot be in a file name are replaced`() {
        assertThat(defaultExportBaseName(info("Bills: 2026/10 <final>"), DATE, now))
            .isEqualTo("Bills_ 2026_10 _final_")
    }

    @Test
    fun `a partial export adds the page numbers to the document name`() {
        val base = defaultExportBaseName(info("Invoice March"), DATE, now)

        assertThat(base + pagesSuffix(listOf(2, 4), 6)).isEqualTo("Invoice March p2,4")
        assertThat(base + pagesSuffix(listOf(3), 6)).isEqualTo("Invoice March p3")
        assertThat(base + pagesSuffix(listOf(1, 2, 3), 3)).isEqualTo("Invoice March")
    }
}
