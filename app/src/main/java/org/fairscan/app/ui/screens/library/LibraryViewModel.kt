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
) {
    val isEmpty get() = recent == null && others.isEmpty()
}

/**
 * The state of the library screen for [entries] (the documents with pages, most recently modified
 * first): the one opened last comes apart, once, and the rest follow in order.
 */
fun libraryUiState(entries: List<LibraryEntry>, lastOpenedId: String?): LibraryUiState {
    val items = entries
        .map { LibraryItem(it.info.id, it.info.name, it.info.updatedAt, it.pageCount) }
        .sortedByDescending { it.updatedAt }
    val recent = items.firstOrNull { it.id == lastOpenedId }
    return LibraryUiState(isLoading = false, recent = recent, others = items.filter { it !== recent })
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

    /** Lists the documents: the last list is shown at once, then the real one replaces it. */
    fun refresh() {
        viewModelScope.launch {
            try {
                if (_state.value.isLoading) {
                    val cached = library.cachedDocuments()
                    if (cached.isNotEmpty()) _state.value = libraryUiState(cached, library.lastOpenedId())
                }
                _state.value = libraryUiState(library.documents(), library.lastOpenedId())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Library", "Failed to list the documents", e)
                _state.update { it.copy(isLoading = false) }
            }
        }
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

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                deleteDocument(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Library", "Failed to delete $id", e)
            }
            refresh()
        }
    }

    /** The cover of a document, or null while there is none. */
    suspend fun loadCover(id: String): Bitmap? = withContext(ioDispatcher) {
        library.cover(id)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
}
