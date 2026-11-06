# Proposal

## Why

The page editor shows one large page and a horizontal strip of small thumbnails. That is perfect
for editing, but poor for getting an overview of a long document (30 receipts), finding a page, or
reordering many pages. CamScanner opens a document on a grid of its processed pages, and tapping a
page edits it. FairScan should have the same two-level structure: overview first, editing second.

## What Changes

- New **document overview** screen between the library and the page editor: a two-column grid
  (three or more in landscape / wide screens) of the processed pages, with the page number on each
  thumbnail.
- The top bar shows the **document name**; tapping it renames the document.
- Tapping a page opens the **page editor** (the current document screen) on that page. Back from
  the editor returns to the overview.
- A long press and drag on a thumbnail **reorders** pages in the grid.
- The bottom bar keeps FairScan's pattern: outlined **Add page** (camera for this document) and
  filled **Export**.
- The overflow menu adds **Delete document**, next to Settings and About.
- **BREAKING** navigation (relative to change 03): opening a document from the library, back from
  the camera with pages, back from export and back from the page editor all go to the overview.

## Capabilities

### New Capabilities
- `document-overview`: the grid view of a document's pages and its actions.

### Modified Capabilities
- `library-screen`: opening a document and the navigation rules now go through the overview.

## Impact

- New `ui/screens/overview/` (`DocumentOverviewScreen`, state from `MainViewModel`).
- `ui/Navigation.kt`: `Screen.Main.Overview`; back rules updated.
- `ui/components/PageList.kt`: reuse of thumbnail rendering and reorderable support in a
  `LazyVerticalGrid`.
- `MainActivity.kt`, `DocumentScreen.kt` (back target), `ExportScreen.kt` (back target).
- Strings (EN + ES).
