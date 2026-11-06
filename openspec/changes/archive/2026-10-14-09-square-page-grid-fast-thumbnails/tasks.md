# Tasks

## 1. Square grid

- [x] 1.1 Make `PageCell` a square (`aspectRatio(1f)`) with the thumbnail fitted and centered, the square as placeholder, with a custom grid column rule (`PageGridCells`: as many columns of at least 150 dp as fit, never fewer than two); verify with previews (portrait, landscape, tall/wide/square pages), with a unit test of the column count and cell size for widths from 320 to 1200 dp (two columns on a phone, cells between 150 and 231 dp from 344 dp wide), and on a device that a phone in portrait shows two equal columns, a tablet size (`wm size`) shows more columns with similar cells, and the grid does not move while thumbnails load

## 2. Thumbnail cache on disk

- [x] 2.1 Add timing logs for the stages of a thumbnail (disk hit, stored image, render from source, decode, resize) and record the baseline time to see every thumbnail of a 20 to 30 page document opened from the library, on the emulator and on a phone
- [x] 2.2 Add `ThumbnailCache` (private cache directory, `<pageId>.<renderKey>.<kind><px>.jpg`, atomic writes, about 50 MB cap pruned oldest first); verify with unit tests (hit, miss, key change, another size, corrupted file, pruning)
- [x] 2.3 Use it in `ImageRepository.getThumbnail` and `getGridThumbnail` (read before computing, write after); verify with unit tests that a second request in a new repository does not compute again and that an edited page is computed again

## 3. Stored processed image

- [x] 3.1 Add the optional `storedProcessed` provider to `ImageRepository`, consulted before rendering from the original, and provide it from `LibraryRepository` for the open document (only when the render key matches); verify with unit tests (no `process()` call when a matching image is stored, rendering when it is missing or its key differs, rotation not applied twice)
- [x] 3.2 When saving a page's processed image to the library, also produce its grid and strip thumbnails; verify with a unit test that opening the saved document finds them in the cache

- [x] 3.3 Render the pages that have no stored processed image in the background, outside `open()` and the library lock, keeping each one when ready only if it is still current; verify with unit tests (open returns while rendering is held, a page edited meanwhile does not keep the old rendering) and on a device that opening a document without stored images takes under a second

## 4. Cheaper thumbnails

- [x] 4.1 Decode at a reduced size in `ImageProcessor.resizeTo` (largest of 1/2, 1/4, 1/8 that keeps both sides at least the target); verify the choice with unit tests and the result on a device
- [x] 4.2 Limit the parallel computations of thumbnails that render from the original capture to two at a time; verify with a unit test of the limit and on a device that thumbnails appear one after another as they are rendered

## 5. Verification

- [x] 5.1 Measure again on the same documents and compare with the baseline (first open of a legacy document, reopening, reopening after clearing the cache, after editing one page)
- [x] 5.2 Verify the module compiles and all unit tests pass (`./gradlew :app:assembleDebug :app:testDebugUnitTest`)
