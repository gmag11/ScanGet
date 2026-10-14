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

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Density
import kotlin.math.max

/** Space between the cells of the page grid, in dp. */
internal const val PAGE_GRID_GAP_DP = 12f

/** The smallest a cell is made, in dp, as long as there are at least [PAGE_GRID_MIN_COLUMNS] columns. */
internal const val PAGE_GRID_MIN_CELL_DP = 150f

/** A normal phone in portrait has two columns; a narrower screen does too, with smaller cells. */
internal const val PAGE_GRID_MIN_COLUMNS = 2

/**
 * How many columns the page grid has in [availableWidthDp], and the side of its (square) cells: as
 * many columns of at least [PAGE_GRID_MIN_CELL_DP] as fit, never fewer than [PAGE_GRID_MIN_COLUMNS],
 * with the width left over shared by the cells. Wider screens get more columns, not bigger cells: a
 * cell is never bigger than the minimum plus its share of one more column.
 */
internal fun pageGridLayout(availableWidthDp: Float): Pair<Int, Float> {
    val fit = ((availableWidthDp + PAGE_GRID_GAP_DP) / (PAGE_GRID_MIN_CELL_DP + PAGE_GRID_GAP_DP)).toInt()
    val columns = max(PAGE_GRID_MIN_COLUMNS, fit)
    val cell = (availableWidthDp - PAGE_GRID_GAP_DP * (columns - 1)) / columns
    return columns to cell
}

/** The columns of the page grid (see [pageGridLayout]). */
internal object PageGridCells : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val (columns, _) = pageGridLayout((availableSize / density).coerceAtLeast(0f))
        val cell = (availableSize - spacing * (columns - 1)) / columns
        val remainder = (availableSize - spacing * (columns - 1)) - cell * columns
        return List(columns) { if (it < remainder) cell + 1 else cell }
    }
}
