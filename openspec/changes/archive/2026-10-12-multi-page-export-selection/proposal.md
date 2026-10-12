# Proposal

## Why

Export always includes every page of the document. Users who scan a batch often want to share or save only some pages (for example one page of a multi-page scan) and today must delete pages from the document to do it.

## What Changes

- The export screen shows the document's pages as a thumbnail strip.
- A long press on a thumbnail enters a selection mode and selects that page; in that mode a short press toggles a page's selection.
- The export (share, save and the generated preview/size info) includes only the selected pages.
- With no page selected, only the active page (the one last viewed in the document screen) is exported.
- A "Select all" button selects every page.
- **BREAKING**: with no selection, the export screen exports the active page only, where it used to export the whole document. Choosing "Select all" restores the previous result.
- Dragging a thumbnail to reorder keeps working outside selection mode and is disabled while selecting.
- Exports requested by another app (external PDF call) are unchanged and still contain every page.

## Capabilities

### New Capabilities
- `export-page-selection`: choosing which pages of the document are included in an export, via the export screen's selection mode.

### Modified Capabilities

## Impact

- `ui/screens/export/ExportScreen.kt`, `ExportUiState.kt`, `ExportViewModel.kt`: page strip, selection state, preparation keyed on the selection.
- `ui/components/PageList.kt`: optional selection mode (long press, tap toggle, selected visuals).
- `domain/ExportPreparation.kt`: `pagesToExport` / `jpegsToExport` take the set of pages to include.
- `MainActivity.kt`: passes the active page index to the export screen.
- New strings (select all, selection count) in `values/` and translations.
- Unit tests for page filtering and selection state.
