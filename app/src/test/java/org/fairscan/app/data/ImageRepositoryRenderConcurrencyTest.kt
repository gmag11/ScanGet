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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.assertj.core.api.Assertions.assertThat
import org.fairscan.app.domain.EncodedImage
import org.fairscan.app.domain.Jpeg
import org.fairscan.app.domain.PageMetadata
import org.fairscan.app.domain.Rotation.R90
import org.fairscan.imageprocessing.Adjustments
import org.fairscan.imageprocessing.CameraIntrinsics
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.ColorMode.COLOR
import org.fairscan.imageprocessing.ImageSize
import org.fairscan.imageprocessing.OpticalMeasures
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger

class ImageRepositoryRenderConcurrencyTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val quad = Quad(Point(.01, .02), Point(.1, .03), Point(.11, .12), Point(.03, .09))
    private val metadata = PageMetadata(
        quad, R90, COLOR, ImageSize(1600, 1200), OpticalMeasures(CameraIntrinsics(42f, 43f), 44f))

    private val running = AtomicInteger()
    private val highest = AtomicInteger()
    private val rendered = AtomicInteger()

    // Rendering takes a while, so that several renders would overlap if nothing limited them
    private val transformations = object : ImageTransformations {
        override fun rotate(input: EncodedImage, rotationDegrees: Int): EncodedImage = input
        override fun resizeToThumbnail(input: EncodedImage): Jpeg = Jpeg(byteArrayOf(input.bytes[0]))
        override fun process(source: Jpeg, metadata: PageMetadata, colorMode: ColorMode,
                             adjustments: Adjustments): Jpeg {
            val now = running.incrementAndGet()
            highest.updateAndGet { maxOf(it, now) }
            Thread.sleep(60)
            running.decrementAndGet()
            rendered.incrementAndGet()
            return Jpeg(byteArrayOf(source.bytes[0], 1))
        }
    }

    @Test
    fun `pages are rendered from their originals a few at a time`() {
      runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val pages = 8
        val document = ImageRepository(folder.newFolder("first"), transformations, scope, Logger { _, _, _ -> })
        repeat(pages) { i ->
            document.add(Jpeg(byteArrayOf(i.toByte(), 9)), Jpeg(byteArrayOf(i.toByte())), metadata, COLOR)
            Thread.sleep(2) // page ids are the time in milliseconds: they must differ
        }
        val saved = LoadedDocument(document.documentInfo(), document.snapshot().pages)

        // Opened again: the processed files are gone, every page has to be rendered
        val reopened = ImageRepository(folder.newFolder("second"), transformations, scope, Logger { _, _, _ -> })
        reopened.load(saved) { pageId -> byteArrayOf(pageId.last().code.toByte()) }
        val keys = reopened.pages().map { it.key() }

        keys.map { scope.async { reopened.image(it) } }.awaitAll()

        assertThat(rendered.get()).isEqualTo(pages)
        assertThat(highest.get()).isLessThanOrEqualTo(MAX_PARALLEL_RENDERS)
        assertThat(highest.get()).isGreaterThanOrEqualTo(1)
      }
    }
}
