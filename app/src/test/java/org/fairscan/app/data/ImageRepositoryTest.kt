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

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.fairscan.app.domain.EncodedImage
import org.fairscan.app.domain.Jpeg
import org.fairscan.app.domain.PageMetadata
import org.fairscan.app.domain.PageViewKey
import org.fairscan.app.domain.Png
import org.fairscan.app.domain.Rotation.R0
import org.fairscan.app.domain.Rotation.R180
import org.fairscan.app.domain.Rotation.R270
import org.fairscan.app.domain.Rotation.R90
import org.fairscan.imageprocessing.Adjustments
import org.fairscan.imageprocessing.CameraIntrinsics
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.ColorMode.BLACK_AND_WHITE
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

class ImageRepositoryTest {

    @get:Rule
    var folder: TemporaryFolder = TemporaryFolder()

    private var _filesDir: File? = null

    private val testScope = TestScope()

    val quad1 = Quad(Point(.01, .02), Point(.1, .03), Point(.11, .12), Point(.03, .09))
    val opticalMeasures = OpticalMeasures(CameraIntrinsics(42.0f, 43.0f), 44.0f)
    val sourceSize = ImageSize(1600, 1200)
    val metadata1 = PageMetadata(quad1, R90, COLOR, sourceSize, opticalMeasures)

    fun getFilesDir(): File {
        if (_filesDir == null) {
            _filesDir = folder.newFolder("files_dir")
        }
        return _filesDir!!
    }

    fun repo(
        rotate: (EncodedImage, Int) -> EncodedImage = { input, _ -> input },
        resizeToThumbnail: (EncodedImage) -> Jpeg = { input -> jpeg(input.bytes[0]) },
        process: (Jpeg, PageMetadata, ColorMode, Adjustments) -> Jpeg = { _, _, _, _ ->
            throw UnsupportedOperationException()
        },
        clock: () -> Long = System::currentTimeMillis,
        namer: (Long) -> String = { "Scan $it" },
        scope: CoroutineScope = testScope,
    ): ImageRepository {
        val transformations = object : ImageTransformations {
            override fun rotate(input: EncodedImage, rotationDegrees: Int): EncodedImage =
                rotate(input, rotationDegrees)
            override fun resizeToThumbnail(input: EncodedImage): Jpeg =
                resizeToThumbnail(input)
            override fun process(
                source: Jpeg,
                metadata: PageMetadata,
                colorMode: ColorMode,
                adjustments: Adjustments,
            ): Jpeg = process(source, metadata, colorMode, adjustments)
        }
        val logger = Logger { _, _, _ -> }
        return ImageRepository(getFilesDir(), transformations, scope, logger, namer, clock)
    }

    @Test
    fun add_image() = runTest {
        val repo = repo()
        assertThat(repo.imageIds()).isEmpty()
        val jpeg = jpeg(101, 102, 103)
        repo.add(jpeg, jpeg(51), metadata1, COLOR)
        assertThat(repo.imageIds()).hasSize(1)
        val id = repo.imageIds()[0]
        val key = PageViewKey(id, R0, COLOR, 0)
        assertThat(repo.image(key)).isEqualTo(jpeg)
        assertThat(repo.getThumbnail(key)?.bytes).isEqualTo(byteArrayOf(101))

        val page = repo.pages().first()
        assertThat(page.id).isEqualTo(id)
        assertThat(page.manualRotation).isEqualTo(R0)
        val metadata = page.metadata
        assertThat(metadata).isNotNull()
        assertThat(metadata!!.normalizedQuad).isEqualTo(quad1)
        assertThat(metadata.baseRotation).isEqualTo(metadata1.baseRotation)
        assertThat(metadata.autoColorMode).isEqualTo(metadata1.autoColorMode)
    }

    @Test
    fun delete_image() = runTest {
        val repo = repo()
        val jpeg = jpeg(101, 102, 103)
        repo.add(jpeg, jpeg(51), metadata1, COLOR)
        assertThat(jpegFiles(processedDir())).hasSize(1)
        assertThat(jpegFiles(sourceDir())).hasSize(1)
        assertThat(repo.imageIds()).hasSize(1)
        repo.delete(repo.imageIds()[0])
        assertThat(repo.imageIds()).isEmpty()
        assertThat(jpegFiles(processedDir())).hasSize(0)
        assertThat(jpegFiles(sourceDir())).hasSize(0)
        val repo2 = repo()
        assertThat(repo2.imageIds()).isEmpty()
    }

