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
package org.fairscan.app.ui.screens.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.fairscan.app.data.DocumentInfo
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.data.ImageTransformations
import org.fairscan.app.data.Logger
import org.fairscan.app.data.OcrWordV1
import org.fairscan.app.data.PageOcrV1
import org.fairscan.app.data.library.FileLibraryStore
import org.fairscan.app.data.library.LastOpenedDocument
import org.fairscan.app.data.library.LibraryEntry
import org.fairscan.app.data.library.LibraryRepository
import org.fairscan.app.data.library.normalizeForSearch
import org.fairscan.app.domain.EncodedImage
import org.fairscan.app.domain.Jpeg
import org.fairscan.app.domain.PageMetadata
import org.fairscan.app.domain.Rotation
import org.fairscan.imageprocessing.Adjustments
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.ColorMode.COLOR
import org.fairscan.imageprocessing.ImageSize
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private fun entry(id: String, updatedAt: Long, pages: Int = 1) =
    LibraryEntry(DocumentInfo(id, "Doc $id", createdAt = 1, updatedAt = updatedAt), pages)

class LibraryUiStateTest {

    @Test
    fun `with no document opened last the documents come most recent first and there is no recent`() {
        val state = libraryUiState(listOf(entry("a", 10), entry("c", 30), entry("b", 20)), lastOpenedId = null)

        assertThat(state.isLoading).isFalse()
        assertThat(state.recent).isNull()
        assertThat(state.others.map { it.id }).containsExactly("c", "b", "a")
    }

    @Test
    fun `the document opened last comes apart and is not repeated`() {
        val state = libraryUiState(listOf(entry("a", 10), entry("c", 30), entry("b", 20)), lastOpenedId = "a")

        assertThat(state.recent!!.id).isEqualTo("a")
        assertThat(state.others.map { it.id }).containsExactly("c", "b")
    }

    @Test
    fun `the document opened last is first even when it is not the most recently modified`() {
        val state = libraryUiState(listOf(entry("new", 99), entry("old", 1)), lastOpenedId = "old")

        assertThat(state.recent!!.id).isEqualTo("old")
        assertThat(state.others.map { it.id }).containsExactly("new")
    }

    @Test
    fun `a document opened last that is not in the library gives no recent`() {
        val state = libraryUiState(listOf(entry("a", 10), entry("b", 20)), lastOpenedId = "deleted")

        assertThat(state.recent).isNull()
        assertThat(state.others.map { it.id }).containsExactly("b", "a")
    }

    @Test
    fun `an item carries what the list shows`() {
        val state = libraryUiState(listOf(entry("a", 1234, pages = 7)), lastOpenedId = null)

        assertThat(state.others.single()).isEqualTo(LibraryItem("a", "Doc a", 1234, 7))
    }

    @Test
    fun `an empty library is empty`() {
        val state = libraryUiState(emptyList(), lastOpenedId = "a")

        assertThat(state.isEmpty).isTrue()
        assertThat(state.isLoading).isFalse()
        assertThat(LibraryUiState().isLoading).isTrue() // before anything is known
    }

    @Test
    fun `the only document being the recent one leaves no others`() {
        val state = libraryUiState(listOf(entry("a", 10)), lastOpenedId = "a")

        assertThat(state.recent!!.id).isEqualTo("a")
        assertThat(state.others).isEmpty()
        assertThat(state.isEmpty).isFalse()
    }
}

class LibrarySearchTest {

    private fun item(id: String, name: String, updatedAt: Long = 1) = LibraryItem(id, name, updatedAt, 1)

    private val docs = listOf(
        item("1", "Factura luz", 30), item("2", "Recibo agua", 20), item("3", "factura gas", 10),
        item("4", "Camión alquiler", 5))

    @Test
    fun `the documents whose name contains the query are kept, in the same order`() {
        assertThat(filterDocuments(docs, "fact").map { it.id }).containsExactly("1", "3")
    }

    @Test
    fun `capitals do not matter`() {
        assertThat(filterDocuments(docs, "FACTURA").map { it.id }).containsExactly("1", "3")
        assertThat(filterDocuments(docs, "recibo").map { it.id }).containsExactly("2")
    }

