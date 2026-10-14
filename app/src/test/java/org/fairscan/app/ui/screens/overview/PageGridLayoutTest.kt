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

import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
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

    @Test
    fun `a portrait page fills the width and is aligned to the top`() {
        val fill = thumbnailFill(width = 170, height = 240)
        assertThat(fill.scale).isEqualTo(ContentScale.FillWidth)
        assertThat(fill.alignment).isEqualTo(Alignment.TopStart)
    }

    @Test
    fun `a landscape page fills the height and is aligned to the left`() {
        val fill = thumbnailFill(width = 240, height = 170)
        assertThat(fill.scale).isEqualTo(ContentScale.FillHeight)
        assertThat(fill.alignment).isEqualTo(Alignment.CenterStart)
    }

    @Test
    fun `a square page fills the cell with nothing cut off`() {
        // Width and height are the same size, so either way the page is shown whole
        assertThat(thumbnailFill(200, 200).scale).isEqualTo(ContentScale.FillHeight)
    }
}
