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
import org.fairscan.app.data.DocumentMetadataV3
import org.fairscan.app.data.PageV2
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileLibraryStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val root by lazy { folder.newFolder("library") }
    private val store by lazy { FileLibraryStore(root) }

    private fun metadata(id: String, name: String = "Doc $id", vararg pageIds: String) =
        DocumentMetadataV3(id = id, name = name, createdAt = 1, updatedAt = 2,
            pages = pageIds.map { PageV2(it) })

    @Test
    fun `a written document is listed with its description`() {
        store.writeMetadata(metadata("d1", "Invoice", "100", "200"))

        val entries = store.list()

        assertThat(entries).hasSize(1)
        assertThat(entries[0].info.id).isEqualTo("d1")
        assertThat(entries[0].info.name).isEqualTo("Invoice")
        assertThat(entries[0].pageCount).isEqualTo(2)
        assertThat(store.readMetadata("d1")).isEqualTo(metadata("d1", "Invoice", "100", "200"))
    }

    @Test
    fun `an entry has a nomedia file and no leftover temporary file`() {
        store.writeMetadata(metadata("d1"))
        store.writeMetadata(metadata("d1", "Renamed"))

        assertThat(File(root, "d1").list()!!.toSet())
            .containsExactlyInAnyOrder(".nomedia", "document.json")
        assertThat(store.readMetadata("d1")!!.name).isEqualTo("Renamed")
    }

    @Test
    fun `sources and cover round trip`() {
        store.writeMetadata(metadata("d1", pageIds = arrayOf("100", "200")))
        store.writeSource("d1", "100", byteArrayOf(1, 2))
        store.writeSource("d1", "200", byteArrayOf(3))
        store.writeCover("d1", byteArrayOf(9))

        assertThat(store.sourceIds("d1")).containsExactlyInAnyOrder("100", "200")
        assertThat(store.readSource("d1", "100")).isEqualTo(byteArrayOf(1, 2))
        assertThat(store.readSource("d1", "999")).isNull()
        assertThat(store.readCover("d1")).isEqualTo(byteArrayOf(9))

        store.deleteSource("d1", "100")
        assertThat(store.sourceIds("d1")).containsExactly("200")
    }

    @Test
    fun `entries that are not FairScan documents are not listed`() {
        File(root, "photos").apply { mkdirs(); File(this, "a.jpg").writeText("x") }
        File(root, "notes.txt").writeText("hello")
        File(root, "other").apply { mkdirs(); File(this, "document.json").writeText("""{"a":1}""") }
        File(root, "broken").apply { mkdirs(); File(this, "document.json").writeText("{not json") }
        // A description of version 2, or with another id, is not a library document either
        File(root, "v2").apply { mkdirs(); File(this, "document.json").writeText("""{"version":2,"pages":[]}""") }
        File(root, "wrong-id").apply {
            mkdirs()
            File(this, "document.json").writeText(
                """{"version":3,"id":"other","name":"x","createdAt":1,"updatedAt":1,"pages":[]}""")
        }
        store.writeMetadata(metadata("mine"))

        assertThat(store.list().map { it.info.id }).containsExactly("mine")
        assertThat(store.readMetadata("photos")).isNull()
        assertThat(store.readMetadata("wrong-id")).isNull()
    }

    @Test
    fun `a document cannot be written over a foreign entry`() {
        File(root, "photos").apply { mkdirs(); File(this, "a.jpg").writeText("x") }
        File(root, "file").writeText("a file named like a document")

        assertThat(store.isForeign("photos")).isTrue()
        assertThat(store.isForeign("file")).isTrue()
        assertThat(store.isForeign("free")).isFalse()
        assertThat(runCatching { store.writeMetadata(metadata("photos")) }.isFailure).isTrue()
        assertThat(runCatching { store.writeMetadata(metadata("file")) }.isFailure).isTrue()

        assertThat(File(root, "photos").list()!!.toList()).containsExactly("a.jpg")
        assertThat(File(root, "file").readText()).isEqualTo("a file named like a document")
    }

    @Test
    fun `a document whose sources are written before its description is not foreign`() {
        store.writeSource("d1", "100", byteArrayOf(1))

        assertThat(store.isForeign("d1")).isFalse()
        assertThat(store.list()).isEmpty()
        store.writeMetadata(metadata("d1", pageIds = arrayOf("100")))

        assertThat(store.list().map { it.info.id }).containsExactly("d1")
        assertThat(store.readSource("d1", "100")).isEqualTo(byteArrayOf(1))
    }

    @Test
    fun `a directory with sources and other files is foreign`() {
        File(root, "d1/sources").mkdirs()
        File(root, "d1/sources/1.jpg").writeText("x")
        File(root, "d1/notes.txt").writeText("not ours")

        assertThat(store.isForeign("d1")).isTrue()
        assertThat(runCatching { store.writeMetadata(metadata("d1")) }.isFailure).isTrue()
    }

    @Test
    fun `an empty directory can be used for a document`() {
        File(root, "d1").mkdirs()

        assertThat(store.isForeign("d1")).isFalse()
        store.writeMetadata(metadata("d1"))

        assertThat(store.readMetadata("d1")).isNotNull()
    }

    @Test
    fun `ids that point outside the library are refused`() {
        assertThat(runCatching { store.writeMetadata(metadata("../escape")) }.isFailure).isTrue()
        assertThat(store.readMetadata("..")).isNull()
        assertThat(File(folder.root, "escape")).doesNotExist()
    }

    @Test
    fun `deleting a document removes its files and the directory`() {
        store.writeMetadata(metadata("d1", pageIds = arrayOf("100")))
        store.writeSource("d1", "100", byteArrayOf(1))
        store.writeCover("d1", byteArrayOf(2))

        store.delete("d1")

        assertThat(File(root, "d1")).doesNotExist()
        assertThat(store.list()).isEmpty()
    }

    @Test
    fun `deleting a document leaves files that are not FairScan's`() {
        store.writeMetadata(metadata("d1", pageIds = arrayOf("100")))
        store.writeSource("d1", "100", byteArrayOf(1))
        File(root, "d1/my-notes.txt").writeText("keep")
        File(root, "d1/sources/scan-by-hand.jpg").writeText("keep too")
        File(root, "readme.txt").writeText("outside")
        File(root, "photos").apply { mkdirs(); File(this, "a.jpg").writeText("x") }

        store.delete("d1")

        assertThat(File(root, "d1/my-notes.txt").readText()).isEqualTo("keep")
        assertThat(File(root, "d1/sources/scan-by-hand.jpg").readText()).isEqualTo("keep too")
        assertThat(File(root, "d1/document.json")).doesNotExist()
        assertThat(File(root, "d1/sources/100.jpg")).doesNotExist()
        assertThat(File(root, "readme.txt").readText()).isEqualTo("outside")
        assertThat(File(root, "photos/a.jpg")).exists()
        assertThat(store.list()).isEmpty()
    }

    @Test
    fun `deleting something that is not a FairScan document does nothing`() {
        File(root, "photos").apply { mkdirs(); File(this, "a.jpg").writeText("x") }
        File(root, "photos/sources").apply { mkdirs(); File(this, "1.jpg").writeText("x") }

        store.delete("photos")
        store.delete("missing")
        store.deleteSource("photos", "1")

        assertThat(File(root, "photos/a.jpg")).exists()
        assertThat(File(root, "photos/sources/1.jpg")).exists()
    }

    @Test
    fun `the entry is hidden from galleries before its first capture is written`() {
        store.writeSource("d1", "100", byteArrayOf(1))

        assertThat(File(root, "d1/.nomedia")).exists()
        assertThat(File(root, "d1/sources/100.jpg")).exists()
        assertThat(store.isForeign("d1")).isFalse() // still a document being written
    }

    @Test
    fun `an empty location has nothing in it`() {
        assertThat(store.inspect()).isEqualTo(FolderInspection(0, 0))
        assertThat(store.inspect().isEmpty).isTrue()
    }

    @Test
    fun `a location with a previous installation counts its documents`() {
        store.writeMetadata(metadata("d1"))
        store.writeMetadata(metadata("d2"))
        store.writeMetadata(metadata("d3"))

        assertThat(store.inspect()).isEqualTo(FolderInspection(fairScanDocuments = 3, otherItems = 0))
        assertThat(store.inspect().isEmpty).isFalse()
    }

    @Test
    fun `a location with other content counts it apart`() {
        File(root, "photos").apply { mkdirs(); File(this, "a.jpg").writeText("x") }
        File(root, "notes.txt").writeText("hello")
        File(root, "broken").apply { mkdirs(); File(this, "document.json").writeText("{not json") }

        assertThat(store.inspect()).isEqualTo(FolderInspection(fairScanDocuments = 0, otherItems = 3))
    }

    @Test
    fun `a mixed location counts both`() {
        store.writeMetadata(metadata("d1"))
        store.writeMetadata(metadata("d2"))
        File(root, "photos").mkdirs()
        File(root, "readme.txt").writeText("x")

        assertThat(store.inspect()).isEqualTo(FolderInspection(fairScanDocuments = 2, otherItems = 2))
    }

    @Test
    fun `inspecting writes nothing`() {
        File(root, "photos").mkdirs()
        val before = root.walkTopDown().map { it.relativeTo(root).path }.sorted().toList()

        store.inspect()
        store.list()

        assertThat(root.walkTopDown().map { it.relativeTo(root).path }.sorted().toList()).isEqualTo(before)
    }

    @Test
    fun `a description that is only in its temporary file is still read`() {
        store.writeMetadata(metadata("d1", "Invoice"))
        // As left by a provider that removed the old description and was interrupted before renaming
        File(root, "d1/document.json").renameTo(File(root, "d1/document.json.tmp"))

        assertThat(store.readMetadata("d1")!!.name).isEqualTo("Invoice")
        assertThat(store.list().map { it.info.id }).containsExactly("d1")
    }
}
