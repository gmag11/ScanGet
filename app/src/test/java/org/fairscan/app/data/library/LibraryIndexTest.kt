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

import org.assertj.core.api.Assertions.assertThat
import org.fairscan.app.data.DocumentInfo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LibraryIndexTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file by lazy { File(folder.newFolder("files"), "library-index.json") }
    private val index by lazy { LibraryIndex(file) }

    private fun entry(id: String, pages: Int = 1) =
        LibraryEntry(DocumentInfo(id, "Doc $id", createdAt = 1, updatedAt = 2), pages)

    @Test
    fun `there is nothing cached at first`() {
        assertThat(index.read("loc")).isNull()
    }

    @Test
    fun `documents round trip`() {
        val entries = listOf(entry("a", 3), entry("b", 1))

        index.write("loc", entries)

        assertThat(index.read("loc")).isEqualTo(entries)
    }

    @Test
    fun `the cache of another location is not used`() {
        index.write("folder-a", listOf(entry("a")))

        assertThat(index.read("folder-b")).isNull()
    }

    @Test
    fun `a missing cache is rebuilt by writing again`() {
        index.write("loc", listOf(entry("a")))
        file.delete()
        assertThat(index.read("loc")).isNull()

        index.write("loc", listOf(entry("a"), entry("b")))

        assertThat(index.read("loc")!!.map { it.info.id }).containsExactly("a", "b")
    }

    @Test
    fun `an unreadable cache is ignored and can be overwritten`() {
        file.writeText("{not json")
        assertThat(index.read("loc")).isNull()

        index.write("loc", listOf(entry("a")))

        assertThat(index.read("loc")).hasSize(1)
    }

    @Test
    fun `clearing removes the cache`() {
        index.write("loc", listOf(entry("a")))

        index.clear()

        assertThat(index.read("loc")).isNull()
    }
}
