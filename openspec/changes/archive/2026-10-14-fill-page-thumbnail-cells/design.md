# Design

## Context

`PageCell` draws the thumbnail with `ContentScale.Fit` centered in a `Box` with `aspectRatio(1f)`;
the cell is a square whatever the page, and the bitmap is the "grid thumbnail", which is scaled so
that its longest side is `GRID_THUMBNAIL_SIZE_DP` (240 dp times the screen density) and kept in a
disk cache (`ThumbnailCache`). Cells are 150–230 dp wide. See proposal.md for the motivation.

## Goals / Non-Goals

**Goals:**
- Fill the whole square with the page as the specs say, with no empty bands and no layout change.

**Non-Goals:**
- Changing the grid columns, the cell size, the page editor, the strip of thumbnails or the export.
- Letting the user choose the display mode (no setting).

## Decisions

- **Choose the scale from the bitmap, in the cell.** For a loaded bitmap, `portrait = height > width`:
  portrait → `ContentScale.FillWidth` with `Alignment.TopStart`; otherwise (landscape, and square, for
  which both give the same result) → `ContentScale.FillHeight` with `Alignment.CenterStart`. The
  `Image` fills the cell and the `Box` clips (`Modifier.clipToBounds()`), so the overflow is cut.
  The proportions come from the bitmap itself, which already has rotation and crop applied, so no
  new data is needed. Alternative rejected: `ContentScale.Crop`, which centers and would hide the
  top of the page, the most recognizable part.
- **Alignment of the portrait page**: `TopStart`/`TopCenter` are the same as the width fills the cell;
  `TopStart` is used. For landscape `CenterStart`, the height fills the cell so the vertical
  alignment is irrelevant.
- **Thumbnail resolution.** With the width filled, a portrait thumbnail of 240 dp on its longest
  side is about 170 dp wide for A4, but a cell can be 230 dp wide: it would be enlarged by about a
  third and look soft on tablets. Raise `GRID_THUMBNAIL_SIZE_DP` so that the width of a portrait page
  covers the biggest cell: 230 dp / 0.7 ≈ 330, so 320–340 dp. The cache must not serve old, smaller
  thumbnails: the size is part of the cache construction (`ThumbnailCache(dir, thumb, grid)`) and the
  existing entries must be discarded when it changes; this is checked in the tasks. Memory: the
  in-memory cache holds encoded JPEGs (200 entries), so the effect is small.
- **No change while loading**: the cell keeps `aspectRatio(1f)` and the placeholder background, so
  the grid does not move when the bitmap arrives (existing requirement).
- **Overlays unchanged**: number badge and selection mark are children of the same `Box`, so they stay
  at their corners above the clipped image.

## Risks / Trade-offs

- [A tall page loses its bottom part and a wide page its right part in the grid] → intended; the
  full page is one tap away.
- [Soft thumbnails if the thumbnail size is not raised] → raise the grid thumbnail size and drop
  the old disk cache entries.
- [Larger thumbnails are slower to make and use more disk] → measure the time of the grid on a device;
  the thumbnails are made once per rendering and kept.
