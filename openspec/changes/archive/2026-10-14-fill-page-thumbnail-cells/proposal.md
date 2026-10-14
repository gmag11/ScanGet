# Proposal

## Why

In the document overview each page is shown whole inside a square cell, so a portrait or landscape
page leaves bands of empty space on its sides or above and below, and the pages look smaller than
they could. Using the whole cell makes the page content bigger and easier to recognize.

## What Changes

- A thumbnail no longer is fitted whole inside its square cell. It is scaled to cover the cell on
  the side that matters and aligned so that what is shown is the start of the page:
  - **Portrait page** (taller than wide): the width of the page fills the width of the cell and the
    page is aligned to the top; the bottom of a page that is taller than the cell is cut off.
  - **Landscape page** (wider than tall): the height of the page fills the height of the cell and the
    page is aligned to the left; the right side of a page that is wider than the cell is cut off.
  - A square page fills the cell exactly.
- The cell stays square and keeps its size, the number badge and the selection mark; only what is
  drawn inside changes. The page editor and the export are not affected (the page is never cropped
  there, only its thumbnail is clipped).
- **BREAKING** (visual only): the grid no longer shows each page whole.

## Capabilities

### New Capabilities

### Modified Capabilities
- `document-overview`: the "Page grid" requirement changes how the thumbnail is placed in its cell
  (fills width and aligned top for portrait, fills height and aligned left for landscape, instead of
  fitted whole and centered).

## Impact

- UI: `PageCell` in `DocumentOverviewScreen` (content scale, alignment and clipping, chosen from the
  proportions of the thumbnail).
- Thumbnail size: the grid thumbnails (`GRID_THUMBNAIL_SIZE_DP`) are made with their longest side
  at that size, so filling the width of the biggest cells would enlarge them a little; the size may
  need to grow (see design).
- Previews and UI tests of the overview. No data, format or dependency changes.
