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
package org.fairscan.app.ui.screens.export

import org.fairscan.app.data.DocumentInfo
import org.fairscan.app.ui.screens.settings.DefaultFilenameStyle
import java.util.Date

// Characters that cannot be in a file name on common file systems
private val INVALID_FILENAME_CHARS = Regex("""[\\/:*?"<>|\u0000-\u001f]""")

/**
 * Base of the default file name of an export (before the page suffix): the name of the document when
 * the user gave it one, or else the name built from the current date in the default style, as it has
 * always been. A document is considered to have the default name if it is the name generated when it
 * was created.
 */
fun defaultExportBaseName(
    document: DocumentInfo?,
    style: DefaultFilenameStyle,
    now: Date = Date(),
): String {
    val name = document?.name?.replace(INVALID_FILENAME_CHARS, "_")?.trim()
    if (document == null || name.isNullOrEmpty()) return style.filename(now)
    val generated = DefaultFilenameStyle.entries.map { it.filename(Date(document.createdAt)) }
    return if (document.name in generated) style.filename(now) else name
}
