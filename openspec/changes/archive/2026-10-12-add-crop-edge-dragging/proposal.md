## Why

In the crop screen the page outline can only be adjusted by dragging its four corners. Fine-tuning one side (e.g. the page is correctly framed except the bottom margin) requires moving two corners and re-balancing them. Letting the user drag a whole side is faster and more precise.

## What Changes

- Each of the four sides of the crop quad becomes draggable, in addition to the corners.
- Dragging a side moves it transversally: the side translates perpendicular to its own direction, so its orientation never changes. Movement along the side is ignored.
- The loupe follows the touched side's midpoint while it is dragged.
- Corner dragging, convexity check, clamping to the image and lift-wiggle rollback keep working as today. Corners keep priority over sides when a touch is near both.

## Capabilities

### New Capabilities
- `crop-quad-editing`: how the user adjusts the page outline in the crop screen (corner and side handles, constraints).

### Modified Capabilities

## Impact

- `app/.../ui/screens/crop/QuadEditingHandler.kt`: side hit-testing and side update logic.
- `CropScreen.kt`, `CropScreenState.kt`: drag start/update/loupe handling for sides.
- `QuadOverlay.kt`: side handle drawing and system-gesture exclusion for sides.
- `QuadEditingHandlerTest.kt`: new unit tests. No data model, dependency or persistence changes.
