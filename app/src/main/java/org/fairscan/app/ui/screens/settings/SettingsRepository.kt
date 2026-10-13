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
package org.fairscan.app.ui.screens.settings

import android.content.Context
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.fairscan.app.R
import org.fairscan.app.data.library.LastOpenedDocument
import org.fairscan.app.data.library.LibraryLocationSettings
import org.fairscan.app.data.library.StoredLibraryLocation
import org.fairscan.app.domain.ExportQuality
import org.fairscan.imageprocessing.ColorMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsRepository(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : LibraryLocationSettings, LastOpenedDocument {

    private val DEFAULT_COLOR_MODE = stringPreferencesKey("default_color_mode")
    private val DEFAULT_FILENAME_STYLE = stringPreferencesKey("default_filename_style")
    private val EXPORT_DIR_URI = stringPreferencesKey("export_dir_uri")
    private val EXPORT_FORMAT = stringPreferencesKey("export_format")
    private val EXPORT_QUALITY = stringPreferencesKey("export_quality")
    private val TORCH_ALWAYS_ON = booleanPreferencesKey("torch_always_on")
    private val OCR_ON_SAVE = booleanPreferencesKey("ocr_on_save")
    private val LIBRARY_LOCATION_KIND = stringPreferencesKey("library_location_kind")
    private val LIBRARY_LOCATION = stringPreferencesKey("library_location")
    private val LIBRARY_LOCATION_NAME = stringPreferencesKey("library_location_name")
    private val LAST_OPENED_DOCUMENT = stringPreferencesKey("last_opened_document_id")

    val defaultColorMode: Flow<DefaultColorMode> =
        dataStore.data.map { prefs -> parseDefaultColorMode(prefs[DEFAULT_COLOR_MODE]) }

    val torchAlwaysOn: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[TORCH_ALWAYS_ON] ?: false }

    /** Whether the text of a page is recognized in the background when it is saved. */
    val ocrOnSave: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[OCR_ON_SAVE] ?: true }

    val exportDirUri: Flow<String?> =
        dataStore.data.map { prefs ->
            prefs[EXPORT_DIR_URI]
        }

    fun resolveExportDirName(uri: String): String? {
        return DocumentFile.fromTreeUri(context, uri.toUri())?.name
    }

    val exportFormat: Flow<ExportFormat> =
        dataStore.data.map { prefs ->
            when (prefs[EXPORT_FORMAT]) {
                "JPEG" -> ExportFormat.JPEG
                "PDF", null -> ExportFormat.PDF
                else -> ExportFormat.PDF
            }
        }

    val exportQuality: Flow<ExportQuality> =
        dataStore.data.map { prefs ->
            when (prefs[EXPORT_QUALITY]) {
                "LOW" -> ExportQuality.LOW
                "HIGH" -> ExportQuality.HIGH
                "BALANCED", null -> ExportQuality.BALANCED
                else -> ExportQuality.BALANCED
            }
        }

    val defaultFilenameStyle: StateFlow<DefaultFilenameStyle> =
        dataStore.data
            .map { prefs ->
                when (prefs[DEFAULT_FILENAME_STYLE]) {
                    "DATE" -> DefaultFilenameStyle.DATE
                    "SCAN_DATE_TIME" -> DefaultFilenameStyle.SCAN_DATE_TIME
                    else -> DefaultFilenameStyle.SCAN_DATE_TIME
                }
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = DefaultFilenameStyle.SCAN_DATE_TIME
            )

    suspend fun setDefaultColorMode(mode: DefaultColorMode) {
        dataStore.edit { prefs ->
            prefs[DEFAULT_COLOR_MODE] = mode.name
        }
    }

    suspend fun setOcrOnSave(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[OCR_ON_SAVE] = enabled
        }
    }

    suspend fun setTorchAlwaysOn(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[TORCH_ALWAYS_ON] = enabled
        }
    }

    suspend fun setExportDirUri(uri: String?) {
        dataStore.edit { prefs ->
            if (uri == null) {
                prefs.remove(EXPORT_DIR_URI)
            } else {
                prefs[EXPORT_DIR_URI] = uri
            }
        }
    }

    /** The library location chosen by the user, or null if there is none yet. */
    override suspend fun load(): StoredLibraryLocation? =
        dataStore.data.first().let { prefs ->
            val kind = when (prefs[LIBRARY_LOCATION_KIND]) {
                "DIRECT" -> StoredLibraryLocation.Kind.DIRECT
                "SAF" -> StoredLibraryLocation.Kind.SAF
                else -> return@let null
            }
            prefs[LIBRARY_LOCATION]?.let { StoredLibraryLocation(kind, it, prefs[LIBRARY_LOCATION_NAME]) }
        }

    override suspend fun save(location: StoredLibraryLocation?) {
        dataStore.edit { prefs ->
            if (location == null) {
                prefs.remove(LIBRARY_LOCATION_KIND)
                prefs.remove(LIBRARY_LOCATION)
                prefs.remove(LIBRARY_LOCATION_NAME)
            } else {
                prefs[LIBRARY_LOCATION_KIND] = location.kind.name
                prefs[LIBRARY_LOCATION] = location.value
                if (location.name != null) prefs[LIBRARY_LOCATION_NAME] = location.name
                else prefs.remove(LIBRARY_LOCATION_NAME)
            }
        }
    }

    override suspend fun lastOpenedId(): String? = dataStore.data.first()[LAST_OPENED_DOCUMENT]

    override suspend fun setLastOpenedId(id: String?) {
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(LAST_OPENED_DOCUMENT) else prefs[LAST_OPENED_DOCUMENT] = id
        }
    }

    suspend fun setExportFormat(format: ExportFormat) {
        dataStore.edit { prefs ->
            prefs[EXPORT_FORMAT] = format.name
        }
    }

    suspend fun setExportQuality(quality: ExportQuality) {
        dataStore.edit { prefs ->
            prefs[EXPORT_QUALITY] = quality.name
        }
    }

    suspend fun setDefaultFilenameStyle(style: DefaultFilenameStyle) {
        dataStore.edit { prefs ->
            prefs[DEFAULT_FILENAME_STYLE] = style.name
        }
    }
}

