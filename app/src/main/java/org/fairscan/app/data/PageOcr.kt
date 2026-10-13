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

import kotlinx.serialization.Serializable
import org.fairscan.imageprocessing.ImageRect
import org.fairscan.imageprocessing.OcrTextBox

/** One recognized word of [PageOcrV1], with the positions the PDF text layer needs. */
@Serializable
data class OcrWordV1(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val lineHeight: Int,
    val lineBottom: Int,
)

/**
 * The text recognized in a page, as kept in the library. [renderKey] identifies the rendering of the
 * page it was computed from and [imageWidth]x[imageHeight] is the size of the recognized image: the
 * positions are in pixels of that image.
 */
@Serializable
data class PageOcrV1(
    val version: Int = 1,
    val renderKey: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val words: List<OcrWordV1>,
) {
    fun toTextBoxes(): List<OcrTextBox> = words.map {
        OcrTextBox(it.text, ImageRect(it.left, it.top, it.right, it.bottom), it.lineHeight, it.lineBottom)
    }

    /** The recognized words as one text, for searching. */
    fun text(): String = words.joinToString(" ") { it.text }
}

/** What recognizing a page gives: its words and the size of the image they refer to. */
data class PageRecognition(
    val textBoxes: List<OcrTextBox>,
    val imageWidth: Int,
    val imageHeight: Int,
) {
    fun toStored(renderKey: String) = PageOcrV1(
        renderKey = renderKey,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        words = textBoxes.map {
            OcrWordV1(it.text, it.box.left, it.box.top, it.box.right, it.box.bottom, it.lineHeight, it.lineBottom)
        },
    )
}

fun PageOcrV1.toRecognition() = PageRecognition(toTextBoxes(), imageWidth, imageHeight)
