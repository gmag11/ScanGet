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
import org.fairscan.app.data.DocumentMetadataV3
import org.fairscan.app.data.PageOcrV1
import org.fairscan.app.data.toInfo

const val METADATA_FILE_NAME = "document.json"
const val COVER_FILE_NAME = "cover.jpg"
const val NOMEDIA_FILE_NAME = ".nomedia"
const val LIBRARY_SOURCES_DIR_NAME = "sources"
const val LIBRARY_PROCESSED_DIR_NAME = "processed"
const val PROCESSED_INDEX_FILE_NAME = "processed.json"
const val LIBRARY_OCR_DIR_NAME = "ocr"
private const val TEMP_SUFFIX = ".tmp"

// Page ids are timestamps, so the sources written by FairScan are named like "1760300000000.jpg"
private val SOURCE_FILE = Regex("""(\d+)\.jpg""")
// "<pageId>.<renderKey>.jpg|png": how processed images were named before they had the name of the
// document, and still are when the name wanted for a page is taken
private val PROCESSED_FILE = Regex("""(\d+)\.([0-9a-f]{8})\.(jpg|png)""")
private val OCR_FILE = Regex("""(\d+)\.json""")

/** What `processed.json` says about a page: the file of the processed images folder and its rendering. */
@Serializable
private data class ProcessedEntry(val file: String, val renderKey: String)

@Serializable
private data class ProcessedIndex(val version: Int = 1, val pages: Map<String, ProcessedEntry> = emptyMap())

