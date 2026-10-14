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

import org.fairscan.app.domain.EncodedImage
import org.fairscan.app.domain.Jpeg
import org.fairscan.app.domain.Png
import java.io.File

/** Which thumbnail of a page: the small one of the page strip, or the bigger one of the grid. */
enum class ThumbnailKind(val code: Char) { STRIP('s'), GRID('g') }

/**
 * The thumbnails of pages kept in a directory of the app's private cache, so that they are not made
 * again each time a document is opened. One file per page, rendering of the page ([renderKey], see
 * [org.fairscan.app.domain.PageViewKey.renderKey]), kind and size: a page that was edited, or a
 * thumbnail size that changed, simply does not find its file. It is only a cache: the system or the
 * user may empty the directory, and what is missing or unreadable is made again.
 *
 * When the files go over [maxBytes] the least recently used ones (by modification time, which a read
 * refreshes) are removed.
 */
class ThumbnailCache(
    private val dir: File,
    private val stripSizePx: Int,
    private val gridSizePx: Int,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    private fun sizeOf(kind: ThumbnailKind) = if (kind == ThumbnailKind.GRID) gridSizePx else stripSizePx

    private fun file(pageId: String, renderKey: String, kind: ThumbnailKind, extension: String) =
        File(dir, "$pageId.$renderKey.${kind.code}${sizeOf(kind)}.$extension")

    /** The thumbnail kept for the page as rendered with [renderKey], or null if there is none that can be used. */
    fun read(pageId: String, renderKey: String, kind: ThumbnailKind): EncodedImage? {
        for (extension in listOf("png", "jpg")) {
            val file = file(pageId, renderKey, kind, extension)
            if (!file.isFile) continue
            val bytes = try {
                file.readBytes()
            } catch (_: Exception) {
                continue
            }
            if (!looksComplete(bytes, extension)) {
                file.delete()
                continue
            }
            file.setLastModified(System.currentTimeMillis())
            return if (extension == "png") Png(bytes) else Jpeg(bytes)
        }
        return null
    }

    /** Keeps [image] as the thumbnail of the page; a failure to write it is not a problem. */
    fun write(pageId: String, renderKey: String, kind: ThumbnailKind, image: EncodedImage) {
        try {
            dir.mkdirs()
            val extension = if (image is Png) "png" else "jpg"
            // The other format of the same thumbnail (the page changed from color to black and white
            // would have another key, but be safe) is not kept next to this one
            val target = file(pageId, renderKey, kind, extension)
            val temp = File(dir, target.name + ".tmp")
            temp.writeBytes(image.bytes)
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) temp.delete()
            }
            prune()
        } catch (_: Exception) {
            // the thumbnail will be made again
        }
    }

    /** Removes the oldest files until what is kept is under [maxBytes]. */
    internal fun prune() {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            total -= file.length()
            file.delete()
        }
    }

    // A file cut short (interrupted write, full disk) is not a thumbnail
    private fun looksComplete(bytes: ByteArray, extension: String): Boolean {
        if (bytes.size < 12) return false
        return if (extension == "png") {
            bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
                bytes[bytes.size - 8] == 'I'.code.toByte() && bytes[bytes.size - 7] == 'E'.code.toByte() &&
                bytes[bytes.size - 6] == 'N'.code.toByte() && bytes[bytes.size - 5] == 'D'.code.toByte()
        } else {
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
                bytes[bytes.size - 2] == 0xFF.toByte() && bytes[bytes.size - 1] == 0xD9.toByte()
        }
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 50L * 1024 * 1024
    }
}
