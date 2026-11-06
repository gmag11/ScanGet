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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.fairscan.app.data.DocumentMetadataV3
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.data.ImageTransformations
import org.fairscan.app.data.ThumbnailCache
import org.fairscan.app.data.ThumbnailKind
import org.fairscan.app.data.Logger
import org.fairscan.app.data.PageRecognition
import org.fairscan.app.data.PageV2
import org.fairscan.app.data.PROCESSED_DIR_NAME
import org.fairscan.app.data.toAdjustments
import org.fairscan.app.data.toSerializable
import org.fairscan.app.domain.EncodedImage
import org.fairscan.app.domain.Jpeg
import org.fairscan.app.domain.TextRecognizer
import org.fairscan.app.domain.PageMetadata
import org.fairscan.app.domain.Rotation.R90
import org.fairscan.imageprocessing.Adjustments
import org.fairscan.imageprocessing.CameraIntrinsics
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.ColorMode.COLOR
import org.fairscan.imageprocessing.ColorMode.GRAYSCALE
import org.fairscan.imageprocessing.ImageSize
import org.fairscan.imageprocessing.OcrTextBox
import org.fairscan.imageprocessing.ImageRect
import org.fairscan.imageprocessing.OpticalMeasures
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val libraryDir by lazy { folder.newFolder("library") }
    private val store by lazy { FileLibraryStore(libraryDir) }
    private var clockValue = 1_000L
    private val thumbnails by lazy { ThumbnailCache(File(folder.root, "thumbnails"), 240, 480) }

    private val quad = Quad(Point(.01, .02), Point(.1, .03), Point(.11, .12), Point(.03, .09))
    private val metadata = PageMetadata(
        quad, R90, COLOR, ImageSize(1600, 1200), OpticalMeasures(CameraIntrinsics(42f, 43f), 44f))

    // The "processed image" encodes what it was made from, to check the same rendering comes back
    private var processCalls = 0
    private var processGate: java.util.concurrent.CountDownLatch? = null
    private var rotationMarksImages = false // a rotated image gets an extra byte, to see how often it is rotated

    private val transformations = object : ImageTransformations {
        override fun rotate(input: EncodedImage, rotationDegrees: Int): EncodedImage =
            if (rotationMarksImages) Jpeg(input.bytes + 99) else input
        // Looks like a JPEG (FFD8 ... FFD9) to the thumbnail cache; the third byte tells where it comes from
        override fun resizeToThumbnail(input: EncodedImage): Jpeg = Jpeg(ByteArray(20).also {
            it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = input.bytes[0]
            it[18] = 0xFF.toByte(); it[19] = 0xD9.toByte()
        })
        override fun process(
            source: Jpeg, metadata: PageMetadata, colorMode: ColorMode, adjustments: Adjustments,
        ): Jpeg {
            processCalls++
            processGate?.await() // a test can keep the rendering waiting
            return Jpeg(byteArrayOf(source.bytes[0], colorMode.ordinal.toByte(),
                adjustments.brightness.toByte(), (metadata.normalizedQuad.topLeft.x * 100).toInt().toByte()))
        }
    }

    private class Session(val images: ImageRepository, val library: LibraryRepository, val dir: File)

    // What the library uses as its store; null while the library is not available
    private var currentStore: LibraryStore? = null

    private class FakeLastOpened(var id: String? = null) : LastOpenedDocument {
        override suspend fun lastOpenedId() = id
        override suspend fun setLastOpenedId(id: String?) { this.id = id }
    }
    private val lastOpened = FakeLastOpened()

    private fun TestScope.session(name: String, debounce: Long = 500, ocr: LibraryOcr? = null): Session {
        currentStore = store
        val dir = folder.newFolder(name)
        val images = ImageRepository(dir, transformations, backgroundScope, Logger { _, _, _ -> },
            documentNamer = { "Scan $it" }, clock = { clockValue }, thumbnailDiskCache = thumbnails)
        val library = LibraryRepository({ currentStore }, images, backgroundScope, Logger { _, _, _ -> },
            debounceMillis = debounce, clock = { clockValue },
            ioDispatcher = StandardTestDispatcher(testScheduler), lastOpened = lastOpened, ocr = ocr)
        runCurrent() // lets the autosave subscribe
        return Session(images, library, dir)
    }

    private suspend fun Session.addPage(first: Byte = 1) {
        images.add(Jpeg(byteArrayOf(first, 5)), Jpeg(byteArrayOf(first)), metadata, COLOR)
        clockValue++
        Thread.sleep(2) // page ids are the real time in milliseconds: they must differ
    }

    @Test
    fun `a document is saved after its first page, with source and cover`() = runTest {
        val s = session("work")
        assertThat(store.list()).isEmpty()

        s.addPage(7)
        advanceTimeBy(600); runCurrent()

        val entry = store.list().single()
        assertThat(entry.pageCount).isEqualTo(1)
        val id = entry.info.id
        val page = s.images.pages().single()
        assertThat(store.readSource(id, page.id)).isEqualTo(byteArrayOf(7))
        // The cover is rendered on a real IO thread, which the virtual time does not wait for
        s.library.flush()
        assertThat(store.readCover(id)).isNotNull()
    }

    @Test
    fun `saving waits for the pause in the changes`() = runTest {
        val s = session("work")

        s.addPage()
        advanceTimeBy(300); runCurrent()
        assertThat(store.list()).isEmpty()
        s.addPage()
        advanceTimeBy(300); runCurrent()
        assertThat(store.list()).isEmpty()
        advanceTimeBy(300); runCurrent()

        assertThat(store.list().single().pageCount).isEqualTo(2)
    }

    @Test
    fun `a document without pages is not added to the library`() = runTest {
        val s = session("work")

        s.library.flush()
        s.library.startNewDocument()

        assertThat(store.list()).isEmpty()
        assertThat(s.library.documents()).isEmpty()
    }

    @Test
    fun `a saved document is opened with the same pages and editing data`() = runTest {
        val s = session("work")
        s.addPage(1); s.addPage(2)
        val ids = s.images.pages().map { it.id }
        val userQuad = Quad(Point(.05, .05), Point(.9, .06), Point(.92, .9), Point(.04, .88))
        s.images.setUserQuad(ids[0], userQuad)
        s.images.setRendering(ids[1], GRAYSCALE, Adjustments(brightness = 20))
        s.images.rotate(ids[1], clockwise = true)
        s.images.movePage(ids[1], 0)
        s.library.flush()
        val expectedPages = s.images.pages()
        val expectedImages = expectedPages.map { s.images.image(it.key())?.bytes?.toList() }
        val id = s.images.documentInfo().id

        // Another working directory, as after reinstalling or clearing the app data
        val other = session("work2")
        assertThat(other.library.open(id)).isTrue()

        assertThat(other.images.pages()).isEqualTo(expectedPages)
        assertThat(other.images.pages().map { other.images.image(it.key())?.bytes?.toList() })
            .isEqualTo(expectedImages)
        assertThat(other.images.documentInfo().name).isEqualTo(s.images.documentInfo().name)
        assertThat(other.images.documentInfo().updatedAt).isEqualTo(s.images.documentInfo().updatedAt)
    }

    @Test
    fun `opening a document is not an edit`() = runTest {
        val s = session("work")
        s.addPage()
        s.library.flush()
        val saved = store.readMetadata(s.images.documentInfo().id)!!
        val other = session("work2")
        clockValue += 50_000

        other.library.open(saved.id)
        advanceTimeBy(2_000); runCurrent()
        other.library.flush()

        assertThat(store.readMetadata(saved.id)).isEqualTo(saved)
    }

    @Test
    fun `starting a new document keeps the previous one`() = runTest {
        val s = session("work")
        s.addPage(); s.addPage()
        val firstId = s.images.documentInfo().id

        s.library.startNewDocument()

        assertThat(s.images.pages()).isEmpty()
        assertThat(s.images.documentInfo().id).isNotEqualTo(firstId)
        assertThat(store.readMetadata(firstId)!!.pages).hasSize(2)
        assertThat(s.library.documents().map { it.info.id }).containsExactly(firstId)
    }

    @Test
    fun `no edit is lost when the document is closed before the autosave ran`() = runTest {
        val s = session("work")
        s.addPage()
        s.library.flush()
        val firstId = s.images.documentInfo().id
        val pageId = s.images.pages()[0].id

        // Edited and left right away, without waiting for the pause that triggers the autosave
        s.images.rotate(pageId, clockwise = true)
        s.images.setRendering(pageId, GRAYSCALE, Adjustments(brightness = 30))
        s.library.startNewDocument()

        val saved = store.readMetadata(firstId)!!.pages.single()
        assertThat(saved.manualRotationDegrees).isEqualTo(90)
        assertThat(saved.colorMode).isEqualTo(GRAYSCALE)
        assertThat(saved.adjustments.toAdjustments().brightness).isEqualTo(30)

        // And with open(), going back to the first document
        s.addPage(9)
        val secondId = s.images.documentInfo().id
        s.images.rotate(s.images.pages()[0].id, clockwise = false)
        s.library.open(firstId)
        assertThat(store.readMetadata(secondId)!!.pages.single().manualRotationDegrees).isEqualTo(270)
    }

    @Test
    fun `opening another document keeps the current one`() = runTest {
        val s = session("work")
        s.addPage(1)
        val firstId = s.images.documentInfo().id
        s.library.startNewDocument()
        s.addPage(2); s.addPage(3)
        val secondId = s.images.documentInfo().id

        assertThat(s.library.open(firstId)).isTrue()

        assertThat(s.images.documentInfo().id).isEqualTo(firstId)
        assertThat(s.images.pages()).hasSize(1)
        assertThat(store.readMetadata(secondId)!!.pages).hasSize(2)
        assertThat(s.library.open("unknown")).isFalse()
        assertThat(s.images.documentInfo().id).isEqualTo(firstId)
    }

    @Test
    fun `documents are listed most recently modified first`() = runTest {
        val s = session("work")
        s.addPage(); val a = s.images.documentInfo().id
        clockValue += 1_000
        s.library.startNewDocument(); s.addPage(); val b = s.images.documentInfo().id
        clockValue += 1_000
        s.library.startNewDocument(); s.addPage(); val c = s.images.documentInfo().id

        assertThat(s.library.documents().map { it.info.id }).containsExactly(c, b, a)

        clockValue += 1_000
        s.library.open(a)
        s.images.rotate(s.images.pages()[0].id, clockwise = true)
        assertThat(s.library.documents().map { it.info.id }).containsExactly(a, c, b)
    }

    @Test
    fun `deleting a page removes its source from the library`() = runTest {
        val s = session("work")
        s.addPage(1); s.addPage(2)
        s.library.flush()
        val id = s.images.documentInfo().id
        val (first, second) = s.images.pages().map { it.id }

        s.images.delete(first)
        s.library.flush()

        assertThat(store.sourceIds(id)).containsExactly(second)
        assertThat(store.readMetadata(id)!!.pages.map { it.id }).containsExactly(second)
    }

    @Test
    fun `a document whose pages were all deleted is removed when it is closed`() = runTest {
        val s = session("work")
        s.addPage()
        s.library.flush()
        val id = s.images.documentInfo().id
        s.images.delete(s.images.pages()[0].id)
        s.library.flush()

        assertThat(s.library.documents()).isEmpty() // hidden while it is still open
        s.library.startNewDocument()

        assertThat(File(libraryDir, id)).doesNotExist()
    }

    @Test
    fun `renaming the open document and another one`() = runTest {
        val s = session("work")
        s.addPage()
        val firstId = s.images.documentInfo().id
        s.library.startNewDocument()
        s.addPage()
        val secondId = s.images.documentInfo().id

        s.library.rename(secondId, "Open one")
        s.library.rename(firstId, "Closed one")

        assertThat(store.readMetadata(secondId)!!.name).isEqualTo("Open one")
        assertThat(store.readMetadata(firstId)!!.name).isEqualTo("Closed one")
        assertThat(s.images.documentInfo().name).isEqualTo("Open one")
    }

    @Test
    fun `deleting the open document and another one`() = runTest {
        val s = session("work")
        s.addPage()
        val firstId = s.images.documentInfo().id
        s.library.startNewDocument()
        s.addPage()
        val secondId = s.images.documentInfo().id
        s.library.flush()

        s.library.delete(firstId)
        assertThat(store.list().map { it.info.id }).containsExactly(secondId)
        assertThat(s.images.pages()).hasSize(1)

        s.library.delete(secondId)
        advanceTimeBy(1_000); runCurrent()

        assertThat(store.list()).isEmpty()
        assertThat(s.images.pages()).isEmpty()
        assertThat(libraryDir.list()!!).isEmpty()
    }

    @Test
    fun `a working document with a legacy version 2 file is added to the library`() = runTest {
        // A previous version of the app: processed image, source and a version 2 description
        val dir = folder.newFolder("work")
        File(dir, "sources").mkdirs(); File(dir, PROCESSED_DIR_NAME).mkdirs()
        File(dir, "sources/111.jpg").writeBytes(byteArrayOf(4))
        File(dir, "$PROCESSED_DIR_NAME/111.color.jpg").writeBytes(byteArrayOf(4, 5))
        File(dir, "$PROCESSED_DIR_NAME/document.json").writeText(
            Json { encodeDefaults = true }.encodeToString(
                org.fairscan.app.data.DocumentMetadataV2(pages = listOf(
                    PageV2("111", colorMode = COLOR, isColored = true, quad = quad.toSerializable())))))
        val images = ImageRepository(dir, transformations, backgroundScope, Logger { _, _, _ -> },
            documentNamer = { "Scan $it" }, clock = { clockValue })
        val library = LibraryRepository({ store }, images, backgroundScope, Logger { _, _, _ -> },
            ioDispatcher = StandardTestDispatcher(testScheduler))
        runCurrent()
        assertThat(store.list()).isEmpty() // nothing is saved until the library is available

        library.flush()

        val saved = store.readMetadata(images.documentInfo().id)!!
        assertThat(saved.pages.map { it.id }).containsExactly("111")
        assertThat(saved.name).isEqualTo("Scan 1000")
        assertThat(store.readSource(saved.id, "111")).isEqualTo(byteArrayOf(4))
    }

    @Test
    fun `a document never takes the place of something that is not FairScan's`() = runTest {
        // The working document claims an id that a foreign folder of the library already uses
        val dir = folder.newFolder("work")
        File(dir, "sources").mkdirs(); File(dir, PROCESSED_DIR_NAME).mkdirs()
        File(dir, "sources/111.jpg").writeBytes(byteArrayOf(4))
        File(dir, "$PROCESSED_DIR_NAME/111.color.jpg").writeBytes(byteArrayOf(4, 5))
        File(dir, "$PROCESSED_DIR_NAME/document.json").writeText(
            Json { encodeDefaults = true }.encodeToString(DocumentMetadataV3(
                id = "taken", name = "x", createdAt = 1, updatedAt = 1,
                pages = listOf(PageV2("111", colorMode = COLOR, isColored = true, quad = quad.toSerializable())))))
        File(libraryDir, "taken").apply { mkdirs(); File(this, "holiday.jpg").writeText("mine") }
        val images = ImageRepository(dir, transformations, backgroundScope, Logger { _, _, _ -> },
            clock = { clockValue })
        val library = LibraryRepository({ store }, images, backgroundScope, Logger { _, _, _ -> },
            ioDispatcher = StandardTestDispatcher(testScheduler))
        runCurrent()

        library.flush()

        val id = images.documentInfo().id
        assertThat(id).isNotEqualTo("taken")
        assertThat(store.readMetadata(id)).isNotNull()
        assertThat(File(libraryDir, "taken").list()!!.toList()).containsExactly("holiday.jpg")
    }

    // --- The library is not always available ---

    @Test
    fun `nothing is written while the library is not available and everything is when it comes back`() = runTest {
        val s = session("work")
        s.addPage(1); s.addPage(2)
        s.library.flush()
        val id = s.images.documentInfo().id
        val pageIds = s.images.pages().map { it.id }

        // The folder is lost; the user keeps editing
        currentStore = null
        s.images.rotate(pageIds[0], clockwise = true)
        s.addPage(3)
        s.library.flush()
        advanceTimeBy(1_000); runCurrent()
        assertThat(store.readMetadata(id)!!.pages).hasSize(2) // the library knows nothing of the edits
        assertThat(s.library.documents()).isEmpty()

        // It is back, perhaps another folder: the document is written in full
        currentStore = store
        s.library.flush()

        val saved = store.readMetadata(id)!!
        assertThat(saved.pages).hasSize(3)
        assertThat(saved.pages[0].manualRotationDegrees).isEqualTo(90)
        assertThat(store.sourceIds(id)).hasSize(3)
    }

    @Test
    fun `the working document is written in full to a new library location`() = runTest {
        val s = session("work")
        s.addPage(1); s.addPage(2)
        s.library.flush()
        val id = s.images.documentInfo().id
        val other = FileLibraryStore(folder.newFolder("other-library"))

        currentStore = other
        s.library.flush()

        assertThat(other.readMetadata(id)!!.pages).hasSize(2)
        assertThat(other.sourceIds(id)).hasSize(2)
        assertThat(other.readCover(id)).isNotNull()
    }

    @Test
    fun `a document in progress before the library existed is saved once there is one`() = runTest {
        currentStore = null
        val dir = folder.newFolder("work")
        val images = ImageRepository(dir, transformations, backgroundScope, Logger { _, _, _ -> },
            documentNamer = { "Scan $it" }, clock = { clockValue })
        val library = LibraryRepository({ currentStore }, images, backgroundScope, Logger { _, _, _ -> },
            ioDispatcher = StandardTestDispatcher(testScheduler))
        runCurrent()
        images.add(Jpeg(byteArrayOf(1, 5)), Jpeg(byteArrayOf(1)), metadata, COLOR)
        library.flush()
        assertThat(store.list()).isEmpty()
        val before = images.snapshot()

        currentStore = store // the user has chosen the folder
        library.flush()

        assertThat(store.readMetadata(before.id)!!.pages.map { it.id }).containsExactlyElementsOf(before.pages.map { it.id })
        assertThat(store.readSource(before.id, before.pages[0].id)).isEqualTo(byteArrayOf(1))
    }

    @Test
    fun `a new document cannot replace one that cannot be saved`() = runTest {
        val s = session("work")
        s.addPage()
        currentStore = null

        assertThat(s.library.canStartNewDocument()).isFalse()
        assertThat(runCatching { s.library.startNewDocument() }.exceptionOrNull())
            .isInstanceOf(LibraryUnavailableException::class.java)
        assertThat(s.images.pages()).hasSize(1) // untouched
    }

    @Test
    fun `an empty document can be replaced even without a library`() = runTest {
        val s = session("work")
        currentStore = null

        assertThat(s.library.canStartNewDocument()).isTrue()
        s.library.startNewDocument()
    }

    @Test
    fun `listing and opening do nothing without a library`() = runTest {
        val s = session("work")
        s.addPage()
        s.library.flush()
        val id = s.images.documentInfo().id
        currentStore = null

        assertThat(s.library.documents()).isEmpty()
        assertThat(s.library.open(id)).isFalse()
        assertThat(s.library.cover(id)).isNull()
    }

    // --- Cached list of documents ---

    @Test
    fun `the list of documents is cached for the next start and rebuilt when the cache is lost`() = runTest {
        val indexFile = File(folder.newFolder("index"), "library-index.json")
        val index = LibraryIndex(indexFile)
        currentStore = store
        val images = ImageRepository(folder.newFolder("work"), transformations, backgroundScope,
            Logger { _, _, _ -> }, documentNamer = { "Scan $it" }, clock = { clockValue })
        val library = LibraryRepository({ currentStore }, images, backgroundScope, Logger { _, _, _ -> },
            ioDispatcher = StandardTestDispatcher(testScheduler), index = index, locationKey = { "here" })
        runCurrent()
        images.add(Jpeg(byteArrayOf(1, 5)), Jpeg(byteArrayOf(1)), metadata, COLOR)
        assertThat(library.cachedDocuments()).isEmpty()

        val listed = library.documents()

        assertThat(library.cachedDocuments()).isEqualTo(listed)
        assertThat(listed).hasSize(1)

        indexFile.delete()
        assertThat(library.cachedDocuments()).isEmpty()
        library.documents()
        assertThat(library.cachedDocuments()).isEqualTo(listed)
    }

    @Test
    fun `the cached list of another location is not shown`() = runTest {
        val index = LibraryIndex(File(folder.newFolder("index"), "library-index.json"))
        var key = "folder-a"
        val images = ImageRepository(folder.newFolder("work"), transformations, backgroundScope,
            Logger { _, _, _ -> }, documentNamer = { "Scan $it" }, clock = { clockValue })
        currentStore = store
        val library = LibraryRepository({ currentStore }, images, backgroundScope, Logger { _, _, _ -> },
            ioDispatcher = StandardTestDispatcher(testScheduler), index = index, locationKey = { key })
        runCurrent()
        images.add(Jpeg(byteArrayOf(1, 5)), Jpeg(byteArrayOf(1)), metadata, COLOR)
        library.documents()
        assertThat(library.cachedDocuments()).hasSize(1)

        key = "folder-b"

        assertThat(library.cachedDocuments()).isEmpty()
    }

    // --- The document opened or created last ---

    @Test
    fun `a document that is new in the library is the one opened last`() = runTest {
        val s = session("work")
        assertThat(s.library.lastOpenedId()).isNull()

        s.addPage()
        s.library.flush()
        val firstId = s.images.documentInfo().id
        assertThat(s.library.lastOpenedId()).isEqualTo(firstId)

        s.library.startNewDocument()
        s.addPage()
        s.library.flush()

        assertThat(s.library.lastOpenedId()).isEqualTo(s.images.documentInfo().id)
        assertThat(s.library.lastOpenedId()).isNotEqualTo(firstId)
    }

    @Test
    fun `editing a document does not change which one was opened last`() = runTest {
        val s = session("work")
        s.addPage()
        val firstId = s.images.documentInfo().id
        s.library.startNewDocument()
        s.addPage()
        s.library.flush()
        s.library.open(firstId)
        s.library.startNewDocument() // an empty document is not saved: nothing is created
        s.library.flush()

        assertThat(s.library.lastOpenedId()).isEqualTo(firstId)
    }

    @Test
    fun `opening a document makes it the one opened last, also when it is already open`() = runTest {
        val s = session("work")
        s.addPage()
        val firstId = s.images.documentInfo().id
        s.library.startNewDocument()
        s.addPage()
        val secondId = s.images.documentInfo().id
        s.library.flush()
        assertThat(s.library.lastOpenedId()).isEqualTo(secondId)

        s.library.open(firstId)
        assertThat(s.library.lastOpenedId()).isEqualTo(firstId)

        s.library.open(firstId) // already the working document
        assertThat(s.library.lastOpenedId()).isEqualTo(firstId)
    }

    @Test
    fun `a document that does not exist is not recorded as opened`() = runTest {
        val s = session("work")
        s.addPage()
        s.library.flush()
        val id = s.images.documentInfo().id

        assertThat(s.library.open("unknown")).isFalse()

        assertThat(s.library.lastOpenedId()).isEqualTo(id)
    }

    @Test
    fun `deleting the document opened last forgets it and deleting another keeps it`() = runTest {
        val s = session("work")
        s.addPage()
        val firstId = s.images.documentInfo().id
        s.library.startNewDocument()
        s.addPage()
        val secondId = s.images.documentInfo().id
        s.library.flush()

        s.library.delete(firstId)
        assertThat(s.library.lastOpenedId()).isEqualTo(secondId)

        s.library.delete(secondId)
        assertThat(s.library.lastOpenedId()).isNull()
    }

    // --- Processed images and OCR data ---

    private class FakeRecognizer(var available: Boolean = true) : TextRecognizer {
        val calls = mutableListOf<Boolean>() // the "halve" flag of each call
        var gate: CompletableDeferred<Unit>? = null
        override val isAvailable get() = available
        override suspend fun recognize(image: EncodedImage, halve: Boolean): PageRecognition? {
            calls += halve
            gate?.await()
            return PageRecognition(listOf(OcrTextBox("word${calls.size}", ImageRect(1, 2, 3, 4), 10, 4)), 100, 200)
        }
    }

    // advanceUntilIdle can return while a chain of dispatches between test dispatchers is unfinished
    private fun TestScope.settle() = repeat(3) { advanceUntilIdle(); runCurrent() }

    private fun TestScope.ocrOf(recognizer: TextRecognizer, enabled: Boolean = true) = LibraryOcr(
        recognizer, { enabled }, backgroundScope, Logger { _, _, _ -> },
        dispatcher = StandardTestDispatcher(testScheduler), ioDispatcher = StandardTestDispatcher(testScheduler))

    @Test
    fun `the processed image of each page is saved, replaced when edited and removed with the page`() = runTest {
        val s = session("work")
        s.addPage(1); s.addPage(2)
        s.library.flush()
        val id = store.list().single().info.id
        val pages = s.images.pages()

        assertThat(store.processedPages(id)).containsOnlyKeys(pages.map { it.id })
        assertThat(store.processedPages(id).getValue(pages[0].id).renderKey).isEqualTo(pages[0].key().renderKey())

        s.images.setRendering(pages[0].id, GRAYSCALE, Adjustments.NEUTRAL)
        s.library.flush()

        val edited = s.images.pages()[0]
        assertThat(store.processedPages(id).getValue(edited.id).renderKey).isEqualTo(edited.key().renderKey())
        assertThat(File(libraryDir, "$id/processed").list()!!).hasSize(2)

        s.images.delete(pages[1].id)
        s.library.flush()

        assertThat(store.processedPages(id)).containsOnlyKeys(edited.id)
    }

    @Test
    fun `a document opens without its processed images`() = runTest {
        val s = session("work")
        s.addPage(1)
        s.library.flush()
        val id = store.list().single().info.id
        File(libraryDir, "$id/processed").deleteRecursively()
        s.library.startNewDocument()

        assertThat(s.library.open(id)).isTrue()

        assertThat(s.images.pages()).hasSize(1)
        assertThat(s.images.image(s.images.pages()[0].key())).isNotNull()
    }

    @Test
    fun `a saved page is recognized in the background and its text is kept`() = runTest {
        val recognizer = FakeRecognizer()
        val s = session("work", ocr = ocrOf(recognizer))
        s.addPage(1)
        s.library.flush()
        settle()

        val id = store.list().single().info.id
        val page = s.images.pages().single()
        val ocr = store.readOcr(id, page.id)!!
        assertThat(ocr.renderKey).isEqualTo(page.key().renderKey())
        assertThat(ocr.words.map { it.text }).containsExactly("word1")
        assertThat(recognizer.calls).hasSize(1)

        // Saving again does not recognize it again
        s.library.flush()
        settle()
        assertThat(recognizer.calls).hasSize(1)
    }

    @Test
    fun `nothing is recognized when the setting is off or there is no language`() = runTest {
        val off = FakeRecognizer()
        val s = session("work", ocr = ocrOf(off, enabled = false))
        s.addPage(1)
        s.library.flush()
        settle()

        val none = FakeRecognizer(available = false)
        val s2 = session("work2", ocr = ocrOf(none))
        s2.addPage(1)
        s2.library.flush()
        settle()

        assertThat(off.calls).isEmpty()
        assertThat(none.calls).isEmpty()
        assertThat(store.list().flatMap { store.ocrPageIds(it.info.id) }).isEmpty()
    }

    @Test
    fun `editing a page drops its text and recognizes it again`() = runTest {
        val recognizer = FakeRecognizer()
        val s = session("work", ocr = ocrOf(recognizer))
        s.addPage(1)
        s.library.flush()
        settle()
        val id = store.list().single().info.id
        val page = s.images.pages().single()

        s.images.rotate(page.id, clockwise = true)
        s.library.flush()
        // The old text is gone at once, the new one comes with the recognition
        assertThat(store.readOcr(id, page.id)?.renderKey).isNotEqualTo(page.key().renderKey())
        settle()

        val rotated = s.images.pages().single()
        assertThat(store.readOcr(id, page.id)!!.renderKey).isEqualTo(rotated.key().renderKey())
        assertThat(recognizer.calls).hasSize(2)
    }

    @Test
    fun `a recognition of a page that was edited meanwhile is not kept`() = runTest {
        val recognizer = FakeRecognizer().apply { gate = CompletableDeferred() }
        val s = session("work", ocr = ocrOf(recognizer))
        s.addPage(1)
        s.library.flush()
        runCurrent() // the recognition starts and waits
        val id = store.list().single().info.id
        val page = s.images.pages().single()

        s.images.rotate(page.id, clockwise = true)
        s.library.flush()
        recognizer.gate!!.complete(Unit)
        settle()

        val rotated = s.images.pages().single()
        assertThat(store.readOcr(id, page.id)!!.renderKey).isEqualTo(rotated.key().renderKey())
        assertThat(recognizer.calls).hasSize(2)
    }

    @Test
    fun `the text of a deleted page is removed`() = runTest {
        val s = session("work", ocr = ocrOf(FakeRecognizer()))
        s.addPage(1); s.addPage(2)
        s.library.flush()
        settle()
        val id = store.list().single().info.id
        val pages = s.images.pages()
        assertThat(store.ocrPageIds(id)).hasSize(2)

        s.images.delete(pages[0].id)
        s.library.flush()
        settle()

        assertThat(store.ocrPageIds(id)).containsExactly(pages[1].id)
    }

    @Test
    fun `a reopened document with pages that were not recognized is completed`() = runTest {
        val recognizer = FakeRecognizer()
        var enabled = false
        val ocr = LibraryOcr(recognizer, { enabled }, backgroundScope, Logger { _, _, _ -> },
            dispatcher = StandardTestDispatcher(testScheduler), ioDispatcher = StandardTestDispatcher(testScheduler))
        val s = session("work", ocr = ocr)
        s.addPage(1)
        s.library.flush()
        s.library.startNewDocument()
        settle()
        val id = store.list().single().info.id
        val page = store.readMetadata(id)!!.pages.single()
        assertThat(recognizer.calls).isEmpty()
        assertThat(store.readOcr(id, page.id)).isNull()

        enabled = true
        s.library.open(id)
        settle()

        assertThat(store.readOcr(id, page.id)).isNotNull()
        assertThat(recognizer.calls).hasSize(1)
    }

    @Test
    fun `the searchable text of a document follows its recognition`() = runTest {
        val ocr = ocrOf(FakeRecognizer())
        val s = session("work", ocr = ocr)
        s.addPage(1)
        s.library.flush()
        settle()
        val entry = s.library.documents().single()

        assertThat(s.library.searchTexts(listOf(entry))).containsEntry(entry.info.id, "word1")

        // Editing the page recognizes it again: what was read before is not served any more
        s.images.rotate(s.images.pages().single().id, clockwise = true)
        s.library.flush()
        settle()

        assertThat(s.library.searchTexts(listOf(entry))).containsEntry(entry.info.id, "word2")
    }

    @Test
    fun `a document without recognized text has no searchable text`() = runTest {
        val s = session("work")
        s.addPage(1)
        s.library.flush()

        assertThat(s.library.searchTexts(s.library.documents())).isEmpty()
    }

    private fun processedFiles(id: String) = File(libraryDir, "$id/processed").list()!!.toSet()

    @Test
    fun `cropping a page again replaces its processed image`() = runTest {
        val s = session("work")
        s.addPage(1); s.addPage(2)
        s.library.flush()
        val id = store.list().single().info.id
        val before = processedFiles(id)
        val page = s.images.pages()[0]

        s.images.setUserQuad(page.id, Quad(Point(.1, .1), Point(.9, .1), Point(.9, .9), Point(.1, .9)))
        s.library.flush()

        val edited = s.images.pages()[0]
        assertThat(edited.key().renderKey()).isNotEqualTo(page.key().renderKey())
        assertThat(store.processedPages(id).getValue(edited.id).renderKey).isEqualTo(edited.key().renderKey())
        assertThat(processedFiles(id)).hasSize(2).isEqualTo(before)
    }

    @Test
    fun `processed images are named after the document and the page number`() = runTest {
        val s = session("work")
        s.addPage(1); s.addPage(2)
        s.library.flush()
        val id = store.list().single().info.id
        val name = s.images.documentInfo().name

        assertThat(processedFiles(id)).containsExactlyInAnyOrder("$name-page01.jpg", "$name-page02.jpg")

        s.library.rename(id, "Invoice")
        s.library.flush()

        assertThat(processedFiles(id)).containsExactlyInAnyOrder("Invoice-page01.jpg", "Invoice-page02.jpg")
        assertThat(store.readProcessed(id, s.images.pages()[1].id)).isNotNull()
    }

    @Test
    fun `renaming a document that is not open renames its processed images`() = runTest {
        val s = session("work")
        s.addPage(1); s.addPage(2)
        s.library.flush()
        val id = store.list().single().info.id
        s.library.startNewDocument()

        s.library.rename(id, "Receipts")

        assertThat(processedFiles(id)).containsExactlyInAnyOrder("Receipts-page01.jpg", "Receipts-page02.jpg")
    }

    @Test
    fun `deleting or moving a page renumbers the processed images`() = runTest {
        val s = session("work")
        s.addPage(1); s.addPage(2); s.addPage(3)
        s.library.flush()
        val id = store.list().single().info.id
        val name = s.images.documentInfo().name
        val pages = s.images.pages()

        s.images.delete(pages[0].id)
        s.library.flush()

        assertThat(processedFiles(id)).containsExactlyInAnyOrder("$name-page01.jpg", "$name-page02.jpg")
        assertThat(store.processedPages(id).getValue(pages[1].id).fileName).isEqualTo("$name-page01.jpg")

        s.images.movePage(pages[2].id, 0)
        s.library.flush()

        assertThat(store.processedPages(id).getValue(pages[2].id).fileName).isEqualTo("$name-page01.jpg")
        assertThat(store.processedPages(id).getValue(pages[1].id).fileName).isEqualTo("$name-page02.jpg")
        assertThat(processedFiles(id)).hasSize(2)
    }

    // --- Pages shown from the processed images of the library ---

    @Test
    fun `a page of an opened document is shown from the stored processed image without rendering it`() = runTest {
        val s = session("work")
        s.addPage(1)
        s.library.flush()
        val id = store.list().single().info.id
        val key = s.images.pages().single().key()
        val stored = store.readProcessed(id, key.pageId)!!
        s.library.startNewDocument()
        processCalls = 0

        assertThat(s.library.open(id)).isTrue()
        val image = s.images.image(s.images.pages().single().key())!!

        assertThat(image.bytes).isEqualTo(stored)
        assertThat(processCalls).isEqualTo(0)
    }

    @Test
    fun `a rotated page is not rotated again when it comes from the stored image`() = runTest {
        rotationMarksImages = true
        val s = session("work")
        s.addPage(1)
        s.images.rotate(s.images.pages().single().id, clockwise = true)
        s.library.flush()
        val id = store.list().single().info.id
        val rotatedOnce = s.images.image(s.images.pages().single().key())!!.bytes
        s.library.startNewDocument()
        processCalls = 0

        s.library.open(id)
        val image = s.images.image(s.images.pages().single().key())!!

        assertThat(image.bytes).isEqualTo(rotatedOnce)
        assertThat(processCalls).isEqualTo(0)
    }

    @Test
    fun `a page without a stored image is rendered from its original capture`() = runTest {
        val s = session("work")
        s.addPage(1)
        s.library.flush()
        val id = store.list().single().info.id
        val pageId = s.images.pages().single().id
        s.library.startNewDocument()
        store.deleteProcessed(id, pageId)
        processCalls = 0

        s.library.open(id)
        s.images.image(s.images.pages().single().key())

        // The library stores it again when the document is opened, rendering it once
        assertThat(processCalls).isEqualTo(1)
    }

    @Test
    fun `a page edited after opening is rendered again and not taken from the stored image`() = runTest {
        val s = session("work")
        s.addPage(1)
        s.library.flush()
        val id = store.list().single().info.id
        s.library.startNewDocument()
        s.library.open(id)
        val page = s.images.pages().single()
        processCalls = 0

        s.images.setRendering(page.id, GRAYSCALE, Adjustments.NEUTRAL)
        val edited = s.images.image(s.images.pages().single().key())!!

        assertThat(processCalls).isEqualTo(1)
        assertThat(edited.bytes[1]).isEqualTo(GRAYSCALE.ordinal.toByte())
    }

    @Test
    fun `the thumbnails of a page are kept when it is saved to the library`() = runTest {
        val s = session("work")
        s.addPage(1)

        s.library.flush()

        val key = s.images.pages().single().key()
        assertThat(thumbnails.read(key.pageId, key.renderKey(), ThumbnailKind.GRID)).isNotNull()
        assertThat(thumbnails.read(key.pageId, key.renderKey(), ThumbnailKind.STRIP)).isNotNull()
    }

    @Test
    fun `opening a document does not wait for its pages to be rendered, they are kept in the background`() = runTest {
        val s = session("work")
        s.addPage(1); s.addPage(2)
        s.library.flush()
        val id = store.list().single().info.id
        val pageIds = s.images.pages().map { it.id }
        s.library.startNewDocument()
        pageIds.forEach { store.deleteProcessed(id, it) }
        processCalls = 0
        val gate = java.util.concurrent.CountDownLatch(1)
        processGate = gate

        // The rendering is held: open() returns anyway, with nothing stored yet
        assertThat(s.library.open(id)).isTrue()
        assertThat(store.processedPages(id)).isEmpty()

        gate.countDown()
        s.library.awaitBackgroundWork()

        assertThat(store.processedPages(id)).containsOnlyKeys(pageIds)
        assertThat(processCalls).isEqualTo(2)
    }

    @Test
    fun `a page edited while it was rendered in the background does not keep the old rendering`() = runTest {
        val s = session("work")
        s.addPage(1)
        s.library.flush()
        val id = store.list().single().info.id
        val page = s.images.pages().single()
        s.library.startNewDocument()
        store.deleteProcessed(id, page.id)
        val gate = java.util.concurrent.CountDownLatch(1)
        processGate = gate
        s.library.open(id)

        // The user changes the filter while the old rendering is still being made
        val edit = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            s.images.setRendering(page.id, GRAYSCALE, Adjustments.NEUTRAL)
        }
        gate.countDown()
        edit.join()
        s.library.awaitBackgroundWork()

        // Whatever was kept is for the rendering the page has now
        val current = s.images.pages().single().key().renderKey()
        store.processedPages(id)[page.id]?.let { assertThat(it.renderKey).isEqualTo(current) }
        s.library.flush()
        assertThat(store.processedPages(id).getValue(page.id).renderKey).isEqualTo(current)
    }

    // --- Copying pages to another document ---

    private suspend fun TestScope.workingWithPages(count: Int): Session {
        val s = session("work")
        repeat(count) { s.addPage((it + 1).toByte()) }
        advanceTimeBy(600); runCurrent()
        return s
    }

    @Test
    fun `pages are copied to a new document in order, with their own ids`() = runTest {
        val s = workingWithPages(3)
        val pages = s.images.pages()

        val target = s.library.appendPages(listOf(pages[2].id, pages[0].id), null)

        val copied = store.readMetadata(target.id)!!
        assertThat(copied.pages).hasSize(2)
        // In the order of the document, not of the request
        assertThat(store.readSource(target.id, copied.pages[0].id)).isEqualTo(byteArrayOf(1))
        assertThat(store.readSource(target.id, copied.pages[1].id)).isEqualTo(byteArrayOf(3))
        assertThat(copied.pages.map { it.id }).doesNotContainAnyElementsOf(pages.map { it.id })
        assertThat(copied.pages[0].quad).isEqualTo(s.images.snapshot().pages[0].quad)
        // The working document did not change
        assertThat(s.images.pages().map { it.id }).containsExactlyElementsOf(pages.map { it.id })
        assertThat(store.list()).hasSize(2)
        assertThat(store.processedPages(target.id)).hasSize(2)
    }

    @Test
    fun `pages are appended at the end of an existing document`() = runTest {
        val s = workingWithPages(2)
        val first = s.images.documentInfo().id
        s.library.startNewDocument()
        s.addPage(9)
        advanceTimeBy(600); runCurrent()
        val other = s.images.documentInfo().id
        s.library.open(first)
        val pages = s.images.pages()

        s.library.appendPages(listOf(pages[1].id), other)

        val result = store.readMetadata(other)!!
        assertThat(result.pages).hasSize(2)
        assertThat(store.readSource(other, result.pages[0].id)).isEqualTo(byteArrayOf(9))
        assertThat(store.readSource(other, result.pages[1].id)).isEqualTo(byteArrayOf(2))
    }

    @Test
    fun `a copy is independent from the original`() = runTest {
        val s = workingWithPages(1)
        val page = s.images.pages().single()
        val target = s.library.appendPages(listOf(page.id), null)

        s.images.setRendering(page.id, GRAYSCALE, Adjustments.NEUTRAL)
        s.library.flush()

        val copy = store.readMetadata(target.id)!!.pages.single()
        assertThat(copy.colorMode).isEqualTo(COLOR)
        assertThat(store.readSource(target.id, copy.id)).isEqualTo(byteArrayOf(1))
    }

    @Test
    fun `copying to the working document or to an unknown one is refused and writes nothing`() = runTest {
        val s = workingWithPages(1)
        val page = s.images.pages().single()
        val own = s.images.documentInfo().id

        assertThat(runCatching { s.library.appendPages(listOf(page.id), own) }.isFailure).isTrue()
        assertThat(runCatching { s.library.appendPages(listOf(page.id), "nope") }.isFailure).isTrue()
        assertThat(store.list()).hasSize(1)
    }

    @Test
    fun `the other documents do not include the working one`() = runTest {
        val s = workingWithPages(1)
        s.library.startNewDocument()
        s.addPage(5)
        advanceTimeBy(600); runCurrent()

        val others = s.library.otherDocuments()

        assertThat(others).hasSize(1)
        assertThat(others.single().info.id).isNotEqualTo(s.images.documentInfo().id)
    }
}
