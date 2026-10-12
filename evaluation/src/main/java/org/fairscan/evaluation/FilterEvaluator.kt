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
package org.fairscan.evaluation

import org.fairscan.imageprocessing.AdjustmentControl
import org.fairscan.imageprocessing.Adjustments
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.Mode
import org.fairscan.imageprocessing.detectDocumentQuad
import org.fairscan.imageprocessing.enhanceCapturedImage
import org.fairscan.imageprocessing.extractDocument
import org.fairscan.imageprocessing.scaledTo
import org.fairscan.imageprocessing.toImageSize
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.imgcodecs.Imgcodecs
import java.io.File

/**
 * Renders every filter, with neutral and a few sample adjustments, for each photo of a folder and
 * writes a side-by-side HTML page to evaluation/reports/filters/index.html. Meant to tune the
 * filters and their default constants by eye.
 *
 * Usage, from the repository root (see evaluation/README.md):
 *   FilterEvaluator [imagesDir] [--neutral-only]
 *
 * imagesDir defaults to evaluation/dataset/images. For each IMG.jpg the document is cropped with
 * the quad detected from imagesDir/../masks/IMG.png when that mask exists, and the whole photo is
 * used otherwise (handy for photos that are already cropped). Use a folder outside the repository
 * for private photos: the reports folder is ignored by git, but nothing else is.
 */
fun main(args: Array<String>) {
    nu.pattern.OpenCV.loadLocally()
    val neutralOnly = "--neutral-only" in args
    val imagesDir = args.firstOrNull { !it.startsWith("--") }
        ?.let { File(it) }
        ?: File("evaluation/dataset/images")
    FilterEvaluator.run(imagesDir, File("evaluation/reports/filters"), neutralOnly)
}

object FilterEvaluator {

    // Same budget as ExportQuality.BALANCED; black and white is rendered at 4x, as in the app.
    private const val MAX_PIXELS = 2_000_000L
    private const val BITONAL_MAX_PIXELS = MAX_PIXELS * 4
    private const val JPEG_QUALITY = 90

    private const val SAMPLE_STEP = 50

    data class Variant(val label: String, val adjustments: Adjustments)

    fun variants(mode: ColorMode, neutralOnly: Boolean): List<Variant> {
        val result = mutableListOf(Variant("neutral", Adjustments.NEUTRAL))
        if (neutralOnly) return result
        for (control in AdjustmentControl.entries.filter { it in mode.controls }) {
            val name = control.name.lowercase()
            if (control == AdjustmentControl.DETAIL) {
                result += Variant("$name +$SAMPLE_STEP", Adjustments().with(control, SAMPLE_STEP))
            } else {
                result += Variant("$name -$SAMPLE_STEP", Adjustments().with(control, -SAMPLE_STEP))
                result += Variant("$name +$SAMPLE_STEP", Adjustments().with(control, SAMPLE_STEP))
            }
        }
        return result
    }

    fun run(imagesDir: File, outputDir: File, neutralOnly: Boolean) {
        outputDir.mkdirs()
        val maskDir = File(imagesDir.parentFile, "masks")
        val imgFiles = imagesDir.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg") }
            ?.sortedBy { it.name }
            ?: emptyList()
        if (imgFiles.isEmpty()) {
            println("No JPEG images in ${imagesDir.absolutePath}")
            return
        }

        val sections = mutableListOf<String>()
        for (imgFile in imgFiles) {
            val name = imgFile.nameWithoutExtension
            println("Processing $name...")
            val source = Imgcodecs.imread(imgFile.absolutePath)
            if (source.empty()) {
                println("  Could not read $name, skipping")
                continue
            }
            val rendered = renderAll(name, source, File(maskDir, "$name.png"), outputDir, neutralOnly)
            source.release()
            sections += section(name, rendered)
        }
        File(outputDir, "index.html").writeText(page(sections))
        println("Report written to ${File(outputDir, "index.html").absolutePath}")
    }

