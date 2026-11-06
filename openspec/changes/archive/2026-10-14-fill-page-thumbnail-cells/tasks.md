# Tasks

## 1. Thumbnail placement

- [x] 1.1 In `PageCell` choose the content scale and alignment from the bitmap (portrait: fill width, top; landscape and square: fill height, left), clip to the cell, and keep the number badge and the selection mark; verify with `./gradlew :app:assembleDebug`
- [x] 1.2 Update the overview previews with a portrait, a landscape and a square page, and verify they render without empty bands in Android Studio or on a device
- [x] 1.3 Extract the choice of scale and alignment into a small function and add a unit test for portrait, landscape and square proportions; verify with `./gradlew :app:testDebugUnitTest`

## 2. Thumbnail resolution

- [x] 2.1 Raise `GRID_THUMBNAIL_SIZE_DP` so that a portrait page fills the widest cell (about 230 dp) without being enlarged, make sure the thumbnails kept on disk with the old size are not used (check how `ThumbnailCache` keys them, change it if needed), and verify with a test that a cache created with another size does not return old entries
- [x] 2.2 Check the time and the look of the grid on a device with a document of 20+ pages, and verify that it opens as fast as before and the thumbnails are sharp on a tablet-sized cell

## 3. UI tests

- [x] 3.1 Add an instrumented test showing portrait, landscape and square thumbnails in cells, checking that the cells are square and the same size (`./gradlew :app:connectedDebugAndroidTest`) and that the existing selection tests still pass
