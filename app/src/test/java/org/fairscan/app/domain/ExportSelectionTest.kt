package org.fairscan.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportSelectionTest {

    private fun page(id: String) = ScanPage(id, Rotation.R0, null, 0, null)
    private val pages = listOf(page("a"), page("b"), page("c"), page("d"))

    private fun ids(list: List<ScanPage>) = list.map { it.id }

    @Test
    fun selected_pages_are_returned_in_document_order() {
        assertEquals(listOf("b", "d"), ids(pagesForExport(pages, setOf("d", "b"), "a")))
    }

    @Test
    fun empty_selection_exports_the_active_page() {
        assertEquals(listOf("c"), ids(pagesForExport(pages, emptySet(), "c")))
    }

    @Test
    fun unknown_active_page_falls_back_to_first_page() {
        assertEquals(listOf("a"), ids(pagesForExport(pages, emptySet(), "zzz")))
        assertEquals(listOf("a"), ids(pagesForExport(pages, emptySet(), null)))
    }

    @Test
    fun deleted_pages_are_ignored_in_selection() {
        assertEquals(listOf("c"), ids(pagesForExport(pages, setOf("gone", "c"), "a")))
        assertEquals(listOf("b"), ids(pagesForExport(pages, setOf("gone"), "b")))
    }

    @Test
    fun reordered_pages_follow_new_order() {
        val reordered = listOf(pages[3], pages[0], pages[1], pages[2])
        assertEquals(listOf("d", "b"), ids(pagesForExport(reordered, setOf("b", "d"), "a")))
    }

    @Test
    fun no_page_in_document_gives_empty_list() {
        assertEquals(emptyList<ScanPage>(), pagesForExport(emptyList(), setOf("a"), "a"))
    }

    @Test
    fun suffix_is_empty_for_whole_document() {
        assertEquals("", pagesSuffix(listOf(1, 2, 3), 3))
    }

    @Test
    fun suffix_for_single_page() {
        assertEquals(" p3", pagesSuffix(listOf(3), 6))
    }

    @Test
    fun suffix_for_non_contiguous_pages() {
        assertEquals(" p2,4", pagesSuffix(listOf(2, 4), 6))
        assertEquals(" p1,2", pagesSuffix(listOf(1, 2), 6))
    }

    @Test
    fun suffix_collapses_ranges_of_three_or_more() {
        assertEquals(" p2-4,6", pagesSuffix(listOf(2, 3, 4, 6), 8))
    }

    @Test
    fun long_press_starts_selection_and_is_ignored_afterwards() {
        assertEquals(setOf("a"), selectionAfterLongPress(emptySet(), "a"))
        assertEquals(setOf("a"), selectionAfterLongPress(setOf("a"), "b"))
    }

    @Test
    fun tap_toggles_pages_only_in_selection_mode() {
        assertEquals(emptySet<String>(), selectionAfterTap(emptySet(), "a"))
        assertEquals(setOf("a", "b"), selectionAfterTap(setOf("a"), "b"))
        assertEquals(setOf("a"), selectionAfterTap(setOf("a", "b"), "b"))
    }

    @Test
    fun selection_mode_ends_when_last_page_is_unselected() {
        val selected = selectionAfterTap(selectionAfterLongPress(emptySet(), "a"), "a")
        assertEquals(emptySet<String>(), selected)
    }

    @Test
    fun exported_page_numbers_reflect_selection() {
        assertEquals(listOf(2, 4), exportedPageNumbers(pages, setOf("d", "b"), "a"))
        assertEquals(listOf(3), exportedPageNumbers(pages, emptySet(), "c"))
    }

    @Test
    fun preparation_key_changes_with_selected_pages() {
        fun key(selected: Set<String>) = org.fairscan.app.ui.screens.export.ExportPreparationKey(
            pagesForExport(pages, selected, "a").map { it.key() }.let {
                kotlinx.collections.immutable.persistentListOf(*it.toTypedArray())
            },
            org.fairscan.app.ui.screens.settings.ExportFormat.PDF,
            ExportQuality.BALANCED,
            "",
        )
        assertEquals(key(setOf("b")), key(setOf("b")))
        org.junit.Assert.assertNotEquals(key(setOf("b")), key(setOf("b", "c")))
    }
}
