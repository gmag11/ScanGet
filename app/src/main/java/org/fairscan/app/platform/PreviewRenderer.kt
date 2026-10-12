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
package org.fairscan.app.platform

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fairscan.app.domain.Jpeg
import org.fairscan.app.domain.PageMetadata
import org.fairscan.app.domain.Rotation
import org.fairscan.imageprocessing.Adjustments
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.enhanceCapturedImage
import org.fairscan.imageprocessing.extractDocument
import org.fairscan.imageprocessing.rotate
import org.fairscan.imageprocessing.scaledTo
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Renders previews of one page for the adjustments panel. The source photo is decoded and warped
 * once, to a reduced size, and every filter is then applied to that copy, which makes a render
 * fast enough to follow a slider. It follows the same steps as the final render (see
 * [processedImage]): crop with the page's quad, filter, rotate. Brightness, contrast and
 * saturation are not applied here: the screen applies them on top of the preview.
 *
 * Call [close] when the panel closes to release the native memory of the reduced copy.
 */
class PreviewRenderer(
    private val source: Jpeg,
    private val metadata: PageMetadata,
    private val manualRotation: Rotation,
) : AutoCloseable {

    private val lock = Any()
    private var warped: Mat? = null
    private var closed = false
    private var running = 0

    suspend fun render(colorMode: ColorMode, adjustments: Adjustments): Bitmap? =
        withContext(Dispatchers.Default) {
            val base = synchronized(lock) {
                if (closed) return@withContext null
                running++
                warped ?: warpSource().also { warped = it }
            }
            try {
                val filtered = enhanceCapturedImage(base, colorMode, PREVIEW_MAX_PIXELS, adjustments)
                val rotated = rotate(filtered, manualRotation.degrees)
                try {
                    toBitmap(rotated)
                } finally {
                    filtered.release()
                    rotated.release()
                }
            } finally {
                synchronized(lock) {
                    running--
                    if (closed && running == 0) release()
                }
            }
        }

    override fun close() {
        synchronized(lock) {
            closed = true
            // A render that is still running (cancelled ones cannot be interrupted) releases it.
            if (running == 0) release()
        }
    }

    // The perspective-corrected page with no filter, at preview size, rotated by the base rotation.
    private fun warpSource(): Mat {
        val sourceMat = source.toMat()
        try {
            val quad = metadata.normalizedQuad.scaledTo(1, 1, sourceMat.width(), sourceMat.height())
            return extractDocument(sourceMat, quad, metadata.baseRotation.degrees, ColorMode.ORIGINAL,
                PREVIEW_MAX_PIXELS, metadata.opticalMeasures)
        } finally {
            sourceMat.release()
        }
    }

    private fun release() {
        warped?.release()
        warped = null
    }

    private fun toBitmap(image: Mat): Bitmap {
        val rgba = Mat()
        val conversion = when (image.channels()) {
            1 -> Imgproc.COLOR_GRAY2RGBA
            4 -> Imgproc.COLOR_BGRA2RGBA
            else -> Imgproc.COLOR_BGR2RGBA
        }
        Imgproc.cvtColor(image, rgba, conversion)
        val bitmap = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgba, bitmap)
        rgba.release()
        return bitmap
    }

    companion object {
        // About what a phone screen shows, so a render takes a fraction of a full-size one.
        const val PREVIEW_MAX_PIXELS = 1_000_000L
    }
}
