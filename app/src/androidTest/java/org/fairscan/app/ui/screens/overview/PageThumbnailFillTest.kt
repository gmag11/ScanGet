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
package org.fairscan.app.ui.screens.overview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import kotlinx.collections.immutable.toImmutableList
import org.fairscan.app.PageTransferState
import org.fairscan.app.domain.PageViewKey
import org.fairscan.app.domain.Rotation
import org.fairscan.app.ui.dummyNavigation
import org.fairscan.app.ui.state.DocumentUiModel
import org.fairscan.app.ui.state.PageThumbnail
import org.fairscan.app.ui.theme.FairScanTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PageThumbnailFillTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val blue = Color.rgb(0, 0, 255)

    // Page 1 portrait, page 2 landscape, page 3 square; all blue, with a red mark in the top-left corner
    private fun thumbnail(id: String): Bitmap {
        val (w, h) = when (id) {
            "1" -> 170 to 240
            "2" -> 240 to 170
            else -> 200 to 200
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(blue)
        val paint = android.graphics.Paint().apply { color = Color.RED }
        canvas.drawRect(0f, 0f, 20f, 20f, paint)
        return bitmap
    }

    private fun show() {
        val document = DocumentUiModel(
            (1..3).map { PageThumbnail(PageViewKey("$it", Rotation.R0, null, 0), null) }.toImmutableList())
        rule.setContent {
            FairScanTheme {
                DocumentOverviewScreen(
                    documentName = "Invoice", document = document, navigation = dummyNavigation(),
                    onPageClick = {}, onPageReorder = { _, _ -> }, onAddPage = {}, onExport = {},
                    onRename = { true }, onDeleteDocument = {},
                    loadThumbnail = { key -> thumbnail(key.pageId) },
                    pageActions = PageActions({}, { _, _ -> }, { _, _ -> }, { emptyList() }),
                    transfer = PageTransferState.Idle, onTransferShown = {},
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun cellsAreSquareAndTheSameSize() {
        show()
        val sizes = (1..3).map { rule.onNodeWithContentDescription("$it").fetchSemanticsNode().size }
        sizes.forEach { assertEquals(it.width, it.height) }
        assertEquals(1, sizes.toSet().size)
    }

    @Test
    fun everyThumbnailFillsItsCellWithNoEmptyBands() {
        show()
        for (page in 1..3) {
            val image = rule.onNodeWithContentDescription("$page").captureToImage()
            val w = image.width
            val h = image.height
            val pixels = IntArray(w * h).also { image.readPixels(it) }
            val m = 12 // inside the rounded corners and the border of the card
            // The corner of the red mark is the top-left of the page; the other three corners and the
            // middle of each side must be the page, never the background of the cell
            for ((x, y) in listOf(w - m to m, m to h - m, w - m to h - m, w / 2 to m, w / 2 to h - m, m to h / 2, w - m to h / 2)) {
                assertEquals("page $page at $x,$y", blue, pixels[y * w + x])
            }
        }
    }

    @Test
    fun theStartOfThePageStaysInView() {
        show()
        // Top-left of every page (portrait aligned top, landscape aligned left) is the red mark
        for (page in 1..3) {
            val image = rule.onNodeWithContentDescription("$page").captureToImage()
            val w = image.width
            val pixels = IntArray(w * image.height).also { image.readPixels(it) }
            assertEquals("page $page", Color.RED, pixels[12 * w + 12])
        }
    }
}