    @Test
    fun `accents do not matter, in the query or in the name`() {
        assertThat(filterDocuments(docs, "camion").map { it.id }).containsExactly("4")
        assertThat(filterDocuments(docs, "CAMIÓN").map { it.id }).containsExactly("4")
        assertThat(filterDocuments(listOf(item("5", "Camion")), "camión").map { it.id }).containsExactly("5")
    }

    @Test
    fun `the recognized text is searched too, without capitals or accents`() {
        val texts = mapOf("2" to normalizeForSearch("Pago de la HIPOTECA del mes"))

        assertThat(filterDocuments(docs, "hipoteca", texts).map { it.id }).containsExactly("2")
        assertThat(filterDocuments(docs, "Hipotéca", texts).map { it.id }).containsExactly("2")
        assertThat(filterDocuments(docs, "fact", texts).map { it.id }).containsExactly("1", "3")
    }

    @Test
    fun `a document without recognized text is only found by name`() {
        val texts = mapOf("2" to "hipoteca")

        assertThat(filterDocuments(docs, "hipoteca", texts).map { it.id }).doesNotContain("1", "3", "4")
        assertThat(filterDocuments(docs, "luz", texts).map { it.id }).containsExactly("1")
    }

    @Test
    fun `the query is looked for anywhere in the name`() {
        assertThat(filterDocuments(docs, "agua").map { it.id }).containsExactly("2")
        assertThat(filterDocuments(docs, "luz").map { it.id }).containsExactly("1")
    }

    @Test
    fun `an empty or blank query keeps every document`() {
        assertThat(filterDocuments(docs, "")).isEqualTo(docs)
        assertThat(filterDocuments(docs, "   ")).isEqualTo(docs)
    }

    @Test
    fun `spaces around the query are ignored`() {
        assertThat(filterDocuments(docs, "  gas ").map { it.id }).containsExactly("3")
    }

    @Test
    fun `a query that matches nothing gives nothing`() {
        assertThat(filterDocuments(docs, "zzz")).isEmpty()
    }

    private fun entries() = listOf(entry("a", 10), entry("c", 30), entry("b", 20))

    @Test
    fun `with a search there is no recent section and the matches come in order`() {
        val state = libraryUiState(
            listOf(LibraryEntry(DocumentInfo("x", "Factura luz", 1, 30), 1),
                LibraryEntry(DocumentInfo("y", "Recibo", 1, 20), 1),
                LibraryEntry(DocumentInfo("z", "factura gas", 1, 10), 1)),
            lastOpenedId = "z", query = "fact")

        assertThat(state.recent).isNull() // not apart, even though it was opened last
        assertThat(state.others.map { it.id }).containsExactly("x", "z")
        assertThat(state.isSearching).isTrue()
    }

    @Test
    fun `without a search the state is the usual one`() {
        val state = libraryUiState(entries(), lastOpenedId = "a", query = null)

        assertThat(state.isSearching).isFalse()
        assertThat(state.recent!!.id).isEqualTo("a")
        assertThat(state.shown.map { it.id }).containsExactly("a", "c", "b")
    }

    @Test
    fun `a search that matches nothing is empty but still a search`() {
        val state = libraryUiState(entries(), lastOpenedId = null, query = "zzz")

        assertThat(state.isEmpty).isTrue()
        assertThat(state.isSearching).isTrue()
    }

