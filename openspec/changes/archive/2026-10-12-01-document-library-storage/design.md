# Design

## Context

`ImageRepository` manages one working document in `filesDir`: `sources/<id>.jpg` (raw capture),
`scanned_pages/<id>.<mode>[.qN][.aHASH].jpg|png` (processed renderings) and
`scanned_pages/document.json` (`DocumentMetadataV2`: per page id, base/manual rotation, auto quad,
`userQuad` + `quadVersion`, color mode, adjustments, optical measures, source size). The editing
data is therefore already complete; what is missing is keeping several documents and not deleting
them. `MainViewModel.startNewDocument()` calls `imageRepository.clear()`. On start, a non-empty
working document leads to `ResumeScanScreen`. `loadPages()` drops pages whose processed file is
missing, so processed files are currently required. In `EXTERNAL_SCAN_TO_PDF` mode,
`SessionViewModel` uses a temporary directory deleted on clear. See proposal.md for motivation.

### Series overview

This is the first of five changes that together add a document library:

1. `01-document-library-storage` — this change: persistence, no new screen.
2. `02-library-folder-selection` — at first launch the user confirms the library folder
   (`FairScan` at the root of the user's storage by default, SAF), reusing the documents of a
   previous installation found there.
3. `03-library-home-screen` — library list as the home screen, recent document first,
   open/rename/delete.
4. `04-document-page-grid` — document overview grid between the library and the page editor.
5. `05-library-search-and-selection` — search and multi-selection actions in the library.

Target flow once all are done:

```
            ┌───────────────┐  Scan / Import   ┌─────────┐
  launch ──▶│    Library    │─────────────────▶│ Camera  │
            │ (list, 03/05) │◀── back (empty) ─│         │
            └──────┬────────┘                  └────┬────┘
                   │ tap document                   │ "N pages" / back
                   ▼                                ▼
            ┌───────────────┐   Add page    ┌──────────────┐
            │ Document grid │──────────────▶│   Camera     │
            │     (04)      │               └──────────────┘
            └──┬─────────┬──┘
     tap page  │         │ Export
               ▼         ▼
     ┌──────────────┐  ┌──────────┐
     │ Page editor  │  │  Export  │
     │ (current     │  │ (as now) │
     │ DocumentScr.)│  └──────────┘
     └──────┬───────┘
            │ Crop
            ▼
       ┌──────────┐
       │   Crop   │
       └──────────┘
```

## Goals / Non-Goals

**Goals:**
- Keep every document (sources + editing data) and reopen it with identical results.
- Autosave with no "save" button.
- A storage abstraction that change 02 backs with the user's library folder (SAF).

**Non-Goals:**
- Any library UI (03, 04, 05). Until 03 lands, documents accumulate but are only reachable through
  the existing "resume scan" screen for the last one.
- Choosing the folder (02). This change is not releasable alone: documents must never be kept in
  the app's private storage, so it ships together with 02.
- Editing two documents at once, sync conflict resolution, cloud storage.

## Decisions

- **Working copy + library store.** `ImageRepository` keeps operating on a working directory in
  internal storage (fast `File` access, processed-image cache). The library is a separate
  `LibraryStore` holding the canonical copy of each document. Opening a document copies it into the
  working directory; edits are written back. Alternative: run `ImageRepository` directly inside the
  library folder (rejected: impossible with SAF in 03, and processed files would pollute the user's
  folder).
- **Library entry layout** (one directory per document, named by document id):
  ```
  <library>/
    <docId>/
      .nomedia                    # keep raw captures out of the gallery
      document.json               # DocumentMetadataV3
      cover.jpg                   # thumbnail of page 1 (cache, regenerable)
      sources/<pageId>.jpg        # raw captures, never modified
  ```
  The directory name is the stable id (`yyyyMMdd-HHmmss-<random4>`), not the document name, so
  renaming is a JSON write and never a directory rename (expensive and fragile with SAF). The name
  lives in `document.json`. Alternative: human-readable directory names (rejected for that reason;
  the readable name is still in the JSON and in exports). `.nomedia` goes in each entry, not at the
  library root, because in change 02 the root may be a folder that also holds the user's own files,
  which must stay visible in galleries.
- **Only own entries are touched.** An entry is a FairScan document only if it is a directory
  holding a `document.json` that parses as FairScan metadata (v1–v3) and whose `id` matches the
  directory name. Anything else in the library root is ignored: never listed, modified, moved or
  deleted. A new id that collides with an existing foreign directory is regenerated. Deleting a
  document removes only the files FairScan wrote (`document.json`, `cover.jpg`, `.nomedia`,
  `sources/<pageId>.jpg` of its pages), then the directories if they are empty.
- **`DocumentMetadataV3`** = v2 + `id`, `name`, `createdAt`, `updatedAt` (epoch millis). The same
  format is used in the working directory and in the library, so syncing is a file copy.
  `loadMetadata` accepts v1/v2/v3; v1/v2 get a generated id, the default name and `createdAt` from
  the oldest source file.
- **Processed images become a cache.** When a page's processed file is missing (always the case
  right after opening), `ImageRepository` renders it from the source with the stored quad, color
  mode and adjustments instead of dropping the page. `loadPages` filters on the source file instead
  of the processed file. Rendering is lazy (first `image()`/`getThumbnail()` call) so opening a
  30-page document is not blocked.
