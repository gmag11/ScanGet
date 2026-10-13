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
package org.fairscan.app.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.data.PageRecognition
import org.fairscan.app.platform.processedImage
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.EstimatedDimensions
import org.fairscan.imageprocessing.estimateRealDimensions
import org.fairscan.imageprocessing.resizeForMaxPixels
import org.fairscan.imageprocessing.scaledTo
import org.opencv.core.Mat

fun interface ImageProvider<T> {
    suspend fun get(): T
}

/**
 * The text of a page that is exported: what was already recognized for it, if it is still valid, and
 * what to do with the text recognized while exporting it (to keep it for next time).
 */
class PageOcrSource(
    val stored: PageRecognition?,
    val onRecognized: (PageRecognition) -> Unit = {},
)

data class PageToExport<T>(
    val page: ScanPage,
    val image: ImageProvider<T>,
) {
    /** Where the text layer of a PDF comes from; null means it is recognized and not kept. */
    var ocr: PageOcrSource? = null
        private set

    fun withOcr(source: PageOcrSource?): PageToExport<T> = also { it.ocr = source }

    /**
     * The text for the PDF of this page: the stored one when there is one, otherwise what [recognize]
     * finds (null when nothing can be recognized, which is not the same as a page without text),
     * which is then given to [PageOcrSource.onRecognized] to be kept.
     */
    suspend fun recognizedText(recognize: suspend () -> PageRecognition?): PageRecognition? {
        val source = ocr
        source?.stored?.let { return it }
        return recognize()?.also { source?.onRecognized?.invoke(it) }
    }

    fun estimatedDimensions(): EstimatedDimensions? {
        val metadata = page.metadata
        if (metadata == null)
            return null
        val size = metadata.sourceSize
        if (size == null)
            return null
        val quad = metadata.normalizedQuad.scaledTo(1.0, 1.0, size.width, size.height)
        val realDimensions = estimateRealDimensions(
            quad, size.width.toInt(), size.height.toInt(), metadata.opticalMeasures
        ).snapToStandardFormat()
        return realDimensions.applyRotation(page.totalRotation())
    }
}

private fun EstimatedDimensions.applyRotation(rotation: Rotation): EstimatedDimensions {
    if ((rotation == Rotation.R90 || rotation == Rotation.R270)
        && this is EstimatedDimensions.Physical) {
        return EstimatedDimensions.Physical(heightMm, widthMm)
    }
    return this
}

suspend fun pagesToExport(
    imageRepository: ImageRepository,
    exportQuality: ExportQuality,
    pageFilter: (List<ScanPage>) -> List<ScanPage> = { it },
    ocrFor: suspend (ScanPage) -> PageOcrSource? = { null },
): List<PageToExport<EncodedImage>> {

    val pages = pageFilter(imageRepository.pages())
    val exports = when (exportQuality) {
        ExportQuality.BALANCED -> pages.map {
            PageToExport(it) { image(it, imageRepository) }
        }

        ExportQuality.LOW -> pages.map { page ->
            PageToExport(page) {
                resizeImageForMaxPixels(
                    image = image(page, imageRepository),
                    maxPixels = exportQuality.maxPixels(page.colorMode ?: ColorMode.COLOR).toDouble(),
                    jpegQuality = exportQuality.jpegQuality
                )
            }
        }

        ExportQuality.HIGH -> pages.map { page ->
            PageToExport(page) {
                val source = imageRepository.source(page.id)
                val metadata = page.metadata
                val colorMode = page.colorMode
                if (source != null && metadata != null && colorMode != null) {
                    val rotation = page.totalRotation()
                    processedImage(source, metadata, rotation, colorMode, exportQuality, page.adjustments)
                }
                else
                    image(page, imageRepository)
            }
        }
    }
    // The text kept for the pages may be read from a slow location
    return withContext(Dispatchers.IO) { exports.map { it.withOcr(ocrFor(it.page)) } }
}

suspend fun jpegsToExport(
    imageRepository: ImageRepository,
    exportQuality: ExportQuality,
    pageFilter: (List<ScanPage>) -> List<ScanPage> = { it },
): List<PageToExport<Jpeg>> {
    return pagesToExport(imageRepository, exportQuality, pageFilter).map {
        PageToExport(
            it.page,
            {
                val image = it.image.get()
                when (image) {
                    is Jpeg -> image
                    is Png -> resizeToJpeg(image, exportQuality)
                }
            })
    }
}

fun resizeToJpeg(png: Png, exportQuality: ExportQuality): Jpeg {
    val input = png.toMat()
    // For JPEG, we resize to the same size as for ColorMode.COLOR
    val resized = resizeForMaxPixels(input, exportQuality.maxPixels(ColorMode.COLOR).toDouble())
    val jpeg = Jpeg.fromMat(resized, exportQuality.jpegQuality)
    input.release()
    resized.release()
    return jpeg
}

private suspend fun image(page: ScanPage, imageRepository: ImageRepository): EncodedImage {
    val key = page.key()
    return imageRepository.image(key)
        ?: throw IllegalArgumentException("Image not found for $key")
}

private fun resizeImageForMaxPixels(
    image: EncodedImage,
    maxPixels: Double,
    jpegQuality: Int
): EncodedImage {
    var decoded: Mat? = null
    var resized: Mat? = null
    try {
        decoded = image.toMat()
        resized = resizeForMaxPixels(decoded, maxPixels)
        return when (image) {
            is Jpeg -> Jpeg.fromMat(resized, jpegQuality)
            is Png -> Png.fromMat(resized)
        }
    } finally {
        decoded?.release()
        resized?.release()
    }
}
