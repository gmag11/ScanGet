## Context

`QuadEditingHandler` hit-tests the four corners (radius 90 px) and moves one corner per drag step, rejecting non-convex results. `CropScreenState.draggedCornerIndex` and `touchDownCornerIndex` carry the active handle; `DragQuadOverlay` in `CropScreen.kt` wires touch-down (loupe) and drag gestures. `QuadOverlay` draws corner circles and excludes corner areas from system gestures.

## Goals / Non-Goals

**Goals:**
- Drag any side perpendicular to its direction, reusing the existing gesture pipeline.
- No change to corner behavior.

**Non-Goals:**
- Moving the whole quad, or rotating/scaling it.
- Keeping adjacent sides' directions fixed (see Decisions).

## Decisions

- **Unified handle index.** Handles 0–3 stay corners (TL, TR, BR, BL); 4–7 are sides (top, right, bottom, left, i.e. between corners i and i+1). This lets `draggedCornerIndex` / `touchDownCornerIndex` and the loupe code carry sides with minimal change. Alternative: a separate side-index state — rejected as duplicated plumbing.
- **Side move = translate both end corners by the same vector.** The vector is the drag delta projected onto the side's unit normal. The side's direction is preserved exactly; adjacent sides pivot on their opposite corners and so change direction. Alternative: slide the end corners along the adjacent sides' lines (adjacent sides keep direction, side stays parallel) — rejected as it makes corners move non-uniformly, fails on near-parallel neighbours, and the user's request only constrains the dragged side's direction. The normal is computed in screen/pixel space (aspect ratio matters), then converted to normalized coordinates.
- **Validity.** If translating would push either end outside [0,1], the step is rejected as a whole (not clamped per-corner, which would rotate the side). Convexity is checked as for corners; a failing step is ignored.
- **Hit-testing.** A side is hit when the touch is within the touch radius of its midpoint handle; corners are tested first and win. Using the midpoint handle (rather than the whole segment) keeps corner/side targets distinct and system-gesture exclusion rects small (Android limits them) — one extra rect per side.
- **Loupe.** Focus position for a side index is the side midpoint.
- **Drawing.** A smaller/different-shape handle (e.g. rounded bar aligned to the side) at each midpoint.

## Risks / Trade-offs

- [Adjacent sides rotate when a side moves] → expected; user can fix with corners.
- [Midpoint handles overlap corners on very short sides] → corner priority; side still reachable by midpoint unless the side is shorter than ~2 touch radii.
- [More system-gesture exclusion rects] → 8 small rects, within Android's limit.
