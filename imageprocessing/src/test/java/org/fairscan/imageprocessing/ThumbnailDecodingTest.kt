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
package org.fairscan.imageprocessing

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ThumbnailDecodingTest {

    private fun jpeg(width: Int, height: Int, sofMarker: Int = 0xC0, vararg segmentsBefore: ByteArray): ByteArray {
        val out = mutableListOf<Byte>(0xFF.toByte(), 0xD8.toByte())
        segmentsBefore.forEach { seg -> out.addAll(seg.toList()) }
        out.addAll(listOf(0xFF, sofMarker, 0x00, 0x11, 0x08).map { it.toByte() })
        out.addAll(listOf(height shr 8, height and 0xFF, width shr 8, width and 0xFF).map { it.toByte() })
        out.addAll(List(20) { 0 })
        return out.toByteArray()
    }

    private fun app0(): ByteArray = byteArrayOf(0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10) + ByteArray(14)

    private fun png(width: Int, height: Int): ByteArray {
        val b = ByteArray(33)
        b[0] = 0x89.toByte(); b[1] = 'P'.code.toByte(); b[2] = 'N'.code.toByte(); b[3] = 'G'.code.toByte()
        for (i in 0..3) {
            b[16 + i] = (width shr (24 - 8 * i)).toByte()
            b[20 + i] = (height shr (24 - 8 * i)).toByte()
        }
        return b
    }

    @Test
    fun `the size of a JPEG is read from its frame header`() {
        assertThat(encodedImageSize(jpeg(4000, 3000))).isEqualTo(4000 to 3000)
        assertThat(encodedImageSize(jpeg(640, 480, sofMarker = 0xC2))).isEqualTo(640 to 480) // progressive
    }

    @Test
    fun `segments before the frame header are skipped`() {
        assertThat(encodedImageSize(jpeg(1200, 1600, 0xC0, app0(), app0()))).isEqualTo(1200 to 1600)
    }

    @Test
    fun `the size of a PNG is read from its header`() {
        assertThat(encodedImageSize(png(2480, 3508))).isEqualTo(2480 to 3508)
    }

    @Test
    fun `something that is not an image or is cut short has no size`() {
        assertThat(encodedImageSize(ByteArray(0))).isNull()
        assertThat(encodedImageSize(byteArrayOf(1, 2, 3, 4, 5))).isNull()
        assertThat(encodedImageSize(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))).isNull()
    }

    @Test
    fun `the decoding is reduced as much as the thumbnail allows and never more`() {
        // A 4000x3000 page for a 480 px thumbnail: 1/8 gives 500 px, enough
        assertThat(decodeReductionFor(4000, 3000, 480)).isEqualTo(8)
        // 1/8 would give 250 px, too small for 480: 1/4 (500 px) is the most
        assertThat(decodeReductionFor(2000, 1500, 480)).isEqualTo(4)
        assertThat(decodeReductionFor(1000, 800, 480)).isEqualTo(2)
        // The page is already about the size of the thumbnail
        assertThat(decodeReductionFor(700, 500, 480)).isEqualTo(1)
        assertThat(decodeReductionFor(480, 480, 480)).isEqualTo(1)
        // The longer side counts, whatever the orientation
        assertThat(decodeReductionFor(3000, 4000, 480)).isEqualTo(8)
    }

    @Test
    fun `the reduced image is never smaller than the thumbnail`() {
        for (longest in 100..6000 step 37) {
            for (target in listOf(120, 240, 480, 720)) {
                val reduction = decodeReductionFor(longest, longest * 3 / 4, target)
                assertThat(reduction).isIn(1, 2, 4, 8)
                if (longest >= target) assertThat(longest / reduction).isGreaterThanOrEqualTo(target)
            }
        }
    }
}
