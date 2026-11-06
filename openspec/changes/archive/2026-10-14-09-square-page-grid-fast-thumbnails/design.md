# Design

## Context

`DocumentOverviewScreen` shows a `LazyVerticalGrid` with `GridCells.Adaptive(150.dp)`; each
`PageCell` is a `Card` whose image has `ContentScale.FillWidth`, so its height follows the page; the
placeholder has a 3:4 shape. A cell asks `MainViewModel.loadThumbnail(key)` →
`ImageRepository.getGridThumbnail(key)`.

`getGridThumbnail` goes through `getOrCompute(gridThumbnailCache, …)` (in-memory LRU of 200) →
`computeResized` → `image(key)` (in-memory LRU of 50) → `computeProcessedImage`: if the page's
processed file is not in the private working directory, `renderFromSource` runs
`transformations.process(...)` on the original capture (perspective crop and filter with OpenCV on
the full photo), writes the processed file, reads it back, rotates it if needed; then
`resizeToGridThumbnail` decodes that image in full (`decodeJpegOrPng`, `IMREAD_COLOR`), shrinks it
and encodes it again.

When a document is opened from the library, `ImageRepository.load()` calls `clearFiles()` (empties
the private sources and processed directories and the three caches) and copies each original
capture from the library into the private `sources/` directory. So the originals are private and
local by the time the overview is shown: the location of the originals is not what is slow (it
adds one read per page, once, at open time). What is slow is that **every processed page is
rendered again** on each open. Since change 06 the library also keeps `processed/<name>-pageNN.*`
for each page (with its render key in `processed.json`), but nothing reads it back. Grid thumbnails
are only in memory, so they are lost with the process. `LibraryRepository.open()` also calls
`writeProcessedImages`, which only renders pages whose stored processed image is missing or stale
(once per legacy document, then it is a no-op).

This was first read from the code, then measured (debug build, x86_64 emulator, a document of 8
photos of 4000x3000 px opened from the library while another document was the open one):
`open()` takes about 0.6 s (28 ms to close the previous one, 0.49 s to copy the 8 originals into
the private directory, 0.1 s for the stored processed images), and then the 8 pages are rendered
from their originals one after another at about 330 to 510 ms each (about 2.7 s), with the
decode, resize and encode of each thumbnail costing under 10 ms for the small processed pages (the
processed pages are 136 KB: the crop is small). Seeing all the thumbnails therefore takes about
3 s on the emulator, and the render from the original is about 90% of it; on a phone, with a slower
CPU and larger processed pages, it is longer. Reopening the document that is already the open one
takes 3 to 50 ms per thumbnail, because its processed files are still in the private directory.

## Goals / Non-Goals

**Goals:**
- Square cells with the thumbnail fitted inside; two columns on a normal phone in portrait.
- Thumbnails that appear fast when a document is opened again, and cheaper when they must be made.

**Non-Goals:**
- Changing how pages are rendered for editing or export, the library layout, or the `document.json`.
- Thumbnails of the library list (covers already have their own file) and a cache shared between
  devices.
- Reordering or paging the grid differently.

## Decisions

- **Square cell.** `PageCell`: a `Card` with `aspectRatio(1f)`; inside, a `Box` with the page
  thumbnail `ContentScale.Fit`, `Alignment.Center`, over `surfaceVariant`; the page number badge
  stays on top. The placeholder is the empty square, so nothing moves. Columns stay
  a custom `GridCells` (`PageGridCells`, with the arithmetic in the pure function
  `pageGridLayout(availableWidthDp)`): as many columns of at least 150 dp as fit, never fewer than
  two (plain `Adaptive(150.dp)` would give one column under 344 dp), and the leftover width shared
  between them, so a cell never gets bigger than about 150 + (150 + gap) / columns.
  With the 16 dp padding and 12 dp gap: two columns for widths below about 470 dp (a 360 to 430 dp
  phone: cells of 158 to 190 dp, at most about 230 dp just under the limit), three from 474 dp, and
  four or five on a tablet or in landscape (cells of about 155 to 200 dp). So bigger screens get more
  columns, not bigger cells, and the existing grid thumbnail size (`GRID_THUMBNAIL_SIZE_DP = 240`)
  still covers the largest cell without upscaling. A test of the column and cell-size arithmetic
  (pure function over the screen width) guards the 150 to 230 dp range.
- **Thumbnail disk cache** (`ThumbnailCache`): a flat directory in the app's private cache
  (`cacheDir/page-thumbnails`), one JPEG per `<pageId>.<renderKey>.<kind><px>.jpg` where `renderKey`
  is `PageViewKey.renderKey()` (change 06: rotation, crop version, filter, adjustments), `kind` is
  `g` (grid) or `s` (strip) and `px` the maximum side in pixels (so a different density or size does
  not read a wrong file). Writes go to a temp file and are renamed. When the directory goes over
  about 50 MB the oldest files (by modification time) are removed at the next write. Being in
  `cacheDir`, the system may clear it: it is only a cache.