    @Test
    fun `deletePages removes several pages, keeps the others in order and deletes their files`() = runTest {
        val repo = repo()
        repeat(4) {
            repo.add(jpeg(101, 102, 103), jpeg((50 + it).toByte()), metadata1, COLOR)
            Thread.sleep(2) // page ids come from the clock
        }
        val ids = repo.imageIds()
        assertThat(ids).hasSize(4)
        repo.deletePages(listOf(ids[1], ids[3]))
        assertThat(repo.imageIds()).containsExactly(ids[0], ids[2])
        assertThat(jpegFiles(processedDir())).hasSize(2)
        assertThat(jpegFiles(sourceDir())).hasSize(2)
        assertThat(repo().imageIds()).containsExactly(ids[0], ids[2])
    }

    @Test
    fun delete_unknown_id() = runTest {
        val repo = repo()
        repo.delete("x")
        assertThat(repo.imageIds()).isEmpty()
    }

    @Test
    fun `should find existing files at initialization with no json`() = runTest {
        processedDir().mkdirs()
        File(processedDir(), "1.jpg").writeBytes(byteArrayOf(101, 102, 103))
        assertThat(repo().imageIds()).containsExactly("1")
    }

    @Test
    fun `should find existing files at initialization if json is invalid`() = runTest {
        writeDocumentDotJson("xxx")
        File(processedDir(), "1.jpg").writeBytes(byteArrayOf(101, 102, 103))
        assertThat(repo().imageIds()).containsExactly("1")
    }

    @Test
    fun `no json and two files with same id`() = runTest {
        processedDir().mkdirs()
        File(processedDir(), "1768153479486.jpg").writeBytes(byteArrayOf(101, 102, 103))
        File(processedDir(), "1768153479486-270.jpg").writeBytes(byteArrayOf(105, 106, 107))
        val repo = repo()
        assertThat(repo.imageIds()).containsExactly("1768153479486")
    }

    @Test
    fun `should find existing files at initialization with no json and with rotation`() = runTest {
        processedDir().mkdirs()
        val bytes = byteArrayOf(101, 102, 103)
        File(processedDir(), "1-90.jpg").writeBytes(bytes)
        val repo = repo()
        assertThat(repo.imageIds()).containsExactly("1")
        assertThat(repo.image(PageViewKey("1", R0, null, 0))?.bytes).isEqualTo(bytes)
    }

    @Test
    fun `pages stored without adjustments load as neutral`() = runTest {
        writeDocumentDotJson("""{"version":2,"pages":[{"id":"1","colorMode":"COLOR"}]}""")
        File(processedDir(), "1.color.jpg").writeBytes(byteArrayOf(101))
        val page = repo().pages().single()
        assertThat(page.adjustments).isEqualTo(Adjustments.NEUTRAL)
        assertThat(page.key().adjustments.isNeutral).isTrue()
    }

    @Test
    fun `stored adjustments are loaded and are part of the page key`() = runTest {
        val adjustments = Adjustments(brightness = 15, contrast = -20)
        writeDocumentDotJson(
            """{"version":2,"pages":[{"id":"1","colorMode":"COLOR","adjustments":{"brightness":15,"contrast":-20}}]}""")
        File(processedDir(), "1.color.a${adjustments.hash()}.jpg").writeBytes(byteArrayOf(101))
        val page = repo().pages().single()
        assertThat(page.adjustments).isEqualTo(adjustments)
        assertThat(page.key().adjustments).isEqualTo(page.adjustments)
        assertThat(page.key()).isNotEqualTo(page.copy(adjustments = Adjustments.NEUTRAL).key())
    }

    @Test
    fun `neutral pages keep the file names used before adjustments existed`() = runTest {
        val repo = repo(process = { _, _, _, _ -> jpeg(41) })
        repo.add(jpeg(10), jpeg(20), metadata1, COLOR)
        val id = repo.imageIds().first()
        assertThat(File(processedDir(), "$id.color.jpg")).exists()

        repo.setRendering(id, GRAYSCALE, Adjustments.NEUTRAL)
        assertThat(File(processedDir(), "$id.grayscale.jpg")).exists()
        assertThat(processedDir().list()!!.filter { ".a" in it.removePrefix(id) }).isEmpty()
    }

