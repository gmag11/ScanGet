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

data class TreeEntry(val name: String, val isDirectory: Boolean)

/**
 * A directory tree that documents can be stored in: a folder of the file system, or a folder granted
 * through the system folder picker. Paths are lists of names relative to the root of the tree.
 */
interface DocumentTree {

    /** The entries of the directory at [path], or null if there is no directory there. */
    fun children(path: List<String>): List<TreeEntry>?

    fun exists(path: List<String>): Boolean

    fun isDirectory(path: List<String>): Boolean

    fun isFile(path: List<String>): Boolean

    fun readBytes(path: List<String>): ByteArray?

    /**
     * Writes the file, creating the directories above it. A reader never sees a half-written file:
     * the previous content stays until the new one is complete.
     */
    fun writeBytes(path: List<String>, bytes: ByteArray)

    /**
     * Deletes a file or an **empty** directory and returns whether it did. A directory that still
     * has content is never deleted, whatever the underlying system does with such a request.
     */
    fun delete(path: List<String>): Boolean
}
