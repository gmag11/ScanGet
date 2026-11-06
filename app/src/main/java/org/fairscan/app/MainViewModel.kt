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
import org.fairscan.app.data.DocumentInfo
import org.fairscan.app.data.library.LibraryEntry
import org.fairscan.app.data.library.LibraryRepository
import org.fairscan.app.data.library.LibraryUnavailableException
import org.fairscan.app.data.library.LibraryStatus
import org.fairscan.app.domain.CapturedPage
import org.fairscan.app.domain.Rotation
import org.fairscan.app.domain.PageViewKey
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

private const val TAG = "MainViewModel"

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

    private val _documentName = MutableStateFlow("")

    /** The name of the document being edited. */
    val documentName: StateFlow<String> = _documentName


    init {
        viewModelScope.launch {
            val pages = imageRepository.pages()

            _pages.value = pages
            refreshDocumentName()

            // Wait until the library location is known
            val status = libraryStatus?.filterNotNull()?.first()
            _navigationState.value = initialNavigationState(status)
        }
        // Once the library folder is chosen, the setup screen gives way to the usual first screen
        libraryStatus?.let { statuses ->
            viewModelScope.launch {
                statuses.collect { status ->
                    if (status is LibraryStatus.Ready && _navigationState.value?.current == Screen.Main.LibrarySetup) {
                        _navigationState.value = initialNavigationState(status)
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
            Triple(pages, index, loadingIds)
        }
            .mapLatest { (pages, index, loadingIds) ->
                pages.getOrNull(index)?.let {
                    val isLoading = it.id in loadingIds
                    val canBeCropped = it.metadata != null
                    val bitmap = loadBitmap(it)
                    // The neighbours are loaded too, so they are already there when the user swipes
                    CurrentPageUiState(
                        it.key(), bitmap, it.colorMode, canBeCropped, isLoading,
                        index = index,
                        previousBitmap = pages.getOrNull(index - 1)?.let { loadBitmap(it) },
                        nextBitmap = pages.getOrNull(index + 1)?.let { loadBitmap(it) },
                    )
                }
            }
            .flowOn(Dispatchers.IO)

    private suspend fun loadBitmap(page: ScanPage): Bitmap? = try {
        imageRepository.image(page.key())?.toBitmap()
    } catch (e: Exception) {
        logger.e("MainViewModel", "Failed to load image for ${page.id}", e)
        null
    }

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
        _navigationState.update { stack -> stack?.navigateBack(hasPages = _pages.value.isNotEmpty()) }
        // Back to the editor from elsewhere shows a page that exists
        val pageCount = _pages.value.size
        if (_navigationState.value?.current is Screen.Main.Document && pageCount > 0) {
            _currentPageIndex.value = min(pageCount - 1, _currentPageIndex.value)
        }
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
            refreshDocumentName()
        }
    }

    private suspend fun refreshDocumentName() {
        _documentName.value = imageRepository.documentInfo().name
    }

    /** Renames the document being edited. A blank name is not accepted; returns whether it was. */
    fun renameDocument(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        viewModelScope.launch {
            val id = imageRepository.documentInfo().id
            library?.rename(id, trimmed) ?: imageRepository.rename(trimmed)
            refreshDocumentName()
        }
        return true
    }

    /** Deletes the document being edited and goes back to the library. */
    fun deleteCurrentDocument() {
        viewModelScope.launch {
            deleteDocument(imageRepository.documentInfo().id)
            navigateTo(Screen.Main.Library)
        }
    }

    // --- Page selection actions (document overview) ---

    private val _pageTransfer = MutableStateFlow<PageTransferState>(PageTransferState.Idle)

    /** What the page actions of the overview are doing, or have just done. */
    val pageTransfer: StateFlow<PageTransferState> = _pageTransfer

    /** The result of the last page action was shown. */
    fun pageTransferShown() {
        if (_pageTransfer.value !is PageTransferState.Running) _pageTransfer.value = PageTransferState.Idle
    }

    /** The saved documents other than the open one, to choose where to copy or move pages. */
    suspend fun otherDocuments(): List<LibraryEntry> = library?.otherDocuments().orEmpty()

    /** Removes the pages [ids] from the open document; without pages left, the document goes too. */
    fun deletePages(ids: Set<String>) = runPageTransfer {
        removePages(ids)
        PageTransferState.Done(PageTransferState.Action.DELETED, ids.size, null)
    }

    /** Adds copies of the pages [ids] to the document [targetId], or to a new one if it is null. */
    fun copyPages(ids: Set<String>, targetId: String?) = runPageTransfer {
        val target = appendPagesTo(ids, targetId)
        PageTransferState.Done(PageTransferState.Action.COPIED, ids.size, target.name)
    }

    /** Like [copyPages], and then removes the pages from the open document. */
    fun movePages(ids: Set<String>, targetId: String?) = runPageTransfer {
        val target = appendPagesTo(ids, targetId)
        removePages(ids)
        PageTransferState.Done(PageTransferState.Action.MOVED, ids.size, target.name)
    }

    private suspend fun appendPagesTo(ids: Set<String>, targetId: String?): DocumentInfo =
        library?.appendPages(ids, targetId) ?: throw LibraryUnavailableException()

    private suspend fun removePages(ids: Set<String>) {
        val pages = withContext(Dispatchers.IO) {
            imageRepository.deletePages(ids)
            imageRepository.pages()
        }
        _pages.value = pages
        if (pages.isEmpty()) {
            deleteDocument(imageRepository.documentInfo().id)
            navigateTo(Screen.Main.Library)
        }
    }

    private fun runPageTransfer(action: suspend () -> PageTransferState) {
        if (_pageTransfer.value is PageTransferState.Running) return
        _pageTransfer.value = PageTransferState.Running
        viewModelScope.launch {
            _pageTransfer.value = try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "Page action failed", e)
                PageTransferState.Failed
            }
        }
    }

    /** A page as a thumbnail big enough for the grid of the overview. */
    suspend fun gridThumbnail(key: PageViewKey): Bitmap? = withContext(Dispatchers.IO) {
        imageRepository.getGridThumbnail(key)?.toBitmap()
    }

    private val _isOpeningDocument = MutableStateFlow(false)

    /** A document is being opened (copied from the library to be edited), which can take a moment. */
    val isOpeningDocument: StateFlow<Boolean> = _isOpeningDocument

    /** Makes the saved document [id] the current one; [onOpened] is called once it is. */
    fun openDocument(id: String, onOpened: () -> Unit = {}) {
        cancelAdjusting()
        _isOpeningDocument.value = true
        viewModelScope.launch {
            try {
                val opened = withContext(Dispatchers.IO) { library?.open(id) ?: false }
                if (opened) {
                    _pages.value = imageRepository.pages()
                    _currentPageIndex.value = 0
                    refreshDocumentName()
                    onOpened()
                }
            } finally {
                _isOpeningDocument.value = false
            }
        }
    }

    /**
     * Removes a document from the library. If it is the one being edited, the app forgets its pages
     * too, so that the next scan starts with an empty document.
     */
    suspend fun deleteDocument(id: String) {
        val library = library ?: return
        val wasCurrent = imageRepository.documentInfo().id == id
        library.delete(id)
        if (wasCurrent) {
            _pages.value = persistentListOf()
            refreshDocumentName()
        }
    }

    // Set by the library for the screen it opens next, which uses it once

    private var importOnCameraOpen = false
    private var exportAllPages = false

    /** The camera, when opened next, starts by asking which photos to import. */
    fun requestImportOnCameraOpen() { importOnCameraOpen = true }

    fun consumeImportOnCameraOpen(): Boolean = importOnCameraOpen.also { importOnCameraOpen = false }

    /** The export screen, when opened next, exports every page and not only the current one. */
    fun requestExportAllPages() { exportAllPages = true }

    fun consumeExportAllPages(): Boolean = exportAllPages.also { exportAllPages = false }

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

/** The state of a delete, copy or move of pages started from the document overview. */
sealed interface PageTransferState {
    object Idle : PageTransferState
    object Running : PageTransferState
    object Failed : PageTransferState
    enum class Action { DELETED, COPIED, MOVED }
    /** [documentName] is the document that received the pages (null when they were deleted). */
    data class Done(val action: Action, val count: Int, val documentName: String?) : PageTransferState
}
