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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fairscan.app.R
import org.fairscan.imageprocessing.AdjustmentControl
import org.fairscan.imageprocessing.Adjustments
import org.fairscan.imageprocessing.hasTone
import org.fairscan.imageprocessing.range
import org.fairscan.imageprocessing.toneMatrix
import kotlin.math.roundToInt

/** What the Document screen can ask of the adjustments panel. */
data class AdjustmentActions(
    val onStart: () -> Unit = {},
    val onChange: (AdjustmentControl, Int) -> Unit = { _, _ -> },
    val onReset: () -> Unit = {},
    val onApplyToAll: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onConfirm: () -> Unit = {},
)

/**
 * Brightness, contrast and saturation as a color filter, to draw them over the preview. It is the
 * same matrix that the final render applies, so both look alike. Null when the tone is neutral.
 */
fun toneColorFilter(adjustments: Adjustments): ColorFilter? =
    if (adjustments.hasTone) ColorFilter.colorMatrix(ColorMatrix(toneMatrix(adjustments))) else null

@Composable
fun AdjustmentsPanel(
    state: AdjustmentState<*>,
    actions: AdjustmentActions,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.adjustments),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = actions.onReset,
                    enabled = state.adjustments != Adjustments.NEUTRAL,
                ) {
                    Text(stringResource(R.string.adjustments_reset))
                }
            }
            // A filter can have up to five controls: scroll rather than squeeze the page.
            Column(
                Modifier
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                AdjustmentControl.entries
                    .filter { it in state.colorMode.controls }
                    .forEach { control ->
                        AdjustmentSlider(control, state.adjustments.get(control), actions.onChange)
                    }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = actions.onCancel) {
                    Text(stringResource(R.string.cancel))
                }
                OutlinedButton(
                    onClick = actions.onApplyToAll,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(R.string.adjustments_apply_to_all),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(onClick = actions.onConfirm) {
                    Text(stringResource(R.string.apply))
                }
            }
        }
    }
}

@Composable
private fun AdjustmentSlider(
    control: AdjustmentControl,
    value: Int,
    onChange: (AdjustmentControl, Int) -> Unit,
) {
    val range = control.range
    // One row per control keeps five of them within the height of the panel.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(control.labelResource),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.34f),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(control, it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.weight(0.54f),
        )
        Text(
            value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.12f),
        )
    }
}

private val AdjustmentControl.labelResource: Int
    get() = when (this) {
        AdjustmentControl.BRIGHTNESS -> R.string.adjustment_brightness
        AdjustmentControl.CONTRAST -> R.string.adjustment_contrast
        AdjustmentControl.SATURATION -> R.string.adjustment_saturation
        AdjustmentControl.DETAIL -> R.string.adjustment_detail
        AdjustmentControl.SHADOW_REMOVAL -> R.string.adjustment_shadow_removal
        AdjustmentControl.THRESHOLD -> R.string.adjustment_threshold
    }
