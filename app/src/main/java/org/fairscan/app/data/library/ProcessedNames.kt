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

import org.fairscan.app.data.sanitizeFileName

/**
 * The name of the processed image of the page at [index] (from 0) of the document [documentName]:
 * "Invoice March-page07.jpg". The number has two digits at least.
 */
fun processedFileName(documentName: String, index: Int, isPng: Boolean): String {
    val base = sanitizeFileName(documentName).trim('.', ' ').ifEmpty { "Document" }
    return "$base-page%02d.%s".format(index + 1, if (isPng) "png" else "jpg")
}

/**
 * Gives the processed images of the document [id] the names that match its name and the order of
 * its pages ([pageIds]): after renaming the document, or deleting or moving pages. No image is ever
 * overwritten: a page whose new name is still held by another one waits for it to be renamed, and if
 * they hold each other's names (two pages swapped) one of them steps aside under a temporary name.
 * A name held by a file of the user is not taken: the page keeps the name it has.
 */
fun syncProcessedNames(store: LibraryStore, id: String, documentName: String, pageIds: List<String>) {
    val stored = store.processedPages(id)
    val current = HashMap<String, String>() // page id -> its file now
    val pending = LinkedHashMap<String, String>() // page id -> the file it should have
    pageIds.forEachIndexed { index, pageId ->
        val entry = stored[pageId] ?: return@forEachIndexed
        current[pageId] = entry.fileName
        val wanted = processedFileName(documentName, index, entry.isPng)
        if (wanted != entry.fileName) pending[pageId] = wanted
    }
    while (pending.isNotEmpty()) {
        var progress = false
        for ((pageId, wanted) in pending.entries.toList()) {
            if (store.renameProcessed(id, pageId, wanted)) {
                current[pageId] = wanted
                pending.remove(pageId)
                progress = true
            }
        }
        if (progress) continue
        // Nobody can move: the names are held by pages that wait too (a cycle), or by foreign files
        val held = pending.entries.firstOrNull { (pageId, wanted) ->
            current.any { it.key != pageId && it.value == wanted }
        } ?: return // only the user's files are in the way
        val ext = current.getValue(held.key).substringAfterLast('.')
        val temporary = "${held.key}.moving.$ext"
        if (!store.renameProcessed(id, held.key, temporary)) return
        current[held.key] = temporary
    }
}
