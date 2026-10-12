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
package org.fairscan.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.fairscan.app.domain.EncodedImage
import org.fairscan.app.domain.Jpeg
import org.fairscan.app.domain.PageMetadata
import org.fairscan.app.domain.PageViewKey
import org.fairscan.app.domain.Png
import org.fairscan.app.domain.Rotation
import org.fairscan.app.domain.ScanPage
import org.fairscan.app.ui.screens.settings.DefaultFilenameStyle
import org.fairscan.imageprocessing.Adjustments
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.ImageSize
import org.fairscan.imageprocessing.OpticalMeasures
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad
import org.fairscan.imageprocessing.cameraIntrinsics
import java.io.File
import java.util.Date
import java.util.Collections.synchronizedMap

const val SOURCE_DIR_NAME = "sources"
const val PROCESSED_DIR_NAME = "scanned_pages"

// Processed files made with non-neutral adjustments end with ".a<hash>.jpg" or ".png".
private val ADJUSTED_FILE = Regex("""\.a[0-9a-f]{8}\.(jpg|png)$""")
const val THUMBNAIL_DIR_NAME = "thumbnails"

/**
 * Repository responsible for:
 * - page persistence (document.json)
 * - image files (work, source, thumbnails)
 * - page-level operations (add, rotate, move, delete)
 */
