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

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import java.util.zip.CRC32
import kotlin.random.Random

/** Deterministic test pages for the post-processing tests. */
object TestPages {

    /**
     * Cream paper with a horizontal lighting gradient and a darker band on the right, black "text"
     * bars, one red and one blue block, and mild noise.
     */
    fun colorPage(width: Int = 300, height: Int = 400): Mat {
        val rnd = Random(1)
        val data = ByteArray(width * height * 3)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val light = 235 - 60 * x / width - if (x > width * 3 / 4) 30 else 0
                val i = (y * width + x) * 3
                val noise = rnd.nextInt(-3, 4)
                data[i] = (light - 18 + noise).coerceIn(0, 255).toByte()     // B
                data[i + 1] = (light - 6 + noise).coerceIn(0, 255).toByte()  // G
                data[i + 2] = (light + noise).coerceIn(0, 255).toByte()      // R
            }
        }
        val mat = Mat(height, width, CvType.CV_8UC3)
        mat.put(0, 0, data)
        for (row in 0 until 12) {
            mat.submat(Rect(30, 40 + row * 24, 140 + (row % 3) * 30, 8)).setTo(Scalar(30.0, 30.0, 30.0))
        }
        mat.submat(Rect(200, 60, 60, 60)).setTo(Scalar(40.0, 40.0, 190.0))  // red (BGR)
        mat.submat(Rect(200, 200, 60, 60)).setTo(Scalar(190.0, 80.0, 40.0)) // blue (BGR)
        return mat
    }

    fun checksum(mat: Mat): Long {
        val bytes = ByteArray((mat.total() * mat.elemSize()).toInt())
        mat.get(0, 0, bytes)
        return CRC32().apply { update(bytes) }.value
    }
}