- **Lookup order** in `getGridThumbnail`/`getThumbnail`, after the in-memory LRU: private disk cache
  → (compute) processed image → resize → write to the disk cache. "Processed image" is the working
  file if present, else the one stored by the library, else a render from the original capture.
- **Stored processed image as a source.** `ImageRepository` gets an optional provider
  `storedProcessed: suspend (PageViewKey) -> EncodedImage?`, set by `LibraryRepository` for the open
  document. In `computeProcessedImage`, when the working file for the key is missing and the
  provider returns an image, that image is the result for that exact key (it is stored already
  rotated, as `image(key)` returns it, so no rotation is applied again) and nothing is rendered. The
  provider reads `LibraryStore.processedPages(id)[pageId]` and returns the file only if its
  `renderKey` equals the key's render key. The working file is **not** written from it (the working
  files are the unrotated base files; mixing them would break `deleteStaleAdjustedFiles` and the
  rotation logic); the in-memory LRU and the thumbnail disk cache keep the result.
- **Write on save.** When `LibraryRepository.writeProcessedImages` has a page's processed image in
  hand it also asks `getGridThumbnail(key)`/`getThumbnail(key)`, which find the image in the memory
  cache and write the files: opening that document later hits the disk cache directly.
- **Reduced decode.** `ImageProcessor.resizeTo` decodes with OpenCV `IMREAD_REDUCED_COLOR_2/4/8`
  (JPEG decodes at 1/2, 1/4 or 1/8 straight from the DCT; PNG falls back to a full decode) choosing
  the largest reduction that keeps both sides at least as large as the target size, then resizes as
  today. The choice is a pure function with unit tests.
- **Bounded parallelism.** The computations that render from the original capture and the
  thumbnail resizes use `Dispatchers.IO.limitedParallelism(2)` instead of one coroutine per page on
  the whole IO pool, so a 40-page document does not decode 40 full photos at once (memory and
  contention) and the first visible cells finish first. Disk-cache hits do not wait for that
  limit.
- **Missing stored images are rendered in the background.** Measuring a document saved before
  change 06 (no stored processed images) showed that `LibraryRepository.open()` itself took 11.4 s:
  `writeProcessedImages` rendered every missing page one by one, with the library locked, so the
  library list also waited behind it (`documents()` flushes first). Now `writeProcessedImages` keeps
  at once only the images that are at hand (in memory or in the working directory, new pages) and
  hands the pages that need rendering from the original capture to a background job: it renders them
  one after another outside the library lock (sharing the work with the screens that ask for the
  same page, which is deduplicated by the repository), and keeps each when it is ready if the
  document, the library and the rendering of the page are still the same (otherwise the next save
  keeps the current one). The OCR queue is told again once the images exist.
- **Measure.** A `Trace`/log with the time of each stage (disk hit, stored image, render, decode,
  resize) is added while working and kept at debug level, to compare before and after on a document
  of 20 to 30 pages.

## Risks / Trade-offs

- [A stale stored processed image served after an edit] → keyed by render key on both sides; a key
  that does not match is ignored and the page is rendered as before.
- [Stored images are read from the library location (slow on some storage)] → one read per page, a
  few MB, only when the thumbnail is not in the disk cache; much cheaper than rendering.
- [Disk cache growth] → size cap with oldest-first pruning, and it can be wiped without loss.
- [Legacy documents without stored processed images] → the first open still renders (once);
  `writeProcessedImages` then stores them and the thumbnails are cached.
- [Reduced decode changes thumbnail pixels slightly] → only below the displayed size (the reduction
  never goes under the target), so nothing is upscaled.

## Results (debug build, x86_64 emulator, document of 8 photos of 4000x3000 px)

| Case | Opening the document | Until all 8 grid thumbnails are shown |
|---|---|---|
| Before: processed images stored, thumbnails rendered from the originals | 0.6 s | about 3.0 s after opening (8 renders of 330 to 510 ms) |
| Stored images used, thumbnail cache empty | 0.6 s | 0.7 s |
| Thumbnails already in the disk cache | 0.6 s | 0.4 s |
| Disk cache emptied, library images present | 0.7 s | 0.6 s |
| Document saved before change 06 (nothing stored), first open: before | 11.4 s (all pages rendered inside open) | 0.2 s after that |
| The same, after: rendered in the background | 0.6 s | thumbnails appear one every 0.4 s, all in 3.6 s; stored for next time |

On a phone the renders from the original take longer (slower CPU, same 2-at-a-time limit), so the
benefit of not rendering at all is larger there.
