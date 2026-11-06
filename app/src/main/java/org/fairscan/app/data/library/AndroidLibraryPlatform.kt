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
package org.fairscan.app.data.library

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile

class AndroidLibraryPlatform(private val context: Context) : LibraryPlatform {

    private val resolver: ContentResolver get() = context.contentResolver

    override fun hasSafPermission(uri: String): Boolean =
        resolver.persistedUriPermissions.any {
            it.uri.toString() == uri && it.isReadPermission && it.isWritePermission
        }

    override fun isSafFolderReachable(uri: String): Boolean = try {
        DocumentFile.fromTreeUri(context, uri.toUri())?.let { it.exists() && it.canWrite() } == true
    } catch (_: Exception) {
        false
    }

    override fun safStore(uri: String): LibraryStore =
        TreeLibraryStore(ContentResolverDocumentTree(resolver, uri.toUri()))

    override fun safFolderName(uri: String): String? = try {
        DocumentFile.fromTreeUri(context, uri.toUri())?.name
    } catch (_: Exception) {
        null
    }

    override fun releaseSafPermission(uri: String) {
        try {
            resolver.releasePersistableUriPermission(uri.toUri(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: Exception) {
            // The permission was not held: nothing to release
        }
    }
}
