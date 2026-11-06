# Proposal

## Why

In the document overview each page is a card whose height depends on the page (tall for a portrait
page, short for a landscape one), so the grid is ragged and jumps when thumbnails arrive. A square
cell with the page fitted inside it is calmer, and a phone in portrait shows two columns.

The thumbnails also take long to appear when a document is opened from the library. Reading the
code, the cause is not where the files are kept (the original captures are copied to the app's
private storage when the document is opened) but **how each thumbnail is made**: opening a document
empties the private cache of processed pages, so every thumbnail first renders its page again from
the original photo (perspective crop and filter with OpenCV on a full-size image, seconds per
page), then decodes the whole processed image and shrinks it. Nothing of this is kept between
sessions (grid thumbnails exist only in memory), and the processed images that the library now
stores (change 06) are not used. Page thumbnails of a big document therefore pay the full cost every
time the document is opened.

## What Changes

- **Square cells.** Each page of the overview is shown in a square cell; its thumbnail is scaled to
  fit inside the square (whole page visible, centered, page number on top). The grid keeps adaptive
  columns with a minimum cell size that gives **two columns on a normal phone in portrait**
  (width under about 470 dp). On wider screens (tablets, landscape) the cells do **not** get much
  bigger: more columns fit instead, and cells only grow a little (about 150 to 230 dp at any
  width). The placeholder is the square itself, so the
  layout does not move when a thumbnail arrives.
- **Thumbnails kept on disk.** Thumbnails of the grid and of the page strip are kept in the app's
  private cache, per page and rendering (page, rotation, crop, filter, adjustments, size), and read
  from there before anything else. They are written when the page is saved to the library and the
  first time they are made.
- **Stored processed image used.** When a page has to be shown and its processed file is not in the
  working cache, the processed image that the library holds for the same rendering is used before
  rendering the page again from its original photo.
- **Cheaper thumbnails.** Making a thumbnail decodes the processed image at a reduced size instead of
  in full, and the thumbnails that have to be rendered from original photos are limited to a few in
  parallel instead of all at once.
- Nothing changes in how documents are stored, rendered for editing or exported.

**Assumptions** (cheap to revert): cell size from the existing adaptive grid with a 150 dp minimum
(two columns up to about 470 dp of width); the disk cache is capped at about 50 MB and pruned by age.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `document-overview`: the page grid uses square cells with the thumbnail fitted inside; thumbnails
  of pages that were already rendered are reused instead of being rendered again.

## Impact

- `ui/screens/overview/DocumentOverviewScreen.kt` (`PageCell`, `PageGrid`).
- `data/ImageRepository.kt` (thumbnail lookup order, stored processed image provider, concurrency),
  new `data/ThumbnailCache.kt`, `data/library/LibraryRepository.kt` (provides the stored processed
  image, writes thumbnails when saving), `platform/ImageProcessor.kt` (reduced-size decode).
- Disk: up to about 50 MB of private cache; cleared by the system or the user without loss.
- No change to the library folder, `document.json` or the export.
