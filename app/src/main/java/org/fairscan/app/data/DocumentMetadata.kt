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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.fairscan.imageprocessing.Adjustments
import org.fairscan.imageprocessing.ColorMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

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

/** A document of the library: the pages of V2 plus the identity and dates of the document. */
@Serializable
data class DocumentMetadataV3(
    val version: Int = 3,
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pages: List<PageV2>,
)

/** What identifies a document in the library, apart from its pages. Dates are epoch millis. */
data class DocumentInfo(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class LoadedDocument(val info: DocumentInfo, val pages: List<PageV2>)

fun DocumentMetadataV3.toInfo() = DocumentInfo(id, name, createdAt, updatedAt)

fun DocumentInfo.toMetadata(pages: List<PageV2>) =
    DocumentMetadataV3(id = id, name = name, createdAt = createdAt, updatedAt = updatedAt, pages = pages)

/**
 * Reads document.json of any version. Versions before 3 have no document identity, so [legacyInfo]
 * provides it (a new id, the default name and dates).
 */
fun parseDocumentMetadata(text: String, legacyInfo: () -> DocumentInfo): LoadedDocument {
    val element = Json.parseToJsonElement(text)
    val version = element.jsonObject["version"]?.jsonPrimitive?.int ?: 1
    return when (version) {
        1 -> LoadedDocument(
            legacyInfo(),
            Json.decodeFromJsonElement<DocumentMetadataV1>(element).pages.map { pageFromLegacyFileName(it.file) },
        )
        2 -> LoadedDocument(legacyInfo(), Json.decodeFromJsonElement<DocumentMetadataV2>(element).pages)
        3 -> Json.decodeFromJsonElement<DocumentMetadataV3>(element).let {
            LoadedDocument(DocumentInfo(it.id, it.name, it.createdAt, it.updatedAt), it.pages)
        }
        else -> error("Unsupported metadata version: $version")
    }
}

fun pageFromLegacyFileName(fileName: String): PageV2 {
    val name = fileName.removeSuffix(".jpg")
    val dashIndex = name.lastIndexOf('-')
    val id = if (dashIndex >= 0) name.substring(0, dashIndex) else name
    return PageV2(id)
}

/** Stable identifier of a document, also the name of its directory: `yyyyMMdd-HHmmss-xxxx`. */
fun newDocumentId(now: Long, random: Random = Random): String {
    val time = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(now))
    return "$time-%04x".format(random.nextInt(0x10000))
}

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
