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
package org.fairscan.app.domain

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.fairscan.app.data.PageRecognition
import org.fairscan.imageprocessing.ImageRect
import org.fairscan.imageprocessing.OcrTextBox
import org.junit.Test

class PageToExportOcrTest {

    private val page = ScanPage("100", Rotation.R0, null, 0, null)
    private val stored = PageRecognition(listOf(OcrTextBox("old", ImageRect(0, 0, 1, 1), 1, 1)), 10, 20)
    private val found = PageRecognition(listOf(OcrTextBox("new", ImageRect(0, 0, 1, 1), 1, 1)), 30, 40)

    @Test
    fun `stored text is used without recognizing`() = runTest {
        var kept: PageRecognition? = null
        val export = PageToExport(page) { "image" }.withOcr(PageOcrSource(stored) { kept = it })

        val text = export.recognizedText { error("must not recognize") }

        assertThat(text).isSameAs(stored)
        assertThat(kept).isNull()
    }

    @Test
    fun `recognized text is handed over to be kept`() = runTest {
        var kept: PageRecognition? = null
        val export = PageToExport(page) { "image" }.withOcr(PageOcrSource(null) { kept = it })

        val text = export.recognizedText { found }

        assertThat(text).isSameAs(found)
        assertThat(kept).isSameAs(found)
    }

    @Test
    fun `nothing is kept when nothing can be recognized`() = runTest {
        var kept: PageRecognition? = null
        val export = PageToExport(page) { "image" }.withOcr(PageOcrSource(null) { kept = it })

        assertThat(export.recognizedText { null }).isNull()
        assertThat(kept).isNull()
    }

    @Test
    fun `a page without a source recognizes as before`() = runTest {
        val export = PageToExport(page) { "image" }

        assertThat(export.recognizedText { found }).isSameAs(found)
    }
}
