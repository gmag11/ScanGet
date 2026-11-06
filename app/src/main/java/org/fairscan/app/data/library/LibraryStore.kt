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
import org.fairscan.app.data.PageOcrV1

/** A document as listed by the library. */
data class LibraryEntry(
    val info: DocumentInfo,
    val pageCount: Int,
)

/** What a library location holds, as found by [LibraryStore.inspect]. */
data class FolderInspection(
    /** Recognized FairScan documents, probably from a previous installation. */
    val fairScanDocuments: Int,
    /** Everything else: files and folders that are not FairScan's and are never touched. */
    val otherItems: Int,
) {
    val isEmpty get() = fairScanDocuments == 0 && otherItems == 0
}

/**
 * The processed image of a page as stored: made for the rendering [renderKey], in the file
 * [fileName] (a PNG or a JPEG) of the processed images folder of the document.
 */
data class StoredProcessed(val renderKey: String, val fileName: String) {
    val isPng get() = fileName.endsWith(".png")
}

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

    /** Counts what the location holds, without writing anything. */
    fun inspect(): FolderInspection

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

    /** The processed image kept for each page (by page id), which may be out of date. */
    fun processedPages(id: String): Map<String, StoredProcessed>

    fun readProcessed(id: String, pageId: String): ByteArray?

    /**
     * Keeps the processed image of the page in the file [fileName] (".jpg" or ".png"), replacing and
     * removing the file it had. A file that is not this page's (another page's, or one of the user's)
     * is never overwritten: the image then goes in a file named after the page and key, and can be
     * given its name later with [renameProcessed].
     */
    fun writeProcessed(id: String, pageId: String, renderKey: String, fileName: String, bytes: ByteArray)

    /** Renames the processed image of the page. Returns false, and changes nothing, if [fileName] is taken. */
    fun renameProcessed(id: String, pageId: String, fileName: String): Boolean

    fun deleteProcessed(id: String, pageId: String)

    /** Ids of the pages that have OCR data. */
    fun ocrPageIds(id: String): Set<String>

    /** The OCR data of the page, or null if there is none (or it cannot be read). */
    fun readOcr(id: String, pageId: String): PageOcrV1?

    fun writeOcr(id: String, pageId: String, ocr: PageOcrV1)

    fun deleteOcr(id: String, pageId: String)

    fun readCover(id: String): ByteArray?

    fun writeCover(id: String, bytes: ByteArray)

    /** Removes the document, only the files that FairScan wrote. Does nothing for a foreign entry. */
    fun delete(id: String)
}
