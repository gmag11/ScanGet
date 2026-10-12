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

/**
 * Pages to include in an export: the selected ones in document order, or only the active page
 * when nothing is selected (the first page if the active page is unknown).
 */
fun pagesForExport(
    pages: List<ScanPage>,
    selectedIds: Set<String>,
    activePageId: String?,
): List<ScanPage> {
    val selected = pages.filter { it.id in selectedIds }
    if (selected.isNotEmpty()) return selected
    val active = pages.firstOrNull { it.id == activePageId } ?: pages.firstOrNull()
    return listOfNotNull(active)
}

/**
 * Suffix for a default file name telling which pages are exported, like " p2,4" or " p2-4".
 * Empty when the whole document is exported. [pageNumbers] are 1-based and ascending.
 */
fun pagesSuffix(pageNumbers: List<Int>, totalPages: Int): String {
    if (pageNumbers.isEmpty() || pageNumbers.size >= totalPages) return ""
    val parts = mutableListOf<String>()
    var i = 0
    while (i < pageNumbers.size) {
        var j = i
        while (j + 1 < pageNumbers.size && pageNumbers[j + 1] == pageNumbers[j] + 1) j++
        if (j - i >= 2) {
            parts += "${pageNumbers[i]}-${pageNumbers[j]}"
        } else {
            for (k in i..j) parts += pageNumbers[k].toString()
        }
        i = j + 1
    }
    return " p" + parts.joinToString(",")
}

/** Selection after a long press: it starts the selection mode, and does nothing within it. */
fun selectionAfterLongPress(selected: Set<String>, pageId: String): Set<String> =
    if (selected.isEmpty()) setOf(pageId) else selected

/** Selection after a tap: it toggles the page in selection mode, and does nothing otherwise. */
fun selectionAfterTap(selected: Set<String>, pageId: String): Set<String> = when {
    selected.isEmpty() -> selected
    pageId in selected -> selected - pageId
    else -> selected + pageId
}

/** 1-based numbers of the pages that [pagesForExport] returns. */
fun exportedPageNumbers(
    pages: List<ScanPage>,
    selectedIds: Set<String>,
    activePageId: String?,
): List<Int> = pagesForExport(pages, selectedIds, activePageId).map { pages.indexOf(it) + 1 }
