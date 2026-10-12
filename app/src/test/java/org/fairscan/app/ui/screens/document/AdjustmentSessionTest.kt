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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.fairscan.imageprocessing.AdjustmentControl.*
import org.fairscan.imageprocessing.Adjustments
import org.fairscan.imageprocessing.ColorMode
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdjustmentSessionTest {

    private class Renders {
        val calls = mutableListOf<Pair<ColorMode, Adjustments>>()
        val count get() = calls.size
        val lastAdjustments get() = calls.last().second
    }

    // advanceUntilIdle does not wait for work in backgroundScope, so move the clock explicitly.
    private fun TestScope.settle() {
        advanceTimeBy(10_000)
        runCurrent()
    }

    private fun TestScope.session(
        mode: ColorMode = ColorMode.COLOR,
        initial: Adjustments = Adjustments.NEUTRAL,
        renders: Renders = Renders(),
        renderTimeMillis: Long = 0,
        onError: (Throwable) -> Unit = {},
        failWhen: (Adjustments) -> Boolean = { false },
    ): Pair<AdjustmentSession<String>, Renders> {
        val session = AdjustmentSession<String>(
            colorMode = mode,
            initial = initial,
            scope = backgroundScope,
            debounceMillis = 200,
            onError = onError,
        ) { m, adj ->
            renders.calls += m to adj
            if (renderTimeMillis > 0) delay(renderTimeMillis)
            if (failWhen(adj)) throw IllegalStateException("render failed")
            "preview of ${adj.canonical()}"
        }
        return session to renders
    }

    @Test
    fun start_renders_the_initial_adjustments_once_without_waiting() = runTest {
        val (session, renders) = session(initial = Adjustments(shadowRemoval = 30, brightness = 20))
        session.start()
        runCurrent()

        assertThat(renders.count).isEqualTo(1)
        // The preview is rendered without tone: the screen applies it.
        assertThat(renders.lastAdjustments).isEqualTo(Adjustments(shadowRemoval = 30))
        assertThat(session.state.value.preview).isEqualTo("preview of ${Adjustments(shadowRemoval = 30).canonical()}")
        assertThat(session.state.value.isRendering).isFalse()
        assertThat(session.state.value.adjustments).isEqualTo(Adjustments(shadowRemoval = 30, brightness = 20))
    }

    @Test
    fun tone_changes_are_instant_and_never_render() = runTest {
        val (session, renders) = session()
        session.start()
        runCurrent()
        assertThat(renders.count).isEqualTo(1)

        session.set(BRIGHTNESS, 40)
        session.set(CONTRAST, -20)
        session.set(SATURATION, 10)
        advanceTimeBy(5_000)

        assertThat(session.state.value.adjustments).isEqualTo(Adjustments(brightness = 40, contrast = -20, saturation = 10))
        assertThat(renders.count).isEqualTo(1)
        assertThat(session.state.value.hasChanges).isTrue()
    }

    @Test
    fun a_reprocessing_change_renders_after_the_debounce() = runTest {
        val (session, renders) = session()
        session.start()
        runCurrent()

        session.set(DETAIL, 50)
        advanceTimeBy(199)
        runCurrent()
        assertThat(renders.count).isEqualTo(1)
        assertThat(session.state.value.isRendering).isTrue()

        advanceTimeBy(2)
        runCurrent()
        assertThat(renders.count).isEqualTo(2)
        assertThat(renders.lastAdjustments).isEqualTo(Adjustments(detail = 50))
        assertThat(session.state.value.preview).isEqualTo("preview of ${Adjustments(detail = 50).canonical()}")
        assertThat(session.state.value.isRendering).isFalse()
    }

    @Test
    fun rapid_changes_render_only_the_last_value() = runTest {
        val (session, renders) = session()
        session.start()
        runCurrent()

        for (v in listOf(10, 20, 30, 40, 50, 60)) {
            session.set(SHADOW_REMOVAL, v)
            advanceTimeBy(50)
        }
        settle()

        assertThat(renders.count).isEqualTo(2) // the initial one and the last value
        assertThat(renders.lastAdjustments).isEqualTo(Adjustments(shadowRemoval = 60))
    }

    @Test
    fun a_newer_change_cancels_a_render_in_progress_and_discards_its_result() = runTest {
        val (session, renders) = session(renderTimeMillis = 1_000)
        session.start()
        settle()
        assertThat(renders.count).isEqualTo(1)

        session.set(DETAIL, 20)
        advanceTimeBy(300)          // debounce done, render started and still running
        runCurrent()
        assertThat(renders.count).isEqualTo(2)

        session.set(DETAIL, 80)     // newer value while the first render is still running
        settle()

        assertThat(renders.count).isEqualTo(3)
        assertThat(session.state.value.preview).isEqualTo("preview of ${Adjustments(detail = 80).canonical()}")
        assertThat(session.state.value.isRendering).isFalse()
    }

    @Test
    fun going_back_to_the_shown_preview_does_not_render_again() = runTest {
        val (session, renders) = session(renderTimeMillis = 1_000)
        session.start()
        settle()
        assertThat(renders.count).isEqualTo(1)

        session.set(THRESHOLD, 0)   // same as now: nothing to do
        session.set(DETAIL, 30)
        advanceTimeBy(300)
        runCurrent()                // render for detail 30 is running
        session.set(DETAIL, 0)      // back to what is shown
        settle()

        assertThat(renders.count).isEqualTo(2)
        assertThat(session.state.value.isRendering).isFalse()
        assertThat(session.state.value.preview).isEqualTo("preview of ${Adjustments.NEUTRAL.canonical()}")
    }

    @Test
    fun controls_the_filter_does_not_expose_are_ignored() = runTest {
        val (session, renders) = session(mode = ColorMode.BLACK_AND_WHITE)
        session.start()
        runCurrent()

        session.set(BRIGHTNESS, 50)
        session.set(DETAIL, 50)
        session.set(SHADOW_REMOVAL, 50)
        advanceTimeBy(5_000)
        assertThat(session.state.value.adjustments).isEqualTo(Adjustments.NEUTRAL)
        assertThat(renders.count).isEqualTo(1)

        session.set(THRESHOLD, 30)
        settle()
        assertThat(session.state.value.adjustments).isEqualTo(Adjustments(threshold = 30))
        assertThat(renders.lastAdjustments).isEqualTo(Adjustments(threshold = 30))
    }

    @Test
    fun initial_adjustments_of_controls_the_filter_does_not_expose_are_dropped() = runTest {
        val (session, _) = session(mode = ColorMode.GRAYSCALE, initial = Adjustments(saturation = 40, contrast = 10))
        assertThat(session.state.value.adjustments).isEqualTo(Adjustments(contrast = 10))
        assertThat(session.state.value.hasChanges).isFalse()
    }

    @Test
    fun values_are_clamped_to_the_range_of_the_control() = runTest {
        val (session, _) = session()
        session.set(BRIGHTNESS, 500)
        session.set(DETAIL, -5)
        assertThat(session.state.value.adjustments).isEqualTo(Adjustments(brightness = 100, detail = 0))
    }

    @Test
    fun reset_goes_back_to_neutral_and_rerenders_when_needed() = runTest {
        val (session, renders) = session(initial = Adjustments(detail = 40, contrast = 15))
        session.start()
        runCurrent()
        assertThat(renders.count).isEqualTo(1)

        session.reset()
        settle()

        assertThat(session.state.value.adjustments).isEqualTo(Adjustments.NEUTRAL)
        assertThat(renders.count).isEqualTo(2)
        assertThat(renders.lastAdjustments).isEqualTo(Adjustments.NEUTRAL)
        assertThat(session.state.value.hasChanges).isTrue() // differs from what the page had
    }

    @Test
    fun reset_with_only_tone_changes_does_not_render() = runTest {
        val (session, renders) = session()
        session.start()
        runCurrent()
        session.set(BRIGHTNESS, 30)

        session.reset()
        advanceTimeBy(5_000)

        assertThat(session.state.value.adjustments).isEqualTo(Adjustments.NEUTRAL)
        assertThat(renders.count).isEqualTo(1)
        assertThat(session.state.value.hasChanges).isFalse()
    }

    @Test
    fun cancel_returns_the_initial_adjustments_and_stops_pending_renders() = runTest {
        val initial = Adjustments(detail = 10, brightness = 5)
        val (session, renders) = session(initial = initial)
        session.start()
        runCurrent()
        session.set(DETAIL, 90)
        session.set(BRIGHTNESS, -50)

        val result = session.cancel()
        advanceTimeBy(5_000)

        assertThat(result).isEqualTo(initial)
        assertThat(renders.count).isEqualTo(1)
        assertThat(session.state.value.isRendering).isFalse()
    }

    @Test
    fun confirm_returns_the_current_adjustments_and_stops_pending_renders() = runTest {
        val (session, renders) = session()
        session.start()
        runCurrent()
        session.set(DETAIL, 70)
        session.set(CONTRAST, 25)

        val result = session.confirm()
        advanceTimeBy(5_000)

        assertThat(result).isEqualTo(Adjustments(detail = 70, contrast = 25))
        assertThat(renders.count).isEqualTo(1)
    }

    @Test
    fun a_closed_session_ignores_changes() = runTest {
        val (session, renders) = session()
        session.start()
        runCurrent()
        session.close()

        session.set(DETAIL, 50)
        session.reset()
        session.start()
        advanceTimeBy(5_000)

        assertThat(session.state.value.adjustments).isEqualTo(Adjustments.NEUTRAL)
        assertThat(renders.count).isEqualTo(1)
    }

    @Test
    fun a_failed_render_keeps_the_previous_preview_and_reports_the_error() = runTest {
        val errors = mutableListOf<Throwable>()
        val (session, renders) = session(onError = { errors += it }, failWhen = { it.detail == 50 })
        session.start()
        runCurrent()
        val before = session.state.value.preview

        session.set(DETAIL, 50)
        settle()

        assertThat(errors).hasSize(1)
        assertThat(session.state.value.preview).isEqualTo(before)
        assertThat(session.state.value.isRendering).isFalse()
        // The adjustment itself is kept, and the same value can be tried again.
        assertThat(session.state.value.adjustments.detail).isEqualTo(50)
        session.set(DETAIL, 51)
        session.set(DETAIL, 50)
        settle()
        assertThat(renders.count).isGreaterThan(2)
    }

    @Test
    fun a_render_that_returns_nothing_keeps_the_previous_preview() = runTest {
        var answer: String? = "first"
        val session = AdjustmentSession<String>(ColorMode.COLOR, Adjustments.NEUTRAL, backgroundScope, 200) { _, _ -> answer }
        session.start()
        runCurrent()
        assertThat(session.state.value.preview).isEqualTo("first")

        answer = null
        session.set(DETAIL, 30)
        settle()

        assertThat(session.state.value.preview).isEqualTo("first")
        assertThat(session.state.value.isRendering).isFalse()
    }
}
