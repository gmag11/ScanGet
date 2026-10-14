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

/** Width and height of a JPEG or PNG, read from its header without decoding it; null if unknown. */
fun encodedImageSize(bytes: ByteArray): Pair<Int, Int>? {
    if (bytes.size > 24 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) {
        // PNG: 8 bytes of signature, then the IHDR chunk with width and height as big-endian ints
        return int32(bytes, 16) to int32(bytes, 20)
    }
    if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return null
    var i = 2
    while (i + 9 < bytes.size) {
        if (bytes[i] != 0xFF.toByte()) { i++; continue }
        val marker = bytes[i + 1].toInt() and 0xFF
        when {
            marker == 0xFF -> i++ // fill byte
            marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7 -> i += 2 // markers without a length
            // Start of frame (all kinds but DHT, JPG and DAC): height then width, as 16-bit values
            marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC ->
                return int16(bytes, i + 7) to int16(bytes, i + 5)
            else -> i += 2 + int16(bytes, i + 2)
        }
    }
    return null
}

private fun int16(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

private fun int32(b: ByteArray, at: Int) = (int16(b, at) shl 16) or int16(b, at + 2)

/**
 * How much smaller a JPEG of [width]x[height] can be decoded (1, 2, 4 or 8 times) to make a thumbnail
 * whose longer side is [targetSide]: the largest reduction that leaves the decoded image at least
 * that big, so that it is never enlarged afterwards.
 */
fun decodeReductionFor(width: Int, height: Int, targetSide: Int): Int {
    if (targetSide <= 0) return 1
    val longest = maxOf(width, height)
    return listOf(8, 4, 2).firstOrNull { longest / it >= targetSide } ?: 1
}
