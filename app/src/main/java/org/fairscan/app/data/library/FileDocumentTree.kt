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

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** [DocumentTree] on a directory of the file system. */
class FileDocumentTree(private val root: File) : DocumentTree {

    private fun file(path: List<String>): File {
        require(path.all { it.isNotEmpty() && it != "." && it != ".." && '/' !in it && '\\' !in it }) {
            "Invalid path: $path"
        }
        return path.fold(root) { dir, name -> File(dir, name) }
    }

    override fun children(path: List<String>): List<TreeEntry>? {
        val dir = file(path)
        if (!dir.isDirectory) return null
        return dir.listFiles().orEmpty().map { TreeEntry(it.name, it.isDirectory) }
    }

    override fun exists(path: List<String>) = file(path).exists()

    override fun isDirectory(path: List<String>) = file(path).isDirectory

    override fun isFile(path: List<String>) = file(path).isFile

    override fun readBytes(path: List<String>): ByteArray? =
        file(path).takeIf { it.isFile }?.readBytes()

    override fun writeBytes(path: List<String>, bytes: ByteArray) {
        val target = file(path)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeBytes(bytes)
        try {
            Files.move(temp.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    // File.delete() refuses a directory that is not empty
    override fun delete(path: List<String>) = file(path).delete()
}