    @Test
    fun `adjustments add a hash to the file name and different adjustments use different files`() = runTest {
        val a = Adjustments(brightness = 20)
        val b = Adjustments(brightness = 21)
        writeDocumentDotJson(
            """{"version":2,"pages":[{"id":"1","colorMode":"COLOR","adjustments":{"brightness":20}}]}""")
        File(processedDir(), "1.color.a${a.hash()}.jpg").writeBytes(byteArrayOf(55))
        val repo = repo()

        assertThat(repo.imageIds()).containsExactly("1")
        val keyA = PageViewKey("1", R0, COLOR, 0, a)
        assertThat(repo.image(keyA)?.bytes).isEqualTo(byteArrayOf(55))
        assertThat(a.hash()).isNotEqualTo(b.hash())
        assertThat(repo.image(PageViewKey("1", R0, COLOR, 0, b))).isNull()
        assertThat(repo.image(PageViewKey("1", R0, COLOR, 0, Adjustments.NEUTRAL))).isNull()
    }

    @Test
    fun `adjustments and crop version are both part of the file name`() = runTest {
        val a = Adjustments(contrast = -30)
        writeDocumentDotJson(
            """{"version":2,"pages":[{"id":"1","colorMode":"COLOR","quadVersion":2,"adjustments":{"contrast":-30}}]}""")
        File(processedDir(), "1.color.q2.a${a.hash()}.jpg").writeBytes(byteArrayOf(56))
        val repo = repo()
        assertThat(repo.imageIds()).containsExactly("1")
        assertThat(repo.image(PageViewKey("1", R0, COLOR, 2, a))?.bytes).isEqualTo(byteArrayOf(56))
    }

    @Test
    fun `a page whose adjusted file is missing is dropped at initialization`() = runTest {
        writeDocumentDotJson(
            """{"version":2,"pages":[{"id":"1","colorMode":"COLOR","adjustments":{"brightness":20}}]}""")
        // Only the neutral rendering exists: it does not match the stored adjustments.
        File(processedDir(), "1.color.jpg").writeBytes(byteArrayOf(55))
        assertThat(repo().imageIds()).isEmpty()
    }

    @Test
    fun `deleting a page removes its adjusted files too`() = runTest {
        val a = Adjustments(brightness = 20)
        writeDocumentDotJson(
            """{"version":2,"pages":[{"id":"1","colorMode":"COLOR","adjustments":{"brightness":20}}]}""")
        File(processedDir(), "1.color.a${a.hash()}.jpg").writeBytes(byteArrayOf(55))
        val repo = repo()
        repo.delete("1")
        assertThat(jpegFiles(processedDir())!!.toList()).isEmpty()
    }

    @Test
    fun `should filter pages in json at initialization`() = runTest {
        writeDocumentDotJson("""{"pages":[{"file":"1.jpg"}, {"file":"2.jpg"}]}""")
        File(processedDir(), "2.jpg").writeBytes(byteArrayOf(101, 102, 103))
        assertThat(repo().imageIds()).containsExactly("2")
    }

    @Test
    fun `should rename rotated files with no base file`() = runTest {
        processedDir().mkdirs()
        val bytes = byteArrayOf(105, 106, 107)
        File(processedDir(), "123-90.jpg").writeBytes(bytes)
        File(processedDir(), "123-270.jpg").writeBytes(bytes)
        val repo = repo()
        assertThat(repo.imageIds()).containsExactly("123")
        val jpegFiles = jpegFiles(processedDir())
        assertThat(jpegFiles).hasSize(1).allMatch { it?.name == "123.jpg" }
    }

    @Test
    fun `should rename rotated files with no base file but listed in json`() = runTest {
        writeDocumentDotJson("""{"pages":[{"file":"1-90.jpg"}]}""")
        val bytes = byteArrayOf(105, 106, 107)
        File(processedDir(), "1-90.jpg").writeBytes(bytes)
        val repo = repo()
        assertThat(repo.imageIds()).containsExactly("1")
        assertThat(repo.image(PageViewKey("1", R0, null, 0))?.bytes).isEqualTo(bytes)
    }

    @Test
    fun `should return null on invalid id`() = runTest {
        val repo = repo()
        assertThat(repo.imageIds()).isEmpty()
        assertThat(repo.image(PageViewKey("x", R0, COLOR, 0))).isNull()
    }

    @Test
    fun `clear should delete pages`() = runTest {
        val jpeg = jpeg(101, 102, 103)
        val repo1 = repo()
        repo1.add(jpeg, jpeg(51), metadata1, COLOR)
        assertThat(repo1.imageIds()).isNotEmpty()
        repo1.clear()
        assertThat(repo1.imageIds()).isEmpty()
        assertThat(jpegFiles(processedDir())).isEmpty()
        assertThat(jpegFiles(sourceDir())).isEmpty()
        val repo2 = repo()
        assertThat(repo2.imageIds()).isEmpty()
    }