class ImageRepository(
    scanRootDir: File,
    val transformations: ImageTransformations,
    private val scope: CoroutineScope,
    private val logger: Logger,
    // Default name of a document created or migrated at the given time (epoch millis)
    private val documentNamer: (Long) -> String = { DefaultFilenameStyle.SCAN_DATE_TIME.filename(Date(it)) },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val sourceDir = File(scanRootDir, SOURCE_DIR_NAME).apply { mkdirs() }
    private val processedDir = File(scanRootDir, PROCESSED_DIR_NAME).apply { mkdirs() }
    private val thumbnailDir = File(scanRootDir, THUMBNAIL_DIR_NAME)

    private val mutex = Mutex()

    private val metadataFile = File(processedDir, "document.json")
    private val json = Json { prettyPrint = false; encodeDefaults = true }
    private val loaded = load()
    private var info: DocumentInfo = loaded.info
    private var pages: PageStore = PageStore(loaded.pages)

    private val processingJobs = synchronizedMap(mutableMapOf<PageViewKey, Deferred<Unit>>())
    private val imageCache = createLruCache<PageViewKey, Deferred<EncodedImage?>>(maxEntries = 50)
    private val thumbnailCache = createLruCache<PageViewKey, Deferred<EncodedImage?>>(maxEntries = 1000)

    private fun <K, V> createLruCache(maxEntries: Int): MutableMap<K, V> =
        synchronizedMap(object : LinkedHashMap<K, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<K, V>) = size > maxEntries
        })

    // --- Metadata ---

    private fun load(): LoadedDocument {
        thumbnailDir.deleteRecursively() // clean up dir that was used in older versions
        normalizeLegacyFiles()
        val filesOnDisk = processedDir.listFiles()
            ?.filter { it.extension == "jpg" || it.extension == "png"}
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()

        val metadata = if (metadataFile.exists()) {
            runCatching { loadMetadata() }.getOrNull()
        } else null

        return when {
            metadata != null ->
                // A page is kept as long as it can be shown: from its source (processed files are a
                // cache that is rendered again) or, for old pages without source, from its file
                metadata.copy(pages = metadata.pages
                    .filter {
                        sourceFile(it.id).exists() ||
                            processedImageFileName(it.id, it.colorMode, it.quadVersion, it.adjustments.toAdjustments()) in filesOnDisk
                    })
            else ->
                LoadedDocument(
                    newDocumentInfo(),
                    filesOnDisk
                        .sorted()
                        .map { pageFromLegacyFileName(it) },
                )
        }
    }

    private fun loadMetadata(): LoadedDocument =
        parseDocumentMetadata(metadataFile.readText(), ::newDocumentInfo)

    private fun newDocumentInfo(): DocumentInfo {
        val now = clock()
        val oldestSource = sourceDir.listFiles()?.minOfOrNull { it.lastModified() }
        return DocumentInfo(
            id = newDocumentId(now),
            name = documentNamer(now),
            createdAt = oldestSource ?: now,
            updatedAt = now,
        )
    }

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emits each time the document was changed and its metadata saved (the library saves it then). */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    /** [touch] sets the modification date to now; [notify] tells the library that it changed. */
    private fun saveMetadata(touch: Boolean = true, notify: Boolean = true) {
        if (touch) info = info.copy(updatedAt = clock())
        val metadata = info.toMetadata(pages.pages())
        metadataFile.writeText(json.encodeToString(metadata))
        if (notify) _changes.tryEmit(Unit)
    }

    // --- Main API ---

    suspend fun documentInfo(): DocumentInfo = mutex.withLock { info }

    /** The document as it would be saved in the library. */
    suspend fun snapshot(): DocumentMetadataV3 = mutex.withLock { info.toMetadata(pages.pages()) }

    /** Gives the document another id, when the one it has is used by something else in the library. */
    suspend fun reassignDocumentId() = mutex.withLock {
        info = info.copy(id = newDocumentId(clock()))
        saveMetadata()
    }

    /**
     * Makes [document] the working document, replacing the current one. Its sources are obtained from
     * [readSource]; pages without a source are left out. Processed images are rendered again when
     * needed. Neither the modification date nor the library are touched: this is not an edit.
     */
    suspend fun load(document: LoadedDocument, readSource: (pageId: String) -> ByteArray?) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                clearFiles()
                val kept = document.pages.filter { page ->
                    readSource(page.id)?.also { sourceFile(page.id).writeBytes(it) } != null
                }
                info = document.info
                pages = PageStore(kept)
                saveMetadata(touch = false, notify = false)
            }
        }

    suspend fun rename(name: String) = mutex.withLock {
        info = info.copy(name = name)
        saveMetadata()
    }

    suspend fun pages(): List<ScanPage> = mutex.withLock {
        pages.pages().mapNotNull {
            runCatching {
                val manualRotation = Rotation.fromDegrees(it.manualRotationDegrees)
                ScanPage(it.id, manualRotation, it.colorMode, it.quadVersion, it.toMetadata(),
                    it.adjustments.toAdjustments())
            }.getOrNull()
        }
    }

    suspend fun add(processed: EncodedImage, source: Jpeg, metadata: PageMetadata, colorMode: ColorMode) =
        mutex.withLock {
            val id = "${System.currentTimeMillis()}"
            val key = PageViewKey(id, Rotation.R0, colorMode, 0)
            processedImageFile(key).writeBytes(processed.bytes)
            sourceFile(id).writeBytes(source.bytes)
            pages.addOrReplace(
                PageV2(
                    id = id,
                    quad = metadata.normalizedQuad.toSerializable(),
                    baseRotationDegrees = metadata.baseRotation.degrees,
                    manualRotationDegrees = Rotation.R0.degrees,
                    isColored = metadata.autoColorMode == ColorMode.COLOR,
                    colorMode = colorMode,
                    focalLength = metadata.opticalMeasures?.cameraIntrinsics?.focalLength,
                    sensorWidth = metadata.opticalMeasures?.cameraIntrinsics?.sensorWidth,
                    subjectDistance = metadata.opticalMeasures?.subjectDistance,
                    sourceWidth = metadata.sourceSize?.width?.toInt(),
                    sourceHeight = metadata.sourceSize?.height?.toInt(),
                )
            )
            saveMetadata()
            // Pre-populate cache for R0
            imageCache.put(key, CompletableDeferred(processed))
        }

    /**
     * Renders the page with the given filter and adjustments, and stores both. Controls the filter
     * does not expose are neutralized, so that equal renderings share one file. Callers that switch
     * filter on their own pass [Adjustments.NEUTRAL], since adjustments only make sense for the
     * filter they were made on.
     */
    suspend fun setRendering(id: String, colorMode: ColorMode, adjustments: Adjustments) {
        val effective = adjustments.forMode(colorMode)
        updatePage(id) { page, metadata ->
            PageUpdate(
                updatedPage = page.copy(colorMode = colorMode, adjustments = effective.toSerializable()),
                normalizedQuad = metadata.normalizedQuad,
                colorMode = colorMode,
            )
        }
    }

    suspend fun setUserQuad(id: String, newQuad: Quad) {
        updatePage(id) { page, metadata ->
            PageUpdate(
                updatedPage = page.copy(
                    quadVersion = page.quadVersion + 1,
                    userQuad = newQuad.toSerializable(),
                ),
                normalizedQuad = newQuad,
                colorMode = page.colorMode ?: metadata.autoColorMode,
            )
        }
    }

    private data class PageUpdate(
        val updatedPage: PageV2,
        val normalizedQuad: Quad,
        val colorMode: ColorMode,
    )

    private suspend fun updatePage(
        id: String,
        buildUpdate: (PageV2, PageMetadata) -> PageUpdate
    ) {
        val page = mutex.withLock { pages.get(id) }
        val metadata = page?.toMetadata() ?: return
        val sourceFile = sourceFile(id)
        if (!sourceFile.exists())
            return

        val update = buildUpdate(page, metadata)
        val adjustments = update.updatedPage.adjustments.toAdjustments()
        val key = PageViewKey(
            pageId = id,
            rotation = Rotation.R0,
            colorMode = update.colorMode,
            quadVersion = update.updatedPage.quadVersion,
            adjustments = adjustments,
        )

        renderIfMissing(key, metadata.copy(normalizedQuad = update.normalizedQuad), update.colorMode)

        mutex.withLock {
            pages.update(id) { update.updatedPage }
            saveMetadata()
        }
        deleteStaleAdjustedFiles(id, processedImageFile(key))
    }

    /** Renders the processed file of [key] from the source capture, unless it exists already. */
    private suspend fun renderIfMissing(key: PageViewKey, metadata: PageMetadata, colorMode: ColorMode) {
        val processedFile = processedImageFile(key)
        val job = processingJobs.computeIfAbsent(key) {
            scope.async(Dispatchers.IO) {
                if (!processedFile.exists()) {
                    val sourceJpeg = Jpeg(sourceFile(key.pageId).readBytes())
                    val processedJpeg =
                        transformations.process(
                            sourceJpeg,
                            metadata = metadata,
                            colorMode = colorMode,
                            adjustments = key.adjustments,
                        )
                    processedFile.writeBytes(processedJpeg.bytes)
                }
            }
        }
        try {
            job.await()
        } finally {
            processingJobs.remove(key, job)
        }
    }

    /**
     * Processed files are a cache: when one is missing (after the document was opened from the
     * library, for instance) it is rendered again from the source with the page's saved crop, filter
     * and adjustments. Returns false when it cannot be, e.g. [key] is not the page's current rendering.
     */
    private suspend fun renderFromSource(key: PageViewKey): Boolean {
        val page = mutex.withLock { pages.get(key.pageId) } ?: return false
        val metadata = page.toMetadata() ?: return false
        val isCurrent = key.colorMode == page.colorMode &&
            key.quadVersion == page.quadVersion &&
            key.adjustments == page.adjustments.toAdjustments()
        if (!isCurrent || !sourceFile(key.pageId).exists()) return false
        return try {
            renderIfMissing(key, metadata, key.colorMode ?: metadata.autoColorMode)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("ImageRepository", "Failed to render page ${key.pageId} from its source", e)
            false
        }
    }

    // Experimenting with sliders leaves one file per tried value: once a rendering is committed,
    // keep only that one. Files with neutral adjustments are kept so that switching filter back
    // stays instant.
    private fun deleteStaleAdjustedFiles(id: String, current: File) {
        processedDir.listFiles()
            ?.filter { it.name.startsWith("$id.") && ADJUSTED_FILE.containsMatchIn(it.name) && it != current }
            ?.forEach { it.delete() }
    }

    suspend fun rotate(id: String, clockwise: Boolean) = mutex.withLock {
        val page = pages.get(id) ?: return@withLock
        val delta = if (clockwise) Rotation.R90 else Rotation.R270
        val newRotation = Rotation.fromDegrees(page.manualRotationDegrees).add(delta)
        pages.update(id) {
            it.copy(manualRotationDegrees = newRotation.degrees)
        }
        saveMetadata()
    }

    suspend fun image(key: PageViewKey): EncodedImage? =
        getOrCompute(imageCache, key, ::computeProcessedImage)

    suspend fun getThumbnail(key: PageViewKey): EncodedImage? =
        getOrCompute(thumbnailCache, key, ::computeThumbnail)

    // --- Cache compute functions ---

    private suspend fun getOrCompute(
        cache: MutableMap<PageViewKey, Deferred<EncodedImage?>>,
        key: PageViewKey,
        compute: suspend (PageViewKey) -> EncodedImage?
    ): EncodedImage? {
        val deferred = cache.computeIfAbsent(key) { k ->
            scope.async(Dispatchers.IO) { compute(k) }
        }
        try {
            return deferred.await()
        } catch (e: Exception) {
            cache.remove(key, deferred)
            throw e
        }
    }

    private suspend fun computeProcessedImage(key: PageViewKey): EncodedImage? =
        withContext(Dispatchers.IO) {
            val baseFile = processedImageFile(key)
            if (!baseFile.exists() && !renderFromSource(key)) return@withContext null
            val bytes = baseFile.readBytes()
            val baseImage = if (baseFile.extension == "png") Png(bytes) else Jpeg(bytes)
            if (key.rotation == Rotation.R0) {
                baseImage
            } else {
                transformations.rotate(
                    baseImage,
                    key.rotation.degrees)
            }
        }

    private suspend fun computeThumbnail(key: PageViewKey): EncodedImage? =
        withContext(Dispatchers.IO) {
            val processed = getOrCompute(imageCache, key, ::computeProcessedImage)
                ?: return@withContext null
            try {
                transformations.resizeToThumbnail(processed)
            } catch (e: Exception) {
                val message = "Failed to compute thumbnail for ${key.pageId}"
                logger.e("ImageRepository", message, e)
                null
            }
        }

    // --- Other operations ---

    // Pages with neutral adjustments keep the file names used before adjustments existed. The hash
    // never contains '-', which normalizeLegacyFiles reads as a legacy rotation separator.
    private fun processedImageFileName(
        id: String,
        colorMode: ColorMode?,
        quadVersion: Int,
        adjustments: Adjustments = Adjustments.NEUTRAL,
    ) : String {
        val sb = StringBuilder(id)
        if (colorMode != null)
            sb.append(".").append(colorMode.name.lowercase())
        if (quadVersion > 0)
            sb.append(".q").append(quadVersion)
        if (!adjustments.isNeutral)
            sb.append(".a").append(adjustments.hash())

        if (colorMode?.isBitonal == true)
            sb.append(".png")
        else
            sb.append(".jpg")

        return sb.toString()
    }

    private fun processedImageFile(key: PageViewKey) : File =
        File(processedDir, processedImageFileName(key.pageId, key.colorMode, key.quadVersion, key.adjustments))

    private fun sourceFile(id: String): File =
        File(sourceDir, "$id.jpg")

    fun source(id: String): Jpeg? {
        val file = sourceFile(id)
        return if (file.exists()) Jpeg(file.readBytes()) else null
    }

    suspend fun movePage(id: String, newIndex: Int) = mutex.withLock {
        pages.move(id, newIndex)
        saveMetadata()
    }

    suspend fun delete(id: String) = mutex.withLock {
        pages.delete(id)
        saveMetadata()
        sourceFile(id).delete()
        processedDir.listFiles()
            ?.filter { it.name.startsWith("$id.") || it.name.startsWith("$id-") }
            ?.forEach { it.delete() }
        // No need to clean caches: stale entries will be evicted by LRU
    }

    // Clearing starts a new document, with a new identity
    suspend fun clear() = mutex.withLock {
        info = newDocumentInfo()
        clearFiles()
        saveMetadata()
    }

    /** Empties the working directories (document.json is rewritten by the caller) and the caches. */
    private fun clearFiles() {
        pages.clear()
        sourceDir.listFiles()?.forEach { it.delete() }
        processedDir.listFiles()?.forEach { it.delete() }
        synchronized(imageCache) { imageCache.clear() }
        synchronized(thumbnailCache) { thumbnailCache.clear() }
    }

    // --- Legacy migration ---

    data class DiskPageFiles(
        val base: File?,
        val rotated: List<File>
    )

    // Legacy normalization strategy:
    // If only rotated files exist, keep ONE arbitrarily as base (id.jpg)
    // and discard the others. We intentionally sacrifice exact rotation
    // fidelity to restore a coherent model.
    private fun normalizeLegacyFiles() {
        val jpgs = processedDir.listFiles()?.filter { it.extension == "jpg" }.orEmpty()
        val byId = jpgs.groupBy { file ->
            val name = file.name.removeSuffix(".jpg")
            val dash = name.lastIndexOf('-')
            if (dash >= 0) name.substring(0, dash) else name
        }
        val pages = byId.mapValues { (_, files) ->
            val base = files.find { !it.name.contains('-') }
            val rotated = files.filter { it.name.contains('-') }
            DiskPageFiles(base, rotated)
        }
        pages.forEach { (id, files) ->
            if (files.base == null && files.rotated.isNotEmpty()) {
                val sortedRotatedFiles = files.rotated.sortedBy { it.name }
                val legacyFile = sortedRotatedFiles.first()
                val target = File(processedDir, "$id.jpg")
                if (legacyFile.renameTo(target)) {
                    sortedRotatedFiles.drop(1).forEach { it.delete() }
                }
            }
        }
    }

    fun lastAddedSourceFile(): File? {
        val sourceFiles = sourceDir.listFiles()?.filter { it.extension == "jpg" }
        if (sourceFiles.isNullOrEmpty()) {
            return null
        }
        return sourceFiles.maxByOrNull { it.lastModified() }
    }
}

