# Proposal

## Why

FairScan keeps a single working document. Starting a new scan deletes the previous one, and once a
document is exported only the PDF/JPEG remains: the original captures, the crop outlines and the
filter settings are lost, so a page cannot be re-cropped or re-filtered later and the export cannot
be generated again. This change is the foundation of a document library (a file manager for scans,
in the spirit of CamScanner's home screen) that keeps every scanned document with everything needed
to reproduce its export at any time.

## What Changes

- Every scanned document is saved automatically to a **library** as soon as it has a page, and kept
  up to date after every edit (add, delete, reorder, rotate, crop, filter, adjustments).
- A library document stores the **raw captures** (the source JPEGs) and the **editing data** of each
  page (crop outline, rotation, filter, adjustments, capture metadata), plus a document name and
  creation/modification dates. Processed images are not stored: they are re-rendered from the
  sources, so an export generated from a reopened document is the same as before.
- Documents get a default name (same style as the default export file name) that can be changed.
- Opening a library document makes it the working document; the document that was open stays in
  the library untouched.
- Starting a new document no longer deletes the previous one: it stays in the library.
- The current working document of an existing installation is migrated into the library on first
  launch.
- Scans requested by another app (scan-to-PDF intent) stay temporary and are not saved to the
  library.
- The library never lives in the app's private storage. This change defines how documents are
  stored behind a storage abstraction; the user-visible folder (default `FairScan` at the root of
  the user's storage) is change 02, and the screens to browse the library are changes 03 and 04.
  Changes 01 and 02 must ship in the same release.

## Capabilities

### New Capabilities
- `document-library`: persisting scanned documents (sources + editing data) and reopening them with
  the same result.

### Modified Capabilities

## Impact

- `data/DocumentMetadata.kt`: metadata v3 with document id, name and dates.
- `data/ImageRepository.kt`: reads v3, renders missing processed images on demand from the source,
  notifies changes for autosave.
- New `data/library/` package: `LibraryStore` abstraction, internal-storage implementation,
  `LibraryRepository` (list, open, save, rename, delete), migration of the legacy working document.
- `MainViewModel.kt`: `startNewDocument` closes instead of clearing; `openDocument(id)`.
- `SessionViewModel.kt` / `FairScanApp.kt`: wiring; external launch mode keeps a temporary session.
- Export file name defaults to the document name when it was renamed.
- Unit tests for metadata migration, save/reopen round trip and autosave.
