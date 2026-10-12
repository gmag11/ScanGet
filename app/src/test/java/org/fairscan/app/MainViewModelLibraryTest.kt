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
import kotlinx.coroutines.cancel
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
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        if (this::viewModel.isInitialized) viewModel.viewModelScope.cancel()
        if (this::scope.isInitialized) scope.cancel()
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
}
