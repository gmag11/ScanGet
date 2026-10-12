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
package org.fairscan.app.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fairscan.app.R

/**
 * The dialogs of the library folder flow, shown over whichever screen started it (the setup screen
 * or the settings): confirmation of a folder that is not empty, offer to move the documents,
 * progress of the move, and what could not be moved.
 */
@Composable
fun LibraryFolderDialogs(
    uiState: LibraryFolderUiState,
    onConfirmFolder: () -> Unit,
    onChooseAnother: () -> Unit,
    onMoveAnswer: (Boolean) -> Unit,
    onDismissNotMoved: () -> Unit,
) {
    uiState.confirmation?.let { confirmation ->
        FolderConfirmationDialog(confirmation, onConfirmFolder, onChooseAnother)
    }
    uiState.moveOffer?.let { offer ->
        MoveOfferDialog(offer, onMoveAnswer)
    }
    uiState.moveProgress?.takeIf { uiState.isWorking }?.let { progress ->
        AlertDialog(
            onDismissRequest = {}, // cannot be dismissed: the documents are being moved
            title = { Text(stringResource(R.string.library_move_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.library_moving, progress.done, progress.total))
                }
            },
            confirmButton = {},
        )
    }
    if (uiState.notMoved > 0) {
        AlertDialog(
            onDismissRequest = onDismissNotMoved,
            text = { Text(stringResource(R.string.library_moving_not_moved)) },
            confirmButton = {
                TextButton(onClick = onDismissNotMoved) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}

@Composable
private fun FolderConfirmationDialog(
    confirmation: FolderConfirmation,
    onConfirm: () -> Unit,
    onChooseAnother: () -> Unit,
) {
    val inspection = confirmation.inspection
    AlertDialog(
        onDismissRequest = onChooseAnother,
        title = { Text(stringResource(R.string.library_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.library_confirm_not_empty, confirmation.candidate.displayName))
                if (inspection.fairScanDocuments > 0) {
                    BulletLine("✓", pluralStringResource(
                        R.plurals.library_confirm_documents, inspection.fairScanDocuments, inspection.fairScanDocuments))
                }
                if (inspection.otherItems > 0) {
                    BulletLine("•", pluralStringResource(
                        R.plurals.library_confirm_others, inspection.otherItems, inspection.otherItems))
                }
                Text(
                    stringResource(R.string.library_confirm_question),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.library_confirm_use), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onChooseAnother) {
                Text(stringResource(R.string.library_confirm_choose_another), fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
private fun BulletLine(bullet: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(bullet, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 1.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MoveOfferDialog(offer: MoveOffer, onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = {}, // the user has to say whether the documents follow
        title = { Text(stringResource(R.string.library_move_title)) },
        text = {
            Text(pluralStringResource(
                R.plurals.library_move_message, offer.documentCount,
                offer.documentCount, offer.fromName, offer.candidate.displayName))
        },
        confirmButton = {
            TextButton(onClick = { onAnswer(true) }) {
                Text(stringResource(R.string.library_move_yes), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { onAnswer(false) }) {
                Text(stringResource(R.string.library_move_no), fontWeight = FontWeight.Bold)
            }
        },
    )
}