// Automatic plus every filter, in the order of the filter menu of the Document screen.
// A value stored under its name keeps working: new filters only add entries.
enum class DefaultColorMode(val colorMode: ColorMode?, val labelResource: Int) {
    AUTO(null, R.string.color_mode_auto),
    ORIGINAL(ColorMode.ORIGINAL, R.string.color_mode_original),
    COLOR(ColorMode.COLOR, R.string.color_mode_color),
    LIGHTEN(ColorMode.LIGHTEN, R.string.color_mode_lighten),
    ENHANCE(ColorMode.ENHANCE, R.string.color_mode_enhance),
    NO_SHADOWS(ColorMode.NO_SHADOWS, R.string.color_mode_no_shadows),
    GRAYSCALE(ColorMode.GRAYSCALE, R.string.color_mode_grayscale),
    BLACK_AND_WHITE(ColorMode.BLACK_AND_WHITE, R.string.color_mode_black_and_white),
    NEGATIVE(ColorMode.NEGATIVE, R.string.color_mode_negative),
}

internal fun parseDefaultColorMode(stored: String?): DefaultColorMode =
    DefaultColorMode.entries.firstOrNull { it.name == stored } ?: DefaultColorMode.AUTO

enum class ExportFormat(val mimeType: String) {
    PDF("application/pdf"),
    JPEG("image/jpeg"),
}

enum class DefaultFilenameStyle(val pattern: String) {
    SCAN_DATE_TIME("'Scan' yyyy-MM-dd HH.mm.ss"),
    DATE("yyyy-MM-dd");

    fun filename(): String = filename(Date())

    fun filename(date: Date): String =
        SimpleDateFormat(pattern, Locale.ENGLISH).format(date)
}
