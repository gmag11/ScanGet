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

class ProcessedNamesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val root by lazy { folder.newFolder("library") }
    private val store by lazy { FileLibraryStore(root) }

    private fun document(name: String, pageIds: List<String>, isPng: Boolean = false) {
        store.writeMetadata(DocumentMetadataV3(id = "d1", name = name, createdAt = 1, updatedAt = 2,
            pages = pageIds.map { PageV2(it) }))
        pageIds.forEachIndexed { i, pageId ->
            store.writeProcessed("d1", pageId, "aaaaaaa$i", processedFileName(name, i, isPng), byteArrayOf(pageId.toByte()))
        }
    }

    private fun files() = File(root, "d1/processed").list()!!.toSet()
    private fun content(name: String) = File(root, "d1/processed/$name").readBytes().single().toInt()

    @Test
    fun `the name is the document name, the suffix and the number of the page`() {
        assertThat(processedFileName("Invoice March", 0, false)).isEqualTo("Invoice March-page01.jpg")
        assertThat(processedFileName("Invoice March", 11, true)).isEqualTo("Invoice March-page12.png")
        assertThat(processedFileName("Big", 99, false)).isEqualTo("Big-page100.jpg")
        assertThat(processedFileName("a/b:c", 2, false)).isEqualTo("a_b_c-page03.jpg")
        assertThat(processedFileName("  ", 0, false)).isEqualTo("Document-page01.jpg")
    }

    @Test
    fun `renaming the document renames every image`() {
        document("Scan", listOf("1", "2", "3"))

        syncProcessedNames(store, "d1", "Invoice", listOf("1", "2", "3"))

        assertThat(files()).containsExactlyInAnyOrder("Invoice-page01.jpg", "Invoice-page02.jpg", "Invoice-page03.jpg")
        assertThat(content("Invoice-page02.jpg")).isEqualTo(2)
        assertThat(store.readProcessed("d1", "3")).isEqualTo(byteArrayOf(3))
    }

    @Test
    fun `deleting a page shifts the numbers of the next ones`() {
        document("Doc", listOf("1", "2", "3", "4"))
        store.deleteProcessed("d1", "2")

        syncProcessedNames(store, "d1", "Doc", listOf("1", "3", "4"))

        assertThat(files()).containsExactlyInAnyOrder("Doc-page01.jpg", "Doc-page02.jpg", "Doc-page03.jpg")
        assertThat(content("Doc-page01.jpg")).isEqualTo(1)
        assertThat(content("Doc-page02.jpg")).isEqualTo(3)
        assertThat(content("Doc-page03.jpg")).isEqualTo(4)
    }

    @Test
    fun `swapping two pages swaps their names without losing an image`() {
        document("Doc", listOf("1", "2", "3"))

        syncProcessedNames(store, "d1", "Doc", listOf("2", "1", "3"))

        assertThat(files()).containsExactlyInAnyOrder("Doc-page01.jpg", "Doc-page02.jpg", "Doc-page03.jpg")
        assertThat(content("Doc-page01.jpg")).isEqualTo(2)
        assertThat(content("Doc-page02.jpg")).isEqualTo(1)
        assertThat(content("Doc-page03.jpg")).isEqualTo(3)
    }

    @Test
    fun `a rotation of many pages ends with every page under its number`() {
        document("Doc", listOf("1", "2", "3", "4", "5"))

        syncProcessedNames(store, "d1", "Doc", listOf("5", "1", "2", "3", "4"))

        assertThat(files()).hasSize(5)
        listOf(5, 1, 2, 3, 4).forEachIndexed { i, page -> assertThat(content("Doc-page0${i + 1}.jpg")).isEqualTo(page) }
        assertThat(store.processedPages("d1").values.map { it.fileName }).doesNotContain("1.moving.jpg")
    }

    @Test
    fun `a file of the user with a wanted name stays and the page keeps its name`() {
        document("Scan", listOf("1"))
        File(root, "d1/processed/Invoice-page01.jpg").writeText("mine")

        syncProcessedNames(store, "d1", "Invoice", listOf("1"))

        assertThat(File(root, "d1/processed/Invoice-page01.jpg").readText()).isEqualTo("mine")
        assertThat(store.processedPages("d1").getValue("1").fileName).isEqualTo("Scan-page01.jpg")
        assertThat(store.readProcessed("d1", "1")).isEqualTo(byteArrayOf(1))
    }

    @Test
    fun `names that are already right are left alone`() {
        document("Doc", listOf("1", "2"))

        syncProcessedNames(store, "d1", "Doc", listOf("1", "2"))

        assertThat(files()).containsExactlyInAnyOrder("Doc-page01.jpg", "Doc-page02.jpg")
    }
}
