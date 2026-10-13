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
package org.fairscan.app.domain

import org.assertj.core.api.Assertions.assertThat
import org.fairscan.imageprocessing.Adjustments
import org.fairscan.imageprocessing.ColorMode
import org.junit.Test

class PageViewKeyTest {

    private val base = PageViewKey("100", Rotation.R0, ColorMode.COLOR, 0, Adjustments.NEUTRAL)

    @Test
    fun `the same page gives the same key`() {
        assertThat(base.renderKey()).isEqualTo(base.copy().renderKey())
        assertThat(base.renderKey()).matches("[0-9a-f]{8}")
    }

    @Test
    fun `anything that changes the pixels changes the key`() {
        val keys = listOf(
            base,
            base.copy(rotation = Rotation.R90),
            base.copy(colorMode = ColorMode.GRAYSCALE),
            base.copy(quadVersion = 1),
            base.copy(adjustments = Adjustments.coerced(brightness = 10)),
        ).map { it.renderKey() }

        assertThat(keys).doesNotHaveDuplicates()
    }

    @Test
    fun `the page id is not part of the key`() {
        assertThat(base.copy(pageId = "200").renderKey()).isEqualTo(base.renderKey())
    }
}
