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

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.fairscan.app.data.DocumentMetadataV3
import org.fairscan.app.data.toInfo

const val METADATA_FILE_NAME = "document.json"
const val COVER_FILE_NAME = "cover.jpg"
const val NOMEDIA_FILE_NAME = ".nomedia"
const val LIBRARY_SOURCES_DIR_NAME = "sources"
private const val TEMP_SUFFIX = ".tmp"

// Page ids are timestamps, so the sources written by FairScan are named like "1760300000000.jpg"
private val SOURCE_FILE = Regex("""(\d+)\.jpg""")

/**
 * [LibraryStore] on a [DocumentTree]:
 * ```
 * <root>/<docId>/.nomedia           keeps the captures out of galleries
 *               /document.json
 *               /cover.jpg
 *               /sources/<pageId>.jpg
 * ```
 * The root may be a folder that holds the user's own files too: those are never listed, modified,
 * moved or deleted.
 */
class TreeLibraryStore(private val tree: DocumentTree) : LibraryStore {

    private val json = Json { prettyPrint = false; encodeDefaults = true }

    private fun entry(id: String) = listOf(id)
    private fun metadataPath(id: String) = listOf(id, METADATA_FILE_NAME)
    private fun coverPath(id: String) = listOf(id, COVER_FILE_NAME)
    private fun sourcesPath(id: String) = listOf(id, LIBRARY_SOURCES_DIR_NAME)
    private fun sourcePath(id: String, pageId: String) = listOf(id, LIBRARY_SOURCES_DIR_NAME, "$pageId.jpg")

    override fun list(): List<LibraryEntry> =
        tree.children(emptyList()).orEmpty().filter { it.isDirectory }.mapNotNull { dir ->
            readMetadata(dir.name)?.let { LibraryEntry(it.toInfo(), it.pages.size) }
        }

    override fun inspect(): FolderInspection {
        val children = tree.children(emptyList()).orEmpty()
        val documents = children.count { it.isDirectory && readMetadata(it.name) != null }
        return FolderInspection(documents, children.size - documents)
    }

    override fun readMetadata(id: String): DocumentMetadataV3? {
        if (!isPlainName(id)) return null
        // A write that was interrupted between removing the old description and renaming the new one
        // leaves only the temporary file, which is complete
        val bytes = tree.readBytes(metadataPath(id))
            ?: tree.readBytes(metadataPath(id).dropLast(1) + (METADATA_FILE_NAME + TEMP_SUFFIX))
            ?: return null
        return runCatching { json.decodeFromString<DocumentMetadataV3>(bytes.decodeToString()) }
            .getOrNull()
            ?.takeIf { it.version == 3 && it.id == id }
    }

    override fun writeMetadata(metadata: DocumentMetadataV3) {
        val id = metadata.id
        require(isPlainName(id)) { "Invalid document id: $id" }
        check(!isForeign(id)) { "$id is used by something that is not a FairScan document" }
        ensureNoMedia(id)
        tree.writeBytes(metadataPath(id), json.encodeToString(metadata).encodeToByteArray())
    }

    // Before anything else is written in the entry: a gallery could index the captures otherwise
    private fun ensureNoMedia(id: String) {
        val nomedia = listOf(id, NOMEDIA_FILE_NAME)
        if (!tree.exists(nomedia)) tree.writeBytes(nomedia, ByteArray(0))
    }

    override fun isForeign(id: String): Boolean {
        if (!isPlainName(id)) return true
        if (!tree.exists(entry(id))) return false
        if (!tree.isDirectory(entry(id))) return true
        if (readMetadata(id) != null) return false
        // Without a description it can still be a document being written (its sources come first),
        // which only has files of ours. An empty directory is free to use as well.
        return !tree.children(entry(id)).orEmpty().all { isOwnEntry(id, it) }
    }

    private fun isOwnEntry(id: String, child: TreeEntry): Boolean = when {
        !child.isDirectory ->
            child.name in setOf(NOMEDIA_FILE_NAME, COVER_FILE_NAME, COVER_FILE_NAME + TEMP_SUFFIX,
                METADATA_FILE_NAME + TEMP_SUFFIX)
        child.name == LIBRARY_SOURCES_DIR_NAME ->
            tree.children(sourcesPath(id)).orEmpty().all { it.isOwnSource() }
        else -> false
    }

    private fun TreeEntry.isOwnSource() =
        !isDirectory && (SOURCE_FILE.matches(name) || name.endsWith(".jpg$TEMP_SUFFIX"))

    override fun sourceIds(id: String): Set<String> =
        tree.children(sourcesPath(id)).orEmpty()
            .filter { !it.isDirectory }
            .mapNotNull { SOURCE_FILE.matchEntire(it.name)?.groupValues?.get(1) }
            .toSet()

    override fun readSource(id: String, pageId: String): ByteArray? =
        if (isPlainName(id) && isPlainName(pageId)) tree.readBytes(sourcePath(id, pageId)) else null

    override fun writeSource(id: String, pageId: String, bytes: ByteArray) {
        require(isPlainName(id) && isPlainName(pageId)) { "Invalid id: $id/$pageId" }
        check(!isForeign(id)) { "$id is used by something that is not a FairScan document" }
        ensureNoMedia(id)
        tree.writeBytes(sourcePath(id, pageId), bytes)
    }

    override fun deleteSource(id: String, pageId: String) {
        if (isPlainName(pageId) && readMetadata(id) != null) tree.delete(sourcePath(id, pageId))
    }

    override fun readCover(id: String): ByteArray? =
        if (isPlainName(id)) tree.readBytes(coverPath(id)) else null

    override fun writeCover(id: String, bytes: ByteArray) {
        if (readMetadata(id) == null) return
        tree.writeBytes(coverPath(id), bytes)
    }

    override fun delete(id: String) {
        if (readMetadata(id) == null) return
        // The sources first, the description last: an entry exists as long as its description does
        tree.children(sourcesPath(id)).orEmpty().filter { it.isOwnSource() }
            .forEach { tree.delete(sourcesPath(id) + it.name) }
        tree.delete(sourcesPath(id)) // only succeeds if empty
        for (name in listOf(COVER_FILE_NAME, COVER_FILE_NAME + TEMP_SUFFIX, NOMEDIA_FILE_NAME,
            METADATA_FILE_NAME + TEMP_SUFFIX, METADATA_FILE_NAME)) {
            tree.delete(listOf(id, name))
        }
        tree.delete(entry(id)) // only succeeds if empty: files that are not FairScan's stay
    }

    private fun isPlainName(name: String) =
        name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\\' !in name
}
