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
package org.fairscan.app.ui.components

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fairscan.app.THUMBNAIL_SIZE_DP
import org.fairscan.app.ui.state.DocumentUiModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

const val PAGE_LIST_ELEMENT_SIZE_DP = THUMBNAIL_SIZE_DP

/**
 * Selection mode of the page list. A long press on a page (without moving it) calls [onLongPress].
 * While [isSelecting], reordering is disabled and a click toggles the page through [onPageClick].
 */
data class PageListSelection(
    val selectedIds: Set<String>,
    val onLongPress: (String) -> Unit,
) {
    val isSelecting get() = selectedIds.isNotEmpty()
}

data class CommonPageListState(
    val document: DocumentUiModel,
    val onPageClick: (Int) -> Unit,
    val onPageReorder: (String, Int) -> Unit,
    val listState: LazyListState,
    val showPageNumbers: Boolean,
    val currentPageIndex: Int? = null,
    val onLastItemPosition: ((Offset) -> Unit)? = null,
    val selection: PageListSelection? = null,
)

@Composable
fun CommonPageList(
    state: CommonPageListState,
    modifier: Modifier = Modifier,
    forceHorizontal: Boolean = false,
) {
    val isLandscape = !forceHorizontal &&
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var hasMoved by remember { mutableStateOf(false) }
    val reorderableLazyListState = rememberReorderableLazyListState(state.listState) { from, to ->
        hasMoved = true
        val pageId = state.document.pages[from.index].key.pageId
        state.onPageReorder(pageId, to.index)
    }
    val content: LazyListScope.() -> Unit = {
        itemsIndexed(state.document.pages.map { it.key }, key = { _, item -> item.pageId}) { index, item ->
            ReorderableItem(reorderableLazyListState, key = item.pageId) { isDragging ->
                val borderColor =
                    if (isDragging) MaterialTheme.colorScheme.primary else Color.Transparent
                val selection = state.selection
                val modifier = Modifier
                    .longPressDraggableHandle(
                        enabled = selection?.isSelecting != true,
                        onDragStarted = { hasMoved = false },
                        onDragStopped = {
                            // A long press that did not move the page starts the selection
                            if (selection != null && !hasMoved) selection.onLongPress(item.pageId)
                        },
                    )
                    .border(
                        width = 2.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(6.dp)
                    )
                val image = state.document.thumbnail(index)
                if (image != null) {
                    PageThumbnail(image, index, state, modifier)
                }
            }
        }
    }
    if (isLandscape) {
        LazyColumn (
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier,
            content = content,
        )
    } else {
        LazyRow (
            state = state.listState,
            contentPadding = PaddingValues(4.dp),
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
    if (state.document.isEmpty()) {
        val layoutDirection = LocalLayoutDirection.current
        Box(
            modifier = Modifier
                .height(THUMBNAIL_SIZE_DP.dp)
                .addPositionCallback(
                    state.onLastItemPosition, LocalDensity.current, 0.5f, layoutDirection)
        ) {}
    }
}

@Composable
private fun PageThumbnail(
    image: Bitmap,
    index: Int,
    state: CommonPageListState,
    modifier: Modifier,
) {
    val bitmap = image.asImageBitmap()
    val pageId = state.document.pages[index].key.pageId
    val selection = state.selection
    val isChecked = selection?.selectedIds?.contains(pageId) == true
    val isSelected =
        if (selection?.isSelecting == true) isChecked else index == state.currentPageIndex
    val borderColor =
        if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray
    val maxImageSize = PAGE_LIST_ELEMENT_SIZE_DP.dp
    var imageModifier =
        if (bitmap.height > bitmap.width)
            Modifier.height(maxImageSize)
        else
            Modifier.width(maxImageSize)
    if (index == state.document.lastIndex()) {
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        imageModifier = imageModifier.addPositionCallback(
            state.onLastItemPosition, density, 1.0f, layoutDirection)
    }
    Box(modifier = modifier.height(PAGE_LIST_ELEMENT_SIZE_DP.dp)) {
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(if (isSelected) 3.dp else 1.dp, borderColor),
            modifier = Modifier.padding(4.dp).align(Alignment.Center)
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = "${index + 1}",
                modifier = imageModifier.clickable { state.onPageClick(index) }
            )
        }
        if (state.showPageNumbers) {
            PageNumberBadge(index + 1)
        }
        if (isChecked) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .background(Color.White, CircleShape)
            )
        }
    }
}

/** The number of a page, at the bottom of its thumbnail (in the strip and in the grid of pages). */
@Composable
fun BoxScope.PageNumberBadge(number: Int) {
    Box(
        modifier = Modifier
            .padding(8.dp)
            .align(Alignment.BottomCenter)
            .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(4.dp))
            .padding(vertical = 0.dp, horizontal = 8.dp)
    ) {
        Text(
            text = "$number",
            color = Color.White,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
    }
}

fun Modifier.addPositionCallback(
    callback: ((Offset) -> Unit)?,
    density: Density,
    xFactor: Float,
    layoutDirection: LayoutDirection
): Modifier {
    if (callback == null) {
        return this
    }

    return this.onGloballyPositioned { coordinates ->
        with(density) {
            val width = PAGE_LIST_ELEMENT_SIZE_DP.dp.toPx()

            val x = when (layoutDirection) {
                LayoutDirection.Ltr -> width * xFactor
                LayoutDirection.Rtl -> width * (0.5f - xFactor)
            }

            callback(
                coordinates.localToWindow(Offset(x = x, y = width / 2))
            )
        }
    }
}
