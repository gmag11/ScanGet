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
import org.fairscan.app.data.library.FileLibraryStore
import org.fairscan.app.data.library.LastOpenedDocument
import org.fairscan.app.data.library.LibraryEntry
import org.fairscan.app.data.library.LibraryRepository
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
}
