## 1. Editing logic

- [x] 1.1 In `QuadEditingHandler`, add side hit-testing (handles 4–7 at side midpoints, corners prioritized in `findTouchedCorner`/candidates). Verify with unit tests: midpoint touch selects the side, near-corner touch selects the corner, far touch returns -1.
- [x] 1.2 Add `updateQuadSide(quad, sideIndex, delta, displaySize)` translating both end corners by the delta projected on the side normal (pixel space), rejecting steps that leave [0,1] or make the quad concave. Verify with tests: pure-along movement leaves quad unchanged, across movement keeps side direction, border and concavity rejections.
- [x] 1.3 Route handle indices 0–3 / 4–7 to the right update function (single entry point used by the UI). Verify with a test over all 8 indices.

## 2. UI

- [x] 2.1 Wire side drags in `DragQuadOverlay` (`CropScreen.kt`) and `CropScreenState` (start drag, update, touch-down index, `isDragging`). Verify manually on device/emulator: dragging each side moves it across only.
- [x] 2.2 Draw side handles in `QuadOverlay` and add system-gesture exclusion rects for them. Verify visually and that dragging near screen edges does not trigger back gesture.
- [x] 2.3 Make the loupe focus on the side midpoint in `DragMagnifyingGlass`. Verify manually; add/adjust `MagnifyingGlassTest` if focus logic is extracted.

## 3. Verification

- [x] 3.1 Run `./gradlew :app:testDebugUnitTest` and confirm existing corner tests still pass.
- [x] 3.2 Manual pass: corners still work, corner wins near corners, lift-wiggle rollback works for sides.
