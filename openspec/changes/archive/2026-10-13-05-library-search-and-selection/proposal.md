# Proposal

## Why

Once the library holds dozens of documents, scrolling is no longer enough to find one, and acting
on documents one by one (share three receipts, delete last year's tickets) is tedious. CamScanner
offers a search box and checkboxes on each document; FairScan needs the equivalent, in its own
style.

## What Changes

- A **search** action in the library top bar filters documents by name as the user types
  (case- and accent-insensitive).
- A long press on a document enters **selection mode** (same gesture as page selection on the
  export screen); taps then toggle documents. The top bar shows the count, **Select all** and a
  close action.
- In selection mode the bottom bar offers **Share** (one file per selected document, in the
  configured export format and quality) and **Delete** (with confirmation, showing how many
  documents will be deleted).
- Back or the close action leaves selection mode.

## Capabilities

### New Capabilities
- `library-search`: filtering the library by document name.
- `library-selection`: selecting several documents and acting on them together.

### Modified Capabilities

## Impact

- `ui/screens/library/LibraryScreen.kt`, `LibraryUiState.kt`, `LibraryViewModel.kt`: query,
  selection state, contextual top and bottom bars.
- New `domain/LibraryExport.kt`: export of a library document that is not the working document,
  reusing `ExportPreparation`.
- `MainActivity.kt`: share event for multiple documents (reuses `share(ExportResult)`).
- Strings (EN + ES, plurals for counts).
