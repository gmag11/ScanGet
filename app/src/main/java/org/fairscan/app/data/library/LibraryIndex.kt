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

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.fairscan.app.data.DocumentInfo
import java.io.File

@Serializable
private data class IndexFile(val location: String, val entries: List<IndexEntry>)

@Serializable
private data class IndexEntry(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
)

/**
 * The list of documents of the library as last read, kept in the app's private storage so that it
 * can be shown at once while the real list is read from the (possibly slow) library location. It is
 * only a cache: it is rebuilt from the location whenever it is missing, unreadable, or was made for
 * another location.
 */
class LibraryIndex(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true }

    /** The cached documents of [location], or null if there is no usable cache for it. */
    fun read(location: String): List<LibraryEntry>? {
        val index = runCatching { json.decodeFromString<IndexFile>(file.readText()) }.getOrNull()
            ?: return null
        if (index.location != location) return null
        return index.entries.map {
            LibraryEntry(DocumentInfo(it.id, it.name, it.createdAt, it.updatedAt), it.pageCount)
        }
    }

    fun write(location: String, entries: List<LibraryEntry>) {
        val index = IndexFile(location, entries.map {
            IndexEntry(it.info.id, it.info.name, it.info.createdAt, it.info.updatedAt, it.pageCount)
        })
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.encodeToString(index))
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }

    fun clear() {
        file.delete()
    }
}