/**
 * [LibraryStore] on a [DocumentTree]:
 * ```
 * <root>/<docId>/.nomedia           keeps the captures out of galleries
 *               /document.json
 *               /cover.jpg
 *               /sources/<pageId>.jpg
 *               /processed.json                            which file of processed/ is which page
 *               /processed/<Document name>-page01.jpg|png  the page as rendered, one per page
 *               /ocr/<pageId>.json                         the recognized text, if any
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
    private fun processedPath(id: String) = listOf(id, LIBRARY_PROCESSED_DIR_NAME)
    private fun ocrPath(id: String) = listOf(id, LIBRARY_OCR_DIR_NAME)
    private fun ocrFilePath(id: String, pageId: String) = listOf(id, LIBRARY_OCR_DIR_NAME, "$pageId.json")

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
                METADATA_FILE_NAME + TEMP_SUFFIX, PROCESSED_INDEX_FILE_NAME, PROCESSED_INDEX_FILE_NAME + TEMP_SUFFIX)
        child.name == LIBRARY_SOURCES_DIR_NAME ->
            tree.children(sourcesPath(id)).orEmpty().all { it.isOwnSource() }
        child.name == LIBRARY_PROCESSED_DIR_NAME ->
            ownProcessedFiles(id).let { own -> tree.children(processedPath(id)).orEmpty().all { it.isOwnProcessed(own) } }
        child.name == LIBRARY_OCR_DIR_NAME ->
            tree.children(ocrPath(id)).orEmpty().all { it.isOwnOcr() }
        else -> false
    }

    // The files the list of processed images names, and the ones named like they used to be
    private fun TreeEntry.isOwnProcessed(listed: Set<String>) =
        !isDirectory && (name in listed || PROCESSED_FILE.matches(name) ||
            name.endsWith(TEMP_SUFFIX) && name.removeSuffix(TEMP_SUFFIX).let { it in listed || PROCESSED_FILE.matches(it) })

    private fun ownProcessedFiles(id: String): Set<String> = readProcessedIndex(id).mapTo(HashSet()) { it.value.file }

    private fun TreeEntry.isOwnOcr() =
        !isDirectory && (OCR_FILE.matches(name) || name.endsWith(TEMP_SUFFIX) && OCR_FILE.matches(name.removeSuffix(TEMP_SUFFIX)))

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

    private fun indexPath(id: String) = listOf(id, PROCESSED_INDEX_FILE_NAME)

    // Pages with a processed image, by page id. Without a list (documents of an earlier version) the
    // files named after a page and its rendering are the images.
    private fun readProcessedIndex(id: String): Map<String, ProcessedEntry> {
        if (!isPlainName(id)) return emptyMap()
        val files = tree.children(processedPath(id)).orEmpty().filter { !it.isDirectory }.map { it.name }.toSet()
        val bytes = tree.readBytes(indexPath(id)) ?: tree.readBytes(indexPath(id).dropLast(1) + (PROCESSED_INDEX_FILE_NAME + TEMP_SUFFIX))
        val entries = if (bytes != null) {
            runCatching { json.decodeFromString<ProcessedIndex>(bytes.decodeToString()).pages }.getOrNull().orEmpty()
        } else {
            files.mapNotNull { file ->
                PROCESSED_FILE.matchEntire(file)?.let { it.groupValues[1] to ProcessedEntry(file, it.groupValues[2]) }
            }.toMap()
        }
        return entries.filterValues { it.file in files }
    }

    private fun writeProcessedIndex(id: String, entries: Map<String, ProcessedEntry>) {
        if (entries.isEmpty()) {
            tree.delete(indexPath(id))
        } else {
            tree.writeBytes(indexPath(id), json.encodeToString(ProcessedIndex(pages = entries)).encodeToByteArray())
        }
    }

    override fun processedPages(id: String): Map<String, StoredProcessed> =
        readProcessedIndex(id).mapValues { StoredProcessed(it.value.renderKey, it.value.file) }

    override fun readProcessed(id: String, pageId: String): ByteArray? {
        if (!isPlainName(id) || !isPlainName(pageId)) return null
        val entry = readProcessedIndex(id)[pageId] ?: return null
        return tree.readBytes(processedPath(id) + entry.file)
    }

    override fun writeProcessed(id: String, pageId: String, renderKey: String, fileName: String, bytes: ByteArray) {
        require(isPlainName(id) && isPlainName(pageId)) { "Invalid id: $id/$pageId" }
        require(isProcessedFileName(fileName)) { "Invalid file name: $fileName" }
        require(PROCESSED_FILE.matches("$pageId.$renderKey.jpg")) { "Invalid page or key: $pageId/$renderKey" }
        check(!isForeign(id)) { "$id is used by something that is not a FairScan document" }
        ensureNoMedia(id)
        val index = readProcessedIndex(id).toMutableMap()
        val previous = index[pageId]
        // Only this page's own file can be written over: any other one is another page's or the user's
        val target = if (fileName == previous?.file || !tree.exists(processedPath(id) + fileName)) fileName
        else "$pageId.$renderKey.${fileName.substringAfterLast('.')}"
        tree.writeBytes(processedPath(id) + target, bytes)
        index[pageId] = ProcessedEntry(target, renderKey)
        writeProcessedIndex(id, index)
        // The image of the page for another rendering is out of date: removed once the new one is complete
        if (previous != null && previous.file != target) tree.delete(processedPath(id) + previous.file)
    }

    override fun renameProcessed(id: String, pageId: String, fileName: String): Boolean {
        if (!isPlainName(id) || !isPlainName(pageId) || !isProcessedFileName(fileName) || readMetadata(id) == null) return false
        val index = readProcessedIndex(id).toMutableMap()
        val entry = index[pageId] ?: return false
        if (entry.file == fileName) return true
        if (tree.exists(processedPath(id) + fileName)) return false
        val bytes = tree.readBytes(processedPath(id) + entry.file) ?: return false
        tree.writeBytes(processedPath(id) + fileName, bytes)
        index[pageId] = entry.copy(file = fileName)
        writeProcessedIndex(id, index)
        tree.delete(processedPath(id) + entry.file)
        return true
    }

    override fun deleteProcessed(id: String, pageId: String) {
        if (!isPlainName(pageId) || readMetadata(id) == null) return
        val index = readProcessedIndex(id).toMutableMap()
        val entry = index.remove(pageId) ?: return
        writeProcessedIndex(id, index)
        tree.delete(processedPath(id) + entry.file)
    }

    private fun isProcessedFileName(name: String) =
        isPlainName(name) && (name.endsWith(".jpg") || name.endsWith(".png")) && !name.endsWith(TEMP_SUFFIX)

    override fun ocrPageIds(id: String): Set<String> =
        if (!isPlainName(id)) emptySet() else tree.children(ocrPath(id)).orEmpty()
            .filter { !it.isDirectory }
            .mapNotNull { OCR_FILE.matchEntire(it.name)?.groupValues?.get(1) }
            .toSet()

    override fun readOcr(id: String, pageId: String): PageOcrV1? {
        if (!isPlainName(id) || !isPlainName(pageId)) return null
        val bytes = tree.readBytes(ocrFilePath(id, pageId)) ?: return null
        return runCatching { json.decodeFromString<PageOcrV1>(bytes.decodeToString()) }
            .getOrNull()
            ?.takeIf { it.version == 1 }
    }

    override fun writeOcr(id: String, pageId: String, ocr: PageOcrV1) {
        require(isPlainName(id) && isPlainName(pageId) && OCR_FILE.matches("$pageId.json")) { "Invalid id: $id/$pageId" }
        check(!isForeign(id)) { "$id is used by something that is not a FairScan document" }
        ensureNoMedia(id)
        tree.writeBytes(ocrFilePath(id, pageId), json.encodeToString(ocr).encodeToByteArray())
    }

    override fun deleteOcr(id: String, pageId: String) {
        if (isPlainName(pageId) && readMetadata(id) != null) tree.delete(ocrFilePath(id, pageId))
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
        val listed = ownProcessedFiles(id)
        tree.children(processedPath(id)).orEmpty().filter { it.isOwnProcessed(listed) }
            .forEach { tree.delete(processedPath(id) + it.name) }
        tree.delete(processedPath(id))
        tree.children(ocrPath(id)).orEmpty().filter { it.isOwnOcr() }
            .forEach { tree.delete(ocrPath(id) + it.name) }
        tree.delete(ocrPath(id))
        for (name in listOf(COVER_FILE_NAME, COVER_FILE_NAME + TEMP_SUFFIX, NOMEDIA_FILE_NAME,
            PROCESSED_INDEX_FILE_NAME, PROCESSED_INDEX_FILE_NAME + TEMP_SUFFIX,
            METADATA_FILE_NAME + TEMP_SUFFIX, METADATA_FILE_NAME)) {
            tree.delete(listOf(id, name))
        }
        tree.delete(entry(id)) // only succeeds if empty: files that are not FairScan's stay
    }

    private fun isPlainName(name: String) =
        name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\\' !in name
}
