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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.fairscan.app.data.DocumentMetadataV3
import org.fairscan.app.data.FileManager
import org.fairscan.app.data.ImageTransformations
import org.fairscan.app.data.Logger
import org.fairscan.app.data.PageRecognition
import org.fairscan.app.data.PageV2
import org.fairscan.app.data.PdfWriter
import org.fairscan.app.data.toSerializable
import org.fairscan.app.domain.EncodedImage
import org.fairscan.app.domain.ExportQuality
import org.fairscan.app.domain.Jpeg
import org.fairscan.app.domain.PageMetadata
import org.fairscan.app.domain.TextRecognizer
import org.fairscan.imageprocessing.ImageRect
import org.fairscan.imageprocessing.OcrTextBox
import org.fairscan.app.ui.screens.settings.ExportFormat
import org.fairscan.imageprocessing.Adjustments
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.ColorMode.COLOR
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LibraryExporterTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val quad = Quad(Point(.01, .02), Point(.1, .03), Point(.11, .12), Point(.03, .09))
    private val libraryDir by lazy { folder.newFolder("library") }
    private val store by lazy { FileLibraryStore(libraryDir) }
    private val workDir by lazy { File(folder.root, "work") }
    private val outputDir by lazy { File(folder.root, "out") }
    private val pdfDir by lazy { File(folder.root, "pdfs") }

    // A "processed page" is made of the first byte of its source and its colour mode, which is all
    // the fake PDF writer and the JPEG files need to show where each page comes from
    private val transformations = object : ImageTransformations {
        override fun rotate(input: EncodedImage, rotationDegrees: Int): EncodedImage = input
        override fun resizeToThumbnail(input: EncodedImage): Jpeg = Jpeg(byteArrayOf(input.bytes[0]))
        override fun process(source: Jpeg, metadata: PageMetadata, colorMode: ColorMode,
                             adjustments: Adjustments): Jpeg = Jpeg(byteArrayOf(source.bytes[0], 7))
    }

    // Writes the bytes of the pages it is given, so that a test can tell which pages a PDF has
    private val pdfWriter = PdfWriter { pages, output, disableOcr, onPageCompleted ->
        assertThat(disableOcr).isFalse() // text recognition follows the settings
        pages.forEachIndexed { index, page ->
            output.write(page.image.get().bytes)
            onPageCompleted(index + 1)
        }
    }
    private val exporter by lazy {
        LibraryExporter(workDir, transformations, FileManager(pdfDir, outputDir, pdfWriter), Logger { _, _, _ -> })
    }

    /** A document whose pages have sources starting with the given bytes. */
    private fun addDocument(id: String, name: String, vararg firstBytes: Int) {
        val pages = firstBytes.mapIndexed { i, b ->
            val pageId = "${1000 + i}"
            store.writeSource(id, pageId, byteArrayOf(b.toByte(), 1))
            PageV2(pageId, colorMode = COLOR, isColored = true, quad = quad.toSerializable())
        }
        store.writeMetadata(DocumentMetadataV3(id = id, name = name, createdAt = 1, updatedAt = 2, pages = pages))
    }

    private fun snapshot() = libraryDir.walkTopDown().filter { it.isFile }
        .associate { it.relativeTo(libraryDir).path to it.length() }

    private suspend fun export(ids: List<String>, format: ExportFormat = ExportFormat.PDF,
                               onProgress: (Int, Int) -> Unit = { _, _ -> }) =
        exporter.export(store, ids, format, ExportQuality.BALANCED, outputDir, onProgress)

    @Test
    fun `each document gives a PDF named after it with its pages`() = runTest {
        addDocument("d1", "Invoice", 1, 2, 3)
        addDocument("d2", "Receipts", 4)
        addDocument("d3", "Car insurance", 5, 6)

        val result = export(listOf("d1", "d2", "d3"))

        assertThat(result.files.map { it.name })
            .containsExactly("Invoice.pdf", "Receipts.pdf", "Car insurance.pdf")
        assertThat(File(outputDir, "Invoice.pdf").readBytes()).isEqualTo(byteArrayOf(1, 7, 2, 7, 3, 7))
        assertThat(File(outputDir, "Receipts.pdf").readBytes()).isEqualTo(byteArrayOf(4, 7))
        assertThat(File(outputDir, "Car insurance.pdf").readBytes()).isEqualTo(byteArrayOf(5, 7, 6, 7))
        assertThat(result.sizeInBytes).isEqualTo(result.files.sumOf { it.length() })
    }

    @Test
    fun `two documents with the same name give different files`() = runTest {
        addDocument("d1", "Invoice", 1)
        addDocument("d2", "Invoice", 2)
        addDocument("d3", "invoice", 3) // same file on a case-insensitive system

        val result = export(listOf("d1", "d2", "d3"))

        assertThat(result.files.map { it.name }).containsExactly("Invoice.pdf", "Invoice (2).pdf", "invoice (3).pdf")
        assertThat(result.files.map { it.readBytes()[0].toInt() }).containsExactly(1, 2, 3)
    }

    @Test
    fun `characters that cannot be in a file name are replaced and a blank name still gives a file`() = runTest {
        addDocument("d1", "Bills: 2026/10 <final>", 1)
        addDocument("d2", "   ", 2)

        val result = export(listOf("d1", "d2"))

        assertThat(result.files.map { it.name }).containsExactly("Bills_ 2026_10 _final_.pdf", "Document.pdf")
    }

    @Test
    fun `in JPEG the pages of each document are files named after it`() = runTest {
        addDocument("d1", "Invoice", 1, 2)
        addDocument("d2", "Receipt", 3)

        val result = export(listOf("d1", "d2"), ExportFormat.JPEG)

        assertThat(result.files.map { it.name }).containsExactly("Invoice_1.jpg", "Invoice_2.jpg", "Receipt.jpg")
        assertThat(File(outputDir, "Invoice_1.jpg").readBytes()).isEqualTo(byteArrayOf(1, 7))
        assertThat(File(outputDir, "Invoice_2.jpg").readBytes()).isEqualTo(byteArrayOf(2, 7))
        assertThat(File(outputDir, "Receipt.jpg").readBytes()).isEqualTo(byteArrayOf(3, 7))
    }

    @Test
    fun `progress tells how many documents are done`() = runTest {
        addDocument("d1", "A", 1); addDocument("d2", "B", 2); addDocument("d3", "C", 3)
        val progress = mutableListOf<Pair<Int, Int>>()

        export(listOf("d1", "d2", "d3")) { done, total -> progress += done to total }

        assertThat(progress).containsExactly(0 to 3, 1 to 3, 2 to 3, 3 to 3)
    }

    @Test
    fun `a document that is not in the library any more is skipped`() = runTest {
        addDocument("d1", "A", 1)

        val result = export(listOf("gone", "d1"))

        assertThat(result.files.map { it.name }).containsExactly("A.pdf")
    }

    @Test
    fun `nothing is left behind in the work directory and the library is not changed`() = runTest {
        addDocument("d1", "A", 1, 2); addDocument("d2", "B", 3)
        val before = snapshot()

        export(listOf("d1", "d2"))
        export(listOf("d1", "d2"), ExportFormat.JPEG)

        assertThat(workDir.listFiles().orEmpty()).isEmpty()
        assertThat(snapshot()).isEqualTo(before)
    }

    @Test
    fun `exporting no document gives no file`() = runTest {
        val result = export(emptyList())

        assertThat(result.files).isEmpty()
        assertThat(result.sizeInBytes).isEqualTo(0)
    }

    @Test
    fun `cancelling removes what was made`() {
        addDocument("d1", "A", 1); addDocument("d2", "B", 2); addDocument("d3", "C", 3)
        val scope = CoroutineScope(Dispatchers.Default)
        lateinit var job: Job
        val cancelled = runBlocking {
            job = scope.launch {
                export(listOf("d1", "d2", "d3")) { done, _ ->
                    if (done == 1) job.cancel() // the user cancels after the first document
                }
            }
            job.join()
            job.isCancelled
        }

        assertThat(cancelled).isTrue()
        assertThat(outputDir.listFiles().orEmpty()).isEmpty()
        assertThat(workDir.listFiles().orEmpty()).isEmpty()
    }

    // --- Text recognition kept in the library ---

    private var recognitions = 0

    // Like the real writer: the text of a page is the stored one or else it is recognized (and kept)
    private val recognizingWriter = PdfWriter { pages, output, _, onPageCompleted ->
        pages.forEachIndexed { index, page ->
            page.recognizedText {
                recognitions++
                PageRecognition(listOf(OcrTextBox("word", ImageRect(0, 0, 5, 5), 5, 5)), 10, 20)
            }
            output.write(page.image.get().bytes)
            onPageCompleted(index + 1)
        }
    }
    private val noRecognizer = object : TextRecognizer {
        override val isAvailable = false
        override suspend fun recognize(image: EncodedImage, halve: Boolean): PageRecognition? = null
    }
    private val ocrExporter by lazy {
        val ocr = LibraryOcr(noRecognizer, { false }, CoroutineScope(Job()), Logger { _, _, _ -> })
        LibraryExporter(workDir, transformations, FileManager(pdfDir, outputDir, recognizingWriter),
            Logger { _, _, _ -> }, ocr)
    }

    @Test
    fun `a document that was recognized is exported again without recognizing it`() = runTest {
        addDocument("d1", "Invoice", 1, 2, 3)

        ocrExporter.export(store, listOf("d1"), ExportFormat.PDF, ExportQuality.BALANCED, outputDir) { _, _ -> }
        assertThat(recognitions).isEqualTo(3)
        assertThat(store.ocrPageIds("d1")).hasSize(3)

        ocrExporter.export(store, listOf("d1"), ExportFormat.PDF, ExportQuality.BALANCED, outputDir) { _, _ -> }

        assertThat(recognitions).isEqualTo(3)
    }

    @Test
    fun `only the pages without text are recognized when exporting`() = runTest {
        addDocument("d1", "Invoice", 1, 2, 3)
        ocrExporter.export(store, listOf("d1"), ExportFormat.PDF, ExportQuality.BALANCED, outputDir) { _, _ -> }
        recognitions = 0
        store.deleteOcr("d1", "1001")

        ocrExporter.export(store, listOf("d1"), ExportFormat.PDF, ExportQuality.BALANCED, outputDir) { _, _ -> }

        assertThat(recognitions).isEqualTo(1)
        assertThat(store.ocrPageIds("d1")).hasSize(3)
    }

    @Test
    fun `the text kept for a page that was edited is not used`() = runTest {
        addDocument("d1", "Invoice", 1)
        ocrExporter.export(store, listOf("d1"), ExportFormat.PDF, ExportQuality.BALANCED, outputDir) { _, _ -> }
        recognitions = 0
        // The page is now rendered with another filter
        val metadata = store.readMetadata("d1")!!
        store.writeMetadata(metadata.copy(pages = metadata.pages.map { it.copy(colorMode = ColorMode.GRAYSCALE) }))

        ocrExporter.export(store, listOf("d1"), ExportFormat.PDF, ExportQuality.BALANCED, outputDir) { _, _ -> }

        assertThat(recognitions).isEqualTo(1)
    }
}
