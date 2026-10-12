# Design

## Context

After changes 03–04 the library is a `LazyColumn` of `LibraryItem`s with a row menu, a top bar
(title + overflow) and an Import/Scan bottom bar. The export screen already implements a
long-press selection mode for pages (`export-page-selection`), with a selected border, a check badge
and a "Select all" button; this change uses the same interaction for documents. Export generation
(`ExportPreparation`, `FileManager.generatePdf`, JPEG export) works on the working
`ImageRepository`. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- Find a document by name quickly.
- Share or delete several documents at once.

**Non-Goals:**
- Full-text search in page contents (OCR text is not stored in the library).
- Merging documents, folders or tags, sorting options.
- Saving several documents to the export folder at once (share only, like "share separately").

## Screen design

Search (top bar becomes a search field; list filters live):

```
┌────────────────────────────────────┐
│ ←  [ fact                     ✕ ]  │  TextField in TopAppBar, auto-focus
├────────────────────────────────────┤
│ ┌─────┐  Factura luz             ⋮ │
│ └─────┘  02 Oct 2026 · 2 pages     │
│ ┌─────┐  factura gas             ⋮ │
│ └─────┘  28 Sep 2026 · 1 page      │
├────────────────────────────────────┤
│ ( 🖼+ Import )        [ 📷  Scan ] │
└────────────────────────────────────┘
```

Normal top bar gains a 🔍 icon before ⋮: `FairScan            🔍  ⋮`.

Selection mode (contextual top bar on `secondaryContainer`, bottom bar actions swapped):

```
┌────────────────────────────────────┐
│ ✕  2 selected            ☑ Select all
├────────────────────────────────────┤
│ ┌─────┐  Invoice March          (✓)│  selected: row background
│ └─────┘  12 Oct 2026 · 30 pages    │  primaryContainer 40%, check badge
│ ┌─────┐  Scan 2026-10-11 20.55  ( )│  on the cover; the row menu is
│ └─────┘  11 Oct 2026 · 1 page      │  replaced by an empty circle
│ ┌─────┐  Receipts               (✓)│
│ └─────┘  11 Oct 2026 · 7 pages     │
├────────────────────────────────────┤
│ ( 🗑 Delete )          [ ⇪ Share ] │
└────────────────────────────────────┘
```

While sharing: the Share button shows a `CircularProgressIndicator` and "2 / 3"; tapping it
again cancels.

## Decisions

- **Query** lives in `LibraryUiState.query: String?` (null = search closed). Filtering is a pure
  function `filterDocuments(items, query)` using `Normalizer.normalize(NFD)` + removal of
  combining marks + `lowercase(Locale.ROOT)`; unit-tested.
- **Selection** in `LibraryUiState.selectedIds: Set<String>`, pruned when the list changes
  (deleted or filtered-out documents stay selected only if visible; Select all selects visible
  ones). Same gesture semantics as `export-page-selection`: still long press selects, tap toggles,
  last unselect ends the mode. `BackHandler` closes search first, then selection.
- **Exporting a non-working document**: `LibraryExport.export(id, format, quality)` copies the
  entry into a temporary directory under `cacheDir`, builds a throw-away `ImageRepository` on it
  (as the external scan-to-PDF mode does), runs the existing `pagesToExport`/`generatePdf` or JPEG
  path, and deletes the temp directory. The working document is untouched. OCR follows the
  settings, like a normal export. Files are named with the document name (made unique when two
  documents share a name). Results are combined into one `ExportResult` (`Pdfs` or `Jpeg`) and sent
  with the existing `ExportEvent.Share`.
- **Delete**: one confirmation (plural string with the count), then `LibraryRepository.delete` for
  each id; if the open document is among them, its working copy is cleared.
- **Recent card (from 03)**: while a query is active, the Recent section is hidden and the recent
  document is listed with the others if it matches. In selection mode the Recent card is selectable
  like any row and its quick actions are hidden.
- **Search + selection**: entering selection mode keeps the query; closing selection keeps the
  search open.

## Risks / Trade-offs

- [Sharing many large documents takes long and uses space] → Sequential generation with progress and
  cancel; temporary files go to the export cache cleaned by `cleanUpOldPreparedFiles`.
- [JPEG format with many documents produces many files] → Accepted; same as "share separately".
- [Long press on a row conflicts with list scrolling] → Standard `combinedClickable`; verify on
  device.