    @Test
    fun rotate() = runTest {
        val repo = repo()
        repo.add(jpeg(101, 102, 103), jpeg(51), metadata1, COLOR)
        assertThat(repo.pages().last().metadata).isEqualTo(metadata1)
        val id = repo.pages().last().id
        repo.rotate(id, true)
        assertThat(repo.pages().last().manualRotation).isEqualTo(R90)
        repo.rotate(id, true)
        assertThat(repo.pages().last().manualRotation).isEqualTo(R180)
        repo.rotate(id, true)
        assertThat(repo.pages().last().manualRotation).isEqualTo(R270)
        repo.rotate(id, true)
        assertThat(repo.pages().last().manualRotation).isEqualTo(R0)
        repo.rotate(id, false)
        assertThat(repo.pages().last().manualRotation).isEqualTo(R270)

        val repo2 = repo()
        assertThat(repo2.imageIds()).containsExactly(id)
        assertThat(repo2.pages().last().manualRotation).isEqualTo(R270)
    }

    @Test
    fun rotate_unknown_id() = runTest {
        val repo = repo()
        repo.rotate("x", true)
        assertThat(repo.imageIds()).isEmpty()
    }

    @Test
    fun setRendering_should_process_and_update_metadata() = runTest {
        val jpeg1 = jpeg(10)
        val repo = repo(
            process = { _,  _, mode, _ ->
                assertThat(mode).isEqualTo(GRAYSCALE)
                jpeg(41)
            }
        )
        repo.add(jpeg1, jpeg(20), metadata1, COLOR)
        assertThat(repo.pages().first().colorMode).isEqualTo(COLOR)
        val id = repo.pages().first().id
        repo.setRendering(id, GRAYSCALE, Adjustments.NEUTRAL)
        assertThat(repo.pages().first().colorMode).isEqualTo(GRAYSCALE)
        val key = PageViewKey(id, R0, GRAYSCALE, 0)
        assertThat(repo.image(key)?.bytes).isEqualTo(byteArrayOf(41))
    }

    @Test
    fun setRendering_should_not_run_twice_in_parallel() = runTest {
        var processCalls = 0
        val repo = repo(
            process = { _, _, _, _ ->
                processCalls++
                runBlocking { delay(10) }
                jpeg(1)
            }
        )
        repo.add(jpeg(10), jpeg(20), metadata1, COLOR)
        val id = repo.imageIds().first()
        coroutineScope {
            launch { repo.setRendering(id, GRAYSCALE, Adjustments.NEUTRAL) }
            launch { repo.setRendering(id, GRAYSCALE, Adjustments.NEUTRAL) }
        }
        val key = PageViewKey(id, R0, GRAYSCALE, 0)
        assertThat(repo.image(key)?.bytes).isEqualTo(byteArrayOf(1))
        assertThat(processCalls).isEqualTo(1)
    }

    @Test
    fun setRendering_unknown_id_should_do_nothing() = runTest {
        val repo = repo()
        repo.setRendering("x", GRAYSCALE, Adjustments.NEUTRAL)
        assertThat(repo.imageIds()).isEmpty()
    }

    @Test
    fun `setRendering stores adjustments, renders with them and uses a hashed file`() = runTest {
        val adjustments = Adjustments(brightness = 30, contrast = -10)
        var received: Adjustments? = null
        val repo = repo(process = { _, _, _, adj -> received = adj; jpeg(77) })
        repo.add(jpeg(10), jpeg(20), metadata1, COLOR)
        val id = repo.imageIds().first()

        repo.setRendering(id, COLOR, adjustments)

        assertThat(received).isEqualTo(adjustments)
        val page = repo.pages().first()
        assertThat(page.adjustments).isEqualTo(adjustments)
        assertThat(repo.image(page.key())?.bytes).isEqualTo(byteArrayOf(77))
        assertThat(File(processedDir(), "$id.color.a${adjustments.hash()}.jpg")).exists()
        // The new state is persisted: a new repository loads the same page.
        assertThat(repo().pages().first().adjustments).isEqualTo(adjustments)
    }

    @Test
    fun `setRendering neutralizes the controls the filter does not expose`() = runTest {
        var received: Adjustments? = null
        val repo = repo(process = { _, _, _, adj -> received = adj; jpeg(78) })
        repo.add(jpeg(10), jpeg(20), metadata1, COLOR)
        val id = repo.imageIds().first()

        repo.setRendering(id, BLACK_AND_WHITE, Adjustments(brightness = 50, saturation = 20, threshold = 30))

        assertThat(received).isEqualTo(Adjustments(threshold = 30))
        assertThat(repo.pages().first().adjustments).isEqualTo(Adjustments(threshold = 30))
    }

