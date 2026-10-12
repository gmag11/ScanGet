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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.data.ImageTransformations
import org.fairscan.app.data.Logger
import org.fairscan.app.domain.EncodedImage
import org.fairscan.app.domain.Jpeg
import org.fairscan.app.domain.PageMetadata
import org.fairscan.app.domain.Rotation
import org.fairscan.imageprocessing.Adjustments
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.ColorMode.COLOR
import org.fairscan.imageprocessing.ColorMode.NO_SHADOWS
import org.fairscan.imageprocessing.ImageSize
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val quad = Quad(Point(.01, .02), Point(.1, .03), Point(.11, .12), Point(.03, .09))
    private val metadata = PageMetadata(quad, Rotation.R0, COLOR, ImageSize(1600, 1200), null)

    // What each fake render saw: the page (identified by its source byte), the loading pages at
    // that moment, and how many renders were running.
    private class Render(val page: Int, val loading: Set<String>, val concurrent: Int)

    private val renders = mutableListOf<Render>()
    private val running = AtomicInteger()
    private var maxConcurrent = 0
    private val errors = mutableListOf<Throwable>()
    private var failOnPage: Int? = null
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        if (this::viewModel.isInitialized) viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MainViewModel {
        val transformations = object : ImageTransformations {
            override fun rotate(input: EncodedImage, rotationDegrees: Int): EncodedImage = input
            override fun resizeToThumbnail(input: EncodedImage): Jpeg = Jpeg(byteArrayOf(input.bytes[0]))
            override fun process(
                source: Jpeg,
                metadata: PageMetadata,
                colorMode: ColorMode,
                adjustments: Adjustments,
            ): Jpeg {
                val page = source.bytes[0].toInt()
                val concurrent = running.incrementAndGet()
                try {
                    maxConcurrent = maxOf(maxConcurrent, concurrent)
                    renders += Render(page, viewModel.loadingPageIds.value, concurrent)
                    if (page == failOnPage) throw IllegalStateException("cannot render page $page")
                    return Jpeg(byteArrayOf(page.toByte(), colorMode.ordinal.toByte()))
                } finally {
                    running.decrementAndGet()
                }
            }
        }
        // As in FairScanApp: a failing job must not cancel the scope of the repository.
        val scope = CoroutineScope(SupervisorJob())
        val repository = ImageRepository(folder.newFolder("files"), transformations, scope, Logger { _, _, _ -> })
        viewModel = MainViewModel(repository, Logger { _, _, e -> if (e != null) errors += e })
        return viewModel
    }

    // Adds pages whose source photo starts with 1, 2, 3...; returns their ids in order.
    private suspend fun addPages(count: Int): List<String> {
        val repository = viewModel.imageRepository
        repeat(count) { i ->
            repository.add(Jpeg(byteArrayOf(i.toByte())), Jpeg(byteArrayOf((i + 1).toByte())), metadata, COLOR)
            Thread.sleep(2) // ids are timestamps
        }
        return repository.pages().map { it.id }
    }

    @Test
    fun apply_to_all_gives_every_page_the_same_filter_and_adjustments() = runTest {
        createViewModel()
        val ids = addPages(3)
        val adjustments = Adjustments(detail = 40, contrast = 15)

        viewModel.applyRenderingToAll(ids[1], NO_SHADOWS, adjustments)

        val pages = viewModel.imageRepository.pages()
        assertThat(pages.map { it.id }).isEqualTo(ids)
        assertThat(pages.map { it.colorMode }).containsOnly(NO_SHADOWS)
        assertThat(pages.map { it.adjustments }).containsOnly(adjustments)
        assertThat(viewModel.loadingPageIds.value).isEmpty()
    }

    @Test
    fun apply_to_all_renders_the_current_page_first_and_one_page_at_a_time() = runTest {
        createViewModel()
        val ids = addPages(3)

        viewModel.applyRenderingToAll(ids[1], NO_SHADOWS, Adjustments(detail = 40))

        // Sources are 1, 2, 3 for pages 0, 1, 2: the second page goes first.
        assertThat(renders.map { it.page }).containsExactly(2, 1, 3)
        assertThat(maxConcurrent).isEqualTo(1)
    }

    @Test
    fun every_page_is_loading_until_its_own_rendering_is_ready() = runTest {
        createViewModel()
        val ids = addPages(3)

        viewModel.applyRenderingToAll(ids[1], NO_SHADOWS, Adjustments(detail = 40))

        assertThat(renders[0].loading).containsExactlyInAnyOrder(ids[0], ids[1], ids[2])
        assertThat(renders[1].loading).containsExactlyInAnyOrder(ids[0], ids[2])
        assertThat(renders[2].loading).containsExactly(ids[2])
        assertThat(viewModel.loadingPageIds.value).isEmpty()
    }

    @Test
    fun a_page_that_fails_is_logged_and_the_others_are_still_rendered() = runTest {
        createViewModel()
        val ids = addPages(3)
        failOnPage = 1 // the first page

        viewModel.applyRenderingToAll(ids[1], NO_SHADOWS, Adjustments(detail = 40))

        val pages = viewModel.imageRepository.pages().associateBy { it.id }
        assertThat(pages.getValue(ids[1]).colorMode).isEqualTo(NO_SHADOWS)
        assertThat(pages.getValue(ids[2]).colorMode).isEqualTo(NO_SHADOWS)
        assertThat(pages.getValue(ids[0]).colorMode).isEqualTo(COLOR)
        // Other logged errors come from decoding bitmaps, which the JVM cannot do.
        assertThat(errors.filter { it.message == "cannot render page 1" }).hasSize(1)
        assertThat(viewModel.loadingPageIds.value).isEmpty()
    }

    @Test
    fun apply_to_all_with_unknown_first_page_still_processes_the_document() = runTest {
        createViewModel()
        val ids = addPages(2)

        viewModel.applyRenderingToAll("unknown", NO_SHADOWS, Adjustments(contrast = 20))

        val pages = viewModel.imageRepository.pages()
        assertThat(pages.map { it.id }).isEqualTo(ids)
        assertThat(pages.map { it.colorMode }).containsOnly(NO_SHADOWS)
        assertThat(viewModel.loadingPageIds.value).isEmpty()
    }
}
