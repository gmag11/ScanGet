# Design

## Context

Today `OcrService.runOcr(bitmap)` (Tesseract, one instance guarded by a mutex) is called from
`AndroidPdfWriter` for each page while the PDF is written. It returns `OcrTextBox(text, box,
lineHeight, lineBottom)` **per word** (words under 50% confidence are dropped), and the writer
scales those boxes from the recognized bitmap to the PDF page to place an invisible text layer.
The bitmap is the exported image (black-and-white pages are halved first), so its size depends on
the export quality. The library (changes 01–05) stores `document.json`, `cover.jpg` and
`sources/<pageId>.jpg`; processed images live only in the working directory cache and are rendered
again from the sources when missing (`ImageRepository.renderFromSource`). See proposal.md.

**Answer to the open question:** the OCR does not compute positions per character, only per word
(plus line height and line bottom). That is all the PDF text layer uses, so storing the word boxes is
enough to rebuild the PDF without recognizing again.

## Goals / Non-Goals

**Goals:**
- Recognize once, in the background, store the result, and reuse it for PDFs and search.
- Keep the processed page of every document in the library.

**Non-Goals:**
- Storing PDFs, per-character positions, or confidence values.
- Showing where in a page a search term was found, or highlighting it.
- Changing the OCR engine or languages.

## Decisions

- **Folder layout** (inside the document folder, covered by the existing `.nomedia`):
  ```
  <docId>/document.json
  <docId>/cover.jpg
  <docId>/sources/<pageId>.jpg
  <docId>/processed.json                       which file holds which page, and its renderKey
  <docId>/processed/<Document name>-page01.jpg|png   one per page, as ImageRepository renders it
  <docId>/ocr/<pageId>.json
  ```
  Sources and OCR files are matched by strict patterns (digits + extension). Processed images have
  user-readable names (the document name, "-page" and the number of the page, two digits at least,
  e.g. `Invoice March-page07.jpg`), so they cannot be recognized by pattern: `processed.json` lists
  the ones the app wrote and only those are ever replaced, renamed or deleted. A file of the user
  that already has the name wanted for a page is never overwritten: the page keeps another name
  (`<pageId>.<renderKey>.jpg`) until the name is free. Writes are temp-then-rename as always.
- **OCR file** (`PageOcrV1`): `{version, renderKey, imageWidth, imageHeight, words:[{text, left, top,
  right, bottom, lineHeight, lineBottom}]}`. Separate from `document.json` so the description stays
  small and OCR can be written later without rewriting it.
- **`renderKey`**: a short hash of what determines the processed pixels: base+manual rotation, quad
  (user quad or detected), color mode, adjustments and source size. It is the same information that
  already names adjusted processed files in `ImageRepository`; reuse that computation. OCR data and
  processed image are valid only if their key equals the key of the page now.
- **Scaling**: the writer already scales boxes by `pageWidth / imageWidth`. Stored data keeps the
  size of the image it was computed on, so the PDF code is unchanged apart from where the boxes
  come from. Recognition for storage runs on the processed image at its stored size (black and white
  halved like today), independent of export quality.
- **Background queue**: `LibraryOcrScheduler` in `data/library`: a coroutine on a low-priority
  dispatcher with a conflated queue of `(docId, pageId, renderKey)`. One page at a time (the
  engine is a singleton; the PDF path shares its mutex). Before recognizing it checks the page still
  exists with that key; after, it writes only if the key is still current. Triggered by
  `LibraryRepository` after saving a page or changing its settings, and on app start for pages of
  the opened document that lack valid data (not for the whole library).
- **Processed image**: written by `LibraryRepository` in the same save step as sources (after the
  rendering exists in the working cache; if not rendered yet it is rendered from the source).
  Replaced when the key changes (the previous file of that page is removed, never left next to the
  new one), deleted with the page. After each save the files are renamed to match the document name
  and the position of each page (rename the document, delete or reorder pages): a rename that would
  take a name still held by another page waits for it or goes through a temporary name, so that
  none is ever overwritten. Documents saved before this naming (files `<pageId>.<renderKey>.jpg`
  and no `processed.json`) are adopted and renamed the next time they are saved. Never required to
  open a document: opening keeps using the sources.
- **PDF path**: `PageToExport` gains optional stored OCR (`List<OcrTextBox>` + image size). The
  writer uses it when present and valid, otherwise runs `runOcr` as today. When the page belongs to
  a library document the result of an on-demand run is stored (through the same scheduler API) so
  the next PDF is faster. `LibraryExporter` reads processed images/OCR from the store.
- **Search**: `LibraryIndex` keeps, per document, a normalized string of its OCR text (words joined
  by spaces, `normalizeForSearch`), built lazily on a background thread and cached until the
  document changes. `filterDocuments` takes an optional text lookup; name matching is unchanged and
  results keep the usual order.
- **Setting** `ocrOnSave` (DataStore Boolean, default true) in `SettingsRepository` and the
  settings screen; with no OCR language available the scheduler does nothing.
- **Existing documents**: no migration; documents without these files behave as before and gain
  them the next time they are opened and saved (or their PDF is exported).

## Risks / Trade-offs

- [Battery and CPU on a long scan session] → one page at a time, low priority, conflated, and the
  setting turns it off.
- [Disk use: a processed JPEG per page can be several MB] → accepted; it is a cache that can be
  deleted by the user with the folder; sources remain the truth.
- [Result written for a page edited meanwhile] → the key is rechecked before writing.
- [Search index memory for large libraries] → only normalized text, loaded lazily; measure with 200
  documents on a device.
- [Words under 50% confidence are not stored] → same as today's PDFs; changing that is out of scope.
