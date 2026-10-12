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
package org.fairscan.app.ui.screens.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fairscan.app.data.Logger
import org.fairscan.app.data.library.FolderInspection
import org.fairscan.app.data.library.LibraryLocation
import org.fairscan.app.data.library.LibraryLocations
import org.fairscan.app.data.library.LibraryStatus
import org.fairscan.app.data.library.MoveProgress

/** A folder that was picked and is not empty: the user is shown what is in it before it is used. */
data class FolderConfirmation(val candidate: LibraryLocation, val inspection: FolderInspection)

/** The library has documents, and the new folder is not where they are: should they follow? */
data class MoveOffer(val candidate: LibraryLocation, val documentCount: Int, val fromName: String)

data class LibraryFolderUiState(
    /** Null until the saved location has been read. */
    val status: LibraryStatus? = null,
    val defaultFolderName: String = "",
    /** The system's "All files access" can be asked for, so that FairScan creates its own folder. */
    val allFilesAccessOffered: Boolean = true,
    val permissionDenied: Boolean = false,
    val confirmation: FolderConfirmation? = null,
    val moveOffer: MoveOffer? = null,
    /** While moving documents (or setting the folder up). */
    val isWorking: Boolean = false,
    val moveProgress: MoveProgress? = null,
    val hasError: Boolean = false,
    /** Documents that were left in the previous folder after a move. */
    val notMoved: Int = 0,
)

sealed interface LibraryFolderEvent {
    /** Open the system screen where "All files access" is granted. */
    data object RequestAllFilesAccess : LibraryFolderEvent

    /** Open the system folder picker; [defaultFolderExists] tells where it should start. */
    data class PickFolder(val defaultFolderExists: Boolean) : LibraryFolderEvent
}

/**
 * Choosing the folder of the library, at first launch and later from the settings: proposing the
 * `FairScan` folder or one picked by the user, telling what a non-empty folder holds before using
 * it, and moving the documents when the folder changes.
 */
class LibraryFolderViewModel(
    private val locations: LibraryLocations,
    private val allFilesAccessOffered: Boolean,
    private val logger: Logger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow(
        LibraryFolderUiState(
            defaultFolderName = locations.defaultLocation().displayName,
            allFilesAccessOffered = allFilesAccessOffered,
        )
    )
    val uiState: StateFlow<LibraryFolderUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<LibraryFolderEvent>(
        extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<LibraryFolderEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            locations.status.collect { status -> _state.update { it.copy(status = status) } }
        }
    }

    /** "Use the FairScan folder": asks for "All files access" if needed, then proposes the folder. */
    fun onUseDefaultFolder() {
        _state.update { it.copy(permissionDenied = false, hasError = false) }
        when {
            !allFilesAccessOffered -> onChooseFolder()
            locations.hasAllFilesAccess() -> propose(locations.defaultLocation())
            else -> _events.tryEmit(LibraryFolderEvent.RequestAllFilesAccess)
        }
    }

    /** The user is back from the system screen where "All files access" is granted. */
    fun onAllFilesAccessResult() {
        if (locations.hasAllFilesAccess()) {
            _state.update { it.copy(permissionDenied = false) }
            propose(locations.defaultLocation())
        } else {
            _state.update { it.copy(permissionDenied = true) }
        }
    }

    /** "Choose another folder": opens the system folder picker. */
    fun onChooseFolder() {
        viewModelScope.launch {
            val exists = withContext(ioDispatcher) { locations.defaultFolderExists() }
            _events.tryEmit(LibraryFolderEvent.PickFolder(exists))
        }
    }

    /** The picker returned a folder (its tree URI), for which the app already holds the permission. */
    fun onFolderPicked(uri: String) {
        _state.update { it.copy(permissionDenied = false, hasError = false) }
        viewModelScope.launch {
            propose(withContext(ioDispatcher) { locations.named(LibraryLocation.Saf(uri, null)) })
        }
    }

    /** The user chose another folder in the confirmation: the first one is given up. */
    fun onChooseAnother() {
        _state.value.confirmation?.let { locations.discard(it.candidate) }
        _state.update { it.copy(confirmation = null) }
        onChooseFolder()
    }

    /** The user confirmed the folder shown in the confirmation. */
    fun onConfirmFolder() {
        val confirmation = _state.value.confirmation ?: return
        _state.update { it.copy(confirmation = null) }
        viewModelScope.launch { confirmed(confirmation.candidate) }
    }

    /** The user answered the offer to move the documents. */
    fun onMoveAnswer(move: Boolean) {
        val offer = _state.value.moveOffer ?: return
        _state.update { it.copy(moveOffer = null) }
        viewModelScope.launch { apply(offer.candidate, move) }
    }

    fun onDismissNotMoved() = _state.update { it.copy(notMoved = 0) }

    private fun propose(candidate: LibraryLocation) {
        viewModelScope.launch {
            val inspection = try {
                withContext(ioDispatcher) { locations.inspect(candidate) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(candidate, e)
                return@launch
            }
            if (inspection.isEmpty) {
                confirmed(candidate)
            } else {
                _state.update { it.copy(confirmation = FolderConfirmation(candidate, inspection)) }
            }
        }
    }

    // The folder is accepted: if the library already has documents elsewhere, ask whether they follow
    private suspend fun confirmed(candidate: LibraryLocation) {
        val current = (locations.status.value as? LibraryStatus.Ready)?.location
        if (current != null && current.key != candidate.key) {
            val count = try {
                withContext(ioDispatcher) { locations.currentStore()?.list()?.size ?: 0 }
            } catch (_: Exception) {
                0
            }
            if (count > 0) {
                _state.update { it.copy(moveOffer = MoveOffer(candidate, count, current.displayName)) }
                return
            }
        }
        apply(candidate, move = false)
    }

    private suspend fun apply(candidate: LibraryLocation, move: Boolean) {
        _state.update { it.copy(isWorking = true, moveProgress = null, hasError = false) }
        try {
            val change = withContext(ioDispatcher) {
                locations.change(candidate, move) { progress ->
                    _state.update { it.copy(moveProgress = progress) }
                }
            }
            _state.update { it.copy(isWorking = false, moveProgress = null, notMoved = change.notMoved) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed(candidate, e)
        }
    }

    private fun failed(candidate: LibraryLocation, e: Exception) {
        logger.e("LibraryFolder", "Cannot use $candidate", e)
        locations.discard(candidate)
        _state.update { it.copy(isWorking = false, moveProgress = null, hasError = true) }
    }
}
