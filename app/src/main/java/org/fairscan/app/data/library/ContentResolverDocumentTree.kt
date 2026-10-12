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

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/**
 * [DocumentTree] on a folder granted through the system folder picker ([treeUri] is the tree URI
 * it returned). Only that folder is accessible.
 *
 * Writes go to a temporary file that then takes the place of the target, so that a reader never
 * sees a half-written file; providers that cannot rename fall back to writing the target directly.
 */
class ContentResolverDocumentTree(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
) : DocumentTree {

    private class Doc(val id: String, val name: String, val isDirectory: Boolean)

    private val rootId = DocumentsContract.getTreeDocumentId(treeUri)

    // Resolved documents by path. Any change to the tree empties it, so it cannot go stale.
    private val cache = HashMap<List<String>, Doc>()

    private fun uri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

    private fun list(parentId: String): List<Doc>? = try {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        resolver.query(
            childrenUri,
            arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE),
            null, null, null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(Doc(cursor.getString(0), cursor.getString(1) ?: "",
                        cursor.getString(2) == Document.MIME_TYPE_DIR))
                }
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun resolve(path: List<String>): Doc? {
        if (path.isEmpty()) return Doc(rootId, "", true)
        cache[path]?.let { return it }
        val parent = resolve(path.dropLast(1)) ?: return null
        if (!parent.isDirectory) return null
        val doc = list(parent.id)?.firstOrNull { it.name == path.last() } ?: return null
        cache[path] = doc
        return doc
    }

    override fun children(path: List<String>): List<TreeEntry>? {
        val dir = resolve(path)?.takeIf { it.isDirectory } ?: return null
        return list(dir.id)?.map { TreeEntry(it.name, it.isDirectory) }
    }

    override fun exists(path: List<String>) = resolve(path) != null

    override fun isDirectory(path: List<String>) = resolve(path)?.isDirectory == true

    override fun isFile(path: List<String>) = resolve(path)?.let { !it.isDirectory } == true

    override fun readBytes(path: List<String>): ByteArray? {
        val doc = resolve(path)?.takeIf { !it.isDirectory } ?: return null
        return try {
            resolver.openInputStream(uri(doc.id))?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
    }

    override fun writeBytes(path: List<String>, bytes: ByteArray) {
        require(path.isNotEmpty()) { "Cannot write the root" }
        val parent = ensureDirectories(path.dropLast(1))
        val name = path.last()
        val tempName = "$name.tmp"
        // A leftover temporary file from an interrupted write is replaced
        resolve(path.dropLast(1) + tempName)?.let { deleteDoc(it) }
        val temp = createChild(parent, tempName, isDirectory = false)
        resolver.openOutputStream(uri(temp.id), "wt")!!.use { it.write(bytes) }
        cache.clear()
        // The old file goes only once the new content is complete. Renaming over an existing file is
        // not possible, so there is a moment with only the temporary file: readers fall back to it.
        resolve(path)?.let { deleteDoc(it) }
        if (!rename(temp, name)) {
            // The provider cannot rename: write the target directly and drop the temporary file
            val direct = createChild(parent, name, isDirectory = false)
            resolver.openOutputStream(uri(direct.id), "wt")!!.use { it.write(bytes) }
            deleteDoc(temp)
        }
        cache.clear()
    }

    override fun delete(path: List<String>): Boolean {
        if (path.isEmpty()) return false
        val doc = resolve(path) ?: return false
        // Deleting a directory deletes what is in it with most providers: never ask for that
        if (doc.isDirectory && !list(doc.id).isNullOrEmpty()) return false
        return deleteDoc(doc)
    }

    private fun ensureDirectories(path: List<String>): Doc {
        var current = resolve(emptyList())!!
        val walked = ArrayList<String>()
        for (name in path) {
            walked += name
            val existing = resolve(walked.toList())
            current = when {
                existing == null -> createChild(current, name, isDirectory = true)
                existing.isDirectory -> existing
                else -> error("${walked.joinToString("/")} is not a directory")
            }
        }
        return current
    }

    private fun createChild(parent: Doc, name: String, isDirectory: Boolean): Doc {
        val mime = if (isDirectory) Document.MIME_TYPE_DIR else "application/octet-stream"
        val created = DocumentsContract.createDocument(resolver, uri(parent.id), mime, name)
            ?: error("Cannot create $name")
        cache.clear()
        val doc = resolveCreated(parent, created, name, isDirectory)
        // A provider may silently rename to avoid a clash ("name (1)"): never accept that
        check(doc.name == name) { "The provider created ${doc.name} instead of $name" }
        return doc
    }

    private fun resolveCreated(parent: Doc, created: Uri, name: String, isDirectory: Boolean): Doc {
        val id = DocumentsContract.getDocumentId(created)
        val displayName = list(parent.id)?.firstOrNull { it.id == id }?.name ?: name
        return Doc(id, displayName, isDirectory)
    }

    private fun rename(doc: Doc, newName: String): Boolean = try {
        DocumentsContract.renameDocument(resolver, uri(doc.id), newName) != null
    } catch (_: Exception) {
        false
    }.also { cache.clear() }

    private fun deleteDoc(doc: Doc): Boolean = try {
        DocumentsContract.deleteDocument(resolver, uri(doc.id))
    } catch (_: Exception) {
        false
    }.also { cache.clear() }
}