    @Test
    fun `switching filter with neutral adjustments resets the page adjustments`() = runTest {
        val repo = repo(process = { _, _, _, _ -> jpeg(79) })
        repo.add(jpeg(10), jpeg(20), metadata1, COLOR)
        val id = repo.imageIds().first()
        repo.setRendering(id, COLOR, Adjustments(contrast = 40))
        assertThat(repo.pages().first().adjustments).isEqualTo(Adjustments(contrast = 40))

        repo.setRendering(id, GRAYSCALE, Adjustments.NEUTRAL)

        val page = repo.pages().first()
        assertThat(page.colorMode).isEqualTo(GRAYSCALE)
        assertThat(page.adjustments).isEqualTo(Adjustments.NEUTRAL)
        assertThat(File(processedDir(), "$id.grayscale.jpg")).exists()
    }

    @Test
    fun `setRendering keeps the crop of the page`() = runTest {
        val repo = repo(process = { _, _, _, _ -> jpeg(80) })
        repo.add(jpeg(10), jpeg(20), metadata1, COLOR)
        val id = repo.imageIds().first()
        repo.setUserQuad(id, quad1)
        val before = repo.pages().first()
        assertThat(before.quadVersion).isEqualTo(1)

        repo.setRendering(id, COLOR, Adjustments(brightness = 10))

        val after = repo.pages().first()
        assertThat(after.quadVersion).isEqualTo(1)
        assertThat(File(processedDir(), "$id.color.q1.a${Adjustments(brightness = 10).hash()}.jpg")).exists()
    }

    @Test
    fun `committing a rendering deletes the other adjusted files of the page but keeps neutral ones`() = runTest {
        val repo = repo(process = { _, _, _, _ -> jpeg(81) })
        repo.add(jpeg(10), jpeg(20), metadata1, COLOR)
        val id = repo.imageIds().first()
        val a = Adjustments(brightness = 10)
        val b = Adjustments(brightness = 20)
        val c = Adjustments(brightness = 30)

        repo.setRendering(id, COLOR, a)
        repo.setRendering(id, COLOR, b)
        repo.setRendering(id, GRAYSCALE, Adjustments.NEUTRAL)
        repo.setRendering(id, COLOR, c)

        val names = processedDir().list()!!.filter { it.startsWith("$id.") }.toSet()
        assertThat(names).contains("$id.color.jpg", "$id.grayscale.jpg", "$id.color.a${c.hash()}.jpg")
        assertThat(names).doesNotContain("$id.color.a${a.hash()}.jpg", "$id.color.a${b.hash()}.jpg")
    }

    @Test
    fun `files of other pages are not touched when cleaning adjusted files`() = runTest {
        val repo = repo(process = { _, _, _, _ -> jpeg(82) })
        repo.add(jpeg(10), jpeg(20), metadata1, COLOR)
        val first = repo.imageIds().first()
        repo.setRendering(first, COLOR, Adjustments(contrast = 5))
        // Make sure the second page gets another id.
        Thread.sleep(2)
        repo.add(jpeg(11), jpeg(21), metadata1, COLOR)
        val second = repo.imageIds().last()
        assertThat(second).isNotEqualTo(first)

        repo.setRendering(second, COLOR, Adjustments(contrast = 6))

        assertThat(File(processedDir(), "$first.color.a${Adjustments(contrast = 5).hash()}.jpg")).exists()
    }

    @Test
    fun movePage() = runTest {
        val repo = repo()
        repo.add(jpeg(101), jpeg(51), metadata1, COLOR)
        Thread.sleep(1L) // to avoid file name clashes
        repo.add(jpeg(110), jpeg(51), metadata1, COLOR)
        val id0 = repo.imageIds().first()
        val id1 = repo.imageIds().last()
        repo.movePage(id1, 0)
        assertThat(repo.imageIds()).containsExactly(id1, id0)

        val repo2 = repo()
        assertThat(repo2.imageIds()).containsExactly(id1, id0)
    }

    @Test
    fun move_unknown_id() = runTest {
        val repo = repo()
        repo.movePage("x", 0)
        assertThat(repo.imageIds()).isEmpty()
    }

    @Test
    fun metadata() {
        val quad = quad1.toSerializable()

        assertThat(PageV2("1", 0, 0, quad = null, isColored = true).toMetadata()).isNull()
        assertThat(PageV2("1", 0, 0, quad).toMetadata()).isNull()

        listOf(true, false).forEach { isColored ->
            val metadata = PageV2("1", 0, 0, quad, isColored = isColored).toMetadata()
            assertThat(metadata).isNotNull()
            assertThat(metadata!!.autoColorMode).isEqualTo(
                if (isColored) COLOR else GRAYSCALE
            )
        }
    }