fun Quad.toSerializable(): NormalizedQuad =
    NormalizedQuad(
        topLeft = PointD(topLeft.x, topLeft.y),
        topRight = PointD(topRight.x, topRight.y),
        bottomRight = PointD(bottomRight.x, bottomRight.y),
        bottomLeft = PointD(bottomLeft.x, bottomLeft.y)
    )

fun NormalizedQuad.toQuad(): Quad =
    Quad(
        Point(topLeft.x, topLeft.y),
        Point(topRight.x, topRight.y),
        Point(bottomRight.x, bottomRight.y),
        Point(bottomLeft.x, bottomLeft.y)
    )

fun PageV2.toMetadata(): PageMetadata? {
    if (quad == null || isColored == null) return null
    val cameraIntrinsics = cameraIntrinsics(focalLength, sensorWidth)
    val sourceSize =
        if (sourceWidth != null && sourceHeight != null)
            ImageSize(sourceWidth, sourceHeight)
        else
            null
    return PageMetadata(
        (userQuad ?: quad).toQuad(),
        Rotation.fromDegrees(baseRotationDegrees),
        if (isColored) ColorMode.COLOR else ColorMode.GRAYSCALE,
        sourceSize,
        cameraIntrinsics?.let { OpticalMeasures(it, subjectDistance) },
    )
}
