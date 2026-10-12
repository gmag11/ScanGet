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

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.data.Logger
import org.fairscan.app.data.library.LastOpenedDocument
import org.fairscan.app.data.library.LibraryIndex
import org.fairscan.app.data.library.LibraryLocations
import org.fairscan.app.data.library.LibraryRepository
import org.fairscan.app.data.library.LibraryStatus

import org.fairscan.app.platform.ImageProcessor
import java.io.File
import java.util.Date
import java.util.UUID

class SessionViewModel(
    app: Application,
    val launchMode: LaunchMode,
    appContainer: AppContainer
) : AndroidViewModel(app) {

    private val appLogger = appContainer.logger

    val sessionDir: File = when (launchMode) {
        LaunchMode.NORMAL ->
            app.filesDir

        LaunchMode.EXTERNAL_SCAN_TO_PDF ->
            File(appContainer.sessionsRoot(), UUID.randomUUID().toString())
                .apply { mkdirs() }
    }

    private val sessionContainer = ScanSessionContainer(
        context = app,
        scanRootDir = sessionDir,
        scope = viewModelScope,
        logger = appContainer.logger,
        documentNamer = { appContainer.settingsRepository.defaultFilenameStyle.value.filename(Date(it)) },
        // A scan requested by another app is temporary: it is not kept in the library
        libraryLocations = if (launchMode == LaunchMode.NORMAL) appContainer.libraryLocations else null,
        libraryIndex = appContainer.libraryIndex,
        lastOpened = appContainer.settingsRepository,
    )

    val imageRepository: ImageRepository = sessionContainer.imageRepository

    /** Null when documents are not kept (scan requested by another app, no library location). */
    val libraryRepository: LibraryRepository? = sessionContainer.libraryRepository

    init {
        // The working document is saved to the library as soon as there is one (a document left by a
        // previous version, or by a crash, is added to it) and every time the library comes back
        if (launchMode == LaunchMode.NORMAL) {
            viewModelScope.launch {
                appContainer.libraryLocations.status.collect { status ->
                    if (status is LibraryStatus.Ready) saveToLibrary()
                }
            }
        }
    }

    /** Saves the working document to the library now, without waiting for the autosave. */
    fun saveToLibrary() {
        val library = libraryRepository ?: return
        viewModelScope.launch {
            try {
                library.flush()
            } catch (e: Exception) {
                appLogger.e("SessionViewModel", "Failed to save the working document", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()

        if (launchMode == LaunchMode.EXTERNAL_SCAN_TO_PDF) {
            sessionDir.deleteRecursively()
        }
    }
}

class ScanSessionContainer(
    context: Context,
    scanRootDir: File,
    scope: CoroutineScope,
    logger: Logger,
    documentNamer: (Long) -> String,
    libraryLocations: LibraryLocations?,
    libraryIndex: LibraryIndex,
    lastOpened: LastOpenedDocument,
) {
    private val density = context.resources.displayMetrics.density
    private val thumbnailSizePx = (THUMBNAIL_SIZE_DP * density).toInt()

    val imageRepository = ImageRepository(
        scanRootDir,
        ImageProcessor(thumbnailSizePx),
        scope,
        logger,
        documentNamer,
    )

    val libraryRepository: LibraryRepository? = libraryLocations?.let { locations ->
        LibraryRepository(
            storeProvider = locations::currentStore,
            imageRepository = imageRepository,
            scope = scope,
            logger = logger,
            index = libraryIndex,
            locationKey = locations::currentKey,
            lastOpened = lastOpened,
        )
    }
}
