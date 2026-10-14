# Design

## Context

`DocumentOverviewScreen` shows the working document as a `LazyVerticalGrid` with `reorderable`
(`longPressDraggableHandle`); the open document lives in `ImageRepository` (`PageStore`, sources and
processed images in the working dir) and is mirrored to the library by `LibraryRepository` after each
change (debounced `flush`). Other documents exist only in the `LibraryStore` (metadata, sources,
processed images, OCR, cover). The library home already has a selection mode (`library-selection`) to
mirror. See proposal.md for scope.

## Goals / Non-Goals

**Goals:**
- Page selection, delete, copy and move with the behaviour of the specs.
- Copy/move work against any stored document, not only the working one, and never corrupt either
  side if interrupted.

**Non-Goals:**
- Choosing the insertion position in the destination (always appended).
- Selecting pages from the page editor, or "select all" (not requested; can be added later).
- Cross-library or cross-app transfer.

## Decisions

- **Selection state in the screen, not the view model.** A `Set<String>` of page ids kept in
  `rememberSaveable` inside `DocumentOverviewScreen`, as it is pure UI state and disappears with the
  screen. Filtered against the current pages so a deletion cannot leave stale ids. Alternative (view
  model state, like the library) rejected: the overview has no other consumer of it.
- **Long press vs drag.** The grid keeps `longPressDraggableHandle`, whose `onDragStarted` is not
  triggered by a long press without movement. Use `onDragStopped`/a drag-start flag: if the drag
  stops without having moved (or use `combinedClickable(onLongClick)` on the cell when no selection
  is active) selection starts; while selecting, the handle is disabled (`enabled = false`) and the
  cell uses `clickable` to toggle. To be verified on a device; fallback: `combinedClickable` for the
  long press and a drag handle only when not selecting, accepting that a long press then enters
  selection and a second long press on a selected page starts the drag is not supported.
- **Contextual top bar.** While selecting, the `TopAppBar` shows a close icon, "N selected" and a
  three-dot `DropdownMenu` (Move, Copy, Delete); the bottom bar / landscape side panel buttons are
  disabled. Same look as the library's selection bar.
- **Destination dialog** (`PageDestinationDialog`): an `AlertDialog` with a `LazyColumn` of the
  library entries from `LibraryRepository.documents()` (cached list first, like the library) minus
  the current id, ordered as in the library, with a first row "New document". Loaded by a view-model
  function so the screen only receives a list of `(id, name, pageCount)`.
- **Transfer in `LibraryRepository`**: `suspend fun appendPages(sourcePageIds, targetId: String?)`
  under the repository mutex:
  1. `flush()` the working document so the store holds the current state of the pages.
  2. Read the working pages' metadata from the snapshot, and for each selected page read its source
     (`imageRepository.source(id)`) and OCR/processed image from the store.
  3. Target = `store.readMetadata(targetId)` or a new document (new id and default name through the
     same code path as `startNewDocument`, without touching the working document). Give each copied
     page a **new page id** (ids must be unique inside a document and the same capture may end in
     the same document twice); write the source, processed image (named for the target document by
     `ProcessedNames`), OCR, then metadata last, as `writeSources` already orders it, so a crash never
     leaves metadata referring to a missing file. Refresh the target cover if it has one page more
     than before only when the first page changed, and `updatedAt`.
  4. Return the ids created. Only after success the caller deletes the originals (move).
  No change to the document format; the page record is copied with only its id replaced.
- **Delete / move removal**: `ImageRepository.deletePages(ids)` removing in one mutex section and one
  `saveMetadata`, instead of N calls to `delete`, so one change notification and one flush. If the
  document ends up with no pages, the view model calls the existing `deleteCurrentDocument` path (the
  library already removes empty documents on close), then navigates to the library.
- **Progress / errors**: the view model exposes a `PageTransferState` (idle / running / error). The
  overview shows an indeterminate `LinearProgressIndicator` and blocks touches while running; on
  success a `Snackbar` "N pages copied to «name»" / "moved to"; on failure a snackbar error and no
  removal of originals.
- **Strings** go in `values` and every existing locale folder with English text as placeholder for
  untranslated ones (project convention: check existing `values-*` when implementing).

## Risks / Trade-offs

- [Long press vs drag gesture conflict] → verify on device; fallback described above.
- [Interrupted move: pages duplicated in both documents] → acceptable and non-destructive; the
  originals are removed only after the destination is fully written.
- [Large moves are slow on a SAF (`ContentResolverDocumentTree`) library] → progress indicator and
  work on the IO dispatcher; no cancellation in this change.
- [Copied OCR/processed images missing in the store] → they are a cache by spec
  (`document-library`); the copy simply omits them and they are produced again.
- [Page ids] → new ids on copy avoid collisions; the page file names in the target follow the
  `Name-pageNN` scheme by the existing rename-sync after the append.
