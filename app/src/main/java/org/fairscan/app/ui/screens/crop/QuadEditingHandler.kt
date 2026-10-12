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
package org.fairscan.app.ui.screens.crop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.fairscan.imageprocessing.Point
import org.fairscan.imageprocessing.Quad
import kotlin.math.hypot

class QuadEditingHandler {

    companion object {
        const val CORNER_RADIUS = 40f
        const val CORNER_TOUCH_RADIUS = 90f
        const val SIDE_HANDLE_HALF_LENGTH = 50f

        /** Handle indices 0..3 are the corners (TL, TR, BR, BL); 4..7 are the sides top, right, bottom, left. */
        const val FIRST_SIDE_INDEX = 4
        const val HANDLE_COUNT = 8

        fun isSide(handleIndex: Int) = handleIndex in FIRST_SIDE_INDEX until HANDLE_COUNT
    }

    fun findTouchedCorner(
        touchPos: Offset,
        quad: Quad,
        containerSize: IntSize,
        displaySize: IntSize
    ): Int {
        return findTouchedCornerCandidates(touchPos, quad, containerSize, displaySize)
            .firstOrNull() ?: -1
    }

    fun findTouchedCornerCandidates(
        touchPos: Offset,
        quad: Quad,
        containerSize: IntSize,
        displaySize: IntSize
    ): List<Int> {
        val corners = getCornerPositions(quad, containerSize, displaySize)
        return corners
            .mapIndexed { index, corner -> index to (touchPos - corner).getDistance() }
            .filter { (_, distance) -> distance < CORNER_TOUCH_RADIUS }
            .sortedBy { (_, distance) -> distance }
            .map { (index, _) -> index }
    }

    /** Corner under the touch if any, otherwise the side whose midpoint is under the touch, otherwise -1. */
    fun findTouchedHandle(
        touchPos: Offset,
        quad: Quad,
        containerSize: IntSize,
        displaySize: IntSize
    ): Int {
        val corner = findTouchedCorner(touchPos, quad, containerSize, displaySize)
        if (corner >= 0) return corner
        return (0 until 4)
            .map { side -> side to (touchPos - sideMidpointOnScreen(quad, side, containerSize, displaySize)).getDistance() }
            .filter { (_, distance) -> distance < CORNER_TOUCH_RADIUS }
            .minByOrNull { (_, distance) -> distance }
            ?.let { (side, _) -> FIRST_SIDE_INDEX + side }
            ?: -1
    }

    /** Midpoint of the side (0 = top, 1 = right, 2 = bottom, 3 = left) in container coordinates. */
    fun sideMidpointOnScreen(quad: Quad, side: Int, containerSize: IntSize, displaySize: IntSize): Offset {
        val (a, b) = sideEnds(quad, side)
        return (QuadCoordinateUtils.normalizedToScreen(a, containerSize, displaySize) +
                QuadCoordinateUtils.normalizedToScreen(b, containerSize, displaySize)) / 2f
    }

    /** Moves a corner (index 0..3) or a side (index 4..7) by a normalized [delta]. */
    fun updateQuad(quad: Quad, handleIndex: Int, delta: Offset, displaySize: IntSize): Quad =
        if (isSide(handleIndex)) {
            updateQuadSide(quad, handleIndex - FIRST_SIDE_INDEX, delta, displaySize)
        } else {
            updateQuadCorner(quad, handleIndex, delta)
        }

    /**
     * Translates a side perpendicular to its own direction: only the component of [delta]
     * (normalized coordinates) across the side is kept, so the side stays parallel to itself.
     * The step is ignored if it would leave the image or make the quad non-convex.
     */
    fun updateQuadSide(quad: Quad, side: Int, delta: Offset, displaySize: IntSize): Quad {
        if (side !in 0..3) return quad
        val (a, b) = sideEnds(quad, side)
        // Work in pixels: the image aspect ratio distorts normalized coordinates.
        val w = displaySize.width.toDouble()
        val h = displaySize.height.toDouble()
        val dx = (b.x - a.x) * w
        val dy = (b.y - a.y) * h
        val length = hypot(dx, dy)
        if (length == 0.0) return quad
        val nx = -dy / length
        val ny = dx / length
        val across = delta.x * w * nx + delta.y * h * ny
        val move = Point(across * nx / w, across * ny / h)
        val newA = a + move
        val newB = b + move
        if (!isInsideImage(newA) || !isInsideImage(newB)) return quad
        val candidate = when (side) {
            0 -> quad.copy(topLeft = newA, topRight = newB)
            1 -> quad.copy(topRight = newA, bottomRight = newB)
            2 -> quad.copy(bottomRight = newA, bottomLeft = newB)
            else -> quad.copy(bottomLeft = newA, topLeft = newB)
        }
        return if (candidate.isConvex()) candidate else quad
    }

    private fun sideEnds(quad: Quad, side: Int): Pair<Point, Point> = when (side) {
        0 -> quad.topLeft to quad.topRight
        1 -> quad.topRight to quad.bottomRight
        2 -> quad.bottomRight to quad.bottomLeft
        else -> quad.bottomLeft to quad.topLeft
    }

    private fun isInsideImage(p: Point) = p.x in 0.0..1.0 && p.y in 0.0..1.0

    fun updateQuadCorner(quad: Quad, cornerIndex: Int, delta: Offset): Quad {
        val normalizedDelta = Point(delta.x.toDouble(), delta.y.toDouble())
        val candidate = when (cornerIndex) {
            0 -> quad.copy(topLeft = clampPoint(quad.topLeft + normalizedDelta))
            1 -> quad.copy(topRight = clampPoint(quad.topRight + normalizedDelta))
            2 -> quad.copy(bottomRight = clampPoint(quad.bottomRight + normalizedDelta))
            3 -> quad.copy(bottomLeft = clampPoint(quad.bottomLeft + normalizedDelta))
            else -> quad
        }
        return if (candidate.isConvex()) candidate else quad
    }

    private fun getCornerPositions(quad: Quad, containerSize: IntSize, displaySize: IntSize): List<Offset> {
        return listOf(
            QuadCoordinateUtils.normalizedToScreen(quad.topLeft, containerSize, displaySize),
            QuadCoordinateUtils.normalizedToScreen(quad.topRight, containerSize, displaySize),
            QuadCoordinateUtils.normalizedToScreen(quad.bottomRight, containerSize, displaySize),
            QuadCoordinateUtils.normalizedToScreen(quad.bottomLeft, containerSize, displaySize)
        )
    }


    private fun clampPoint(point: Point): Point {
        return Point(
            point.x.coerceIn(0.0, 1.0),
            point.y.coerceIn(0.0, 1.0)
        )
    }

    private operator fun Point.plus(other: Point): Point {
        return Point(this.x + other.x, this.y + other.y)
    }
}