    private class Rendered(val mode: ColorMode, val variant: Variant, val file: String, val kB: Long)

    private fun renderAll(
        name: String,
        source: Mat,
        maskFile: File,
        outputDir: File,
        neutralOnly: Boolean,
    ): List<Rendered> {
        val quad = detectQuad(source, maskFile)
        // The crop is rendered once per budget, then each filter is applied to it.
        val page = extractPage(source, quad, MAX_PIXELS)
        val bitonalPage = extractPage(source, quad, BITONAL_MAX_PIXELS)

        val rendered = mutableListOf<Rendered>()
        for (mode in ColorMode.entries) {
            val input = if (mode.isBitonal) bitonalPage else page
            val maxPixels = if (mode.isBitonal) BITONAL_MAX_PIXELS else MAX_PIXELS
            for (variant in variants(mode, neutralOnly)) {
                val out = enhanceCapturedImage(input, mode, maxPixels, variant.adjustments)
                val extension = if (mode.isBitonal) "png" else "jpg"
                val fileName = "$name-${mode.name.lowercase()}-${slug(variant.label)}.$extension"
                val file = File(outputDir, fileName)
                val params = if (mode.isBitonal) MatOfInt() else MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, JPEG_QUALITY)
                if (!Imgcodecs.imwrite(file.absolutePath, out, params)) {
                    throw RuntimeException("Could not write image to ${file.absolutePath}")
                }
                params.release()
                out.release()
                rendered += Rendered(mode, variant, fileName, file.length() / 1024)
            }
        }
        page.release()
        bitonalPage.release()
        return rendered
    }

    private fun detectQuad(source: Mat, maskFile: File): org.fairscan.imageprocessing.Quad? {
        if (!maskFile.exists()) return null
        val maskMat = Imgcodecs.imread(maskFile.absolutePath, Imgcodecs.IMREAD_UNCHANGED)
        if (maskMat.empty()) return null
        val mask = MatMask(maskMat)
        return detectDocumentQuad(mask, source.size().toImageSize(), Mode.CAPTURE)
            ?.scaledTo(mask.width, mask.height, source.width(), source.height())
    }

    // The perspective-corrected crop with no enhancement, or the whole photo when there is no quad.
    private fun extractPage(source: Mat, quad: org.fairscan.imageprocessing.Quad?, maxPixels: Long): Mat =
        if (quad != null) {
            extractDocument(source, quad, 0, ColorMode.ORIGINAL, maxPixels)
        } else {
            org.fairscan.imageprocessing.resizeForMaxPixels(source, maxPixels.toDouble())
        }

    private fun slug(label: String) = label.replace(" -", "_m").replace(" +", "_p").replace(' ', '_')

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun section(name: String, rendered: List<Rendered>): String {
        val rows = rendered.groupBy { it.mode }.entries.joinToString("\n") { (mode, items) ->
            val cards = items.joinToString("\n") { r ->
                """
                <figure>
                  <a href="${r.file}" target="_blank"><img src="${r.file}" loading="lazy"></a>
                  <figcaption>${escape(r.variant.label)} (${r.kB} kB)</figcaption>
                </figure>
                """.trimIndent()
            }
            """
            <h3>${mode.name}</h3>
            <div class="row">
            $cards
            </div>
            """.trimIndent()
        }
        return "<section><h2>${escape(name)}</h2>\n$rows\n</section>"
    }

    private fun page(sections: List<String>) = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <title>Filter comparison</title>
          <style>
            body { font-family: sans-serif; margin: 1em; }
            .row { display: flex; flex-wrap: wrap; gap: 8px; }
            figure { margin: 0; width: 260px; font-size: 12px; }
            figure img { width: 260px; border: 1px solid #ccc; background: #888; }
          </style>
        </head>
        <body>
        <h1>Filter comparison</h1>
        <p>Click an image to open it at full size. Each row is a filter; the first image of a row has neutral adjustments.</p>
        ${sections.joinToString("\n")}
        </body>
        </html>
    """.trimIndent()
}
