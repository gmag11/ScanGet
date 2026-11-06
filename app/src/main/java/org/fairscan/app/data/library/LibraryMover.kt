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

import org.fairscan.app.data.DocumentMetadataV3
import org.fairscan.app.data.PageV2
import org.fairscan.app.data.newDocumentId

data class MoveProgress(val done: Int, val total: Int)

data class MoveResult(
    /** Documents now in the destination and gone from the source, by their id in the source. */
    val moved: Map<String, String>,
    /** Documents left in the source because the destination already holds a newer (or equal) copy. */
    val keptInSource: Int,
    /** Documents that could not be moved and are still complete in the source. */
    val failed: Int,
)

/**
 * Moves the documents of a library location to another one. Only FairScan documents are moved: the
 * rest of the source location is never touched. A document is removed from the source only once it
 * is complete, and readable, in the destination, so an interruption never loses one.
 */
class LibraryMover(private val clock: () -> Long = System::currentTimeMillis) {

    fun move(
        from: LibraryStore,
        to: LibraryStore,
        onProgress: (MoveProgress) -> Unit = {},
    ): MoveResult {
        val entries = from.list()
        val moved = LinkedHashMap<String, String>()
        var kept = 0
        var failed = 0
        onProgress(MoveProgress(0, entries.size))
        entries.forEachIndexed { index, entry ->
            try {
                when (val id = moveOne(from, to, entry.info.id)) {
                    null -> kept++
                    else -> moved[entry.info.id] = id
                }
            } catch (_: Exception) {
                failed++
            }
            onProgress(MoveProgress(index + 1, entries.size))
        }
        return MoveResult(moved, kept, failed)
    }

    /** Returns the id of the document in the destination, or null if it was left in the source. */
    private fun moveOne(from: LibraryStore, to: LibraryStore, id: String): String? {
        val metadata = from.readMetadata(id) ?: error("$id is not readable")
        val existing = to.readMetadata(id)
        // The same document is already there: the most recently modified copy is kept
        if (existing != null && existing.updatedAt >= metadata.updatedAt) return null

        // Something that is not a FairScan document uses the id in the destination: use another one
        var targetId = id
        if (existing == null && to.isForeign(id)) {
            do {
                targetId = newDocumentId(clock())
            } while (to.isForeign(targetId) || to.readMetadata(targetId) != null)
        }

        // Sources first, the description last: the document exists in the destination once it is complete
        val pages = ArrayList<PageV2>()
        for (page in metadata.pages) {
            val source = from.readSource(id, page.id) ?: continue // a page without capture cannot be shown
            to.writeSource(targetId, page.id, source)
            copyCache(from, to, id, targetId, page.id)
            pages += page
        }
        val copy: DocumentMetadataV3 = metadata.copy(id = targetId, pages = pages)
        to.writeMetadata(copy)
        check(to.readMetadata(targetId) == copy) { "$targetId was not written completely" }
        from.readCover(id)?.let { to.writeCover(targetId, it) }

        // Complete and verified: the source copy can go (only its own files are removed)
        from.delete(id)
        return targetId
    }

    // The processed image and the OCR data can be made again: the move does not fail without them
    private fun copyCache(from: LibraryStore, to: LibraryStore, id: String, targetId: String, pageId: String) {
        runCatching {
            from.processedPages(id)[pageId]?.let { processed ->
                from.readProcessed(id, pageId)?.let { to.writeProcessed(targetId, pageId, processed.renderKey, processed.fileName, it) }
            }
            from.readOcr(id, pageId)?.let { to.writeOcr(targetId, pageId, it) }
        }
    }
}
