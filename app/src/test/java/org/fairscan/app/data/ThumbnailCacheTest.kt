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

import org.assertj.core.api.Assertions.assertThat
import org.fairscan.app.domain.Jpeg
import org.fairscan.app.domain.Png
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ThumbnailCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dir by lazy { File(folder.root, "thumbs") }

    // Minimal well-formed files: a JPEG starts FFD8 and ends FFD9, a PNG ends with IEND and its CRC
    private fun jpeg(size: Int = 100, fill: Int = 1) =
        Jpeg(ByteArray(size) { fill.toByte() }.also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[size - 2] = 0xFF.toByte(); it[size - 1] = 0xD9.toByte() })

    private fun png(size: Int = 100) = Png(ByteArray(size) { 2 }.also {
        it[0] = 0x89.toByte(); it[1] = 'P'.code.toByte()
        "IEND".forEachIndexed { i, c -> it[size - 8 + i] = c.code.toByte() }
    })

    private fun cache(maxBytes: Long = ThumbnailCache.DEFAULT_MAX_BYTES, grid: Int = 480, strip: Int = 240) =
        ThumbnailCache(dir, strip, grid, maxBytes)

    @Test
    fun `a thumbnail that was kept is found again, also by a new cache`() {
        cache().write("100", "aaaaaaaa", ThumbnailKind.GRID, jpeg(fill = 7))

        val found = cache().read("100", "aaaaaaaa", ThumbnailKind.GRID)

        assertThat(found).isInstanceOf(Jpeg::class.java)
        assertThat(found!!.bytes).isEqualTo(jpeg(fill = 7).bytes)
    }

    @Test
    fun `a black and white thumbnail keeps its PNG format`() {
        cache().write("100", "aaaaaaaa", ThumbnailKind.GRID, png())

        assertThat(cache().read("100", "aaaaaaaa", ThumbnailKind.GRID)).isInstanceOf(Png::class.java)
    }

    @Test
    fun `nothing is found for another page, rendering, kind or size`() {
        cache().write("100", "aaaaaaaa", ThumbnailKind.GRID, jpeg())

        assertThat(cache().read("200", "aaaaaaaa", ThumbnailKind.GRID)).isNull()
        assertThat(cache().read("100", "bbbbbbbb", ThumbnailKind.GRID)).isNull()
        assertThat(cache().read("100", "aaaaaaaa", ThumbnailKind.STRIP)).isNull()
        assertThat(cache(grid = 720).read("100", "aaaaaaaa", ThumbnailKind.GRID)).isNull()
    }

    @Test
    fun `a file that was cut short is not used and is removed`() {
        cache().write("100", "aaaaaaaa", ThumbnailKind.GRID, jpeg(size = 200))
        val file = dir.listFiles()!!.single()
        file.writeBytes(file.readBytes().copyOf(50)) // truncated

        assertThat(cache().read("100", "aaaaaaaa", ThumbnailKind.GRID)).isNull()
        assertThat(file).doesNotExist()
    }

    @Test
    fun `writing replaces the previous thumbnail and leaves no temporary file`() {
        val c = cache()
        c.write("100", "aaaaaaaa", ThumbnailKind.GRID, jpeg(fill = 1))
        c.write("100", "aaaaaaaa", ThumbnailKind.GRID, jpeg(fill = 9))

        assertThat(c.read("100", "aaaaaaaa", ThumbnailKind.GRID)!!.bytes).isEqualTo(jpeg(fill = 9).bytes)
        assertThat(dir.list()!!.filter { it.endsWith(".tmp") }).isEmpty()
        assertThat(dir.list()!!).hasSize(1)
    }

    @Test
    fun `the least recently used thumbnails are removed when the cache is too big`() {
        val big = cache()
        for (page in listOf("1", "2", "3")) {
            big.write(page, "aaaaaaaa", ThumbnailKind.GRID, jpeg(size = 200))
            dir.listFiles()!!.first { it.name.startsWith("$page.") }.setLastModified(1_000L * page.toInt())
        }
        // Page 1 is the oldest, but it is read again: pages 2 and 3 are now older than it
        big.read("1", "aaaaaaaa", ThumbnailKind.GRID)

        val small = cache(maxBytes = 450)
        small.write("4", "aaaaaaaa", ThumbnailKind.GRID, jpeg(size = 200))

        assertThat(dir.listFiles()!!.sumOf { it.length() }).isLessThanOrEqualTo(450)
        assertThat(small.read("2", "aaaaaaaa", ThumbnailKind.GRID)).isNull()
        assertThat(small.read("1", "aaaaaaaa", ThumbnailKind.GRID)).isNotNull()
        assertThat(small.read("4", "aaaaaaaa", ThumbnailKind.GRID)).isNotNull()
    }

    @Test
    fun `an emptied directory is only a cache miss`() {
        val c = cache()
        c.write("100", "aaaaaaaa", ThumbnailKind.GRID, jpeg())
        dir.deleteRecursively()

        assertThat(c.read("100", "aaaaaaaa", ThumbnailKind.GRID)).isNull()
        c.write("100", "aaaaaaaa", ThumbnailKind.GRID, jpeg()) // the directory is created again
        assertThat(c.read("100", "aaaaaaaa", ThumbnailKind.GRID)).isNotNull()
    }
}