    @Test
    fun `pages with invalid metadata should be skipped`() = runTest {
        val bytes = byteArrayOf(105, 106, 107)

        writeDocumentDotJson("""{"version":2, "pages":[{"id":"1", "manualRotationDegrees":90}]}""")
        File(processedDir(), "1.jpg").writeBytes(byteArrayOf(101))
        File(processedDir(), "1-90.jpg").writeBytes(bytes)
        assertThat(repo().imageIds()).containsExactly("1")

        writeDocumentDotJson("""{"version":2, "pages":[{"id":"1", "manualRotationDegrees":42}]}""")
        File(processedDir(), "1.jpg").writeBytes(byteArrayOf(101))
        File(processedDir(), "1-42.jpg").writeBytes(bytes)
        assertThat(repo().imageIds()).isEmpty()
    }

    @Test
    fun last_added_source_file() = runTest {
        val repo = repo()
        assertThat(repo.lastAddedSourceFile()).isNull()
        repo.add(jpeg(101), jpeg(51), metadata1, COLOR)
        assertThat(repo.lastAddedSourceFile()).hasBinaryContent(byteArrayOf(51))
        Thread.sleep(1)
        repo.add(jpeg(102), jpeg(52), metadata1, COLOR)
        assertThat(repo.lastAddedSourceFile()).hasBinaryContent(byteArrayOf(52))

        val id = repo.imageIds().last()
        repo.movePage(id, 0)
        assertThat(repo.lastAddedSourceFile()).hasBinaryContent(byteArrayOf(52))
        repo.delete(id)
        assertThat(repo.lastAddedSourceFile()).hasBinaryContent(byteArrayOf(51))

        val repo2 = repo()
        assertThat(repo2.lastAddedSourceFile()).hasBinaryContent(byteArrayOf(51))

        repo2.clear()
        assertThat(repo2.lastAddedSourceFile()).isNull()
    }

    @Test
    fun png_image() = runTest {
        val repo = repo()
        val png = png(101, 102, 103)
        repo.add(png, jpeg(51), metadata1, ColorMode.BLACK_AND_WHITE)
        assertThat(repo.imageIds()).hasSize(1)
        val id = repo.imageIds()[0]
        val key = PageViewKey(id, R0, BLACK_AND_WHITE, 0)
        assertThat(repo.image(key)).isEqualTo(png)
        assertThat(repo.getThumbnail(key)?.bytes).isEqualTo(byteArrayOf(101))

        val repo2 = repo()
        assertThat(repo2.imageIds()).containsExactly(id)
    }

    @Test
    fun `new document gets an identity that is saved as version 3`() = runTest {
        var now = 1_000L
        val repo = repo(clock = { now }, namer = { "Scan at $it" })
        repo.add(jpeg(1), jpeg(2), metadata1, COLOR)

        val info = repo.documentInfo()
        assertThat(info.name).isEqualTo("Scan at 1000")
        assertThat(info.createdAt).isEqualTo(1_000L)
        val saved = Json.decodeFromString<DocumentMetadataV3>(File(processedDir(), "document.json").readText())
        assertThat(saved.version).isEqualTo(3)
        assertThat(saved.id).isEqualTo(info.id)
        assertThat(saved.pages).hasSize(1)

        now = 5_000L
        val reopened = repo(clock = { now })
        assertThat(reopened.documentInfo().id).isEqualTo(info.id)
        assertThat(reopened.documentInfo().name).isEqualTo("Scan at 1000")
    }

    @Test
    fun `document identity and creation date survive page operations while the modification date moves`() =
        runTest {
            var now = 1_000L
            val repo = repo(clock = { now })
            repo.add(jpeg(1), jpeg(2), metadata1, COLOR)
            val first = repo.documentInfo()

            now = 2_000L
            repo.rotate(repo.imageIds()[0], clockwise = true)
            now = 3_000L
            Thread.sleep(2) // page ids are the time in milliseconds: they must differ
            repo.add(jpeg(3), jpeg(4), metadata1, COLOR)
            repo.movePage(repo.imageIds()[1], 0)

            val info = repo.documentInfo()
            assertThat(info.id).isEqualTo(first.id)
            assertThat(info.name).isEqualTo(first.name)
            assertThat(info.createdAt).isEqualTo(first.createdAt)
            assertThat(info.updatedAt).isEqualTo(3_000L)
        }

