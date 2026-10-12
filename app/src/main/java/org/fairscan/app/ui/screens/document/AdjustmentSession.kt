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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fairscan.imageprocessing.AdjustmentControl
import org.fairscan.imageprocessing.Adjustments
import org.fairscan.imageprocessing.ColorMode

/** What the adjustments panel shows. [P] is the type of the preview image. */
data class AdjustmentState<P>(
    val colorMode: ColorMode,
    val adjustments: Adjustments,
    /** Page rendered with the current filter and the reprocessing controls, without the tone. */
    val preview: P?,
    val isRendering: Boolean,
    val hasChanges: Boolean,
)

/**
 * The editing session behind the adjustments panel, for one page and one filter.
 *
 * Brightness, contrast and saturation only change [AdjustmentState.adjustments]: the screen applies
 * them on top of the preview as a color matrix. The other controls need the filter to run again,
 * which [render] does on a reduced-size copy of the page. Renders are debounced and a newer request
 * cancels the previous one, so only the latest result is ever shown.
 *
 * The session decides nothing about the stored page: [confirm] and [cancel] return the adjustments
 * to keep, and the caller commits them.
 */
class AdjustmentSession<P : Any>(
    private val colorMode: ColorMode,
    initial: Adjustments,
    private val scope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val onError: (Throwable) -> Unit = {},
    /** Renders the preview for the filter and the reprocessing controls (tone is neutral). */
    private val render: suspend (ColorMode, Adjustments) -> P?,
) {
    private val initial = initial.forMode(colorMode)

    private val _state = MutableStateFlow(
        AdjustmentState<P>(colorMode, this.initial, preview = null, isRendering = false, hasChanges = false)
    )
    val state: StateFlow<AdjustmentState<P>> = _state.asStateFlow()

    private var renderJob: Job? = null
    // The reprocessing part of the adjustments that the preview shows or is being rendered for.
    private var requested: Adjustments? = null
    // The same for the preview that is currently shown.
    private var shown: Adjustments? = null
    private var closed = false

    /** Renders the preview of the initial adjustments, with no delay. */
    fun start() {
        if (closed) return
        requestRender(delayMillis = 0)
    }

    fun set(control: AdjustmentControl, value: Int) {
        if (closed || control !in colorMode.controls) return
        update { it.with(control, value) }
        if (control.needsReprocessing) requestRender(debounceMillis)
    }

    /** Brings every control of the filter back to neutral. */
    fun reset() {
        if (closed) return
        update { Adjustments.NEUTRAL }
        requestRender(debounceMillis)
    }

    /** Ends the session and returns the adjustments the page had before it. */
    fun cancel(): Adjustments {
        close()
        return initial
    }

    /** Ends the session and returns the adjustments to store. */
    fun confirm(): Adjustments {
        val result = _state.value.adjustments
        close()
        return result
    }

    fun close() {
        closed = true
        renderJob?.cancel()
        renderJob = null
        _state.update { it.copy(isRendering = false) }
    }

    private fun update(change: (Adjustments) -> Adjustments) {
        _state.update {
            val adjustments = change(it.adjustments)
            it.copy(adjustments = adjustments, hasChanges = adjustments != initial)
        }
    }

    private fun requestRender(delayMillis: Long) {
        val wanted = _state.value.adjustments.withoutTone()
        if (wanted == requested) return
        requested = wanted

        renderJob?.cancel()
        if (wanted == shown) {
            // Back to what the preview already shows: nothing to render.
            renderJob = null
            _state.update { it.copy(isRendering = false) }
            return
        }
        _state.update { it.copy(isRendering = true) }
        renderJob = scope.launch {
            try {
                if (delayMillis > 0) delay(delayMillis)
                val image = render(colorMode, wanted)
                ensureActive()
                if (image != null) shown = wanted
                _state.update { it.copy(preview = image ?: it.preview, isRendering = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep the previous preview, and allow the same request to be tried again.
                if (requested == wanted) requested = null
                _state.update { it.copy(isRendering = false) }
                onError(e)
            }
        }
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 200L
    }
}
