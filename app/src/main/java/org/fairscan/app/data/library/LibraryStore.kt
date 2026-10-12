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

import org.fairscan.app.data.DocumentInfo
import org.fairscan.app.data.DocumentMetadataV3

/** A document as listed by the library. */
data class LibraryEntry(
    val info: DocumentInfo,
    val pageCount: Int,
)

/**
 * Where the documents of the library are kept: one entry per document, identified by its id.
 *
 * An entry is a FairScan document only if it contains a valid `document.json` (version 3) whose id is
 * the entry's id. Anything else found in the library location is not FairScan's: implementations
 * never list, modify or delete it.
 */
interface LibraryStore {

    /** The recognized documents. */
    fun list(): List<LibraryEntry>

    /** Description of the document, or null if there is no recognized document with this id. */
    fun readMetadata(id: String): DocumentMetadataV3?

    /**
     * Writes the description of the document, creating the entry if needed. A reader never sees a
     * half-written description. Fails if [DocumentMetadataV3.id] names something that is not a
     * FairScan document.
     */
    fun writeMetadata(metadata: DocumentMetadataV3)

    /** True if something that is not a FairScan document already uses this id in the library. */
    fun isForeign(id: String): Boolean

    /** Ids of the pages whose source capture is stored. */
    fun sourceIds(id: String): Set<String>

    fun readSource(id: String, pageId: String): ByteArray?

    fun writeSource(id: String, pageId: String, bytes: ByteArray)

    fun deleteSource(id: String, pageId: String)

    fun readCover(id: String): ByteArray?

    fun writeCover(id: String, bytes: ByteArray)

    /** Removes the document, only the files that FairScan wrote. Does nothing for a foreign entry. */
    fun delete(id: String)
}
