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
package org.fairscan.app.ui.screens.document

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import android.graphics.Bitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import net.engawapg.lib.zoomable.ZoomState
import net.engawapg.lib.zoomable.zoomable
import org.fairscan.app.R
import org.fairscan.app.domain.PageViewKey
import org.fairscan.app.domain.Rotation
import org.fairscan.app.ui.Navigation
import org.fairscan.app.ui.components.CommonPageListState
import org.fairscan.app.ui.components.ConfirmationDialog
import org.fairscan.app.ui.components.MainActionButton
import org.fairscan.app.ui.components.MyScaffold
import org.fairscan.app.ui.components.SecondaryActionButton
import org.fairscan.app.ui.dummyNavigation
import org.fairscan.app.ui.fakeDocument
import org.fairscan.app.ui.fakeImage
import org.fairscan.app.ui.theme.FairScanTheme
import org.fairscan.imageprocessing.ColorMode
import org.fairscan.imageprocessing.ColorMode.BLACK_AND_WHITE
import org.fairscan.imageprocessing.ColorMode.COLOR
import org.fairscan.imageprocessing.ColorMode.ENHANCE
import org.fairscan.imageprocessing.ColorMode.GRAYSCALE
import org.fairscan.imageprocessing.ColorMode.LIGHTEN
import org.fairscan.imageprocessing.ColorMode.NEGATIVE
import org.fairscan.imageprocessing.ColorMode.NO_SHADOWS
import org.fairscan.imageprocessing.ColorMode.ORIGINAL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    uiState: DocumentUiState,
    navigation: Navigation,
    onExportClick: () -> Unit,
    onDeleteImage: () -> Unit,
    onRotateImage: (Boolean) -> Unit,
    onColorModeSelected: (ColorMode) -> Unit,
    onCropClick: () -> Unit,
    onPageReorder: (String, Int) -> Unit,
    onPageSelected: (Int) -> Unit,
    adjustmentState: AdjustmentState<Bitmap>? = null,
    adjustmentActions: AdjustmentActions = AdjustmentActions(),
) {
    val showDeletePageDialog = rememberSaveable { mutableStateOf(false) }

    val document = uiState.document
    val currentPageIndex = uiState.currentPageIndex
    // While the adjustments panel is open, going back closes it and the rest of the screen waits.
    val adjusting = adjustmentState != null
    BackHandler { if (adjusting) adjustmentActions.onCancel() else navigation.back() }
    val screenNavigation =
        if (adjusting) navigation.copy(back = adjustmentActions.onCancel) else navigation

    val listState = rememberLazyListState()
    LaunchedEffect(currentPageIndex) {
        listState.scrollToItem(currentPageIndex)
    }

    MyScaffold(
        navigation = screenNavigation,
        pageListState = CommonPageListState(
            document,
            onPageClick = { index -> if (!adjusting) onPageSelected(index) },
            onPageReorder = { id, index -> if (!adjusting) onPageReorder(id, index) },
            currentPageIndex = currentPageIndex,
            listState = listState,
            showPageNumbers = true,
        ),
        bottomBar = {
            BottomBar(onExportClick, navigation.toCameraScreen, enabled = !adjusting)
        },
    ) { modifier ->
        DocumentPreview(
            uiState,
            { showDeletePageDialog.value = true },
            onRotateImage,
            onColorModeSelected,
            onCropClick,
            adjustmentState,
            adjustmentActions,
            onPageSelected,
            modifier
        )
        if (showDeletePageDialog.value) {
            ConfirmationDialog(
                title = stringResource(R.string.delete_page),
                message = stringResource(R.string.delete_page_warning),
                showDialog = showDeletePageDialog
            ) { onDeleteImage() }
        }
    }
}

