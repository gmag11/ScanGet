# Tasks

## 1. Domain: choosing the pages to export

- [x] 1.1 Add `pagesForExport(pages, selectedIds, activePageId)` in `domain/ExportPreparation.kt` (selected in document order; active page if empty; first page if the active id is unknown) and verify with a unit test covering selection, empty selection, reordered and deleted pages
- [x] 1.2 Make `pagesToExport` and `jpegsToExport` accept the list of pages to include, keep the external PDF call on all pages, and verify existing tests still pass (`./gradlew :app:testDebugUnitTest`)

## 2. Export state and preparation

- [x] 2.1 Add `selectedPageIds` to `ExportUiState` and `onPageLongPress`, `onPageTap`, `selectAll` to `ExportViewModel`, pruning stale ids; verify with a unit test that selection mode ends when the last page is unselected
- [x] 2.2 Pass the active page id from `MainActivity` into the export flow and include the exported page ids in `ExportPreparationKey`; verify with a unit test that changing the selection triggers a new preparation and the page count reflects it
- [x] 2.3 Restart preparation (debounced) when the selection changes; verify that share/save use the latest selection

## 3. Distinct file names

- [x] 3.1 Add `pagesSuffix` and a default-name rule that includes page numbers for partial exports, recomputed on selection change; verify with unit tests (single page, non-contiguous, range, whole document gets no suffix)
- [x] 3.2 Verify on a device that saving two exports with the same name never overwrites (MediaStore, SAF and the Android 8-9 file path already add a suffix; no new code expected)

## 4. Page strip UI

- [x] 4.1 Add optional selection support to `CommonPageList` (tap/long press, disabled drag-reorder in selection mode, selected border and check badge); verify reorder still works from the document screen and a still long press does not reorder
- [x] 4.2 Show the strip, "Select all" button and selected count on `ExportScreen` in portrait and landscape; add strings and Spanish translation, and verify on a device/emulator the scenarios in `specs/export-page-selection/spec.md`
- [x] 4.3 Update the Compose previews of `ExportScreen` and verify the module compiles (`./gradlew :app:assembleDebug`)

## 5. Share each page separately

- [x] 5.1 Add `onShareSeparatelyClicked` and `ExportResult.Pdfs` generating one file per selected page, and the "Share each page separately" button shown with 2+ selected pages (English and Spanish strings); verify it compiles and unit tests pass
- [x] 5.2 Verify on a device that the share sheet receives one file per selected page, with different names, for both PDF and JPEG formats

