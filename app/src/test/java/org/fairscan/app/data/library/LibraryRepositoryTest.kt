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

import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.fairscan.app.data.Logger
import org.fairscan.app.data.PageV2
import org.fairscan.app.data.PROCESSED_DIR_NAME
import org.fairscan.app.data.toAdjustments
import org.fairscan.app.data.toSerializable
import org.fairscan.app.domain.EncodedImage
import org.fairscan.app.domain.Jpeg
import org.fairscan.app.domain.PageMetadata
import org.fairscan.app.domain.Rotation.R90
import org.fairscan.imageprocessing.Adjustments
import org.fairscan.imageprocessing.CameraIntrinsics
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.ColorMode.COLOR
import org.fairscan.imageprocessing.ColorMode.GRAYSCALE
import org.fairscan.imageprocessing.ImageSize
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

    private val quad = Quad(Point(.01, .02), Point(.1, .03), Point(.11, .12), Point(.03, .09))
    private val metadata = PageMetadata(
        quad, R90, COLOR, ImageSize(1600, 1200), OpticalMeasures(CameraIntrinsics(42f, 43f), 44f))

    // The "processed image" encodes what it was made from, to check the same rendering comes back
    private val transformations = object : ImageTransformations {
        override fun rotate(input: EncodedImage, rotationDegrees: Int): EncodedImage = input
        override fun resizeToThumbnail(input: EncodedImage): Jpeg = Jpeg(byteArrayOf(input.bytes[0]))
        override fun process(
            source: Jpeg, metadata: PageMetadata, colorMode: ColorMode, adjustments: Adjustments,
        ): Jpeg = Jpeg(byteArrayOf(source.bytes[0], colorMode.ordinal.toByte(),
            adjustments.brightness.toByte(), (metadata.normalizedQuad.topLeft.x * 100).toInt().toByte()))
    }

    private class Session(val images: ImageRepository, val library: LibraryRepository, val dir: File)

    private fun TestScope.session(name: String, debounce: Long = 500): Session {
        val dir = folder.newFolder(name)
        val images = ImageRepository(dir, transformations, backgroundScope, Logger { _, _, _ -> },
            documentNamer = { "Scan $it" }, clock = { clockValue })
        val library = LibraryRepository(store, images, backgroundScope, Logger { _, _, _ -> },
            debounceMillis = debounce, clock = { clockValue },
            ioDispatcher = StandardTestDispatcher(testScheduler))
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
        val library = LibraryRepository(store, images, backgroundScope, Logger { _, _, _ -> },
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
        val library = LibraryRepository(store, images, backgroundScope, Logger { _, _, _ -> },
            ioDispatcher = StandardTestDispatcher(testScheduler))
        runCurrent()

        library.flush()

        val id = images.documentInfo().id
        assertThat(id).isNotEqualTo("taken")
        assertThat(store.readMetadata(id)).isNotNull()
        assertThat(File(libraryDir, "taken").list()!!.toList()).containsExactly("holiday.jpg")
    }
}