    @Test
    fun `rename is saved`() = runTest {
        val repo = repo()
        repo.add(jpeg(1), jpeg(2), metadata1, COLOR)

        repo.rename("Invoice March")

        assertThat(repo.documentInfo().name).isEqualTo("Invoice March")
        assertThat(repo().documentInfo().name).isEqualTo("Invoice March")
    }

    @Test
    fun `clear starts a new document with a new identity`() = runTest {
        val repo = repo()
        repo.add(jpeg(1), jpeg(2), metadata1, COLOR)
        val before = repo.documentInfo()

        repo.clear()

        assertThat(repo.documentInfo().id).isNotEqualTo(before.id)
        assertThat(repo.imageIds()).isEmpty()
    }

    @Test
    fun `version 2 file is read as a document with a generated identity and is saved as version 3`() =
        runTest {
            val first = repo()
            val jpeg = jpeg(101)
            first.add(jpeg, jpeg(51), metadata1, COLOR)
            val id = first.imageIds()[0]
            val v2 = Json { encodeDefaults = true }.encodeToString(
                DocumentMetadataV2(pages = listOf(PageV2(id, colorMode = COLOR, isColored = true,
                    quad = quad1.toSerializable())))
            )
            writeDocumentDotJson(v2)

            val repo = repo(namer = { "Migrated" })
            assertThat(repo.documentInfo().name).isEqualTo("Migrated")
            assertThat(repo.documentInfo().id).isNotEmpty()
            assertThat(repo.imageIds()).containsExactly(id)

            repo.rotate(id, clockwise = true)
            val saved = Json.decodeFromString<DocumentMetadataV3>(File(processedDir(), "document.json").readText())
            assertThat(saved.version).isEqualTo(3)
            assertThat(saved.id).isEqualTo(repo.documentInfo().id)
        }

    @Test
    fun `pages are kept and rendered again from their source when processed files are gone`() = runTest {
        val rendered = mutableListOf<Triple<ColorMode, Quad, Adjustments>>()
        val process = { source: Jpeg, metadata: PageMetadata, mode: ColorMode, adjustments: Adjustments ->
            rendered.add(Triple(mode, metadata.normalizedQuad, adjustments))
            jpeg(source.bytes[0], 9)
        }
        val first = repo(process = process)
        first.add(jpeg(101), jpeg(51), metadata1, COLOR)
        val id = first.imageIds()[0]
        val userQuad = Quad(Point(.05, .05), Point(.9, .06), Point(.92, .9), Point(.04, .88))
        val adjustments = Adjustments(brightness = 20, contrast = -10)
        first.setUserQuad(id, userQuad)
        first.setRendering(id, GRAYSCALE, adjustments)
        val saved = first.pages().first()
        val keyBefore = saved.key()
        val imageBefore = first.image(keyBefore)
        rendered.clear()

        jpegFiles(processedDir())!!.forEach { it!!.delete() }
        val reopened = repo(process = process)

        assertThat(reopened.imageIds()).containsExactly(id)
        val page = reopened.pages().first()
        assertThat(page.key()).isEqualTo(keyBefore)
        assertThat(reopened.image(page.key())?.bytes).isEqualTo(imageBefore?.bytes)
        assertThat(rendered).containsExactly(Triple(GRAYSCALE, userQuad, adjustments.forMode(GRAYSCALE)))
        assertThat(reopened.getThumbnail(page.key())).isNotNull()
        assertThat(rendered).hasSize(1)
    }

    @Test
    fun `a page whose source and processed file are both gone is dropped`() = runTest {
        val first = repo()
        first.add(jpeg(101), jpeg(51), metadata1, COLOR)
        jpegFiles(processedDir())!!.forEach { it!!.delete() }
        jpegFiles(sourceDir())!!.forEach { it!!.delete() }

        assertThat(repo().imageIds()).isEmpty()
    }

    @Test
    fun `a rendering that is not the current one of the page is not rendered from the source`() = runTest {
        val first = repo()
        first.add(jpeg(101), jpeg(51), metadata1, COLOR)
        val id = first.imageIds()[0]
        jpegFiles(processedDir())!!.forEach { it!!.delete() }

        val reopened = repo()

        assertThat(reopened.image(PageViewKey(id, R0, GRAYSCALE, 0))).isNull()
    }

    @Test
    fun `a failing render gives no image instead of an error`() = runTest {
        val first = repo()
        first.add(jpeg(101), jpeg(51), metadata1, COLOR)
        val page = first.pages().first()
        jpegFiles(processedDir())!!.forEach { it!!.delete() }

        // As in the app, where the scope has a supervisor job: a failed render must not cancel it
        val reopened = repo(
            process = { _, _, _, _ -> throw IllegalStateException("boom") },
            scope = CoroutineScope(SupervisorJob()),
        )

        assertThat(reopened.image(page.key())).isNull()
    }

