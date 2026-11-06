# Proposal

## Why

Text recognition (OCR) only runs while a PDF is generated, so every export waits for it, its result
is thrown away afterwards, and the library cannot be searched by what the documents say. The
library also keeps only the raw captures: the processed pages (cropped, filtered) are rendered again
every time they are needed. Recognizing the text once, in the background, when a scan is saved fixes
all three: faster PDFs, no repeated work, and search by content.

## What Changes

- **OCR in the background**: when a page is saved to the library, its text is recognized without
  blocking the user, and the result is stored with the document.
- **What is stored**: the OCR produces **words** (not single characters), each with its text, its
  bounding box in the image, and the height and bottom of its line. These are exactly the inputs the
  PDF text layer needs, so they are stored as they are, together with the size of the image they
  refer to and the settings of the page they were computed from. A PDF can then be rebuilt **without
  running the OCR again**.
- **Setting** "Recognize text when saving a scan" (on by default, as PDFs are already searchable).
  When off, nothing runs in the background and a PDF export recognizes the text on demand as today.
- **Processed image in the library**: the processed (cropped, rotated, filtered, adjusted) image of
  each page is saved in the document folder next to the raw capture (one image per page of the
  document), and replaced when the page is edited.
- **Stale results**: the stored OCR is used only while it matches the page as it is now; editing the
  page (crop, rotation, filter, adjustments) discards it and, if the setting is on, recognizes it
  again.
- **Search by content**: the library search also matches the recognized text of the documents that
  have it. Documents without OCR data are still found by name.
- PDFs are still generated on request, never stored.

## Capabilities

### New Capabilities
- `page-ocr`: recognition of the text of a page, its storage, its staleness rules, the setting and
  its use to build PDFs.

### Modified Capabilities
- `document-library`: the library also keeps the processed image and the OCR data of each page; they
  are a cache that is never required to reopen a document.
- `library-folder`: the new files are hidden from galleries and are the only ones the app may
  delete inside a document folder.
- `library-search`: the search also matches the recognized text.

## Impact

- `data/library/LibraryStore.kt`, `TreeLibraryStore.kt`: processed images and OCR files per page
  (`processed/<pageId>.<ext>`, `ocr/<pageId>.json`); moving and deleting documents include them.
- `data/library/LibraryRepository.kt`: after saving a page, writes the processed image and
  schedules the OCR; removes both for deleted pages.
- `domain/OcrService.kt`: runs from a background queue (one page at a time, as the engine is a
  singleton) and can report the page size it recognized.
- `platform/AndroidPdfWriter.kt`, `LibraryExporter`, `ExportViewModel`: build the text layer from
  stored OCR when valid, run the OCR only for pages without it.
- `ui/screens/settings`: new setting. `ui/screens/library/LibraryViewModel.kt` and `LibraryIndex`:
  searchable text per document.
- Disk use grows (processed image per page plus a small JSON).
- Strings (EN + ES).
