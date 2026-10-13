# Tasks

## 1. Storage

- [x] 1.1 Add `PageOcrV1` (serializable) and a `renderKey` function for a page; verify with unit tests (same page → same key, any change of rotation/quad/color mode/adjustments → different key)
- [x] 1.2 Extend `LibraryStore` and `TreeLibraryStore`/fakes with processed images and OCR files (read/write/delete/list, strict name patterns, temp-then-rename, included in delete and move); verify with unit tests that foreign files in a document folder are never touched

## 2. Processed images

- [x] 2.1 Write the processed image of each page in `LibraryRepository` when saving, replace it when the key changes, delete it with the page; verify with unit tests (one image per page, replaced on edit, removed on delete, document opens when missing)

## 3. Background OCR

- [x] 3.1 Add `ocrOnSave` to `SettingsRepository` and the settings screen (EN and ES strings, on by default); verify with a unit test of the default
- [x] 3.2 Add `LibraryOcrScheduler` (single worker, conflated queue, key rechecked before and after recognition, no-op when off or without languages) and call it from `LibraryRepository`; verify with unit tests using a fake `OcrService` (stored after save, discarded when the page changed meanwhile, nothing when off)
- [x] 3.3 Discard OCR data when a page is edited or deleted; verify with unit tests

## 4. PDF from stored OCR

- [x] 4.1 Let `PageToExport`/`AndroidPdfWriter` take stored OCR boxes and skip recognition when present; verify that the text layer is generated from given boxes without calling the OCR
- [x] 4.2 Use stored data in `LibraryExporter` and the export screens, recognize only pages without valid data and store that result; verify with unit tests (fully recognized document → zero OCR calls; one missing page → one call)

## 5. Search by content

- [x] 5.1 Add per-document OCR text to `LibraryIndex` and to `filterDocuments`; verify with unit tests (match in text, accents/case, document without OCR found only by name, cache refreshed after an edit)

## 6. Verification

- [ ] 6.1 Verify on a device: scan with the setting on and off, text found by search, PDF without a long OCR wait, edit a page and see the data refreshed, delete a document and see its folder removed, a foreign file in a document folder untouched
- [x] 6.2 Verify the module compiles and all unit tests pass (`./gradlew :app:assembleDebug :app:testDebugUnitTest`)

## 7. Processed image names

- [x] 7.1 Replace the per-page maps of processed images in `LibraryStore` by a list of the files written (`processed.json`) with their render keys; a new image replaces the previous file of its page and never overwrites another page's or a foreign file; adopt files of the previous naming; verify with unit tests (replace on re-crop leaves one file, foreign file with the wanted name untouched, adoption)
- [x] 7.2 Name the processed images "<document name>-pageNN" and rename them after each save when the document name or the page positions change (delete, reorder, rename of an open or a closed document) without overwriting; verify with unit tests (swap, shift after delete, rename, foreign collision)
- [ ] 7.3 Verify on a device: re-crop a page, reorder and delete pages, rename the document, and see the folder
