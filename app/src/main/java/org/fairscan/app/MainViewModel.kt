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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.data.Logger
import org.fairscan.app.data.library.LibraryRepository
import org.fairscan.app.data.library.LibraryStatus
import org.fairscan.app.domain.CapturedPage
import org.fairscan.app.domain.Rotation
import org.fairscan.app.domain.ScanPage
import org.fairscan.app.platform.PreviewRenderer
import org.fairscan.app.ui.NavigationState
import org.fairscan.app.ui.initialNavigationState
import org.fairscan.app.ui.Screen
import org.fairscan.app.ui.screens.crop.CropInitState
import org.fairscan.app.ui.screens.document.CurrentPageUiState
import org.fairscan.app.ui.screens.document.AdjustmentSession
import org.fairscan.app.ui.screens.document.AdjustmentState
import org.fairscan.app.ui.screens.document.DocumentUiState
import org.fairscan.app.ui.state.DocumentUiModel
import org.fairscan.app.ui.state.PageThumbnail
import org.fairscan.imageprocessing.AdjustmentControl
import org.fairscan.imageprocessing.Adjustments
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.ImageSize
import org.fairscan.imageprocessing.Quad
import kotlin.math.min

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    val imageRepository: ImageRepository,
    private val logger: Logger,
    // Where documents are kept; null when they are not (scan requested by another app)
    private val library: LibraryRepository? = null,
    // State of the library location; null when there is no library to set up
    private val libraryStatus: StateFlow<LibraryStatus?>? = null,
): ViewModel() {

    private val _navigationState = MutableStateFlow<NavigationState?>(null)
    val currentScreen: StateFlow<Screen?> = _navigationState.map { it?.current }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _pages = MutableStateFlow<List<ScanPage>>(emptyList())


    init {
        viewModelScope.launch {
            val pages = imageRepository.pages()

            _pages.value = pages

            // Wait until the library location is known
            val status = libraryStatus?.filterNotNull()?.first()
            _navigationState.value = initialNavigationState(pages.isNotEmpty(), status)
        }
        // Once the library folder is chosen, the setup screen gives way to the usual first screen
        libraryStatus?.let { statuses ->
            viewModelScope.launch {
                statuses.collect { status ->
                    if (status is LibraryStatus.Ready && _navigationState.value?.current == Screen.Main.LibrarySetup) {
                        _navigationState.value = initialNavigationState(_pages.value.isNotEmpty(), status)
                    }
                }
            }
        }
    }

    val documentUiModel: StateFlow<DocumentUiModel> =
        _pages.map { pages ->
            pages.map {
                val jpeg = imageRepository.getThumbnail(it.key())
                PageThumbnail(it.key(), jpeg)
            }.toImmutableList()
        }
        .flowOn(Dispatchers.IO)
        .map { DocumentUiModel(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DocumentUiModel()
        )

    private val _currentPageIndex = MutableStateFlow(0)
    // Pages that are being rendered. Several can be at once with "apply to all".
    private val _loadingPageIds = MutableStateFlow<Set<String>>(emptySet())
    val loadingPageIds: StateFlow<Set<String>> = _loadingPageIds
    private val currentPageUiState: Flow<CurrentPageUiState?> =
        combine(_currentPageIndex, _pages, _loadingPageIds) { index, pages, loadingIds ->
            val page = pages.getOrNull(index)
            Pair(page, loadingIds)
        }
            .mapLatest { (page, loadingIds) ->
                page?.let {
                    val isLoading = it.id in loadingIds
                    val canBeCropped = page.metadata != null
                    val bitmap = try {
                         imageRepository.image(it.key())?.toBitmap()
                    } catch (e: Exception) {
                        logger.e("MainViewModel", "Failed to load image for ${it.id}", e)
                        null
                    }
                    CurrentPageUiState(it.key(), bitmap, it.colorMode, canBeCropped, isLoading)
                }
            }
            .flowOn(Dispatchers.IO)

    val documentUiState: StateFlow<DocumentUiState> =
        combine(_currentPageIndex, currentPageUiState, documentUiModel) { index, page, document ->
            DocumentUiState(index, page, document)
        }
            .stateIn(
                viewModelScope, SharingStarted.Eagerly,
                DocumentUiState(0, null, DocumentUiModel())
            )

    fun onPageSelected(index: Int) {
        _currentPageIndex.value = index
    }

    fun navigateTo(destination: Screen) {
        // The adjustments panel belongs to the Document screen.
        if (destination !is Screen.Main.Document) cancelAdjusting()
        if (destination is Screen.Main.Document) {
            require(_pages.value.isNotEmpty()) {
                "Cannot navigate to DocumentScreen with zero pages"
            }
            _currentPageIndex.value = min(_pages.value.size - 1, destination.initialPage)
        }
        _navigationState.update { it?.navigateTo(destination) }
    }

    fun navigateBack() {
        cancelAdjusting()
        _navigationState.update { stack -> stack?.navigateBack() }
    }

    fun rotateCurrentPage(clockwise: Boolean) {
        viewModelScope.launch {
            val pages = withContext(Dispatchers.IO) {
                imageRepository.rotate(currentPage().id, clockwise)
                imageRepository.pages()
            }
            _pages.value = pages
        }
    }

    fun movePage(id: String, newIndex: Int) {
        viewModelScope.launch {
            val pages = withContext(Dispatchers.IO) {
                imageRepository.movePage(id, newIndex)
                imageRepository.pages()
            }
            _pages.value = pages
        }
    }

    fun deleteCurrentPage() {
        viewModelScope.launch {
            val pages = withContext(Dispatchers.IO) {
                imageRepository.delete(currentPage().id)
                imageRepository.pages()
            }

            if (pages.isEmpty()) {
                navigateTo(Screen.Main.Camera)
                _currentPageIndex.value = 0
            } else if (_currentPageIndex.value >= pages.size) {
                _currentPageIndex.value = pages.size - 1
            }
            _pages.value = pages
        }
    }

    fun setCurrentPageColorMode(colorMode: ColorMode) {
        viewModelScope.launch {
            val currentPage = currentPage()
            if (currentPage.colorMode != colorMode) {
                _loadingPageIds.update { it + currentPage.id }
                val pages = withContext(Dispatchers.IO) {
                    imageRepository.setRendering(currentPage.id, colorMode, Adjustments.NEUTRAL)
                    imageRepository.pages()
                }
                _pages.value = pages
                _loadingPageIds.update { it - currentPage.id }
            }
        }
    }

    // --- Adjustments panel ---

    private val _adjustmentState = MutableStateFlow<AdjustmentState<Bitmap>?>(null)
    /** Null when the adjustments panel is not open. */
    val adjustmentState: StateFlow<AdjustmentState<Bitmap>?> = _adjustmentState

    private var adjustmentSession: AdjustmentSession<Bitmap>? = null
    private var previewRenderer: PreviewRenderer? = null
    private var adjustmentJob: Job? = null
    private var adjustingPageId: String? = null

    /** Opens the adjustments panel for the current page. */
    fun startAdjusting() {
        if (adjustingPageId != null) return
        val page = currentPage()
        if (page.id in _loadingPageIds.value) return
        val metadata = page.metadata ?: return
        val colorMode = page.colorMode ?: return
        adjustingPageId = page.id
        adjustmentJob = viewModelScope.launch {
            val source = withContext(Dispatchers.IO) { imageRepository.source(page.id) }
            if (source == null) {
                adjustingPageId = null
                return@launch
            }
            val renderer = PreviewRenderer(source, metadata, page.manualRotation)
            val session = AdjustmentSession<Bitmap>(
                colorMode = colorMode,
                initial = page.adjustments,
                scope = viewModelScope,
                onError = { logger.e("MainViewModel", "Failed to render the preview of ${page.id}", it) },
            ) { mode, adjustments -> renderer.render(mode, adjustments) }
            previewRenderer = renderer
            adjustmentSession = session
            launch { session.state.collect { _adjustmentState.value = it } }
            session.start()
        }
    }

    fun setAdjustment(control: AdjustmentControl, value: Int) {
        adjustmentSession?.set(control, value)
    }

    fun resetAdjustments() {
        adjustmentSession?.reset()
    }

    /** Closes the panel and keeps the page as it was. */
    fun cancelAdjusting() {
        adjustmentSession?.cancel()
        closeAdjustmentSession()
    }

    /** Closes the panel and renders the page at full resolution with the chosen adjustments. */
    fun confirmAdjusting() {
        val session = adjustmentSession
        val pageId = adjustingPageId
        if (session == null || pageId == null) {
            closeAdjustmentSession()
            return
        }
        val adjustments = session.confirm()
        closeAdjustmentSession()
        val page = _pages.value.firstOrNull { it.id == pageId } ?: return
        val colorMode = page.colorMode ?: return
        if (adjustments == page.adjustments.forMode(colorMode)) return
        viewModelScope.launch {
            _loadingPageIds.update { it + pageId }
            val pages = withContext(Dispatchers.IO) {
                imageRepository.setRendering(pageId, colorMode, adjustments)
                imageRepository.pages()
            }
            _pages.value = pages
            _loadingPageIds.update { it - pageId }
        }
    }

    /**
     * Closes the panel and gives every page of the document the filter and adjustments of the
     * current one. The current page is rendered first.
     */
    fun applyAdjustmentsToAll() {
        val session = adjustmentSession
        val pageId = adjustingPageId
        if (session == null || pageId == null) {
            closeAdjustmentSession()
            return
        }
        val adjustments = session.confirm()
        closeAdjustmentSession()
        val colorMode = _pages.value.firstOrNull { it.id == pageId }?.colorMode ?: return
        viewModelScope.launch { applyRenderingToAll(pageId, colorMode, adjustments) }
    }

    /**
     * Renders every page with [colorMode] and [adjustments], one after the other, [firstPageId]
     * first. Each page shows as loading until its rendering is ready. A page that fails is logged
     * and the others still get processed.
     */
    suspend fun applyRenderingToAll(firstPageId: String, colorMode: ColorMode, adjustments: Adjustments) {
        val ids = withContext(Dispatchers.IO) { imageRepository.pages() }.map { it.id }
        val ordered = listOf(firstPageId) + ids.filter { it != firstPageId }
        _loadingPageIds.update { it + ordered }
        try {
            for (id in ordered) {
                try {
                    _pages.value = withContext(Dispatchers.IO) {
                        imageRepository.setRendering(id, colorMode, adjustments)
                        imageRepository.pages()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e("MainViewModel", "Failed to apply the rendering to $id", e)
                } finally {
                    _loadingPageIds.update { it - id }
                }
            }
        } finally {
            _loadingPageIds.update { it - ordered.toSet() }
        }
    }

    private fun closeAdjustmentSession() {
        adjustmentSession?.close()
        adjustmentJob?.cancel()
        previewRenderer?.close()
        adjustmentSession = null
        adjustmentJob = null
        previewRenderer = null
        adjustingPageId = null
        _adjustmentState.value = null
    }

    override fun onCleared() {
        closeAdjustmentSession()
    }

    fun setCurrentPageUserQuad(userQuad: Quad) {
        viewModelScope.launch {
            val currentPage = currentPage()
            val totalRotation = currentPage.totalRotation()
            val rotateIterations = (4 - totalRotation.degrees / 90) % 4
            val newQuad = userQuad.rotate90(rotateIterations, ImageSize(1, 1))
            _loadingPageIds.update { it + currentPage.id }
            val pages = withContext(Dispatchers.IO) {
                imageRepository.setUserQuad(currentPage.id, newQuad)
                imageRepository.pages()
            }
            _pages.value = pages
            _loadingPageIds.update { it - currentPage.id }
        }
    }

    private fun currentPage(): ScanPage {
        val index = _currentPageIndex.value
        val pages = _pages.value
        return pages.getOrNull(index) ?: throw IllegalStateException(
            "No current page for index $index (${pages.size} pages)")
    }

    /**
     * Leaves the current document, which stays in the library, and starts an empty one. If the
     * document cannot be saved because the library folder is not available, it is kept and the
     * setup screen is shown instead.
     */
    fun startNewDocument() {
        viewModelScope.launch {
            if (library != null && !library.canStartNewDocument()) {
                navigateTo(Screen.Main.LibrarySetup)
                return@launch
            }
            _pages.value = persistentListOf()
            withContext(Dispatchers.IO) {
                library?.startNewDocument() ?: imageRepository.clear()
            }
        }
    }

    /** Makes the saved document [id] the current one; [onOpened] is called once it is. */
    fun openDocument(id: String, onOpened: () -> Unit = {}) {
        cancelAdjusting()
        viewModelScope.launch {
            val opened = withContext(Dispatchers.IO) { library?.open(id) ?: false }
            if (opened) {
                _pages.value = imageRepository.pages()
                _currentPageIndex.value = 0
                onOpened()
            }
        }
    }

    fun handleImageCaptured(capturedPage: CapturedPage) {
        viewModelScope.launch {
            val pages = withContext(Dispatchers.IO) {
                val sourceJpeg = capturedPage.sourceJpeg.await()
                imageRepository.add(
                    capturedPage.pageImage,
                    sourceJpeg,
                    capturedPage.metadata,
                    capturedPage.colorMode,
                )
                imageRepository.pages()
            }
            _pages.value = pages
        }
    }

    private val _cropInitState = MutableStateFlow<CropInitState>(CropInitState.Loading)
    val cropInitState: StateFlow<CropInitState> = _cropInitState

    private var cropInitialStateJob: Job? = null
    fun onClickOnCropButton() {
        cropInitialStateJob?.cancel()
        cropInitialStateJob = viewModelScope.launch {
            _cropInitState.value = CropInitState.Loading

            val page = currentPage()

            val metadata = page.metadata
            val rotation = page.totalRotation()

            val bitmap = withContext(Dispatchers.IO) {
                val source = imageRepository.source(page.id)
                val bytes = source?.bytes ?: return@withContext null

                val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (original != null && rotation != Rotation.R0) {
                    val matrix = Matrix().apply { postRotate(rotation.degrees.toFloat()) }
                    Bitmap.createBitmap(
                        original, 0, 0, original.width, original.height, matrix, true
                    )
                } else {
                    original
                }
            }

            val quad = metadata?.normalizedQuad?.rotate90(
                rotation.degrees / 90,
                ImageSize(1, 1)
            )

            _cropInitState.value = if (bitmap == null || quad == null)
                CropInitState.Error
            else
                CropInitState.Ready(page.id, bitmap, quad)
            navigateTo(Screen.Main.EditImage)
        }

    }
}
