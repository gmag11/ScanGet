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

import org.fairscan.app.data.PageRecognition

/** Recognizes the text of page images. */
interface TextRecognizer {

    /** False while no OCR language is usable: [recognize] would find nothing. */
    val isAvailable: Boolean

    /**
     * The words of [image], or null if it could not be recognized. [halve] recognizes it at half its
     * size, as the PDF export does with black and white pages (four times the pixels of the others).
     */
    suspend fun recognize(image: EncodedImage, halve: Boolean): PageRecognition?
}
