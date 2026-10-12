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
package org.fairscan.imageprocessing

import java.util.zip.CRC32

/**
 * A slider the user can move to tune the rendering of a page. [needsReprocessing] tells whether
 * changing it requires running the filter again. Brightness, contrast and saturation do not: they
 * are a color matrix that can be applied on top of an already rendered image.
 */
enum class AdjustmentControl(val needsReprocessing: Boolean) {
    BRIGHTNESS(false),
    CONTRAST(false),
    SATURATION(false),
    DETAIL(true),
    SHADOW_REMOVAL(true),
    THRESHOLD(true),
}

/**
 * Per-page tuning on top of a [ColorMode]. Every value is an integer so that it can be stored and
 * hashed without rounding issues. The default instance is neutral: it reproduces the unadjusted
 * output of the filter.
 */
data class Adjustments(
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val detail: Int = 0,
    val shadowRemoval: Int = 0,
    val threshold: Int = 0,
) {
    init {
        require(brightness in SIGNED_RANGE) { "brightness out of range: $brightness" }
        require(contrast in SIGNED_RANGE) { "contrast out of range: $contrast" }
        require(saturation in SIGNED_RANGE) { "saturation out of range: $saturation" }
        require(detail in DETAIL_RANGE) { "detail out of range: $detail" }
        require(shadowRemoval in SIGNED_RANGE) { "shadowRemoval out of range: $shadowRemoval" }
        require(threshold in SIGNED_RANGE) { "threshold out of range: $threshold" }
    }

    val isNeutral: Boolean get() = this == NEUTRAL

    fun get(control: AdjustmentControl): Int = when (control) {
        AdjustmentControl.BRIGHTNESS -> brightness
        AdjustmentControl.CONTRAST -> contrast
        AdjustmentControl.SATURATION -> saturation
        AdjustmentControl.DETAIL -> detail
        AdjustmentControl.SHADOW_REMOVAL -> shadowRemoval
        AdjustmentControl.THRESHOLD -> threshold
    }

    /** Returns a copy with [control] set to [value], clamped to the control's range. */
    fun with(control: AdjustmentControl, value: Int): Adjustments {
        val v = value.coerceIn(control.range)
        return when (control) {
            AdjustmentControl.BRIGHTNESS -> copy(brightness = v)
            AdjustmentControl.CONTRAST -> copy(contrast = v)
            AdjustmentControl.SATURATION -> copy(saturation = v)
            AdjustmentControl.DETAIL -> copy(detail = v)
            AdjustmentControl.SHADOW_REMOVAL -> copy(shadowRemoval = v)
            AdjustmentControl.THRESHOLD -> copy(threshold = v)
        }
    }

    /** The same adjustments with brightness, contrast and saturation neutral. */
    fun withoutTone(): Adjustments = copy(brightness = 0, contrast = 0, saturation = 0)

    /** Neutralizes the controls that [mode] does not expose, since they must have no effect. */
    fun forMode(mode: ColorMode): Adjustments =
        AdjustmentControl.entries
            .filter { it !in mode.controls }
            .fold(this) { acc, control -> acc.with(control, 0) }

    /** Stable text form, used to derive [hash]. Do not change it: it appears in file names. */
    fun canonical(): String =
        "b$brightness,c$contrast,s$saturation,d$detail,r$shadowRemoval,t$threshold"

    /** 8 lowercase hex digits (CRC32 of [canonical]). Safe to use in file names. */
    fun hash(): String = "%08x".format(CRC32().apply { update(canonical().toByteArray()) }.value)

    companion object {
        val NEUTRAL = Adjustments()

        /** Builds an instance from untrusted values (e.g. stored metadata), clamping each one. */
        fun coerced(
            brightness: Int = 0,
            contrast: Int = 0,
            saturation: Int = 0,
            detail: Int = 0,
            shadowRemoval: Int = 0,
            threshold: Int = 0,
        ) = Adjustments(
            brightness.coerceIn(SIGNED_RANGE),
            contrast.coerceIn(SIGNED_RANGE),
            saturation.coerceIn(SIGNED_RANGE),
            detail.coerceIn(DETAIL_RANGE),
            shadowRemoval.coerceIn(SIGNED_RANGE),
            threshold.coerceIn(SIGNED_RANGE),
        )
    }
}

private val SIGNED_RANGE = -100..100
private val DETAIL_RANGE = 0..100

/** Allowed values of the control. */
val AdjustmentControl.range: IntRange
    get() = if (this == AdjustmentControl.DETAIL) DETAIL_RANGE else SIGNED_RANGE
