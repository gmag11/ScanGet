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
import org.fairscan.imageprocessing.Adjustments
import org.fairscan.imageprocessing.ColorMode

@Serializable
data class DocumentMetadataV1(
    val version: Int = 1,
    val pages: List<PageV1>
)

@Serializable
data class PageV1(
    val file: String
)

@Serializable
data class DocumentMetadataV2(
    val version: Int = 2,
    val pages: List<PageV2>
)

@Serializable
data class PageV2(
    val id: String,
    val baseRotationDegrees: Int = 0,
    val manualRotationDegrees: Int = 0,
    val quad: NormalizedQuad? = null,
    val quadVersion: Int = 0,
    val userQuad: NormalizedQuad? = null,
    val isColored: Boolean? = null,
    val colorMode: ColorMode? = null,
    val focalLength: Float? = null,
    val sensorWidth: Float? = null,
    val subjectDistance: Float? = null,
    val sourceWidth: Int? = null,
    val sourceHeight: Int? = null,
    // Null means neutral adjustments, which is also what pages saved before adjustments existed have.
    val adjustments: AdjustmentsV1? = null,
)

@Serializable
data class AdjustmentsV1(
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val detail: Int = 0,
    val shadowRemoval: Int = 0,
    val threshold: Int = 0,
)

/** Neutral adjustments are stored as null, so that untouched pages keep the same JSON as before. */
fun Adjustments.toSerializable(): AdjustmentsV1? =
    if (isNeutral) null
    else AdjustmentsV1(brightness, contrast, saturation, detail, shadowRemoval, threshold)

/** Values come from a file on disk, so they are clamped instead of rejected. */
fun AdjustmentsV1?.toAdjustments(): Adjustments =
    if (this == null) Adjustments.NEUTRAL
    else Adjustments.coerced(brightness, contrast, saturation, detail, shadowRemoval, threshold)

@Serializable
data class NormalizedQuad(
    val topLeft: PointD,
    val topRight: PointD,
    val bottomRight: PointD,
    val bottomLeft: PointD
)

@Serializable
data class PointD(
    val x: Double,
    val y: Double
)
