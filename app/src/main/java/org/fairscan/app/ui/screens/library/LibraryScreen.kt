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
package org.fairscan.app.ui.screens.library

import android.content.res.Configuration
import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.fairscan.app.R
import org.fairscan.app.ui.Navigation
import org.fairscan.app.ui.components.AppOverflowMenu
import org.fairscan.app.ui.components.MainActionButton
import org.fairscan.app.ui.components.pageCountText
import org.fairscan.app.ui.dummyNavigation
import org.fairscan.app.ui.screens.export.DocumentShareProgress
import org.fairscan.app.ui.theme.FairScanTheme

/** What can be done from the library screen. Documents are identified by their id. */
data class LibraryActions(
    val onOpen: (String) -> Unit = {},
    val onScan: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onAddPages: (String) -> Unit = {},
    val onShare: (String) -> Unit = {},
    val onExport: (String) -> Unit = {},
    /** Returns whether the name was accepted (a blank name is not). */
    val onRename: (String, String) -> Boolean = { _, _ -> true },
    val onDelete: (String) -> Unit = {},
    val onSearch: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onCloseSearch: () -> Unit = {},
    val onStartSelection: (String) -> Unit = {},
    val onToggleSelection: (String) -> Unit = {},
    val onSelectAll: () -> Unit = {},
    val onClearSelection: () -> Unit = {},
    val onShareSelected: () -> Unit = {},
    val onCancelShare: () -> Unit = {},
    val onDeleteSelected: () -> Unit = {},
)

/**
 * The home screen: the saved documents, the one opened last on top as a card with quick actions,
 * and the actions to scan or import a new document.
 *
 * [busy] is true while a document is being opened; [sharingId] is the document being prepared for
 * sharing, if any; [shareProgress] is the progress of sharing the selected documents, if any.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    navigation: Navigation,
    actions: LibraryActions,
    loadCover: suspend (String) -> Bitmap?,
    busy: Boolean = false,
    sharingId: String? = null,
    shareProgress: DocumentShareProgress? = null,
) {
    var renaming by remember { mutableStateOf<LibraryItem?>(null) }
    var deleting by remember { mutableStateOf<LibraryItem?>(null) }
    var deletingSelected by remember { mutableStateOf(false) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Back leaves the selection first (keeping the search), then the search
    BackHandler(enabled = uiState.isSelecting || uiState.isSearching) {
        if (uiState.isSelecting) actions.onClearSelection() else actions.onCloseSearch()
    }

    Scaffold(
        topBar = {
            when {
                uiState.isSelecting -> SelectionTopBar(uiState.selectedIds.size, actions)
                uiState.isSearching -> SearchTopBar(uiState.query.orEmpty(), actions)
                else -> TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        if (!uiState.isEmpty) {
                            IconButton(onClick = actions.onSearch) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.library_search))
                            }
                        }
                        AppOverflowMenu(navigation)
                    },
                )
            }
        },
        bottomBar = {
            if (!isLandscape) {
                BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        BottomActions(uiState, actions, busy, shareProgress, onDeleteSelected = { deletingSelected = true })
                    }
                }
            }
        },
    ) { innerPadding ->
        val content = @Composable { modifier: Modifier ->
            LibraryContent(
                uiState, actions, loadCover, sharingId,
                onRename = { renaming = it },
                onDelete = { deleting = it },
                modifier = modifier,
            )
        }
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            if (isLandscape) {
                Row(Modifier.fillMaxSize()) {
                    content(Modifier.weight(2f))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BottomActions(uiState, actions, busy, shareProgress, onDeleteSelected = { deletingSelected = true })
                    }
                }
            } else {
                content(Modifier.fillMaxSize())
            }
            if (busy) OpeningIndicator(Modifier.align(Alignment.Center))
        }
    }

    renaming?.let { item ->
        RenameDialog(
            current = item.name,
            onConfirm = { name -> if (actions.onRename(item.id, name)) renaming = null },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { item ->
        DeleteDocumentDialog(
            onConfirm = { actions.onDelete(item.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
    if (deletingSelected && uiState.isSelecting) {
        val count = uiState.selectedIds.size
        DeleteDocumentDialog(
            title = pluralStringResource(R.plurals.library_delete_selected_title, count, count),
            onConfirm = { actions.onDeleteSelected(); deletingSelected = false },
            onDismiss = { deletingSelected = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(query: String, actions: LibraryActions) {
    var text by remember { mutableStateOf(query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = actions.onCloseSearch) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.library_search_close))
            }
        },
        title = {
            TextField(
                value = text,
                onValueChange = { text = it; actions.onQueryChange(it) },
                placeholder = { Text(stringResource(R.string.library_search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        actions = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { text = ""; actions.onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.library_search_clear))
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(count: Int, actions: LibraryActions) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        navigationIcon = {
            IconButton(onClick = actions.onClearSelection) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.library_close_selection))
            }
        },
        title = { Text(stringResource(R.string.pages_selected, count)) },
        actions = {
            TextButton(onClick = actions.onSelectAll) { Text(stringResource(R.string.select_all)) }
        },
    )
}

/** Import and Scan, or Delete and Share while documents are selected. */
@Composable
private fun BottomActions(
    uiState: LibraryUiState,
    actions: LibraryActions,
    busy: Boolean,
    shareProgress: DocumentShareProgress?,
    onDeleteSelected: () -> Unit,
) {
    if (uiState.isSelecting) {
        val sharing = shareProgress != null
        OutlinedButton(onClick = onDeleteSelected, enabled = !sharing) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.delete))
        }
        if (shareProgress != null) {
            // Tapping the button while it works cancels the sharing
            MainActionButton(
                onClick = actions.onCancelShare,
                text = stringResource(R.string.library_share_progress, shareProgress.done, shareProgress.total),
                icon = Icons.Default.Close,
            )
        } else {
            MainActionButton(
                onClick = actions.onShareSelected,
                text = stringResource(R.string.share),
                icon = Icons.Default.Share,
            )
        }
    } else {
        ImportButton(actions.onImport, enabled = !busy)
        ScanButton(actions.onScan, enabled = !busy)
    }
}

