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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.fairscan.app.R
import org.fairscan.app.data.library.LibraryLocation
import org.fairscan.app.data.library.LibraryStatus
import org.fairscan.app.ui.components.AppOverflowMenu
import org.fairscan.app.ui.components.MainActionButton
import org.fairscan.app.ui.dummyNavigation
import org.fairscan.app.ui.Navigation
import org.fairscan.app.ui.theme.FairScanTheme

/**
 * First screen of the app until the user has chosen where the library is kept (and again if that
 * folder cannot be reached any more). The folder itself is obtained through [onUseDefaultFolder]
 * (the `FairScan` folder, created with "All files access") or [onChooseFolder] (the system picker).
 */
@Composable
fun LibrarySetupScreen(
    uiState: LibraryFolderUiState,
    navigation: Navigation,
    onUseDefaultFolder: () -> Unit,
    onChooseFolder: () -> Unit,
) {
    val unavailable = uiState.status as? LibraryStatus.Unavailable
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                AppOverflowMenu(navigation)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Icon(
                imageVector = if (unavailable != null) Icons.Outlined.WarningAmber else Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = if (unavailable != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = stringResource(
                    if (unavailable != null) R.string.library_setup_unavailable_title else R.string.library_setup_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (unavailable != null) {
                    stringResource(R.string.library_setup_unavailable_body, unavailable.location.displayName)
                } else {
                    stringResource(R.string.library_setup_body)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (uiState.allFilesAccessOffered) {
                DefaultFolderCard(uiState.defaultFolderName)
                Text(
                    text = stringResource(R.string.library_setup_access_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = stringResource(R.string.library_setup_choose_folder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (uiState.permissionDenied) {
                Text(
                    text = stringResource(R.string.library_setup_permission_denied),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            if (uiState.hasError) {
                Text(
                    text = stringResource(R.string.library_folder_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            if (uiState.isWorking) {
                CircularProgressIndicator()
                Text(stringResource(R.string.library_folder_working), style = MaterialTheme.typography.bodyMedium)
            } else if (uiState.allFilesAccessOffered) {
                MainActionButton(
                    onClick = onUseDefaultFolder,
                    text = stringResource(R.string.library_setup_use_default, uiState.defaultFolderName),
                    icon = Icons.Default.Check,
                )
                TextButton(onClick = onChooseFolder) {
                    Text(stringResource(R.string.library_setup_choose_other))
                }
            } else {
                MainActionButton(
                    onClick = onChooseFolder,
                    text = stringResource(R.string.library_setup_choose_folder),
                    icon = Icons.Default.Folder,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DefaultFolderCard(folderName: String) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Box(Modifier.weight(1f)) {
            Column {
                Text(
                    text = stringResource(R.string.library_folder_default_label, folderName),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.library_folder_recommended),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Preview(showBackground = true, widthDp = 640, heightDp = 320)
@Composable
fun LibrarySetupScreenPreview() {
    FairScanTheme {
        LibrarySetupScreen(
            LibraryFolderUiState(status = LibraryStatus.NotSetUp, defaultFolderName = "FairScan"),
            dummyNavigation(), {}, {})
    }
}

@Preview(showBackground = true)
@Composable
fun LibrarySetupScreenPermissionDeniedPreview() {
    FairScanTheme {
        LibrarySetupScreen(
            LibraryFolderUiState(status = LibraryStatus.NotSetUp, defaultFolderName = "FairScan",
                permissionDenied = true),
            dummyNavigation(), {}, {})
    }
}

@Preview(showBackground = true)
@Composable
fun LibrarySetupScreenUnavailablePreview() {
    FairScanTheme {
        LibrarySetupScreen(
            LibraryFolderUiState(
                status = LibraryStatus.Unavailable(LibraryLocation.Direct("/storage/emulated/0/FairScan")),
                defaultFolderName = "FairScan"),
            dummyNavigation(), {}, {})
    }
}

@Preview(showBackground = true)
@Composable
fun LibrarySetupScreenWithoutAllFilesAccessPreview() {
    FairScanTheme {
        LibrarySetupScreen(
            LibraryFolderUiState(status = LibraryStatus.NotSetUp, defaultFolderName = "FairScan",
                allFilesAccessOffered = false),
            dummyNavigation(), {}, {})
    }
}
