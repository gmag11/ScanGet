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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fairscan.app.R
import org.fairscan.app.data.library.LibraryEntry

/** What to do with the selected pages once a destination is chosen. */
enum class PageTransferKind { MOVE, COPY }

/** Delete, copy and move of selected pages, as the overview offers them. */
class PageActions(
    val onDelete: (Set<String>) -> Unit,
    /** Copies to the document with this id, or to a new one when it is null. */
    val onCopy: (Set<String>, String?) -> Unit,
    val onMove: (Set<String>, String?) -> Unit,
    /** The documents pages can go to, not including the open one. */
    val loadDestinations: suspend () -> List<LibraryEntry>,
)

/** Asks to confirm the deletion of [count] pages. */
@Composable
fun DeletePagesDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.page_delete_title, count, count)) },
        text = { Text(stringResource(R.string.page_delete_message)) },
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

/**
 * Lets the user pick the document that receives the pages, or ask for a new one ([onChosen] gets
 * null then). The documents are listed once [loadDestinations] returns.
 */
@Composable
fun PageDestinationDialog(
    kind: PageTransferKind,
    loadDestinations: suspend () -> List<LibraryEntry>,
    onChosen: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val destinations by produceState<List<LibraryEntry>?>(initialValue = null) { value = loadDestinations() }
    val title = if (kind == PageTransferKind.MOVE) R.string.page_destination_move_title
        else R.string.page_destination_copy_title
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    DestinationRow(
                        title = stringResource(R.string.page_destination_new),
                        subtitle = null,
                        icon = true,
                        onClick = { onChosen(null) },
                    )
                }
                val list = destinations
                if (list == null) {
                    item {
                        Text(
                            stringResource(R.string.page_destination_loading),
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(list, key = { it.info.id }) { entry ->
                        DestinationRow(
                            title = entry.info.name,
                            subtitle = pluralStringResource(
                                R.plurals.page_destination_pages, entry.pageCount, entry.pageCount),
                            icon = false,
                            onClick = { onChosen(entry.info.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
private fun DestinationRow(title: String, subtitle: String?, icon: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            if (icon) Icons.Default.Add else Icons.Default.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