/** Asks whether to delete a document, which also deletes its original captures. */
@Composable
fun DeleteDocumentDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.library_delete_title),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(stringResource(R.string.library_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
private fun LibraryContent(
    uiState: LibraryUiState,
    actions: LibraryActions,
    loadCover: suspend (String) -> Bitmap?,
    sharingId: String?,
    onRename: (LibraryItem) -> Unit,
    onDelete: (LibraryItem) -> Unit,
    modifier: Modifier,
) {
    when {
        uiState.isEmpty && uiState.isLoading ->
            Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        uiState.isEmpty && uiState.isSearching -> NoResults(modifier)
        uiState.isEmpty -> EmptyLibrary(modifier)
        else -> LazyColumn(modifier = modifier.fillMaxSize()) {
            uiState.recent?.let { recent ->
                item(key = "recent-title") { SectionTitle(stringResource(R.string.library_recent), primary = true) }
                item(key = "recent-card") {
                    RecentCard(
                        recent, actions, loadCover, isSharing = sharingId == recent.id,
                        selecting = uiState.isSelecting, selected = recent.id in uiState.selectedIds,
                        onRename = { onRename(recent) }, onDelete = { onDelete(recent) },
                    )
                }
                if (uiState.others.isNotEmpty()) {
                    item(key = "all-title") { SectionTitle(stringResource(R.string.library_all_documents)) }
                }
            }
            items(uiState.others, key = { it.id }) { item ->
                DocumentRow(
                    item, loadCover,
                    selecting = uiState.isSelecting,
                    selected = item.id in uiState.selectedIds,
                    onOpen = { actions.onOpen(item.id) },
                    onLongPress = { actions.onStartSelection(item.id) },
                    onToggle = { actions.onToggleSelection(item.id) },
                    onRename = { onRename(item) },
                    onExport = { actions.onExport(item.id) },
                    onDelete = { onDelete(item) },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, primary: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun RecentCard(
    item: LibraryItem,
    actions: LibraryActions,
    loadCover: suspend (String) -> Bitmap?,
    isSharing: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .combinedClickable(
                onClick = { if (selecting) actions.onToggleSelection(item.id) else actions.onOpen(item.id) },
                onLongClick = { if (!selecting) actions.onStartSelection(item.id) },
            ),
        colors = if (selected) {
            CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else CardDefaults.elevatedCardColors(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                DocumentCover(item.id, loadCover, Modifier.width(72.dp).height(96.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    DocumentInfoText(item)
                }
                if (selecting) {
                    SelectionMark(selected)
                } else {
                    DocumentMenu(
                        onRename = onRename,
                        onExport = { actions.onExport(item.id) },
                        onDelete = onDelete,
                    )
                }
            }
            if (!selecting) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAction(Icons.Default.Add, stringResource(R.string.library_add_pages),
                        onClick = { actions.onAddPages(item.id) }, modifier = Modifier.weight(1f))
                    QuickAction(Icons.Default.Share, stringResource(R.string.share),
                        onClick = { actions.onShare(item.id) }, modifier = Modifier.weight(1f),
                        inProgress = isSharing)
                    QuickAction(Icons.Default.Done, stringResource(R.string.export),
                        onClick = { actions.onExport(item.id) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    inProgress: Boolean = false,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = !inProgress,
        modifier = modifier,
        contentPadding = PaddingValues8,
    ) {
        if (inProgress) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
    }
}

private val PaddingValues8 = PaddingValues(horizontal = 8.dp, vertical = 8.dp)

@Composable
private fun DocumentRow(
    item: LibraryItem,
    loadCover: suspend (String) -> Bitmap?,
    selecting: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        modifier = Modifier.combinedClickable(
            onClick = if (selecting) onToggle else onOpen,
            onLongClick = if (selecting) null else onLongPress,
        ),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        leadingContent = { DocumentCover(item.id, loadCover, Modifier.width(48.dp).height(64.dp)) },
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { DocumentInfoText(item) },
        trailingContent = {
            if (selecting) SelectionMark(selected) else DocumentMenu(onRename, onExport, onDelete)
        },
    )
}

/** The check at the end of a row while selecting: filled when the document is selected. */
@Composable
private fun SelectionMark(selected: Boolean) {
    Icon(
        imageVector = if (selected) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
        contentDescription = null,
        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(12.dp),
    )
}

@Composable
private fun DocumentInfoText(item: LibraryItem) {
    val context = LocalContext.current
    val date = remember(item.updatedAt) {
        DateUtils.formatDateTime(context, item.updatedAt,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)
    }
    Text(
        text = stringResource(R.string.library_document_info, date, pageCountText(item.pageCount)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DocumentMenu(onRename: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.library_document_actions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                text = { Text(stringResource(R.string.library_rename)) },
                onClick = { expanded = false; onRename() },
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.Done, contentDescription = null) },
                text = { Text(stringResource(R.string.export)) },
                onClick = { expanded = false; onExport() },
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                text = { Text(stringResource(R.string.delete)) },
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}

/** The first page of a document, loaded when it is shown; a plain surface stands for it until then. */
@Composable
private fun DocumentCover(id: String, loadCover: suspend (String) -> Bitmap?, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, id) { value = loadCover(id) }
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Description, contentDescription = null,
            modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.library_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NoResults(modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.library_no_results),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OpeningIndicator(modifier: Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.library_opening), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ImportButton(onClick: () -> Unit, enabled: Boolean) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.import_photos), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ScanButton(onClick: () -> Unit, enabled: Boolean) {
    MainActionButton(
        onClick = onClick,
        text = stringResource(R.string.library_scan),
        icon = Icons.Default.CameraAlt,
        enabled = enabled,
    )
}

/** Asks for the new name of a document, which is not accepted blank. */
@Composable
fun RenameDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    // The whole name is selected, so that typing replaces it
    var value by remember { mutableStateOf(TextFieldValue(current, TextRange(0, current.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_rename_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.library_rename_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.text) }, enabled = value.text.isNotBlank()) {
                Text(stringResource(android.R.string.ok), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold)
            }
        },
    )
}

// --- Previews ---

private val sampleItems = listOf(
    LibraryItem("1", "Scan 2026-10-11 20.55", 1_760_300_000_000, 1),
    LibraryItem("2", "Receipts", 1_760_200_000_000, 7),
    LibraryItem("3", "A document with a rather long name that does not fit on one line", 1_760_100_000_000, 12),
    LibraryItem("4", "Car insurance", 1_760_000_000_000, 3),
)

@Composable
private fun LibraryPreview(state: LibraryUiState, sharingId: String? = null) {
    FairScanTheme {
        LibraryScreen(state, dummyNavigation(), LibraryActions(), loadCover = { null }, sharingId = sharingId)
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Preview(showBackground = true, widthDp = 640, heightDp = 320)
@Composable
fun LibraryScreenPreviewWithRecent() {
    LibraryPreview(LibraryUiState(false, LibraryItem("0", "Invoice March", 1_760_400_000_000, 30), sampleItems))
}

@Preview(showBackground = true)
@Composable
fun LibraryScreenPreviewSharing() {
    LibraryPreview(
        LibraryUiState(false, LibraryItem("0", "Invoice March", 1_760_400_000_000, 30), sampleItems),
        sharingId = "0")
}

@Preview(showBackground = true, showSystemUi = true)
@Preview(showBackground = true, widthDp = 640, heightDp = 320)
@Composable
fun LibraryScreenPreviewSelecting() {
    LibraryPreview(LibraryUiState(false, LibraryItem("0", "Invoice March", 1_760_400_000_000, 30), sampleItems,
        selectedIds = setOf("0", "2")))
}

@Preview(showBackground = true)
@Composable
fun LibraryScreenPreviewSearching() {
    LibraryPreview(LibraryUiState(false, null, sampleItems.take(2), query = "re"))
}

@Preview(showBackground = true)
@Composable
fun LibraryScreenPreviewNoResults() {
    LibraryPreview(LibraryUiState(false, null, emptyList(), query = "zzz"))
}

@Preview(showBackground = true)
@Composable
fun LibraryScreenPreviewWithoutRecent() {
    LibraryPreview(LibraryUiState(false, null, sampleItems))
}

@Preview(showBackground = true)
@Composable
fun LibraryScreenPreviewOnlyRecent() {
    LibraryPreview(LibraryUiState(false, sampleItems.first(), emptyList()))
}

@Preview(showBackground = true)
@Composable
fun LibraryScreenPreviewEmpty() {
    LibraryPreview(LibraryUiState(false))
}

@Preview(showBackground = true)
@Composable
fun LibraryScreenPreviewLoading() {
    LibraryPreview(LibraryUiState())
}
