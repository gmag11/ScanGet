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
package org.fairscan.app.ui.screens.overview

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class PageGridLayoutTest {

    // The grid has a 16 dp padding on each side
    private fun layout(screenWidthDp: Int) = pageGridLayout(screenWidthDp - 32f)

    @Test
    fun `a normal phone in portrait has two columns`() {
        for (width in 360..430) {
            assertThat(layout(width).first).describedAs("width $width").isEqualTo(2)
        }
    }

    @Test
    fun `a narrow screen still has two columns`() {
        for (width in 280..343) {
            val (columns, cell) = layout(width)
            assertThat(columns).describedAs("width $width").isEqualTo(2)
            assertThat(cell).isGreaterThan(100f)
        }
    }

    @Test
    fun `wider screens get more columns, and the cells stay between 150 and 231 dp`() {
        for (width in 344..1600) {
            val (columns, cell) = layout(width)
            assertThat(cell).describedAs("cell at $width").isBetween(150f, 231f)
            assertThat(columns).describedAs("columns at $width").isGreaterThanOrEqualTo(2)
        }
    }

    @Test
    fun `a tablet has four or five columns of cells no bigger than on a phone`() {
        val (columns, cell) = layout(800)
        assertThat(columns).isBetween(4, 5)
        assertThat(cell).isLessThan(231f)
        assertThat(layout(1200).first).isGreaterThan(layout(800).first)
    }

    @Test
    fun `the cells and gaps fill the width`() {
        for (width in listOf(320, 360, 411, 474, 600, 800, 1024)) {
            val (columns, cell) = layout(width)
            assertThat(cell * columns + PAGE_GRID_GAP_DP * (columns - 1)).isCloseTo(width - 32f, org.assertj.core.data.Offset.offset(0.01f))
        }
    }
}