@Composable
private fun DocumentPreview(
    uiState: DocumentUiState,
    onDeleteImage: () -> Unit,
    onRotateImage: (Boolean) -> Unit,
    onColorModeSelected: (ColorMode) -> Unit,
    onCropClick: () -> Unit,
    adjustmentState: AdjustmentState<Bitmap>?,
    adjustmentActions: AdjustmentActions,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier,
) {
    val currentPageIndex = uiState.currentPageIndex
    val document = uiState.document
    Column (
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Box (
            modifier = if (adjustmentState != null) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxSize()
        ) {
            if (adjustmentState != null) {
                // The preview has no tone applied: the color filter on top does it. Until the first
                // preview is ready the page that is already rendered is shown, as it is.
                val preview = adjustmentState.preview
                PagePager(
                    uiState, onPageSelected,
                    swipeEnabled = false,
                    currentBitmap = preview ?: uiState.currentPage?.bitmap,
                    currentColorFilter = preview?.let { toneColorFilter(adjustmentState.adjustments) },
                )
                if (adjustmentState.isRendering) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                PagePager(
                    uiState, onPageSelected,
                    swipeEnabled = true,
                    currentBitmap = uiState.currentPage?.bitmap,
                    currentColorFilter = null,
                )
                if (uiState.currentPage?.isLoading ?: false) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                EditButtons(
                    uiState,
                    onColorModeSelected,
                    onCropClick,
                    adjustmentActions.onStart,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
                RotationButtons(onRotateImage, Modifier.align(Alignment.BottomCenter))
                SecondaryActionButton(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete_page),
                    onClick = { onDeleteImage() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                )
            }
            Text("${currentPageIndex + 1} / ${document.pageCount()}",
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(all = 16.dp)
                    .background(
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        if (adjustmentState != null) {
            AdjustmentsPanel(adjustmentState, adjustmentActions)
        }
    }
}

/**
 * The page image(s). Only the images slide when the user swipes; the buttons and the rest of the
 * screen stay where they are. The view model's current page follows the pager once it settles, and
 * the pager follows the view model when the page is changed from elsewhere (the strip).
 */
@Composable
private fun PagePager(
    uiState: DocumentUiState,
    onPageSelected: (Int) -> Unit,
    swipeEnabled: Boolean,
    currentBitmap: Bitmap?,
    currentColorFilter: ColorFilter?,
) {
    val pages = uiState.document.pages
    val currentIndex = uiState.currentPageIndex
    val pagerState = rememberPagerState(initialPage = currentIndex.coerceAtLeast(0)) { pages.size }
    val latestIndex by rememberUpdatedState(currentIndex)
    val latestOnPageSelected by rememberUpdatedState(onPageSelected)

    LaunchedEffect(currentIndex, pages.size) {
        if (currentIndex in 0 until pages.size && pagerState.currentPage != currentIndex) {
            pagerState.animateScrollToPage(currentIndex)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            if (settled != latestIndex) latestOnPageSelected(settled)
        }
    }

    // The page state describes one page and its neighbours; whatever page it was made for.
    val loaded = uiState.currentPage
    HorizontalPager(
        state = pagerState,
        userScrollEnabled = swipeEnabled,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize(),
    ) { index ->
        val key = pages.getOrNull(index)?.key
        val bitmap = when {
            loaded == null -> null
            index == loaded.index -> if (index == currentIndex) currentBitmap else loaded.bitmap
            index == loaded.index - 1 -> loaded.previousBitmap
            index == loaded.index + 1 -> loaded.nextBitmap
            else -> null
        }
        Box(Modifier.fillMaxSize()) {
            if (bitmap != null && key != null) {
                PageImage(
                    bitmap, key,
                    colorFilter = if (index == currentIndex) currentColorFilter else null,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.PageImage(
    bitmap: Bitmap,
    pageKey: PageViewKey,
    colorFilter: ColorFilter?,
) {
    val imageBitmap = bitmap.asImageBitmap()
    // The zoom is kept while the same page is re-rendered, even if its bitmap changes size.
    val zoomState = remember(pageKey.pageId, bitmap.width, bitmap.height) {
        ZoomState(
            contentSize = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
        )
    }

    Box(modifier = Modifier
        .fillMaxSize(0.92f)
        .align(Alignment.Center)) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            colorFilter = colorFilter,
            modifier = Modifier
                .padding(4.dp)
                .align(Alignment.Center)
                .zoomable(zoomState)
        )
    }
}

@Composable
fun RotationButtons(
    onRotateImage: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // RotateLeft on the left, RotateRight on the right: for both LTR and RTL languages
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier = modifier.padding(8.dp)) {
            // Using AutoMirrored icons would lead to an opposite rotation in RTL languages
            @Suppress("DEPRECATION")
            SecondaryActionButton(
                icon = Icons.Default.RotateLeft,
                contentDescription = stringResource(R.string.rotate_left),
                onClick = { onRotateImage(false) }
            )
            Spacer(Modifier.width(8.dp))
            @Suppress("DEPRECATION")
            SecondaryActionButton(
                icon = Icons.Default.RotateRight,
                contentDescription = stringResource(R.string.rotate_right),
                onClick = { onRotateImage(true) }
            )
        }
    }
}

@Composable
fun EditButtons(
    uiState: DocumentUiState,
    onColorModeSelected: (ColorMode) -> Unit,
    onCropClick: () -> Unit,
    onAdjustClick: () -> Unit,
    modifier: Modifier
) {
    Row(modifier = modifier.padding(8.dp)) {
        uiState.currentPage?.colorMode?.let {
            ColorModeButton(
                currentColorMode = it,
                onColorModeSelected = onColorModeSelected,
            )
        }
        Spacer(Modifier.width(8.dp))
        // Adjustments re-render the page from its source photo, which only pages with a crop have.
        if (uiState.currentPage?.canBeCropped == true && uiState.currentPage.colorMode != null) {
            SecondaryActionButton(
                icon = Icons.Default.Tune,
                contentDescription = stringResource(R.string.adjust),
                onClick = onAdjustClick,
                // While the page re-renders its filter and adjustments are still the old ones.
                enabled = !uiState.currentPage.isLoading,
            )
            Spacer(Modifier.width(8.dp))
        }
        if (uiState.currentPage?.canBeCropped ?: false) {
            SecondaryActionButton(
                icon = Icons.Default.Crop,
                contentDescription = stringResource(R.string.crop),
                onClick = onCropClick,
            )
        }
    }
}

@Composable
fun ColorModeButton(
    currentColorMode: ColorMode,
    onColorModeSelected: (ColorMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        SecondaryActionButton(
            icon = Icons.Default.AutoFixHigh,
            contentDescription = stringResource(R.string.color_mode),
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ColorMode.entries.forEach { colorMode ->
                DropdownMenuItem(
                    text = { Text(stringResource(colorMode.labelResource)) },
                    onClick = {
                        onColorModeSelected(colorMode)
                        expanded = false
                    },
                    trailingIcon = {
                        if (currentColorMode == colorMode) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                )
            }
        }
    }
}

private val ColorMode.labelResource: Int
    get() = when (this) {
        ORIGINAL -> R.string.color_mode_original
        COLOR -> R.string.color_mode_color
        LIGHTEN -> R.string.color_mode_lighten
        ENHANCE -> R.string.color_mode_enhance
        NO_SHADOWS -> R.string.color_mode_no_shadows
        GRAYSCALE -> R.string.color_mode_grayscale
        BLACK_AND_WHITE -> R.string.color_mode_black_and_white
        NEGATIVE -> R.string.color_mode_negative
    }

@Composable
private fun BottomBar(
    onExportClick: () -> Unit,
    onAddPageClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        OutlinedButton(
            onClick = onAddPageClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.add_page),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        MainActionButton(
            onClick = onExportClick,
            icon = Icons.Default.Done,
            text = stringResource(R.string.export),
            enabled = enabled,
        )
    }
}

@Composable
@Preview
@Preview(locale = "ar")
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Landscape", showBackground = true, widthDp = 640, heightDp = 320)
fun DocumentScreenPreview() {
    FairScanTheme {
        val image = fakeImage("gallica.bnf.fr-bpt6k5530456s-1", LocalContext.current).toBitmap()
        val document = fakeDocument(
            listOf(1, 2).map { "gallica.bnf.fr-bpt6k5530456s-$it" }.toImmutableList(),
            LocalContext.current
        )
        val key = PageViewKey("123", Rotation.R0, null, 0)
        DocumentScreen(
            uiState = DocumentUiState(1, CurrentPageUiState(key,image, COLOR, true, index = 1), document),
            navigation = dummyNavigation(),
            onExportClick = {},
            onDeleteImage = { },
            onRotateImage = { _ -> },
            onColorModeSelected = { },
            onCropClick = { },
            onPageReorder = { _,_ -> },
            onPageSelected = { _ -> },
        )
    }
}
