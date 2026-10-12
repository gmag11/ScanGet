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
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

const val METADATA_FILE_NAME = "document.json"
const val COVER_FILE_NAME = "cover.jpg"
const val NOMEDIA_FILE_NAME = ".nomedia"
const val LIBRARY_SOURCES_DIR_NAME = "sources"
private const val TEMP_SUFFIX = ".tmp"

// Page ids are timestamps, so the sources written by FairScan are named like "1760300000000.jpg"
private val SOURCE_FILE = Regex("""(\d+)\.jpg""")

/**
 * [LibraryStore] on a directory of the file system:
 * ```
 * <root>/<docId>/.nomedia           keeps the captures out of galleries
 *               /document.json
 *               /cover.jpg
 *               /sources/<pageId>.jpg
 * ```
 */
class FileLibraryStore(private val root: File) : LibraryStore {

    private val json = Json { prettyPrint = false; encodeDefaults = true }

    private fun entryDir(id: String) = File(root, id)
    private fun sourcesDir(id: String) = File(entryDir(id), LIBRARY_SOURCES_DIR_NAME)
    private fun sourceFile(id: String, pageId: String) = File(sourcesDir(id), "$pageId.jpg")

    override fun list(): List<LibraryEntry> =
        root.listFiles { file -> file.isDirectory }.orEmpty().mapNotNull { dir ->
            readMetadata(dir.name)?.let { LibraryEntry(it.toInfo(), it.pages.size) }
        }

    override fun readMetadata(id: String): DocumentMetadataV3? {
        if (!isPlainName(id)) return null
        val file = File(entryDir(id), METADATA_FILE_NAME)
        if (!file.isFile) return null
        return runCatching { json.decodeFromString<DocumentMetadataV3>(file.readText()) }
            .getOrNull()
            ?.takeIf { it.version == 3 && it.id == id }
    }

    override fun writeMetadata(metadata: DocumentMetadataV3) {
        val id = metadata.id
        require(isPlainName(id)) { "Invalid document id: $id" }
        check(!isForeign(id)) { "$id is used by something that is not a FairScan document" }
        val dir = entryDir(id)
        dir.mkdirs()
        File(dir, NOMEDIA_FILE_NAME).takeIf { !it.exists() }?.createNewFile()
        val temp = File(dir, METADATA_FILE_NAME + TEMP_SUFFIX)
        temp.writeText(json.encodeToString(metadata))
        moveReplacing(temp, File(dir, METADATA_FILE_NAME))
    }

    override fun isForeign(id: String): Boolean {
        val dir = entryDir(id)
        if (!dir.exists()) return false
        if (!dir.isDirectory) return true
        if (readMetadata(id) != null) return false
        // Without a description it can still be a document being written (its sources come first),
        // which only has files of ours. An empty directory is free to use as well.
        return !dir.listFiles().orEmpty().all(::isOwnFile)
    }

    private fun isOwnFile(file: File): Boolean = when {
        file.isFile ->
            file.name in setOf(NOMEDIA_FILE_NAME, COVER_FILE_NAME, COVER_FILE_NAME + TEMP_SUFFIX,
                METADATA_FILE_NAME + TEMP_SUFFIX)
        file.isDirectory && file.name == LIBRARY_SOURCES_DIR_NAME ->
            file.listFiles().orEmpty().all { SOURCE_FILE.matches(it.name) || it.name.endsWith(".jpg$TEMP_SUFFIX") }
        else -> false
    }

    override fun sourceIds(id: String): Set<String> =
        sourcesDir(id).listFiles().orEmpty()
            .mapNotNull { SOURCE_FILE.matchEntire(it.name)?.groupValues?.get(1) }
            .toSet()

    override fun readSource(id: String, pageId: String): ByteArray? =
        sourceFile(id, pageId).takeIf { it.isFile }?.readBytes()

    override fun writeSource(id: String, pageId: String, bytes: ByteArray) {
        require(isPlainName(pageId)) { "Invalid page id: $pageId" }
        sourcesDir(id).mkdirs()
        val target = sourceFile(id, pageId)
        val temp = File(target.parentFile, target.name + TEMP_SUFFIX)
        temp.writeBytes(bytes)
        moveReplacing(temp, target)
    }

    override fun deleteSource(id: String, pageId: String) {
        if (isPlainName(pageId) && readMetadata(id) != null) sourceFile(id, pageId).delete()
    }

    override fun readCover(id: String): ByteArray? =
        File(entryDir(id), COVER_FILE_NAME).takeIf { isPlainName(id) && it.isFile }?.readBytes()

    override fun writeCover(id: String, bytes: ByteArray) {
        if (readMetadata(id) == null) return
        val target = File(entryDir(id), COVER_FILE_NAME)
        val temp = File(entryDir(id), COVER_FILE_NAME + TEMP_SUFFIX)
        temp.writeBytes(bytes)
        moveReplacing(temp, target)
    }

    override fun delete(id: String) {
        if (readMetadata(id) == null) return
        val dir = entryDir(id)
        // The sources first, the description last: an entry exists as long as its description does
        sourcesDir(id).listFiles().orEmpty()
            .filter { SOURCE_FILE.matches(it.name) || it.name.endsWith(".jpg$TEMP_SUFFIX") }
            .forEach { it.delete() }
        sourcesDir(id).delete() // only succeeds if empty
        File(dir, COVER_FILE_NAME).delete()
        File(dir, COVER_FILE_NAME + TEMP_SUFFIX).delete()
        File(dir, NOMEDIA_FILE_NAME).delete()
        File(dir, METADATA_FILE_NAME + TEMP_SUFFIX).delete()
        File(dir, METADATA_FILE_NAME).delete()
        dir.delete() // only succeeds if empty: files that are not FairScan's are left in place
    }

    private fun isPlainName(name: String) =
        name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\\' !in name

    private fun moveReplacing(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
