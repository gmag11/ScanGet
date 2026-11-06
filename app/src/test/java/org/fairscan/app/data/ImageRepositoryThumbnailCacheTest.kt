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

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
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

class ImageRepositoryThumbnailCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val testScope = TestScope()
    private val quad = Quad(Point(.01, .02), Point(.1, .03), Point(.11, .12), Point(.03, .09))
    private val metadata = PageMetadata(
        quad, R90, COLOR, ImageSize(1600, 1200), OpticalMeasures(CameraIntrinsics(42f, 43f), 44f))

    // A file that looks like a JPEG to the cache: FFD8 ... FFD9
    private fun jpeg(first: Int, second: Int = 0) = Jpeg(ByteArray(20) { 0 }.also {
        it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = first.toByte(); it[3] = second.toByte()
        it[18] = 0xFF.toByte(); it[19] = 0xD9.toByte()
    })

    private class Counts { var process = 0; var grid = 0; var strip = 0 }

    private fun transformations(counts: Counts) = object : ImageTransformations {
        override fun rotate(input: EncodedImage, rotationDegrees: Int): EncodedImage = input
        override fun resizeToThumbnail(input: EncodedImage): Jpeg { counts.strip++; return jpeg(input.bytes[2].toInt(), 1) }
        override fun resizeToGridThumbnail(input: EncodedImage): Jpeg { counts.grid++; return jpeg(input.bytes[2].toInt(), 2) }
        override fun process(source: Jpeg, metadata: PageMetadata, colorMode: ColorMode,
                             adjustments: Adjustments): Jpeg {
            counts.process++
            return jpeg(source.bytes[2].toInt() + colorMode.ordinal, adjustments.brightness)
        }
    }

    private val cacheDir by lazy { File(folder.root, "thumbnails") }
    private fun cache() = ThumbnailCache(cacheDir, 240, 480)

    private fun repository(name: String, counts: Counts, cache: ThumbnailCache? = cache()) =
        ImageRepository(folder.newFolder(name), transformations(counts), testScope, Logger { _, _, _ -> },
            thumbnailDiskCache = cache)

    // The document as a library holds it: sources and the editing data
    private suspend fun ImageRepository.openDocument(document: LoadedDocument, source: ByteArray) =
        load(document) { source }

    private suspend fun savedDocument(): Pair<LoadedDocument, ByteArray> {
        val counts = Counts()
        val repo = repository("first", counts, cache = null)
        val source = jpeg(40).bytes
        repo.add(jpeg(1), Jpeg(source), metadata, COLOR)
        val page = repo.snapshot().pages.single()
        return LoadedDocument(repo.documentInfo(), listOf(page)) to source
    }

    @Test
    fun `a thumbnail made in one session is read from disk in the next without rendering the page`() = runTest {
        val (document, source) = savedDocument()
        val first = Counts()
        val repo1 = repository("s1", first)
        repo1.openDocument(document, source)
        val key = repo1.pages().single().key()
        val made = repo1.getGridThumbnail(key)!!
        assertThat(first.process).isEqualTo(1)
        assertThat(first.grid).isEqualTo(1)

        // Another session: nothing in memory, the private working directory is empty
        val second = Counts()
        val repo2 = repository("s2", second)
        repo2.openDocument(document, source)

        val again = repo2.getGridThumbnail(repo2.pages().single().key())!!

        assertThat(again.bytes).isEqualTo(made.bytes)
        assertThat(second.process).isEqualTo(0)
        assertThat(second.grid).isEqualTo(0)
    }

    @Test
    fun `grid and strip thumbnails are kept apart`() = runTest {
        val (document, source) = savedDocument()
        val counts = Counts()
        val repo1 = repository("s1", counts)
        repo1.openDocument(document, source)
        val key = repo1.pages().single().key()
        repo1.getGridThumbnail(key); repo1.getThumbnail(key)

        val second = Counts()
        val repo2 = repository("s2", second)
        repo2.openDocument(document, source)
        val key2 = repo2.pages().single().key()

        assertThat(repo2.getGridThumbnail(key2)!!.bytes[3]).isEqualTo(2)
        assertThat(repo2.getThumbnail(key2)!!.bytes[3]).isEqualTo(1)
        assertThat(second.process + second.grid + second.strip).isEqualTo(0)
    }

    @Test
    fun `an edited page makes its thumbnail again and leaves the others`() = runTest {
        val (document, source) = savedDocument()
        val counts = Counts()
        val repo = repository("s1", counts)
        repo.openDocument(document, source)
        val page = repo.pages().single()
        repo.getGridThumbnail(page.key())
        assertThat(counts.grid).isEqualTo(1)

        repo.setRendering(page.id, GRAYSCALE, Adjustments.NEUTRAL)
        val edited = repo.pages().single().key()
        repo.getGridThumbnail(edited)

        assertThat(counts.grid).isEqualTo(2) // the new rendering
        // Both renderings are on disk now: going back finds the first without making it again
        val other = Counts()
        val repo2 = repository("s2", other)
        repo2.openDocument(document, source)
        repo2.getGridThumbnail(repo2.pages().single().key())
        assertThat(other.grid).isEqualTo(0)
    }

    @Test
    fun `a lost cache directory only costs making the thumbnails again`() = runTest {
        val (document, source) = savedDocument()
        val repo1 = repository("s1", Counts())
        repo1.openDocument(document, source)
        repo1.getGridThumbnail(repo1.pages().single().key())
        cacheDir.deleteRecursively()

        val counts = Counts()
        val repo2 = repository("s2", counts)
        repo2.openDocument(document, source)
        val thumbnail = repo2.getGridThumbnail(repo2.pages().single().key())

        assertThat(thumbnail).isNotNull()
        assertThat(counts.grid).isEqualTo(1)
    }

    @Test
    fun `without a cache thumbnails are made as before`() = runTest {
        val (document, source) = savedDocument()
        val counts = Counts()
        val repo = repository("s1", counts, cache = null)
        repo.openDocument(document, source)

        assertThat(repo.getGridThumbnail(repo.pages().single().key())).isNotNull()
        assertThat(counts.grid).isEqualTo(1)
        assertThat(cacheDir).doesNotExist()
    }
}
