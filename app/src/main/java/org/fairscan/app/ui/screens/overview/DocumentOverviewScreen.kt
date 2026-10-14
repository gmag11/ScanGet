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

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import org.fairscan.app.PageTransferState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import org.fairscan.app.R
import org.fairscan.app.domain.PageViewKey
import org.fairscan.app.domain.Rotation
import org.fairscan.app.ui.Navigation
import org.fairscan.app.ui.components.AppOverflowMenu
import org.fairscan.app.ui.components.BackButton
import org.fairscan.app.ui.components.MainActionButton
import org.fairscan.app.ui.components.PageNumberBadge
import org.fairscan.app.ui.dummyNavigation
import org.fairscan.app.ui.screens.library.DeleteDocumentDialog
import org.fairscan.app.ui.screens.library.RenameDialog
import org.fairscan.app.ui.state.DocumentUiModel
import org.fairscan.app.ui.state.PageThumbnail
import org.fairscan.app.ui.theme.FairScanTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

/**
 * The pages of a document as a grid, in order and numbered. A tap opens a page for editing, a long
 * press followed by a drag moves it. The name of the document, in the top bar, can be changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentOverviewScreen(
    documentName: String,
    document: DocumentUiModel,
    navigation: Navigation,
    onPageClick: (Int) -> Unit,
    onPageReorder: (String, Int) -> Unit,
    onAddPage: () -> Unit,
    onExport: () -> Unit,
    /** Returns whether the name was accepted (a blank name is not). */
    onRename: (String) -> Boolean,
    onDeleteDocument: () -> Unit,
    loadThumbnail: suspend (PageViewKey) -> Bitmap?,
    pageActions: PageActions,
    transfer: PageTransferState,
    onTransferShown: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var selected by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(emptySet<String>()) }
    var deletingPages by remember { mutableStateOf(false) }
    var choosingDestination by remember { mutableStateOf<PageTransferKind?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    val running = transfer is PageTransferState.Running

    // What is selected can only be pages that still exist
    val existing = document.pages.map { it.key.pageId }.toSet()
    if (selected.any { it !in existing }) selected = selected intersect existing
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting && !running) { selected = emptySet() }

    LaunchedEffect(transfer) {
        when (transfer) {
            is PageTransferState.Done -> {
                selected = emptySet()
                val count = transfer.count
                val name = transfer.documentName
                snackbarHostState.showSnackbar(when (transfer.action) {
                    PageTransferState.Action.DELETED ->
                        resources.getQuantityString(R.plurals.page_transfer_deleted, count, count)
                    PageTransferState.Action.COPIED ->
                        resources.getQuantityString(R.plurals.page_transfer_copied, count, count, name)
                    PageTransferState.Action.MOVED ->
                        resources.getQuantityString(R.plurals.page_transfer_moved, count, count, name)
                })
                onTransferShown()
            }
            is PageTransferState.Failed -> {
                snackbarHostState.showSnackbar(resources.getString(R.string.page_transfer_failed))
                onTransferShown()
            }
            else -> {}
        }
    }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selecting) {
                SelectionTopBar(
                    count = selected.size,
                    enabled = !running,
                    onClose = { selected = emptySet() },
                    onMove = { choosingDestination = PageTransferKind.MOVE },
                    onCopy = { choosingDestination = PageTransferKind.COPY },
                    onDelete = { deletingPages = true },
                )
            } else TopAppBar(
                navigationIcon = { BackButton(navigation.back) },
                title = {
                    Row(
                        modifier = Modifier.clickable { renaming = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(documentName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.overview_rename_document),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    AppOverflowMenu(navigation, extraItems = { dismiss ->
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            text = { Text(stringResource(R.string.overview_delete_document)) },
                            onClick = { dismiss(); deleting = true },
                        )
                    })
                },
            )
        },
        bottomBar = {
            if (!isLandscape) {
                BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        AddPageButton(onAddPage, enabled = !selecting)
                        ExportButton(onExport, enabled = !selecting)
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            if (isLandscape) {
                Row(Modifier.fillMaxSize()) {
                    PageGrid(document, selected, { selected = it }, onPageClick, onPageReorder, loadThumbnail, Modifier.weight(2f))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AddPageButton(onAddPage, enabled = !selecting)
                        ExportButton(onExport, enabled = !selecting)
                    }
                }
            } else {
                PageGrid(document, selected, { selected = it }, onPageClick, onPageReorder, loadThumbnail, Modifier.fillMaxSize())
            }
            if (running) {
                // Nothing can be touched while pages are being copied or moved
                Box(Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                })
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }

    if (renaming) {
        RenameDialog(
            current = documentName,
            onConfirm = { name -> if (onRename(name)) renaming = false },
            onDismiss = { renaming = false },
        )
    }
    if (deletingPages && selecting) {
        DeletePagesDialog(
            count = selected.size,
            onConfirm = { deletingPages = false; pageActions.onDelete(selected) },
            onDismiss = { deletingPages = false },
        )
    }
    choosingDestination?.let { kind ->
        PageDestinationDialog(
            kind = kind,
            loadDestinations = pageActions.loadDestinations,
            onChosen = { target ->
                choosingDestination = null
                if (kind == PageTransferKind.MOVE) pageActions.onMove(selected, target)
                else pageActions.onCopy(selected, target)
            },
            onDismiss = { choosingDestination = null },
        )
    }
    if (deleting) {
        DeleteDocumentDialog(
            onConfirm = { deleting = false; onDeleteDocument() },
            onDismiss = { deleting = false },
        )
    }
}

