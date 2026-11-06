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

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.down
import androidx.compose.ui.test.center
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.geometry.Offset
import kotlinx.collections.immutable.toImmutableList
import org.fairscan.app.PageTransferState
import org.fairscan.app.R
import org.fairscan.app.data.DocumentInfo
import org.fairscan.app.data.library.LibraryEntry
import org.fairscan.app.domain.PageViewKey
import org.fairscan.app.domain.Rotation
import org.fairscan.app.ui.dummyNavigation
import org.fairscan.app.ui.state.DocumentUiModel
import org.fairscan.app.ui.state.PageThumbnail
import org.fairscan.app.ui.theme.FairScanTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DocumentOverviewSelectionTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<Int>()
    private val reordered = mutableListOf<Pair<String, Int>>()
    private val deleted = mutableListOf<Set<String>>()
    private val copied = mutableListOf<Pair<Set<String>, String?>>()
    private val moved = mutableListOf<Pair<Set<String>, String?>>()
    private var backs = 0
    private var addPage = 0

    private fun str(id: Int) = rule.activity.getString(id)

    private fun show(
        pages: Int = 5,
        destinations: List<LibraryEntry> = emptyList(),
        transfer: PageTransferState = PageTransferState.Idle,
    ) {
        val document = DocumentUiModel(
            (1..pages).map { PageThumbnail(PageViewKey("$it", Rotation.R0, null, 0), null) }.toImmutableList())
        rule.setContent {
            FairScanTheme {
                DocumentOverviewScreen(
                    documentName = "Invoice",
                    document = document,
                    navigation = dummyNavigation().copy(back = { backs++ }),
                    onPageClick = { opened += it },
                    onPageReorder = { id, index -> reordered += id to index },
                    onAddPage = { addPage++ },
                    onExport = {},
                    onRename = { true },
                    onDeleteDocument = {},
                    loadThumbnail = { null },
                    pageActions = PageActions(
                        onDelete = { deleted += it },
                        onCopy = { ids, target -> copied += ids to target },
                        onMove = { ids, target -> moved += ids to target },
                        loadDestinations = { destinations },
                    ),
                    transfer = transfer,
                    onTransferShown = {},
                )
            }
        }
    }

    private fun longPress(page: Int) {
        rule.onNodeWithText("$page").performTouchInput { longClick() }
    }

    private fun selectedCount(count: Int) =
        rule.onNodeWithText(rule.activity.getString(R.string.pages_selected, count)).assertIsDisplayed()

    @Test
    fun tapOpensThePageWhenNothingIsSelected() {
        show()
        rule.onNodeWithText("4").performClick()
        assertEquals(listOf(3), opened)
    }

    @Test
    fun longPressStartsSelectionWithThatPage() {
        show()
        longPress(3)
        selectedCount(1)
        assertTrue(reordered.isEmpty())
        assertEquals("a long press must not open the page", emptyList<Int>(), opened)
    }

    @Test
    fun tapsToggleWhileSelecting() {
        show()
        longPress(3)
        rule.onNodeWithText("5").performClick()
        selectedCount(2)
        rule.onNodeWithText("3").performClick()
        selectedCount(1)
        assertEquals(emptyList<Int>(), opened)
    }

    @Test
    fun deselectingTheLastPageEndsSelection() {
        show()
        longPress(3)
        rule.onNodeWithText("3").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.pages_selected, 1)).assertDoesNotExist()
    }

    @Test
    fun closingEndsSelection() {
        show()
        longPress(2)
        rule.onNodeWithContentDescription(str(R.string.page_selection_close)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.pages_selected, 1)).assertDoesNotExist()
        assertEquals(0, backs)
    }

    @Test
    fun backEndsSelectionAndStaysInTheOverview() {
        show()
        longPress(2)
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithText(rule.activity.getString(R.string.pages_selected, 1)).assertDoesNotExist()
    }

    @Test
    fun menuOffersMoveCopyAndDelete() {
        show()
        longPress(1)
        rule.onNodeWithContentDescription(str(R.string.menu)).performClick()
        rule.onNodeWithText(str(R.string.page_action_move)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.page_action_copy)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.delete)).assertIsDisplayed()
    }

    @Test
    fun addPageIsDisabledWhileSelecting() {
        show()
        rule.onNodeWithText(str(R.string.add_page)).assertIsEnabled()
        longPress(1)
        rule.onNodeWithText(str(R.string.add_page)).assertIsNotEnabled()
    }

    @Test
    fun deleteAsksForConfirmationAndCancelKeepsTheSelection() {
        show()
        longPress(2)
        rule.onNodeWithText("4").performClick()
        rule.onNodeWithContentDescription(str(R.string.menu)).performClick()
        rule.onNodeWithText(str(R.string.delete)).performClick()
        rule.onNodeWithText(rule.activity.resources.getQuantityString(R.plurals.page_delete_title, 2, 2))
            .assertIsDisplayed()
        rule.onNodeWithText(str(R.string.cancel)).performClick()
        assertTrue(deleted.isEmpty())
        selectedCount(2)
    }

    @Test
    fun confirmingDeleteDeletesTheSelectedPages() {
        show()
        longPress(2)
        rule.onNodeWithText("4").performClick()
        rule.onNodeWithContentDescription(str(R.string.menu)).performClick()
        rule.onNodeWithText(str(R.string.delete)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(str(R.string.delete)).assertCountEquals(1)
        rule.onNodeWithText(str(R.string.delete)).performClick()
        assertEquals(listOf(setOf("2", "4")), deleted)
    }

    private val entries = listOf(
        LibraryEntry(DocumentInfo("doc-a", "Invoices", 0, 0), 3),
        LibraryEntry(DocumentInfo("doc-b", "Receipts", 0, 0), 1),
    )

    @Test
    fun copyToAnExistingDocument() {
        show(destinations = entries)
        longPress(1)
        rule.onNodeWithContentDescription(str(R.string.menu)).performClick()
        rule.onNodeWithText(str(R.string.page_action_copy)).performClick()
        rule.onNodeWithText(str(R.string.page_destination_copy_title)).assertIsDisplayed()
        rule.onNodeWithText("Invoices").performClick()
        assertEquals(listOf(setOf("1") to "doc-a"), copied)
        assertTrue(moved.isEmpty())
    }

    @Test
    fun moveToANewDocument() {
        show(destinations = entries)
        longPress(2)
        rule.onNodeWithContentDescription(str(R.string.menu)).performClick()
        rule.onNodeWithText(str(R.string.page_action_move)).performClick()
        rule.onNodeWithText(str(R.string.page_destination_move_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.page_destination_new)).performClick()
        assertEquals(listOf(setOf("2") to null), moved)
    }

    @Test
    fun dismissingTheDestinationDialogDoesNothing() {
        show(destinations = entries)
        longPress(2)
        rule.onNodeWithContentDescription(str(R.string.menu)).performClick()
        rule.onNodeWithText(str(R.string.page_action_copy)).performClick()
        rule.onNodeWithText(str(R.string.cancel)).performClick()
        assertTrue(copied.isEmpty() && moved.isEmpty())
        selectedCount(1)
    }

    @Test
    fun noOtherDocumentsOffersOnlyTheNewOne() {
        show(destinations = emptyList())
        longPress(1)
        rule.onNodeWithContentDescription(str(R.string.menu)).performClick()
        rule.onNodeWithText(str(R.string.page_action_move)).performClick()
        rule.onNodeWithText(str(R.string.page_destination_new)).assertIsDisplayed()
        rule.onNodeWithText("Invoices").assertDoesNotExist()
    }

    @Test
    fun longPressAndDragStillReorders() {
        show()
        rule.onNodeWithText("1").performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(width * 1.5f, 0f), delayMillis = 50)
            moveBy(Offset(width * 0.5f, 0f), delayMillis = 50)
            up()
        }
        rule.waitForIdle()
        assertTrue("page was not reordered", reordered.isNotEmpty())
        assertTrue(reordered.first().first == "1")
        rule.onNodeWithText(rule.activity.getString(R.string.pages_selected, 1)).assertDoesNotExist()
    }

    @Test
    fun resultOfACopyIsAnnounced() {
        show(transfer = PageTransferState.Done(PageTransferState.Action.COPIED, 2, "Invoices"))
        rule.onNodeWithText(rule.activity.resources.getQuantityString(R.plurals.page_transfer_copied, 2, 2, "Invoices"))
            .assertIsDisplayed()
    }

    @Test
    fun aFailureIsAnnounced() {
        show(transfer = PageTransferState.Failed)
        rule.onNodeWithText(str(R.string.page_transfer_failed)).assertIsDisplayed()
    }

    @Test
    fun nothingCanBeTouchedWhileTransferring() {
        show(transfer = PageTransferState.Running)
        rule.onNodeWithText("2").performClick()
        assertTrue(opened.isEmpty())
    }
}
