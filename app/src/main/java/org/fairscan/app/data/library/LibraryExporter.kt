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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.fairscan.app.data.DocumentInfo
import org.fairscan.app.data.FileManager
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.data.ImageTransformations
import org.fairscan.app.data.LoadedDocument
import org.fairscan.app.data.Logger
import org.fairscan.app.data.PageV2
import org.fairscan.app.data.sanitizeFileName
import org.fairscan.app.data.toInfo
import org.fairscan.app.domain.ExportQuality
import org.fairscan.app.domain.jpegsToExport
import org.fairscan.app.domain.pagesToExport
import org.fairscan.app.ui.screens.settings.ExportFormat
import java.io.File

/** The files made from some documents, and their total size. */
data class LibraryExportFiles(val files: List<File>, val sizeInBytes: Long)

/**
 * Makes the files to share of documents of the library, whichever they are and without touching the
 * document being edited. Each one is rebuilt in a temporary directory from what the library holds
 * (the captures and the editing data), runs through the usual export (the same quality, format and
 * text recognition as exporting from the export screen), and the directory is removed.
 */
class LibraryExporter(
    // Where documents are rebuilt, one directory each, while they are exported
    private val workDir: File,
    private val transformations: ImageTransformations,
    private val fileManager: FileManager,
    private val logger: Logger,
) {

    /**
     * One file per document in PDF, named after it; in JPEG, the pages of each document, named after it
     * (numbered when there are several). Two documents with the same name get different files. Files
     * are written in [outputDir]. [onProgress] is told how many documents are done. Cancelling removes
     * what was made.
     */
    suspend fun export(
        store: LibraryStore,
        ids: List<String>,
        format: ExportFormat,
        quality: ExportQuality,
        outputDir: File,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): LibraryExportFiles = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val produced = mutableListOf<File>()
        val usedNames = mutableSetOf<String>()
        try {
            onProgress(0, ids.size)
            ids.forEachIndexed { index, id ->
                ensureActive()
                val metadata = store.readMetadata(id)
                if (metadata == null) {
                    logger.e("LibraryExporter", "Document $id is not in the library any more",
                        IllegalStateException("Missing document"))
                } else {
                    produced += exportOne(store, metadata.toInfo(), metadata.pages, format, quality,
                        outputDir, uniqueName(sanitizeFileName(metadata.name), usedNames), id)
                }
                onProgress(index + 1, ids.size)
            }
            LibraryExportFiles(produced, produced.sumOf { it.length() })
        } catch (e: CancellationException) {
            produced.forEach { it.delete() }
            throw e
        }
    }

    private suspend fun exportOne(
        store: LibraryStore,
        info: DocumentInfo,
        pages: List<PageV2>,
        format: ExportFormat,
        quality: ExportQuality,
        outputDir: File,
        name: String,
        id: String,
    ): List<File> {
        val dir = File(workDir, "$id-${System.nanoTime()}")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val images = ImageRepository(dir, transformations, scope, logger)
            images.load(LoadedDocument(info, pages)) { pageId -> store.readSource(id, pageId) }
            return if (format == ExportFormat.JPEG) {
                val jpegs = jpegsToExport(images, quality)
                jpegs.mapIndexed { index, page ->
                    val suffix = if (jpegs.size == 1) "" else "_${index + 1}"
                    File(outputDir, "$name$suffix.jpg").also { it.writeBytes(page.image.get().bytes) }
                }
            } else {
                val context = currentCoroutineContext()
                val generated = fileManager.generatePdf(pagesToExport(images, quality), disableOcr = false) {
                    // Called after each page: a cancelled export stops here
                    context.ensureActive()
                }
                val target = File(outputDir, "$name.pdf")
                if (target.exists()) target.delete()
                check(generated.file.renameTo(target)) { "Cannot rename ${generated.file} to $target" }
                listOf(target)
            }
        } finally {
            scope.cancel()
            dir.deleteRecursively()
        }
    }

    // "Invoice", "Invoice (2)", "Invoice (3)"...; names that differ only in capitals are the same file on some systems
    private fun uniqueName(name: String, used: MutableSet<String>): String {
        val base = name.ifEmpty { "Document" }
        var candidate = base
        var n = 2
        while (!used.add(candidate.lowercase())) candidate = "$base ($n)".also { n++ }
        return candidate
    }
}
