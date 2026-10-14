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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
) {
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            TopAppBar(
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
                        AddPageButton(onAddPage)
                        ExportButton(onExport)
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            if (isLandscape) {
                Row(Modifier.fillMaxSize()) {
                    PageGrid(document, onPageClick, onPageReorder, loadThumbnail, Modifier.weight(2f))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AddPageButton(onAddPage)
                        ExportButton(onExport)
                    }
                }
            } else {
                PageGrid(document, onPageClick, onPageReorder, loadThumbnail, Modifier.fillMaxSize())
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
    onPageClick: (Int) -> Unit,
    onPageReorder: (String, Int) -> Unit,
    loadThumbnail: suspend (PageViewKey) -> Bitmap?,
    modifier: Modifier,
) {
    val gridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
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
                PageCell(
                    key = page.key,
                    number = index + 1,
                    isDragging = isDragging,
                    loadThumbnail = loadThumbnail,
                    onClick = { onPageClick(index) },
                    modifier = Modifier.longPressDraggableHandle(),
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
            if (isDragging) 2.dp else 1.dp,
            if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        // The cell is a square whatever the page: the thumbnail is fitted inside it, whole and centered,
        // and until it is ready the cell is already its final size
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "$number",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            PageNumberBadge(number, Alignment.BottomStart)
        }
    }
}

@Composable
private fun AddPageButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
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
private fun ExportButton(onClick: () -> Unit) {
    MainActionButton(
        onClick = onClick,
        text = stringResource(R.string.export),
        icon = Icons.Default.Done,
    )
}

// --- Previews ---

private fun previewDocument(pages: Int) = DocumentUiModel(
    (1..pages).map { PageThumbnail(PageViewKey("$it", Rotation.R0, null, 0), null) }.toImmutableList())

@Composable
private fun OverviewPreview(pages: Int, name: String = "Invoice March") {
    FairScanTheme {
        DocumentOverviewScreen(name, previewDocument(pages), dummyNavigation(), {}, { _, _ -> }, {}, {},
            { true }, {}, { null })
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