    @Test
    fun `selected documents that are not shown are dropped`() {
        val named = listOf(
            LibraryEntry(DocumentInfo("x", "Factura luz", 1, 30), 1),
            LibraryEntry(DocumentInfo("y", "Recibo", 1, 20), 1))

        val all = libraryUiState(named, null, query = null, selectedIds = setOf("x", "y", "gone"))
        val filtered = libraryUiState(named, null, query = "fact", selectedIds = setOf("x", "y"))

        assertThat(all.selectedIds).containsExactlyInAnyOrder("x", "y") // "gone" does not exist
        assertThat(filtered.selectedIds).containsExactly("x")
        assertThat(all.isSelecting).isTrue()
        assertThat(libraryUiState(named, null).isSelecting).isFalse()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val store by lazy { FileLibraryStore(folder.newFolder("library")) }
    private val metadata = PageMetadata(
        Quad(Point(.01, .02), Point(.1, .03), Point(.11, .12), Point(.03, .09)),
        Rotation.R0, COLOR, ImageSize(1600, 1200), null)

    private class FakeLastOpened(var id: String? = null) : LastOpenedDocument {
        override suspend fun lastOpenedId() = id
        override suspend fun setLastOpenedId(id: String?) { this.id = id }
    }
    private val lastOpened = FakeLastOpened()

    private lateinit var images: ImageRepository
    private lateinit var library: LibraryRepository
    private val deleted = mutableListOf<String>()
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun setUpWith(scope: kotlinx.coroutines.CoroutineScope) {
        val transformations = object : ImageTransformations {
            override fun rotate(input: EncodedImage, rotationDegrees: Int): EncodedImage = input
            override fun resizeToThumbnail(input: EncodedImage): Jpeg = Jpeg(byteArrayOf(input.bytes[0]))
            override fun process(source: Jpeg, metadata: PageMetadata, colorMode: ColorMode,
                                 adjustments: Adjustments): Jpeg = Jpeg(byteArrayOf(source.bytes[0], 1))
        }
        val logger = Logger { _, _, _ -> }
        images = ImageRepository(folder.newFolder("work"), transformations, scope, logger)
        library = LibraryRepository({ store }, images, scope, logger,
            ioDispatcher = Dispatchers.Unconfined, lastOpened = lastOpened)
        viewModel = LibraryViewModel(library, logger, { id -> deleted += id; library.delete(id) }, Dispatchers.Unconfined)
    }

    // The library renders covers on real IO threads, which the test dispatchers do not wait for. The
    // wait must not block the test thread: the autosave runs on the test scheduler and may hold the
    // library's lock until it gets a turn.
    private suspend fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) {
                "Condition not reached: ${viewModel.uiState.value}, deleted=$deleted"
            }
            withContext(Dispatchers.Default) { delay(10) }
        }
    }

    private suspend fun addDocument(first: Byte = 1): String {
        images.add(Jpeg(byteArrayOf(first, 5)), Jpeg(byteArrayOf(first)), metadata, COLOR)
        Thread.sleep(2) // page ids are timestamps
        library.flush()
        return images.documentInfo().id
    }

    @Test
    fun `refresh lists the documents with the one opened last apart`() = runTest {
        setUpWith(backgroundScope)
        val first = addDocument(1)
        library.startNewDocument()
        val second = addDocument(2)
        library.open(first)

        viewModel.refresh()
        awaitUntil { !viewModel.uiState.value.isLoading }

        val state = viewModel.uiState.value
        assertThat(state.recent!!.id).isEqualTo(first)
        assertThat(state.others.map { it.id }).containsExactly(second)
    }

    @Test
    fun `an empty library is listed as empty and not as loading`() = runTest {
        setUpWith(backgroundScope)

        viewModel.refresh()
        awaitUntil { !viewModel.uiState.value.isLoading }

        assertThat(viewModel.uiState.value.isEmpty).isTrue()
    }

    @Test
    fun `renaming trims the name and shows it`() = runTest {
        setUpWith(backgroundScope)
        val id = addDocument()

        assertThat(viewModel.rename(id, "  Car insurance  ")).isTrue()
        awaitUntil { viewModel.uiState.value.recent?.name == "Car insurance" }

        assertThat(viewModel.uiState.value.recent!!.name).isEqualTo("Car insurance")
        assertThat(store.readMetadata(id)!!.name).isEqualTo("Car insurance")
    }

    @Test
    fun `a blank name is not accepted and changes nothing`() = runTest {
        setUpWith(backgroundScope)
        val id = addDocument()
        val before = store.readMetadata(id)!!.name

        assertThat(viewModel.rename(id, "")).isFalse()
        assertThat(viewModel.rename(id, "   ")).isFalse()

        assertThat(store.readMetadata(id)!!.name).isEqualTo(before)
    }

    @Test
    fun `deleting a document removes it from the list and from the library`() = runTest {
        setUpWith(backgroundScope)
        val first = addDocument(1)
        library.startNewDocument()
        val second = addDocument(2)
        viewModel.refresh()
        awaitUntil { !viewModel.uiState.value.isLoading }

        viewModel.delete(first)
        awaitUntil { viewModel.uiState.value.others.isEmpty() && viewModel.uiState.value.recent?.id == second }

        assertThat(deleted).containsExactly(first)
        assertThat(store.readMetadata(first)).isNull()
        assertThat(viewModel.uiState.value.recent!!.id).isEqualTo(second)
        assertThat(viewModel.uiState.value.others).isEmpty()
    }

    @Test
    fun `deleting the recent document leaves no recent section`() = runTest {
        setUpWith(backgroundScope)
        val first = addDocument(1)
        library.startNewDocument()
        val second = addDocument(2)
        viewModel.refresh()
        awaitUntil { !viewModel.uiState.value.isLoading }

        viewModel.delete(second) // the one opened last, and the open one
        awaitUntil { viewModel.uiState.value.recent == null && viewModel.uiState.value.others.isNotEmpty() }

        val state = viewModel.uiState.value
        assertThat(state.recent).isNull()
        assertThat(state.others.map { it.id }).containsExactly(first)
        assertThat(images.pages()).isEmpty() // the working document went with it
    }

    // --- Search and selection ---

    private suspend fun listedWith(vararg names: String): List<String> {
        val ids = mutableListOf<String>()
        for ((i, name) in names.withIndex()) {
            if (i > 0) library.startNewDocument()
            ids += addDocument((i + 1).toByte())
            library.rename(ids.last(), name)
        }
        viewModel.refresh()
        awaitUntil { !viewModel.uiState.value.isLoading && viewModel.uiState.value.shown.size == names.size }
        return ids
    }

    @Test
    fun `the search filters as the query is typed and closing it shows everything`() = runTest {
        setUpWith(backgroundScope)
        listedWith("Factura luz", "Recibo agua", "factura gas")

        viewModel.openSearch()
        assertThat(viewModel.uiState.value.query).isEqualTo("")
        assertThat(viewModel.uiState.value.shown).hasSize(3)

        viewModel.onQueryChange("fact")
        assertThat(viewModel.uiState.value.shown.map { it.name }).containsExactlyInAnyOrder("Factura luz", "factura gas")
        assertThat(viewModel.uiState.value.recent).isNull()

        viewModel.onQueryChange("zzz")
        assertThat(viewModel.uiState.value.isEmpty).isTrue()

        viewModel.closeSearch()
        assertThat(viewModel.uiState.value.query).isNull()
        assertThat(viewModel.uiState.value.shown).hasSize(3)
    }

    @Test
    fun `typing without an open search does nothing`() = runTest {
        setUpWith(backgroundScope)
        listedWith("A", "B")

        viewModel.onQueryChange("a")

        assertThat(viewModel.uiState.value.query).isNull()
        assertThat(viewModel.uiState.value.shown).hasSize(2)
    }

    @Test
    fun `a long press starts the selection and a tap toggles the others`() = runTest {
        setUpWith(backgroundScope)
        val ids = listedWith("A", "B", "C")

        viewModel.toggleSelection(ids[0]) // a tap outside selection mode selects nothing
        assertThat(viewModel.uiState.value.isSelecting).isFalse()

        viewModel.startSelection(ids[1])
        assertThat(viewModel.uiState.value.selectedIds).containsExactly(ids[1])
        assertThat(viewModel.uiState.value.isSelecting).isTrue()

        viewModel.toggleSelection(ids[2])
        assertThat(viewModel.uiState.value.selectedIds).containsExactlyInAnyOrder(ids[1], ids[2])

        viewModel.startSelection(ids[0]) // a long press while selecting does not change it
        assertThat(viewModel.uiState.value.selectedIds).containsExactlyInAnyOrder(ids[1], ids[2])
    }

    @Test
    fun `selection mode ends when the last document is unselected`() = runTest {
        setUpWith(backgroundScope)
        val ids = listedWith("A", "B")
        viewModel.startSelection(ids[0])
        viewModel.toggleSelection(ids[1])

        viewModel.toggleSelection(ids[0])
        assertThat(viewModel.uiState.value.isSelecting).isTrue()
        viewModel.toggleSelection(ids[1])

        assertThat(viewModel.uiState.value.isSelecting).isFalse()
        assertThat(viewModel.uiState.value.selectedIds).isEmpty()
    }

    @Test
    fun `select all selects what is shown and clearing ends the selection`() = runTest {
        setUpWith(backgroundScope)
        val ids = listedWith("A", "B", "C")
        viewModel.startSelection(ids[0])

        viewModel.selectAll()
        assertThat(viewModel.uiState.value.selectedIds).containsExactlyInAnyOrder(*ids.toTypedArray())

        viewModel.clearSelection()
        assertThat(viewModel.uiState.value.isSelecting).isFalse()
        assertThat(viewModel.uiState.value.shown).hasSize(3) // nothing is deleted
    }

    @Test
    fun `select all respects the search`() = runTest {
        setUpWith(backgroundScope)
        val ids = listedWith("Factura luz", "Recibo agua", "factura gas")
        viewModel.openSearch()
        viewModel.onQueryChange("fact")
        viewModel.startSelection(ids[0])

        viewModel.selectAll()

        assertThat(viewModel.uiState.value.selectedIds).containsExactlyInAnyOrder(ids[0], ids[2])
    }

    @Test
    fun `selected documents hidden by a new query are unselected`() = runTest {
        setUpWith(backgroundScope)
        val ids = listedWith("Factura luz", "Recibo agua")
        viewModel.startSelection(ids[0])
        viewModel.toggleSelection(ids[1])

        viewModel.openSearch()
        viewModel.onQueryChange("recibo")

        assertThat(viewModel.uiState.value.selectedIds).containsExactly(ids[1])
    }

    @Test
    fun `closing the search keeps the selection and entering selection keeps the search`() = runTest {
        setUpWith(backgroundScope)
        val ids = listedWith("Factura luz", "Recibo agua")
        viewModel.openSearch()
        viewModel.onQueryChange("factura")
        viewModel.startSelection(ids[0])
        assertThat(viewModel.uiState.value.query).isEqualTo("factura")

        viewModel.clearSelection()
        assertThat(viewModel.uiState.value.query).isEqualTo("factura") // the search stays open

        viewModel.startSelection(ids[0])
        viewModel.closeSearch()
        assertThat(viewModel.uiState.value.selectedIds).containsExactly(ids[0])
    }

    @Test
    fun `deleting the selected documents removes them all and ends the selection`() = runTest {
        setUpWith(backgroundScope)
        val ids = listedWith("A", "B", "C")
        viewModel.startSelection(ids[0])
        viewModel.toggleSelection(ids[2])

        viewModel.deleteSelected()
        awaitUntil { viewModel.uiState.value.shown.size == 1 }

        assertThat(deleted).containsExactlyInAnyOrder(ids[0], ids[2])
        assertThat(store.readMetadata(ids[0])).isNull()
        assertThat(store.readMetadata(ids[2])).isNull()
        assertThat(store.readMetadata(ids[1])).isNotNull()
        assertThat(viewModel.uiState.value.isSelecting).isFalse()
        assertThat(viewModel.uiState.value.shown.map { it.id }).containsExactly(ids[1])
    }

    @Test
    fun `deleting the selection that includes the open document clears the working copy`() = runTest {
        setUpWith(backgroundScope)
        val ids = listedWith("A", "B") // B is the document being edited
        assertThat(images.documentInfo().id).isEqualTo(ids[1])
        viewModel.startSelection(ids[1])

        viewModel.deleteSelected()
        awaitUntil { viewModel.uiState.value.shown.size == 1 }

        assertThat(images.pages()).isEmpty()
        assertThat(store.readMetadata(ids[1])).isNull()
    }

    @Test
    fun `the search also finds a document by its recognized text`() = runTest {
        setUpWith(backgroundScope)
        val first = addDocument(1)
        library.startNewDocument()
        addDocument(2)
        val page = store.readMetadata(first)!!.pages.single()
        store.writeOcr(first, page.id, PageOcrV1(renderKey = "0badc0de", imageWidth = 10, imageHeight = 10,
            words = listOf(OcrWordV1("Hipoteca", 0, 0, 5, 5, 5, 5), OcrWordV1("Santander", 6, 0, 9, 5, 5, 5))))
        viewModel.refresh()
        awaitUntil { !viewModel.uiState.value.isLoading }

        viewModel.openSearch()
        viewModel.onQueryChange("hipoteca")
        awaitUntil { viewModel.uiState.value.shown.map { it.id } == listOf(first) }

        assertThat(viewModel.uiState.value.shown.map { it.id }).containsExactly(first)
        viewModel.onQueryChange("zzz")
        assertThat(viewModel.uiState.value.shown).isEmpty()
        viewModel.closeSearch()
        assertThat(viewModel.uiState.value.shown).hasSize(2)
    }
}
