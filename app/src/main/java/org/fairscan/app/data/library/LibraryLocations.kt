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
package org.fairscan.app.data.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** What changing the library location did. */
data class LocationChange(
    val location: LibraryLocation,
    /** Documents that could not be moved to the new location and are still in the old one. */
    val notMoved: Int,
)

/**
 * Where the library is kept, whether it can be reached, and changing it.
 *
 * The location is chosen by the user (see [LibraryLocation]); [status] tells if it is set up and
 * usable. [currentStore] gives the store of the library while it is usable, and null otherwise.
 */
class LibraryLocations(
    private val settings: LibraryLocationSettings,
    private val platform: LibraryPlatform,
    private val mover: LibraryMover = LibraryMover(),
    // Directories where documents were kept in app storage by development builds: they are moved to
    // the library once there is one, and removed.
    private val legacyDirs: List<File> = emptyList(),
) {
    // null until the settings are read
    private val _status = MutableStateFlow<LibraryStatus?>(null)
    val status: StateFlow<LibraryStatus?> = _status.asStateFlow()

    private var store: LibraryStore? = null

    /** Reads the saved location and checks that it can be used. */
    suspend fun load() {
        val location = settings.load()?.toLocation()
        apply(location, deep = true)
    }

    /** Checks again that the location can be used, e.g. when the app comes back to the foreground. */
    suspend fun refresh() {
        val location = when (val status = _status.value) {
            is LibraryStatus.Ready -> status.location
            is LibraryStatus.Unavailable -> status.location
            else -> settings.load()?.toLocation()
        }
        apply(location, deep = true)
    }

    /** The store of the library, or null if it is not set up or cannot be reached right now. */
    @Synchronized
    fun currentStore(): LibraryStore? {
        val status = _status.value as? LibraryStatus.Ready ?: return null
        if (!isAvailable(status.location, deep = false)) {
            store = null
            _status.value = LibraryStatus.Unavailable(status.location)
            return null
        }
        return store
    }

    /** Identifies the location in use, or null if there is none. */
    fun currentKey(): String? = (_status.value as? LibraryStatus.Ready)?.location?.key

    /** The folder proposed by default: `FairScan` at the root of the user's storage. */
    fun defaultLocation() = LibraryLocation.Direct(platform.defaultFolderPath())

    fun hasAllFilesAccess() = platform.hasAllFilesAccess()

    /** The folder proposed by default exists (it can then be picked without creating it). */
    fun defaultFolderExists() = platform.existingDirectStore(platform.defaultFolderPath()) != null

    /** [location] with the name of its folder, which a folder granted by the picker does not have yet. */
    fun named(location: LibraryLocation): LibraryLocation =
        if (location is LibraryLocation.Saf && location.name == null) {
            location.copy(name = platform.safFolderName(location.uri))
        } else location

    /** What the folder [candidate] holds. Reads only: nothing is created or written. */
    fun inspect(candidate: LibraryLocation): FolderInspection {
        val store = when (candidate) {
            is LibraryLocation.Direct -> platform.existingDirectStore(candidate.path)
            is LibraryLocation.Saf -> platform.safStore(candidate.uri)
        }
        return store?.inspect() ?: FolderInspection(0, 0)
    }

    /** Gives up a folder that was not used after all. */
    fun discard(candidate: LibraryLocation) {
        val inUse = (_status.value.locationOrNull() as? LibraryLocation.Saf)?.uri
        if (candidate is LibraryLocation.Saf && candidate.uri != inUse) {
            platform.releaseSafPermission(candidate.uri)
        }
    }

    /**
     * Makes [location] the library. With [move] the documents of the current library are moved to
     * it; [onProgress] follows the move. The documents that development builds kept in app storage
     * are moved too. The location is saved only once that is done.
     */
    suspend fun change(
        location: LibraryLocation,
        move: Boolean,
        onProgress: (MoveProgress) -> Unit = {},
    ): LocationChange {
        val named = named(location)
        val newStore = openStore(named, create = true)!!
        val previous = _status.value.locationOrNull()
        var notMoved = 0
        if (move && previous != null && previous.key != named.key) {
            val oldStore = synchronized(this) { store } ?: openStore(previous, create = false)
            if (oldStore != null) notMoved += mover.move(oldStore, newStore, onProgress).let { it.failed + it.keptInSource }
        }
        for (dir in legacyDirs.filter { it.isDirectory }) {
            val result = mover.move(FileLibraryStore(dir), newStore)
            if (result.failed == 0 && result.keptInSource == 0) dir.deleteRecursively() else notMoved += result.failed + result.keptInSource
        }
        settings.save(named.toStored())
        synchronized(this) {
            store = newStore
            _status.value = LibraryStatus.Ready(named)
        }
        if (previous is LibraryLocation.Saf && previous.key != named.key) platform.releaseSafPermission(previous.uri)
        return LocationChange(named, notMoved)
    }

    private fun apply(location: LibraryLocation?, deep: Boolean) {
        if (location == null) {
            synchronized(this) { store = null; _status.value = LibraryStatus.NotSetUp }
            return
        }
        val available = isAvailable(location, deep)
        synchronized(this) {
            if (available) {
                if (_status.value !is LibraryStatus.Ready || store == null) store = openStore(location, create = false)
                _status.value = LibraryStatus.Ready(location)
            } else {
                store = null
                _status.value = LibraryStatus.Unavailable(location)
            }
        }
    }

    // [deep] also asks the system whether a folder granted by the picker can be reached, which is
    // slower than checking that the permission is still held
    private fun isAvailable(location: LibraryLocation, deep: Boolean): Boolean = when (location) {
        is LibraryLocation.Direct ->
            platform.hasAllFilesAccess() && platform.isFolderUsable(location.path)
        is LibraryLocation.Saf ->
            platform.hasSafPermission(location.uri) && (!deep || platform.isSafFolderReachable(location.uri))
    }

    private fun openStore(location: LibraryLocation, create: Boolean): LibraryStore? = when (location) {
        is LibraryLocation.Direct ->
            if (create) platform.directStore(location.path) else platform.existingDirectStore(location.path)
        is LibraryLocation.Saf -> platform.safStore(location.uri)
    }

    private fun LibraryStatus?.locationOrNull(): LibraryLocation? = when (this) {
        is LibraryStatus.Ready -> location
        is LibraryStatus.Unavailable -> location
        else -> null
    }
}
