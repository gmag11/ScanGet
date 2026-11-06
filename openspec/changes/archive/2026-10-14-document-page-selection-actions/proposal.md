# Proposal

## Why

Pages can only be reorganised inside one document. To split a scan, merge two documents or discard
several bad pages the user has to export, re-import or delete pages one by one from the page editor.
The document overview already is the place where pages are arranged, and the library already has a
selection mode for documents; pages deserve the same.

## What Changes

- Long press on a page in the document overview enters a page selection mode (the drag to reorder
  stays available when no selection is active). A tap toggles pages, the count is shown, and the
  mode ends on back, on closing it, or when nothing is selected.
- In selection mode the top bar offers a three-dot menu with **Move**, **Copy** and **Delete**.
- **Delete** asks for confirmation stating the number of pages, then removes them from the document.
- **Move** and **Copy** open a dialog listing the other documents of the library plus a "New
  document" option. The selected pages are appended, in their document order, to the chosen
  document (or to a new one), with their crop, rotation, filter, adjustments and OCR data. Copy
  leaves the originals; Move removes them from the current document.
- Moving or deleting every page of the document removes the (now empty) document and returns to the
  library.

## Capabilities

### New Capabilities
- `document-page-selection`: selecting pages in the document overview and deleting, copying or
  moving them, including the destination picker and the creation of a new destination document.

### Modified Capabilities
- `document-overview`: the long press on a page now enters selection mode instead of starting a
  drag at once; reordering is still done with long press and drag (the drag starts from the long
  press when no selection is active), and taps toggle instead of opening while selecting.

## Impact

- UI: `DocumentOverviewScreen` (selection state, contextual top bar, menu, dialogs), a new
  destination-picker dialog, new strings (all locales that exist).
- `MainViewModel`/`MainActivity`: new callbacks for delete, copy and move of pages.
- Data: `LibraryRepository` gains an operation that appends pages (sources, processed images, OCR,
  metadata with new page ids) to another stored document or to a new one; `ImageRepository` gains
  a multi-page delete. Cover and index of the target document are refreshed.
- No new dependencies and no change to the on-disk document format.
