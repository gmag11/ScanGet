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
import java.io.IOException

class LibraryMoverTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val fromDir by lazy { folder.newFolder("from") }
    private val toDir by lazy { folder.newFolder("to") }
    private val from by lazy { FileLibraryStore(fromDir) }
    private val to by lazy { FileLibraryStore(toDir) }
    private var clock = 1_000L
    private val mover by lazy { LibraryMover { clock++ } }

    private fun add(store: LibraryStore, id: String, updatedAt: Long = 10, vararg pageIds: String) {
        val pages = pageIds.ifEmpty { arrayOf("100") }
        pages.forEach { store.writeSource(id, it, byteArrayOf(it.toInt().toByte(), 1)) }
        store.writeMetadata(DocumentMetadataV3(id = id, name = "Doc $id", createdAt = 1,
            updatedAt = updatedAt, pages = pages.map { PageV2(it) }))
        store.writeCover(id, byteArrayOf(9))
    }

    @Test
    fun `documents are moved with their sources and cover and leave the source`() {
        add(from, "d1", pageIds = arrayOf("100", "200"))
        add(from, "d2")

        val result = mover.move(from, to)

        assertThat(result.moved).containsOnlyKeys("d1", "d2")
        assertThat(result.keptInSource).isEqualTo(0)
        assertThat(result.failed).isEqualTo(0)
        assertThat(to.list().map { it.info.id }).containsExactlyInAnyOrder("d1", "d2")
        assertThat(to.sourceIds("d1")).containsExactlyInAnyOrder("100", "200")
        assertThat(to.readSource("d1", "200")).isEqualTo(byteArrayOf(200.toByte(), 1))
        assertThat(to.readCover("d1")).isEqualTo(byteArrayOf(9))
        assertThat(to.readMetadata("d1")).isEqualTo(from.readMetadata("d1") ?: to.readMetadata("d1"))
        assertThat(from.list()).isEmpty()
        assertThat(fromDir.list()!!).isEmpty()
    }

    @Test
    fun `progress is reported for every document`() {
        add(from, "d1"); add(from, "d2"); add(from, "d3")
        val progress = mutableListOf<MoveProgress>()

        mover.move(from, to) { progress += it }

        assertThat(progress).containsExactly(
            MoveProgress(0, 3), MoveProgress(1, 3), MoveProgress(2, 3), MoveProgress(3, 3))
    }

    @Test
    fun `files that are not FairScan's are not moved nor touched`() {
        add(from, "d1")
        File(fromDir, "photos").apply { mkdirs(); File(this, "a.jpg").writeText("x") }
        File(fromDir, "notes.txt").writeText("hello")
        File(fromDir, "d1/my-notes.txt").writeText("keep")

        mover.move(from, to)

        assertThat(File(fromDir, "photos/a.jpg").readText()).isEqualTo("x")
        assertThat(File(fromDir, "notes.txt").readText()).isEqualTo("hello")
        assertThat(File(fromDir, "d1/my-notes.txt").readText()).isEqualTo("keep")
        assertThat(toDir.list()!!.toList()).containsExactly("d1")
        assertThat(File(toDir, "d1").list()!!.toList()).doesNotContain("my-notes.txt")
    }

    @Test
    fun `the most recently modified copy wins when the document is in both places`() {
        add(from, "newer", updatedAt = 50)
        add(to, "newer", updatedAt = 10)
        add(from, "older", updatedAt = 5)
        add(to, "older", updatedAt = 40)

        val result = mover.move(from, to)

        assertThat(result.moved).containsOnlyKeys("newer")
        assertThat(result.keptInSource).isEqualTo(1)
        assertThat(to.readMetadata("newer")!!.updatedAt).isEqualTo(50)
        assertThat(to.readMetadata("older")!!.updatedAt).isEqualTo(40)
        // The copy that was not used stays where it was
        assertThat(from.readMetadata("older")).isNotNull()
        assertThat(from.readMetadata("newer")).isNull()
    }

    @Test
    fun `a document takes another id when the destination uses its id for something else`() {
        add(from, "d1")
        File(toDir, "d1").apply { mkdirs(); File(this, "holiday.jpg").writeText("mine") }

        val result = mover.move(from, to)

        val newId = result.moved.getValue("d1")
        assertThat(newId).isNotEqualTo("d1")
        assertThat(to.readMetadata(newId)!!.id).isEqualTo(newId)
        assertThat(to.readSource(newId, "100")).isNotNull()
        assertThat(File(toDir, "d1").list()!!.toList()).containsExactly("holiday.jpg")
        assertThat(from.list()).isEmpty()
    }

    // A destination that stops working while a document is being written
    private class FailingStore(private val delegate: LibraryStore, var failOnMetadata: Boolean = false,
                               var failOnSource: String? = null) : LibraryStore by delegate {
        override fun writeMetadata(metadata: DocumentMetadataV3) {
            if (failOnMetadata) throw IOException("disk full")
            delegate.writeMetadata(metadata)
        }
        override fun writeSource(id: String, pageId: String, bytes: ByteArray) {
            if (pageId == failOnSource) throw IOException("disk full")
            delegate.writeSource(id, pageId, bytes)
        }
    }

    @Test
    fun `an interruption after the sources were copied loses nothing`() {
        add(from, "d1", pageIds = arrayOf("100", "200"))
        val failing = FailingStore(to, failOnMetadata = true)

        val result = mover.move(from, failing)

        assertThat(result.failed).isEqualTo(1)
        assertThat(result.moved).isEmpty()
        // Complete in the source...
        assertThat(from.readMetadata("d1")).isNotNull()
        assertThat(from.sourceIds("d1")).containsExactlyInAnyOrder("100", "200")
        // ...and not an existing document in the destination (only its sources were written)
        assertThat(to.list()).isEmpty()
        assertThat(to.readMetadata("d1")).isNull()

        // Trying again, once it works, moves it
        failing.failOnMetadata = false
        assertThat(mover.move(from, failing).moved).containsOnlyKeys("d1")
        assertThat(to.sourceIds("d1")).containsExactlyInAnyOrder("100", "200")
        assertThat(from.list()).isEmpty()
    }

    @Test
    fun `an interruption while copying the sources loses nothing either`() {
        add(from, "d1", pageIds = arrayOf("100", "200"))
        add(from, "d2")

        val result = mover.move(from, FailingStore(to, failOnSource = "200"))

        assertThat(result.failed).isEqualTo(1)
        assertThat(result.moved).containsOnlyKeys("d2") // the other document is not affected
        assertThat(from.readMetadata("d1")).isNotNull()
        assertThat(from.sourceIds("d1")).containsExactlyInAnyOrder("100", "200")
        assertThat(to.list().map { it.info.id }).containsExactly("d2")
    }

    @Test
    fun `a page without capture is left out of the moved document`() {
        add(from, "d1", pageIds = arrayOf("100", "200"))
        from.deleteSource("d1", "200")

        mover.move(from, to)

        assertThat(to.readMetadata("d1")!!.pages.map { it.id }).containsExactly("100")
    }

    @Test
    fun `moving an empty location does nothing`() {
        val result = mover.move(from, to)

        assertThat(result).isEqualTo(MoveResult(emptyMap(), 0, 0))
    }
}
