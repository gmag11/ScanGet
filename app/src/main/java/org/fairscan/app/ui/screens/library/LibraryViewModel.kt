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
package org.fairscan.app.ui.screens.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fairscan.app.data.Logger
import org.fairscan.app.data.library.LibraryEntry
import org.fairscan.app.data.library.LibraryRepository
import org.fairscan.app.data.library.normalizeForSearch

/** A document as the library shows it. [updatedAt] is in epoch milliseconds. */
data class LibraryItem(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val pageCount: Int,
)

data class LibraryUiState(
    /** Nothing known about the documents yet (not even the last list). */
    val isLoading: Boolean = true,
    /** The document opened or created last, shown first; null if there is none or it was deleted. */
    val recent: LibraryItem? = null,
    /** The other documents, most recently modified first. */
    val others: List<LibraryItem> = emptyList(),
    /** What is typed in the search, or null when the search is closed. */
    val query: String? = null,
    /** The documents selected (all of them are shown). Selection mode lasts while there are any. */
    val selectedIds: Set<String> = emptySet(),
) {
    val isEmpty get() = recent == null && others.isEmpty()
    val isSearching get() = query != null
    val isSelecting get() = selectedIds.isNotEmpty()

    /** Every document shown, in the order they are shown. */
    val shown: List<LibraryItem> get() = listOfNotNull(recent) + others
}

/**
 * The [items] whose name contains [query], or whose recognized text does, ignoring case and accents,
 * in the same order. [texts] holds the recognized text of the documents that have it, already
 * normalized (see [normalizeForSearch]); the others are only found by name.
 */
fun filterDocuments(
    items: List<LibraryItem>,
    query: String,
    texts: Map<String, String> = emptyMap(),
): List<LibraryItem> {
    val wanted = normalizeForSearch(query.trim())
    if (wanted.isEmpty()) return items
    return items.filter {
        normalizeForSearch(it.name).contains(wanted) || texts[it.id]?.contains(wanted) == true
    }
}

/**
 * The state of the library screen for [entries] (the documents with pages, most recently modified
 * first). Without a search the document opened last comes apart, once, and the rest follow in order.
 * While a query is typed there is no recent section: the documents that match are listed in order.
 * [texts] is the recognized text of the documents, for the search. [selectedIds] are kept only as far
 * as those documents are shown.
 */
fun libraryUiState(
    entries: List<LibraryEntry>,
    lastOpenedId: String?,
    query: String? = null,
    selectedIds: Set<String> = emptySet(),
    texts: Map<String, String> = emptyMap(),
): LibraryUiState {
    val items = entries
        .map { LibraryItem(it.info.id, it.info.name, it.info.updatedAt, it.pageCount) }
        .sortedByDescending { it.updatedAt }
    val (recent, others) = if (query != null) {
        null to filterDocuments(items, query, texts)
    } else {
        val recent = items.firstOrNull { it.id == lastOpenedId }
        recent to items.filter { it !== recent }
    }
    val shown = listOfNotNull(recent) + others
    val visible = shown.map { it.id }.toSet()
    return LibraryUiState(
        isLoading = false, recent = recent, others = others,
        query = query, selectedIds = selectedIds.filterTo(LinkedHashSet()) { it in visible },
    )
}

class LibraryViewModel(
    private val library: LibraryRepository,
    private val logger: Logger,
    // Removes a document. It is not [LibraryRepository.delete] alone because deleting the document
    // being edited has to be known by the rest of the app too.
    private val deleteDocument: suspend (String) -> Unit = library::delete,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _state.asStateFlow()

    // What the state is made of
    private var entries: List<LibraryEntry>? = null
    private var lastOpenedId: String? = null
    private var query: String? = null
    private var selectedIds: Set<String> = emptySet()
    private var texts: Map<String, String> = emptyMap()

    private fun publish() {
        val known = entries ?: return
        _state.value = libraryUiState(known, lastOpenedId, query, selectedIds, texts)
        // Selected documents that are not shown any more (deleted, or hidden by the search) are dropped
        selectedIds = _state.value.selectedIds
    }

    /** Lists the documents: the last list is shown at once, then the real one replaces it. */
    fun refresh() {
        viewModelScope.launch {
            try {
                if (_state.value.isLoading) {
                    val cached = library.cachedDocuments()
                    if (cached.isNotEmpty()) {
                        entries = cached
                        lastOpenedId = library.lastOpenedId()
                        publish()
                    }
                }
                entries = library.documents()
                lastOpenedId = library.lastOpenedId()
                publish()
                if (query != null) loadTexts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Library", "Failed to list the documents", e)
                if (entries == null) entries = emptyList()
                publish()
            }
        }
    }

    // --- Search ---

    /** Opens the search (with an empty query, which shows every document). */
    fun openSearch() {
        if (query != null) return
        query = ""
        publish()
        viewModelScope.launch { loadTexts() }
    }

    // The recognized text is read once the search is open; until then documents are found by name
    private suspend fun loadTexts() {
        try {
            texts = library.searchTexts(entries.orEmpty())
            publish()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("Library", "Failed to read the text of the documents", e)
        }
    }

    fun onQueryChange(text: String) {
        if (query == null) return
        query = text
        publish()
    }

    /** Closes the search: the query is forgotten and every document is shown. */
    fun closeSearch() {
        if (query == null) return
        query = null
        texts = emptyMap()
        publish()
    }

    // --- Selection ---

    /** A long press on a document: selection mode starts with it, and goes on if it already did. */
    fun startSelection(id: String) {
        if (selectedIds.isNotEmpty()) return
        selectedIds = setOf(id)
        publish()
    }

    /** A tap in selection mode selects the document, or unselects it; the last one ends the mode. */
    fun toggleSelection(id: String) {
        if (selectedIds.isEmpty()) return
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
        publish()
    }

    /** Selects every document shown, so with a search only those that match. */
    fun selectAll() {
        selectedIds = _state.value.shown.map { it.id }.toSet()
        publish()
    }

    fun clearSelection() {
        if (selectedIds.isEmpty()) return
        selectedIds = emptySet()
        publish()
    }

    /** Renames a document. A blank name is not accepted. Returns whether it was. */
    fun rename(id: String, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        viewModelScope.launch {
            try {
                library.rename(id, trimmed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Library", "Failed to rename $id", e)
            }
            refresh()
        }
        return true
    }

    fun delete(id: String) = deleteAll(listOf(id))

    /** Deletes the selected documents, and ends the selection mode. */
    fun deleteSelected() {
        val ids = selectedIds.toList()
        selectedIds = emptySet()
        deleteAll(ids)
    }

    private fun deleteAll(ids: List<String>) {
        viewModelScope.launch {
            for (id in ids) {
                try {
                    deleteDocument(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e("Library", "Failed to delete $id", e)
                }
            }
            refresh()
        }
    }

    /** The cover of a document, or null while there is none. */
    suspend fun loadCover(id: String): Bitmap? = withContext(ioDispatcher) {
        library.cover(id)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
}
