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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.fairscan.app.data.DocumentMetadataV3
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.data.LoadedDocument
import org.fairscan.app.data.Logger
import org.fairscan.app.data.toInfo
import org.fairscan.app.domain.PageViewKey

private const val TAG = "LibraryRepository"
private const val MAX_ID_ATTEMPTS = 5

/** The working document has pages that cannot be saved because the library is not available. */
class LibraryUnavailableException : IllegalStateException("The library folder is not available")

/**
 * The documents scanned so far. The working document (the one [ImageRepository] holds, the only one
 * being edited) is saved to the library automatically after each change, with its source captures
 * and everything needed to render it again; the processed images stay in the working directory as a
 * cache.
 *
 * Where the library is kept can change, or be missing (not set up yet, folder no longer accessible):
 * [storeProvider] gives the current [LibraryStore], or null. While there is none nothing is saved,
 * and the working document stays as it is until there is one again: it is then written in full.
 *
 * Only one document is open at a time: [open] and [startNewDocument] save the current one and keep
 * it in the library.
 */
@OptIn(FlowPreview::class)
class LibraryRepository(
    private val storeProvider: () -> LibraryStore?,
    private val imageRepository: ImageRepository,
    scope: CoroutineScope,
    private val logger: Logger,
    debounceMillis: Long = 500,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val index: LibraryIndex? = null,
    // Identifies the current location, to keep the cached list of documents apart per location
    private val locationKey: () -> String? = { "library" },
) {
    private val mutex = Mutex()

    // The store the state below refers to; when it changes the library holds nothing of the document
    private var activeStore: LibraryStore? = null
    // What the library holds for the working document, to write only what changed
    private var lastWritten: DocumentMetadataV3? = null
    private var lastCover: Pair<String, PageViewKey>? = null

    init {
        scope.launch {
            imageRepository.changes.debounce(debounceMillis).collect {
                try {
                    flush()
                } catch (e: Exception) {
                    logger.e(TAG, "Failed to save the document", e)
                }
            }
        }
    }

    /** The current store, or null. To be called with the mutex held. */
    private fun store(): LibraryStore? {
        val store = storeProvider()
        if (store !== activeStore) {
            activeStore = store
            lastWritten = null
            lastCover = null
        }
        return store
    }

    /** The documents that have pages, most recently modified first. */
    suspend fun documents(): List<LibraryEntry> {
        flush()
        return mutex.withLock {
            withContext(ioDispatcher) {
                val store = store() ?: return@withContext emptyList()
                val entries = store.list()
                    .filter { it.pageCount > 0 }
                    .sortedByDescending { it.info.updatedAt }
                locationKey()?.let { key ->
                    try {
                        index?.write(key, entries)
                    } catch (e: Exception) {
                        logger.e(TAG, "Failed to save the index of the library", e)
                    }
                }
                entries
            }
        }
    }

    /** The documents as last listed for the current location, to show at once; may be out of date. */
    suspend fun cachedDocuments(): List<LibraryEntry> = withContext(ioDispatcher) {
        locationKey()?.let { index?.read(it) }.orEmpty()
            .filter { it.pageCount > 0 }
            .sortedByDescending { it.info.updatedAt }
    }

    suspend fun cover(id: String): ByteArray? = withContext(ioDispatcher) { storeProvider()?.readCover(id) }

    /**
     * Saves the working document now, without waiting for the autosave. A document with no page is
     * not added to the library. Does nothing while the library is not available.
     */
    suspend fun flush() {
        mutex.withLock {
            withContext(ioDispatcher) {
                val store = store() ?: return@withContext
                saveWorkingDocument(store)
            }
        }
    }

    /** Makes the saved document [id] the working document. Returns false if it does not exist. */
    suspend fun open(id: String): Boolean = mutex.withLock {
        withContext(ioDispatcher) {
            val store = store() ?: return@withContext false
            val metadata = store.readMetadata(id) ?: return@withContext false
            if (imageRepository.documentInfo().id == id) return@withContext true
            closeWorkingDocument(store)
            imageRepository.load(LoadedDocument(metadata.toInfo(), metadata.pages)) { pageId ->
                store.readSource(id, pageId)
            }
            lastWritten = metadata
            lastCover = null
            true
        }
    }

    /**
     * Whether [startNewDocument] can run without losing the working document, which is not the
     * case if it has pages that cannot be saved because the library is not available.
     */
    suspend fun canStartNewDocument(): Boolean = mutex.withLock {
        store() != null || imageRepository.snapshot().pages.isEmpty()
    }

    /** Saves the working document, which stays in the library, and starts a new empty one. */
    suspend fun startNewDocument() = mutex.withLock {
        withContext(ioDispatcher) {
            val store = store()
            if (store != null) {
                closeWorkingDocument(store)
            } else if (imageRepository.snapshot().pages.isNotEmpty()) {
                throw LibraryUnavailableException()
            }
            imageRepository.clear()
            lastWritten = null
            lastCover = null
        }
    }

    suspend fun rename(id: String, name: String) {
        if (imageRepository.documentInfo().id == id) {
            imageRepository.rename(name)
            flush()
            return
        }
        mutex.withLock {
            withContext(ioDispatcher) {
                val store = store() ?: return@withContext
                val metadata = store.readMetadata(id) ?: return@withContext
                store.writeMetadata(metadata.copy(name = name, updatedAt = clock()))
            }
        }
    }

    /** Removes the document from the library, with its captures. */
    suspend fun delete(id: String) = mutex.withLock {
        withContext(ioDispatcher) {
            val store = store() ?: return@withContext
            if (imageRepository.documentInfo().id == id) {
                imageRepository.clear()
                lastWritten = null
                lastCover = null
            }
            store.delete(id)
        }
    }

    // --- Private, to be called with the mutex held on the IO dispatcher ---

    /** Saves the working document and forgets it if it has no page. */
    private suspend fun closeWorkingDocument(store: LibraryStore) {
        saveWorkingDocument(store)
        val snapshot = imageRepository.snapshot()
        if (snapshot.pages.isEmpty()) store.delete(snapshot.id)
    }

    private suspend fun saveWorkingDocument(store: LibraryStore) {
        repeat(MAX_ID_ATTEMPTS) {
            val snapshot = imageRepository.snapshot()
            val id = snapshot.id
            val known = lastWritten?.id == id || store.readMetadata(id) != null
            if (!known) {
                if (snapshot.pages.isEmpty()) return
                if (store.isForeign(id)) {
                    // Something that is not ours already has this name: use another id
                    imageRepository.reassignDocumentId()
                    return@repeat
                }
            }
            writeSources(store, snapshot)
            if (snapshot != lastWritten) {
                store.writeMetadata(snapshot)
                lastWritten = snapshot
            }
            writeCover(store, snapshot)
            return
        }
        logger.e(TAG, "Could not find a free id for the document", IllegalStateException("No free id"))
    }

    /** Sources are written before the description, so that it never refers to a missing capture. */
    private suspend fun writeSources(store: LibraryStore, snapshot: DocumentMetadataV3) {
        val stored = store.sourceIds(snapshot.id)
        val pageIds = snapshot.pages.map { it.id }.toSet()
        for (pageId in pageIds - stored) {
            val source = imageRepository.source(pageId)
            if (source == null) {
                logger.e(TAG, "No source to save for page $pageId", IllegalStateException("Missing source"))
            } else {
                store.writeSource(snapshot.id, pageId, source.bytes)
            }
        }
        for (pageId in stored - pageIds) store.deleteSource(snapshot.id, pageId)
    }

    private suspend fun writeCover(store: LibraryStore, snapshot: DocumentMetadataV3) {
        val first = imageRepository.pages().firstOrNull() ?: return
        val marker = snapshot.id to first.key()
        if (marker == lastCover) return
        val cover = imageRepository.getThumbnail(first.key()) ?: return
        store.writeCover(snapshot.id, cover.bytes)
        lastCover = marker
    }
}
