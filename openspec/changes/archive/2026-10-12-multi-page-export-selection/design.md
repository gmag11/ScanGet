# Design

## Context

`ExportViewModel.prepareExportIfNeeded` builds the file(s) from `pagesToExport(imageRepository, quality)`, which returns all pages, and caches the result under an `ExportPreparationKey` (page keys, format, quality, OCR language). The export screen has no page list. The active page index lives in `MainViewModel` (`_currentPageIndex`) and is not changed by navigating to the export screen. `CommonPageList` already uses `longPressDraggableHandle()` for reordering and `clickable` for page clicks. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- Select pages on the export screen and export just those.
- Reuse the existing page strip rather than building a second one.

**Non-Goals:**
- Persisting the selection across screens or app restarts.
- Selection in the document or camera screens.
- Changing the external PDF call.

## Decisions

- **Selection state in `ExportViewModel`**: `selectedPageIds: Set<String>` in `ExportUiState`, with `onPageLongPress`, `onPageTap`, `selectAll`. Page ids are stable under reordering and deletion; selection is pruned against the current pages when the screen opens. Alternative: indexes (rejected, break on reorder).
- **Effective page set**: a pure function `pagesForExport(pages, selectedIds, activePageId)` returns selected pages in document order, or the active page if the selection is empty. Kept in `domain` so it is unit-testable. `pagesToExport`/`jpegsToExport` take a `pageFilter`/list of page ids.
- **Active page**: `MainActivity` passes `documentUiState.currentPageIndex` (resolved to a page id) to the export screen/ViewModel when it opens. Falls back to the first page.
- **Preparation key**: add the exported page ids to `ExportPreparationKey`, so changing the selection cancels and restarts preparation (`prepareExportIfNeeded` is re-invoked on selection change). Share and save stay disabled while generating, as today.
- **Gesture conflict**: `CommonPageListState` gains an optional `selection: PageSelection?` (selected ids, onToggle, onLongPress). When present, thumbnails use `combinedClickable` for tap / long press. Reordering remains `longPressDraggableHandle`: a long press without movement is reported as selection start, while a long press followed by a drag reorders. In selection mode the draggable handle is disabled (`enabled = false`). Risk-checked against the `reorderable` library: `onDragStarted` is only called once the finger moves, so a still long press falls through to `combinedClickable`.
- **Select all button** in the export screen next to the strip; hidden or disabled when all pages are already selected. A short label shows the count ("2 selected") in selection mode.
- **Visuals**: selected thumbnails get a primary-colour border and a check badge; with no selection the active page gets the border only.
- **File names**: `defaultFilename()` appends a page suffix (`pagesSuffix(selectedIndexes, total)`, e.g. " p2,4", ranges collapsed as "p2-4") when the effective pages are not the whole document and the name is not user-defined; the name is recomputed when the selection changes. Uniqueness at the destination is already ensured by the platform (MediaStore, SAF and `FileManager.copyToExternalDir` add a suffix when the name exists), so no new helper is needed; the page suffix just makes default names differ.
- **Share separately**: `ExportViewModel.onShareSeparatelyClicked` generates one file per selected page on demand (independent of the prepared export), named `<base> p<N>`, and emits the existing `ExportEvent.Share` with a multi-file result (`ExportResult.Pdfs` for PDF, `ExportResult.Jpeg` for JPEG). It is shown only with 2+ selected pages. Sharing only; batch saving is out of scope.
- **External call** keeps calling `generatePdf` with all pages (explicit "all pages" argument), so it is independent of UI selection.

## Risks / Trade-offs

- [Default behaviour change: no selection exports one page, not the whole document] → Requested explicitly; "Select all" is one tap, and the strip marks the active page so it is visible what will be exported.
- [Long press ambiguity between select and reorder] → A still long press selects, a dragged one reorders; verify on device.
- [Re-preparation on each selection toggle is costly for big documents] → Preparation is already cancellable; debounce toggles briefly (~300 ms) before restarting.
- [Page count/size info and OCR flags depend on selection] → Derive them from the effective page set when computing `ExportUiState`.