    @Test
    fun `every change to the document emits on changes`() = runTest {
        val process = { source: Jpeg, _: PageMetadata, _: ColorMode, _: Adjustments -> jpeg(source.bytes[0], 7) }
        val repo = repo(process = process)
        var count = 0
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.changes.collect { count++ }
        }
        suspend fun expectOneMore(action: suspend () -> Unit) {
            val before = count
            action()
            assertThat(count).describedAs("emissions").isEqualTo(before + 1)
        }

        expectOneMore { repo.add(jpeg(1), jpeg(2), metadata1, COLOR) }
        Thread.sleep(2) // page ids are the time in milliseconds: they must differ
        expectOneMore { repo.add(jpeg(3), jpeg(4), metadata1, COLOR) }
        val ids = repo.imageIds()
        expectOneMore { repo.rotate(ids[0], clockwise = true) }
        expectOneMore { repo.movePage(ids[1], 0) }
        expectOneMore { repo.setUserQuad(ids[0], quad1) }
        expectOneMore { repo.setRendering(ids[0], GRAYSCALE, Adjustments(brightness = 10)) }
        expectOneMore { repo.rename("Renamed") }
        expectOneMore { repo.delete(ids[1]) }
        collector.cancel()
    }

    @Test
    fun `grid thumbnails are bigger than the strip's, and each size is made once`() = runTest {
        var strips = 0
        var grids = 0
        val transformations = object : ImageTransformations {
            override fun rotate(input: EncodedImage, rotationDegrees: Int): EncodedImage = input
            override fun resizeToThumbnail(input: EncodedImage): Jpeg {
                strips++; return jpeg(input.bytes[0], 1)
            }
            override fun resizeToGridThumbnail(input: EncodedImage): Jpeg {
                grids++; return jpeg(input.bytes[0], 2, 2)
            }
            override fun process(source: Jpeg, metadata: PageMetadata, colorMode: ColorMode,
                                 adjustments: Adjustments): Jpeg = throw UnsupportedOperationException()
        }
        val repo = ImageRepository(getFilesDir(), transformations, testScope, Logger { _, _, _ -> })
        repo.add(jpeg(101, 102, 103), jpeg(51), metadata1, COLOR)
        val key = repo.pages().first().key()

        val strip = repo.getThumbnail(key)!!
        val grid = repo.getGridThumbnail(key)!!
        repo.getThumbnail(key); repo.getGridThumbnail(key); repo.getGridThumbnail(key)

        assertThat(strip.bytes).isEqualTo(byteArrayOf(101, 1))
        assertThat(grid.bytes).isEqualTo(byteArrayOf(101, 2, 2))
        assertThat(strips).isEqualTo(1)
        assertThat(grids).isEqualTo(1)
    }

    @Test
    fun `a transformation without a grid size gives the same thumbnail for the grid`() = runTest {
        val repo = repo(resizeToThumbnail = { input -> jpeg(input.bytes[0], 7) })
        repo.add(jpeg(101, 102), jpeg(51), metadata1, COLOR)
        val key = repo.pages().first().key()

        assertThat(repo.getGridThumbnail(key)!!.bytes).isEqualTo(repo.getThumbnail(key)!!.bytes)
    }

    @Test
    fun `grid thumbnails of a page that cannot be shown are null and clearing forgets them`() = runTest {
        val repo = repo()
        repo.add(jpeg(101), jpeg(51), metadata1, COLOR)
        val key = repo.pages().first().key()
        assertThat(repo.getGridThumbnail(key)).isNotNull()

        repo.clear()

        assertThat(repo.getGridThumbnail(key)).isNull()
    }

    private fun processedDir(): File = File(getFilesDir(), PROCESSED_DIR_NAME)
    private fun sourceDir(): File = File(getFilesDir(), SOURCE_DIR_NAME)

    private fun jpegFiles(dir: File): Array<out File?>?
        = dir.listFiles { f -> f.name.endsWith(".jpg") }

    private fun writeDocumentDotJson(json: String) {
        processedDir().mkdirs()
        File(processedDir(), "document.json").writeText(json)
    }

    suspend fun ImageRepository.imageIds(): PersistentList<String> =
        pages().map { it.id }.toPersistentList()

    private fun jpeg(vararg bytes: Byte) = Jpeg(bytes)
    private fun png(vararg bytes: Byte) = Png(bytes)
}
