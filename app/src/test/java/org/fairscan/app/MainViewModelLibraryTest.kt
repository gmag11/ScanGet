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
package org.fairscan.app

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.data.ImageTransformations
import org.fairscan.app.data.Logger
import org.fairscan.app.data.library.FileLibraryStore
import org.fairscan.app.data.library.LibraryRepository
import org.fairscan.app.domain.EncodedImage
import org.fairscan.app.domain.Jpeg
import org.fairscan.app.domain.PageMetadata
import org.fairscan.app.domain.Rotation
import org.fairscan.app.ui.Screen
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

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelLibraryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val quad = Quad(Point(.01, .02), Point(.1, .03), Point(.11, .12), Point(.03, .09))
    private val metadata = PageMetadata(quad, Rotation.R0, COLOR, ImageSize(1600, 1200), null)
    private val libraryDir by lazy { folder.newFolder("library") }
    private val store by lazy { FileLibraryStore(libraryDir) }
    private lateinit var viewModel: MainViewModel
    private var libraryRepository: LibraryRepository? = null
    private fun library() = libraryRepository!!
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        // Coroutines of the view model may still be finishing on IO threads: let them end while Main
        // exists, or they fail when they try to resume on it and the failure lands on another test
        Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        kotlinx.coroutines.runBlocking {
            if (this@MainViewModelLibraryTest::viewModel.isInitialized) {
                viewModel.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
            }
            if (this@MainViewModelLibraryTest::scope.isInitialized) {
                scope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
            }
        }
        Dispatchers.resetMain()
    }

    private fun createViewModel(withLibrary: Boolean = true): MainViewModel {
        val transformations = object : ImageTransformations {
            override fun rotate(input: EncodedImage, rotationDegrees: Int): EncodedImage = input
            override fun resizeToThumbnail(input: EncodedImage): Jpeg = Jpeg(byteArrayOf(input.bytes[0]))
            override fun process(source: Jpeg, metadata: PageMetadata, colorMode: ColorMode,
                                 adjustments: Adjustments): Jpeg = Jpeg(byteArrayOf(source.bytes[0], 1))
        }
        scope = CoroutineScope(SupervisorJob())
        val logger = Logger { _, _, _ -> }
        val repository = ImageRepository(folder.newFolder(), transformations, scope, logger)
        val library = if (withLibrary) LibraryRepository({ store }, repository, scope, logger) else null
        libraryRepository = library
        viewModel = MainViewModel(repository, logger, library)
        return viewModel
    }

    private suspend fun addPages(count: Int) {
        repeat(count) { i ->
            viewModel.imageRepository.add(
                Jpeg(byteArrayOf(i.toByte())), Jpeg(byteArrayOf((i + 1).toByte())), metadata, COLOR)
            Thread.sleep(2) // ids are timestamps
        }
    }

    // The view model works on real IO threads, which the virtual time does not wait for
    private suspend fun TestScope.awaitUntil(condition: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            advanceUntilIdle()
            check(System.currentTimeMillis() < deadline) { "Condition not reached" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `starting a new document keeps the previous one in the library`() = runTest {
        createViewModel()
        addPages(2)
        val firstId = viewModel.imageRepository.documentInfo().id

        viewModel.startNewDocument()
        awaitUntil { viewModel.imageRepository.documentInfo().id != firstId }

        assertThat(viewModel.imageRepository.pages()).isEmpty()
        assertThat(store.readMetadata(firstId)!!.pages).hasSize(2)
        assertThat(store.sourceIds(firstId)).hasSize(2)
    }

    @Test
    fun `opening a document makes it the current one`() = runTest {
        createViewModel()
        addPages(3)
        val firstId = viewModel.imageRepository.documentInfo().id
        viewModel.startNewDocument()
        awaitUntil { viewModel.imageRepository.documentInfo().id != firstId }
        addPages(1)
        var opened = false

        viewModel.openDocument(firstId) { opened = true }
        awaitUntil { opened && viewModel.documentUiModel.value.pageCount() == 3 }

        assertThat(opened).isTrue()
        assertThat(viewModel.imageRepository.documentInfo().id).isEqualTo(firstId)
        assertThat(viewModel.documentUiModel.value.pageCount()).isEqualTo(3)
        assertThat(viewModel.documentUiState.value.currentPageIndex).isEqualTo(0)
    }

    @Test
    fun `opening a document that does not exist changes nothing`() = runTest {
        createViewModel()
        addPages(1)
        val id = viewModel.imageRepository.documentInfo().id
        var opened = false

        viewModel.openDocument("unknown") { opened = true }
        awaitUntil { viewModel.imageRepository.pages().isNotEmpty() }
        Thread.sleep(100) // nothing to wait for: give the (non-)opening time to happen
        advanceUntilIdle()

        assertThat(opened).isFalse()
        assertThat(viewModel.imageRepository.documentInfo().id).isEqualTo(id)
        assertThat(viewModel.imageRepository.pages()).hasSize(1)
    }

    @Test
    fun `without a library a new document just replaces the current one`() = runTest {
        createViewModel(withLibrary = false)
        addPages(2)

        val firstId = viewModel.imageRepository.documentInfo().id
        viewModel.startNewDocument()
        awaitUntil { viewModel.imageRepository.documentInfo().id != firstId }

        assertThat(viewModel.imageRepository.pages()).isEmpty()
        assertThat(libraryDir.list()!!).isEmpty()
    }

    @Test
    fun `deleting the open document empties the editor and deleting another leaves it`() = runTest {
        createViewModel()
        addPages(2)
        val openId = viewModel.imageRepository.documentInfo().id
        viewModel.startNewDocument()
        awaitUntil { viewModel.imageRepository.documentInfo().id != openId }
        addPages(1)
        val current = viewModel.imageRepository.documentInfo().id
        viewModel.imageRepository.pages() // the working document, not yet in the library
        library().flush()

        viewModel.deleteDocument(openId) // another document
        assertThat(store.readMetadata(openId)).isNull()
        assertThat(viewModel.imageRepository.pages()).hasSize(1)

        viewModel.deleteDocument(current) // the one being edited
        awaitUntil { viewModel.documentUiModel.value.pageCount() == 0 }
        assertThat(store.readMetadata(current)).isNull()
        assertThat(viewModel.imageRepository.pages()).isEmpty()
    }

    @Test
    fun `what the library asks of the next screen is used once`() = runTest {
        createViewModel()

        assertThat(viewModel.consumeImportOnCameraOpen()).isFalse()
        assertThat(viewModel.consumeExportAllPages()).isFalse()

        viewModel.requestImportOnCameraOpen()
        viewModel.requestExportAllPages()
        assertThat(viewModel.consumeImportOnCameraOpen()).isTrue()
        assertThat(viewModel.consumeImportOnCameraOpen()).isFalse()
        assertThat(viewModel.consumeExportAllPages()).isTrue()
        assertThat(viewModel.consumeExportAllPages()).isFalse()
    }

    @Test
    fun `opening shows that a document is being opened until it is`() = runTest {
        createViewModel()
        addPages(2)
        val id = viewModel.imageRepository.documentInfo().id
        viewModel.startNewDocument()
        awaitUntil { viewModel.imageRepository.documentInfo().id != id }
        var opened = false

        viewModel.openDocument(id) { opened = true }
        awaitUntil { opened && !viewModel.isOpeningDocument.value }

        assertThat(viewModel.isOpeningDocument.value).isFalse()
        assertThat(viewModel.documentUiState.value.currentPageIndex).isEqualTo(0)
    }

    @Test
    fun `the name of the document being edited is known and follows renaming`() = runTest {
        createViewModel()
        addPages(1)
        library().flush()
        val id = viewModel.imageRepository.documentInfo().id
        viewModel.startNewDocument()
        awaitUntil { viewModel.imageRepository.documentInfo().id != id }
        awaitUntil { viewModel.documentName.value == viewModel.imageRepository.documentInfo().name }
        val generated = viewModel.documentName.value
        assertThat(generated).isNotEmpty()

        assertThat(viewModel.renameDocument("  Tax 2026  ")).isTrue()
        awaitUntil { viewModel.documentName.value == "Tax 2026" }

        assertThat(viewModel.imageRepository.documentInfo().name).isEqualTo("Tax 2026")
    }

    @Test
    fun `a blank name does not rename the document`() = runTest {
        createViewModel()
        addPages(1)
        awaitUntil { viewModel.documentName.value.isNotEmpty() }
        val before = viewModel.documentName.value

        assertThat(viewModel.renameDocument("")).isFalse()
        assertThat(viewModel.renameDocument("   ")).isFalse()

        assertThat(viewModel.imageRepository.documentInfo().name).isEqualTo(before)
        assertThat(viewModel.documentName.value).isEqualTo(before)
    }

    @Test
    fun `opening a document shows its name`() = runTest {
        createViewModel()
        addPages(1)
        val first = viewModel.imageRepository.documentInfo().id
        viewModel.renameDocument("Receipts")
        awaitUntil { viewModel.documentName.value == "Receipts" }
        library().flush()
        viewModel.startNewDocument()
        awaitUntil { viewModel.imageRepository.documentInfo().id != first }
        awaitUntil { viewModel.documentName.value != "Receipts" }
        var opened = false

        viewModel.openDocument(first) { opened = true }
        awaitUntil { opened && viewModel.documentName.value == "Receipts" }

        assertThat(viewModel.documentName.value).isEqualTo("Receipts")
    }

    @Test
    fun `deleting the document being edited removes it and shows the library`() = runTest {
        createViewModel()
        addPages(2)
        library().flush()
        val id = viewModel.imageRepository.documentInfo().id

        viewModel.deleteCurrentDocument()
        awaitUntil { viewModel.currentScreen.value == Screen.Main.Library }

        assertThat(store.readMetadata(id)).isNull()
        assertThat(viewModel.imageRepository.pages()).isEmpty()
        assertThat(viewModel.imageRepository.documentInfo().id).isNotEqualTo(id)
    }

    @Test
    fun `the view model can be created when its first coroutines run at once, as on Android`() = runTest {
        // On Android viewModelScope runs on Main.immediate: the coroutines launched while the view model
        // is being built start before the end of its constructor, and use state declared further down
        Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())

        createViewModel()

        awaitUntil { viewModel.documentName.value.isNotEmpty() }
        assertThat(viewModel.documentName.value).isNotEmpty()
    }
}