@Composable
private fun PageGrid(
    document: DocumentUiModel,
    selected: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onPageClick: (Int) -> Unit,
    onPageReorder: (String, Int) -> Unit,
    loadThumbnail: suspend (PageViewKey) -> Bitmap?,
    modifier: Modifier,
) {
    val gridState = rememberLazyGridState()
    // A long press that is released without moving the page starts the selection
    var reordered by remember { mutableStateOf(false) }
    // Releasing a long press must not also count as a tap on the page
    val longPressActive = remember { booleanArrayOf(false) }
    val selecting = selected.isNotEmpty()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        reordered = true
        onPageReorder(document.pages[from.index].key.pageId, to.index)
    }
    LazyVerticalGrid(
        columns = PageGridCells,
        state = gridState,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(PAGE_GRID_GAP_DP.dp),
        verticalArrangement = Arrangement.spacedBy(PAGE_GRID_GAP_DP.dp),
    ) {
        itemsIndexed(document.pages, key = { _, page -> page.key.pageId }) { index, page ->
            ReorderableItem(reorderState, key = page.key.pageId) { isDragging ->
                val pageId = page.key.pageId
                PageCell(
                    key = page.key,
                    number = index + 1,
                    isDragging = isDragging,
                    isSelected = pageId in selected,
                    loadThumbnail = loadThumbnail,
                    onClick = {
                        if (longPressActive[0]) {
                            // The release of a long press
                        } else if (selecting) {
                            onSelectionChange(if (pageId in selected) selected - pageId else selected + pageId)
                        } else onPageClick(index)
                    },
                    modifier = Modifier.longPressDraggableHandle(
                        enabled = !selecting,
                        onDragStarted = { reordered = false; longPressActive[0] = true },
                        onDragStopped = {
                            if (!reordered) onSelectionChange(setOf(pageId))
                            // The tap of the release has already come (before this): taps count again
                            longPressActive[0] = false
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun PageCell(
    key: PageViewKey,
    number: Int,
    isDragging: Boolean,
    isSelected: Boolean,
    loadThumbnail: suspend (PageViewKey) -> Bitmap?,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    // Reloaded when the page is rendered again (new filter, crop, rotation...), which changes its key
    val bitmap by produceState<Bitmap?>(initialValue = null, key) { value = loadThumbnail(key) }
    val shape = RoundedCornerShape(4.dp)
    Card(
        modifier = modifier.scale(if (isDragging) 1.05f else 1f),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 6.dp else 1.dp),
        border = BorderStroke(
            if (isDragging || isSelected) 2.dp else 1.dp,
            if (isDragging || isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant),
    ) {
        // The cell is a square whatever the page: the thumbnail fills it (see thumbnailFill), what does
        // not fit is cut off, and until it is ready the cell is already its final size
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clipToBounds()
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val image = bitmap
            if (image != null) {
                val fill = thumbnailFill(image.width, image.height)
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "$number",
                    contentScale = fill.scale,
                    alignment = fill.alignment,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            PageNumberBadge(number, Alignment.BottomStart)
            if (isSelected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Done, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AddPageButton(onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.add_page), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ExportButton(onClick: () -> Unit, enabled: Boolean = true) {
    MainActionButton(
        onClick = onClick,
        enabled = enabled,
        text = stringResource(R.string.export),
        icon = Icons.Default.Done,
    )
}

private val SelectionSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })

/** The bar shown while pages are selected: how many, and Move, Copy and Delete in a menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    enabled: Boolean,
    onClose: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        navigationIcon = {
            IconButton(onClick = onClose, enabled = enabled) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.page_selection_close))
            }
        },
        title = { Text(stringResource(R.string.pages_selected, count)) },
        actions = {
            Box {
                IconButton(onClick = { expanded = true }, enabled = enabled) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                        text = { Text(stringResource(R.string.page_action_move)) },
                        onClick = { expanded = false; onMove() },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        text = { Text(stringResource(R.string.page_action_copy)) },
                        onClick = { expanded = false; onCopy() },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = { expanded = false; onDelete() },
                    )
                }
            }
        },
    )
}

// --- Previews ---

private fun previewDocument(pages: Int) = DocumentUiModel(
    (1..pages).map { PageThumbnail(PageViewKey("$it", Rotation.R0, null, 0), null) }.toImmutableList())

// A page of a given proportion with a few lines, to see how it fills its cell
private fun previewPage(width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = android.graphics.Paint().apply { color = android.graphics.Color.DKGRAY }
    var y = height / 12f
    while (y < height) {
        canvas.drawRect(width / 10f, y, width * 0.9f, y + height / 40f, paint)
        y += height / 14f
    }
    paint.color = android.graphics.Color.RED
    canvas.drawRect(0f, 0f, width.toFloat(), height / 60f + 2, paint) // top edge
    canvas.drawRect(0f, 0f, width / 60f + 2, height.toFloat(), paint) // left edge
    return bitmap
}

@Composable
private fun OverviewPreview(
    pages: Int,
    name: String = "Invoice March",
    loadThumbnail: suspend (PageViewKey) -> Bitmap? = { null },
) {
    FairScanTheme {
        DocumentOverviewScreen(name, previewDocument(pages), dummyNavigation(), {}, { _, _ -> }, {}, {},
            { true }, {}, loadThumbnail, PageActions({}, { _, _ -> }, { _, _ -> }, { emptyList() }),
            PageTransferState.Idle, {})
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Preview(showBackground = true, widthDp = 640, heightDp = 320)
@Composable
fun DocumentOverviewScreenPreview() {
    OverviewPreview(pages = 7)
}

@Preview(showBackground = true, widthDp = 900, heightDp = 600)
@Composable
fun DocumentOverviewScreenTabletPreview() {
    OverviewPreview(pages = 14)
}

@Preview(showBackground = true)
@Composable
fun DocumentOverviewScreenSinglePagePreview() {
    OverviewPreview(pages = 1, name = "A document with a rather long name that does not fit on one line")
}

@Preview(showBackground = true)
@Composable
fun DocumentOverviewScreenPageShapesPreview() {
    // Pages 1, 4: portrait; 2, 5: landscape; 3, 6: square
    OverviewPreview(pages = 6) { key ->
        when (key.pageId.toInt() % 3) {
            1 -> previewPage(170, 240)
            2 -> previewPage(240, 170)
            else -> previewPage(200, 200)
        }
    }
}
