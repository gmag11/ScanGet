# Tasks

## 1. Metadata v3

- [x] 1.1 Add `DocumentMetadataV3` (v2 + `id`, `name`, `createdAt`, `updatedAt`) and make `loadMetadata` read v1/v2/v3, generating id/name/dates for older versions; verify with unit tests for each version
- [x] 1.2 Write v3 from `ImageRepository.saveMetadata` and keep the document fields across page operations; verify existing `ImageRepository` tests still pass (`./gradlew :app:testDebugUnitTest`)

## 2. Processed images as a cache

- [x] 2.1 Make `loadPages` keep pages whose source exists even when the processed file is missing, and render the processed file from the source on first `image()`/`getThumbnail()`; verify with a unit test that deleting `scanned_pages/*.jpg` still yields the same pages and renderings
- [x] 2.2 Emit `changes` from `ImageRepository` after each metadata save; verify with a unit test that add, delete, move, rotate, quad and rendering changes all emit

## 3. Library store and repository

- [x] 3.1 Add the `LibraryStore` interface and `FileLibraryStore` (any `File` dir; tests and debug builds only, entry layout from design.md, `.nomedia` per entry, atomic JSON write, entry recognition rule, delete limited to own files); verify with unit tests on a temp dir, including foreign files and directories in the root and inside an entry that are left untouched
- [x] 3.2 Add `LibraryRepository` with `documents()` (id, name, dates, page count, cover), `open(id)`, `rename(id, name)`, `delete(id)` and debounced autosave from `changes` (create on first page, copy new sources, drop deleted sources, regenerate cover); verify a save/reopen round trip gives identical `ScanPage`s
- [x] 3.3 Flush pending autosave on process `ON_STOP`, on `startNewDocument` and before `open`; verify with a unit test that no edit is lost when flushing right after a change
- [x] 3.4 Remove a document with zero pages from the library when it is closed; verify with a unit test

## 4. App wiring

- [x] 4.1 Wire `LibraryRepository` in `FairScanApp`/`SessionViewModel` for `NORMAL` mode only; verify an external scan-to-PDF leaves the library unchanged
- [x] 4.2 Change `MainViewModel.startNewDocument` to close (flush + clear working copy) instead of deleting, and add `openDocument(id)`; verify with a unit test that the previous document stays in the library
- [x] 4.3 Migrate a legacy working document (no id) into the library once a library store is available (the debug directory here, the chosen folder after 02); verify with a unit test from a v2 working directory, and on a device by installing the previous build, scanning 3 pages and updating
- [x] 4.4 Default the export file name to the document name when it was renamed; verify with a unit test of the default-name rule (renamed, not renamed, partial export suffix)

## 5. Verification

- [x] 5.1 Verify on a device: scan, edit crop and filter, kill the app, reopen, and export; the result matches the export before the restart
- [x] 5.2 Verify the module compiles and all unit tests pass (`./gradlew :app:assembleDebug :app:testDebugUnitTest`)
