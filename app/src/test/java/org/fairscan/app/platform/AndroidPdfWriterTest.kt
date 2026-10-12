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
package org.fairscan.app.platform

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.offset
import org.fairscan.imageprocessing.ColorMode
import org.junit.Test

class AndroidPdfWriterTest {

    @Test fun `portrait smaller than A4 is unchanged`() {
        val (w, h) = constrainToMaxFormat(100.0, 150.0)
        assertThat(w).isEqualTo(100.0)
        assertThat(h).isEqualTo(150.0)
    }

    @Test fun `portrait taller than A4 is constrained preserving ratio`() {
        val (w, h) = constrainToMaxFormat(210.0, 400.0)
        assertThat(h).isCloseTo(297.0, offset(0.1))
        assertThat(w / h).isCloseTo(210.0 / 400.0, offset(0.001))
    }

    @Test fun `landscape wider than A4 is constrained preserving ratio`() {
        val (w, h) = constrainToMaxFormat(300.0, 200.0)
        assertThat(w).isCloseTo(297.0, offset(0.1))
        assertThat(w / h).isCloseTo(300.0 / 200.0, offset(0.001))
    }

    @Test fun `exactly A4 is unchanged`() {
        val (w, h) = constrainToMaxFormat(210.0, 297.0)
        assertThat(w).isCloseTo(210.0, offset(0.001))
        assertThat(h).isCloseTo(297.0, offset(0.001))
    }

    @Test fun `exactly Letter is unchanged`() {
        val (w, h) = constrainToMaxFormat(215.9, 279.4)
        assertThat(w).isCloseTo(215.9, offset(0.001))
        assertThat(h).isCloseTo(279.4, offset(0.001))
    }

    @Test fun `landscape orientation is preserved`() {
        val (w, h) = constrainToMaxFormat(400.0, 250.0)
        assertThat(w).isGreaterThan(h)
    }

    @Test fun `landscape smaller than A4 is unchanged`() {
        val (w, h) = constrainToMaxFormat(297.0, 150.0)
        assertThat(w).isCloseTo(297.0, offset(0.001))
        assertThat(h).isCloseTo(150.0, offset(0.001))
    }

    @Test fun `only black and white is embedded as a bitonal image`() {
        assertThat(pdfImageEncoding(ColorMode.BLACK_AND_WHITE)).isEqualTo(PdfImageEncoding.CCITT_G4)
        val others = ColorMode.entries.filter { it != ColorMode.BLACK_AND_WHITE }
        assertThat(others).isNotEmpty()
        for (mode in others) {
            assertThat(pdfImageEncoding(mode)).describedAs(mode.name).isEqualTo(PdfImageEncoding.JPEG)
        }
    }

    @Test fun `new filters are embedded as continuous tone`() {
        listOf(ColorMode.ORIGINAL, ColorMode.LIGHTEN, ColorMode.ENHANCE, ColorMode.NO_SHADOWS, ColorMode.NEGATIVE)
            .forEach { assertThat(pdfImageEncoding(it)).describedAs(it.name).isEqualTo(PdfImageEncoding.JPEG) }
    }

    @Test fun `a page with no filter is embedded as continuous tone`() {
        assertThat(pdfImageEncoding(null)).isEqualTo(PdfImageEncoding.JPEG)
    }

}