- **Autosave.** `ImageRepository` exposes `changes: SharedFlow<Unit>` emitted after every
  `saveMetadata()`. `LibraryRepository` collects it, debounced 500 ms, and writes `document.json`,
  any new source files, and `cover.jpg` when page 1 changed. Deleted pages' sources are removed from
  the entry. Writes go to a temp name then rename, so a crash never leaves a truncated JSON. The
  pending write is flushed on `ON_STOP` (process lifecycle) so killing the app from recents keeps the
  last edit.
- **Document creation.** `LibraryRepository` creates the entry on the first `changes` emission where
  the working document has ≥1 page and no id is registered yet. A document whose last page is
  deleted is removed from the library when it is closed (new/open another), not immediately, so an
  accidental "delete last page" followed by a capture keeps the document.
- **`startNewDocument()`**: flush autosave, then clear the working directory (the library copy
  stays). `openDocument(id)`: flush, clear, copy entry into working directory, reload pages.
- **Current document id** is persisted in the working directory's `document.json`, so after a
  restart the app knows which library entry the working copy belongs to.
- **Migration.** When a library store becomes available (first launch after the folder is set up
  in 02), `LibraryRepository` finds a working `document.json` without id (v1/v2) with ≥1 page: it
  assigns an id, names it, and saves it to the library. Until then the working document stays as it
  is.
- **External mode** builds `ImageRepository` without a `LibraryRepository`, so nothing is saved.
- **Default name** reuses the export default-file-name style setting. `ExportViewModel` uses the
  document name as default file name when it differs from the generated default; the page suffix
  rule for partial exports still applies.
- **`LibraryStore` interface** (`list()`, `read(id)`, `write(id, files)`, `delete(id)`,
  `openSource(id, pageId)`). This change adds `FileLibraryStore(dir: File)`, used by unit tests
  and, during development until 02 lands, on a debug-only directory. Change 02 adds the SAF
  implementation used in production; `FileLibraryStore` is never used for user data in a release.
- **Private storage holds only caches.** The working copy and processed images in `filesDir` are a
  cache of the open document, always written back to the library; losing them loses nothing.

## Risks / Trade-offs

- [Storage use grows: raw captures are kept for every document] → Expected by the feature; deleting
  a document frees it (03). Show document sizes later if needed.
- [First open of a big document renders every page again] → Lazy per-page rendering with the
  existing caches; thumbnails first.
- [Re-rendering might differ from the original if the image-processing code changes in a future
  version] → Accepted: the stored data are the inputs, outputs follow the current algorithm.
- [Crash between source copy and JSON write] → Sources are written before the JSON; an orphan
  source is ignored and cleaned on the next write.
