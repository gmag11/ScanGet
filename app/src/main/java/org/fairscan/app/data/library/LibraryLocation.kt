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

/**
 * Where the library is kept: a folder of the user's storage that the user chose or created with the
 * system folder picker, and the only one the app can reach. Never the app's private storage.
 */
sealed interface LibraryLocation {

    /** Identifies the location, e.g. to know which location a cache was made for. */
    val key: String

    /** Name to show to the user. */
    val displayName: String

    /** The folder was granted through the system folder picker; [uri] is its tree URI. */
    data class Saf(val uri: String, val name: String?) : LibraryLocation {
        override val key get() = "saf:$uri"
        override val displayName get() = name ?: uri
    }
}

/** What is saved in the settings to find the library again: the tree URI of the folder and its name. */
data class StoredLibraryLocation(val uri: String, val name: String? = null) {
    fun toLocation(): LibraryLocation = LibraryLocation.Saf(uri, name)
}

fun LibraryLocation.toStored(): StoredLibraryLocation = when (this) {
    is LibraryLocation.Saf -> StoredLibraryLocation(uri, name)
}

sealed interface LibraryStatus {
    /** The user has not chosen the library folder yet. */
    data object NotSetUp : LibraryStatus

    /** The folder can be used. */
    data class Ready(val location: LibraryLocation) : LibraryStatus

    /** The folder was chosen but cannot be accessed any more (permission revoked, folder gone...). */
    data class Unavailable(val location: LibraryLocation) : LibraryStatus
}

/** Persistence of the chosen location. */
interface LibraryLocationSettings {
    suspend fun load(): StoredLibraryLocation?
    suspend fun save(location: StoredLibraryLocation?)
}

/** What the library needs from Android. */
interface LibraryPlatform {
    /** The app still holds the permission to a folder granted by the folder picker. */
    fun hasSafPermission(uri: String): Boolean

    /** The granted folder can still be reached. */
    fun isSafFolderReachable(uri: String): Boolean

    fun safStore(uri: String): LibraryStore

    /** The name of a folder granted by the folder picker, if it can be read. */
    fun safFolderName(uri: String): String?

    /** Gives up the permission to a folder granted by the folder picker. */
    fun releaseSafPermission(uri: String)
}
