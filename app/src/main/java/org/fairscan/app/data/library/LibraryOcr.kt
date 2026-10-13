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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fairscan.app.data.Logger
import org.fairscan.app.data.PageRecognition
import org.fairscan.app.data.toRecognition
import org.fairscan.app.domain.Jpeg
import org.fairscan.app.domain.PageOcrSource
import org.fairscan.app.domain.Png
import org.fairscan.app.domain.ScanPage
import org.fairscan.app.domain.TextRecognizer

private const val TAG = "LibraryOcr"

/**
 * The text recognized in the pages of the library: recognizes the pages that are saved, one at a
 * time in the background, and keeps the result with the document.
 *
 * What is kept is only valid for the rendering of the page it was computed from (see
 * [org.fairscan.app.domain.PageViewKey.renderKey]): when the page is edited it is dropped and, if
 * [enabled], recognized again.
 */
class LibraryOcr(
    private val recognizer: TextRecognizer,
    /** The setting "Recognize text when saving a scan". */
    private val enabled: suspend () -> Boolean,
    private val scope: CoroutineScope,
    private val logger: Logger,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private class Pending(
        val store: LibraryStore,
        val docId: String,
        val pageId: String,
        val renderKey: String,
        val halve: Boolean,
    )

    /** Changes each time OCR data is written or dropped, to know when what was read is out of date. */
    @Volatile
    var version = 0
        private set

    private val lock = Any()
    private val pending = LinkedHashMap<Pair<String, String>, Pending>()
    // The render key being waited for or recognized for each page, so that saving again does not repeat it
    private val inFlight = HashMap<Pair<String, String>, String>()
    private var worker: Job? = null

    // The render key of the OCR data of a page as last seen, to avoid reading its file again
    private val known = HashMap<Triple<LibraryStore, String, String>, String>()

    /**
     * Brings the OCR data of the pages of [docId], which was just saved, up to date: data of pages
     * that were deleted or edited is dropped, and the pages without valid data are queued.
     */
    suspend fun pagesSaved(store: LibraryStore, docId: String, pages: List<ScanPage>) {
        val pageIds = pages.map { it.id }.toSet()
        val existing = store.ocrPageIds(docId)
        for (pageId in existing - pageIds) drop(store, docId, pageId)
        val queue = enabled() && recognizer.isAvailable
        for (page in pages) {
            val renderKey = page.key().renderKey()
            val have = storedKey(store, docId, page.id, existing)
            if (have == renderKey) continue
            if (have != null) drop(store, docId, page.id)
            if (queue) {
                enqueue(Pending(store, docId, page.id, renderKey, page.colorMode?.isBitonal == true))
            }
        }
    }

    /** The recognition kept for the page, if it was made for the page as it is now. */
    fun stored(store: LibraryStore, docId: String, page: ScanPage): PageRecognition? {
        val ocr = store.readOcr(docId, page.id) ?: return null
        return if (ocr.renderKey == page.key().renderKey()) ocr.toRecognition() else null
    }

    /** Keeps a recognition that was made for the page as it is now (for instance, while exporting). */
    fun save(store: LibraryStore, docId: String, page: ScanPage, recognition: PageRecognition) {
        if (store.readMetadata(docId) == null) return
        val key = page.key().renderKey()
        store.writeOcr(docId, page.id, recognition.toStored(key))
        synchronized(known) { known[Triple(store, docId, page.id)] = key }
        version++
    }

    /** The text of the page for a PDF: what is kept, and a way to keep what is recognized meanwhile. */
    fun exportSource(store: LibraryStore, docId: String, page: ScanPage) = PageOcrSource(
        stored = stored(store, docId, page),
        onRecognized = { recognition ->
            try {
                save(store, docId, page, recognition)
            } catch (e: Exception) {
                logger.e(TAG, "Failed to keep the text of page ${page.id} of $docId", e)
            }
        },
    )

    private fun storedKey(store: LibraryStore, docId: String, pageId: String, existing: Set<String>): String? {
        val id = Triple(store, docId, pageId)
        if (pageId !in existing) {
            synchronized(known) { known.remove(id) }
            return null
        }
        synchronized(known) { known[id] }?.let { return it }
        val key = store.readOcr(docId, pageId)?.renderKey
        if (key != null) synchronized(known) { known[id] = key }
        return key
    }

    private fun drop(store: LibraryStore, docId: String, pageId: String) {
        store.deleteOcr(docId, pageId)
        synchronized(known) { known.remove(Triple(store, docId, pageId)) }
        version++
    }

    private fun enqueue(job: Pending) {
        synchronized(lock) {
            if (inFlight[job.docId to job.pageId] == job.renderKey) return
            inFlight[job.docId to job.pageId] = job.renderKey
            pending[job.docId to job.pageId] = job
            if (worker?.isActive != true) worker = scope.launch(dispatcher) { drain() }
        }
    }

    private suspend fun drain() {
        while (true) {
            val job = synchronized(lock) {
                val first = pending.entries.firstOrNull()
                if (first == null) worker = null else pending.remove(first.key)
                first?.value
            } ?: return
            try {
                recognize(job)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "Failed to recognize page ${job.pageId} of ${job.docId}", e)
            } finally {
                synchronized(lock) { if (inFlight[job.docId to job.pageId] == job.renderKey) inFlight.remove(job.docId to job.pageId) }
            }
        }
    }

    // Only for the page as it was queued: it is checked again once the recognition is done
    private suspend fun recognize(job: Pending) {
        val image = withContext(ioDispatcher) {
            val stored = job.store.processedPages(job.docId)[job.pageId]
            if (stored?.renderKey != job.renderKey) return@withContext null
            job.store.readProcessed(job.docId, job.pageId)?.let { if (stored.isPng) Png(it) else Jpeg(it) }
        }
        if (image == null) return
        val recognition = recognizer.recognize(image, job.halve) ?: return
        withContext(ioDispatcher) {
            if (job.store.processedPages(job.docId)[job.pageId]?.renderKey != job.renderKey) return@withContext
            if (job.store.readMetadata(job.docId) == null) return@withContext
            job.store.writeOcr(job.docId, job.pageId, recognition.toStored(job.renderKey))
            synchronized(known) { known[Triple(job.store, job.docId, job.pageId)] = job.renderKey }
            version++
        }
    }
}
