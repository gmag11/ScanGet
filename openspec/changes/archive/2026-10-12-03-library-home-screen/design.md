# Design

## Context

After changes 01–02, `LibraryRepository` lists the documents of the user's library folder (id,
name, dates, page count, cover) and `MainViewModel.openDocument(id)` / `startNewDocument()` switch
the working document. The setup screen (02) is shown first while no folder is set up.
Navigation is a single-root stack (`NavigationState`): `Screen.Main.*` replace the stack,
`Screen.Overlay.*` push. Today the root is `Camera`, and `ResumeScan` is shown at launch when the
working document is not empty. FairScan's visual language: Material 3 with the green primary
color, screens without a top bar except Export/Settings (back button and overflow menu float over
the content), a bottom bar on `surfaceContainerHigh` holding an outlined secondary button on the
left and a filled `MainActionButton` on the right ("Import" / "N pages", "Add page" / "Export").
In landscape the bottom bar becomes a side column. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- A library screen that feels native to FairScan, not a CamScanner copy.
- Clear, predictable navigation between library, camera, editor and export.

**Non-Goals:**
- Page grid of a document (04), search and multi-selection (05), folders/tags, sorting options.
- Changes to the camera, editor, crop or export screens beyond navigation targets.

## Screen design

Portrait:

```
┌────────────────────────────────────┐
│ FairScan                         ⋮ │  TopAppBar (Settings, About)
├────────────────────────────────────┤
│ Recent                             │  titleMedium, primary
│ ┌────────────────────────────────┐ │  ElevatedCard, surfaceContainerLow
│ │ ┌───────┐  Invoice March     ⋮ │ │   cover 72×96dp
│ │ │ ▤▤▤▤▤ │  12 Oct 2026, 20:08  │ │   headline: titleMedium
│ │ │ ▤▤▤▤▤ │  ▢ 30 pages          │ │
│ │ └───────┘                      │ │
│ │ (+ Add pages) (⇪ Share) (⤓ Export)│   OutlinedButtons / tonal, equal
│ └────────────────────────────────┘ │   width, like CamScanner's row
│                                    │
│ All documents                      │  titleMedium, onSurfaceVariant
│ ┌─────┐  Scan 2026-10-11 20.55  ⋮  │  ListItem
│ │ ▤▤▤ │  11 Oct 2026, 20:55        │   leading: cover 48×64dp, 4dp radius,
│ └─────┘  ▢ 1 page                  │            1dp outline
│ ───────────────────────────────    │   supporting: date · page count
│ ┌─────┐  Receipts                ⋮ │   trailing: row menu
│ │ ▤▤▤ │  11 Oct 2026, 20:53        │
│ └─────┘  ▢ 7 pages                 │
│                                    │
├────────────────────────────────────┤
│ ( 🖼+ Import )        [ 📷  Scan ] │  bottom bar, same as camera/document
└────────────────────────────────────┘
```

The Recent card is the continuation of the old "resume scan" screen: Add pages is what "Resume
scan" did. Tapping the card body opens the document, like any other item. When there is no recent
document (fresh library, recent deleted) the screen starts directly with the list and no headings;
when the recent document is the only one, the "All documents" heading is omitted.

Row menu (`DropdownMenu`): ✎ Rename · ⇪ Export · 🗑 Delete.
Rename: `AlertDialog` with an `OutlinedTextField` pre-filled and fully selected; Confirm disabled
when blank. Delete: existing `ConfirmationDialog` ("Delete document?" / "The original captures
will be deleted. Exported files are kept.").

Empty state, centered in the list area:

```
            ┌─────┐
            │  📄 │      (outlined document icon, onSurfaceVariant)
            └─────┘
      Your scans will appear here
   Every document is saved automatically
```

Landscape: list on the left (weight 2), the Import/Scan buttons stacked in the right column
(weight 1), like the document screen.


## Decisions

- **`LibraryViewModel`** exposes `StateFlow<LibraryUiState>` (`documents: ImmutableList<LibraryItem>`,
  `isLoading`) built from `LibraryRepository.documents()`, refreshed on screen entry and after
  autosave. Covers are loaded as `Bitmap` lazily per item (`LaunchedEffect(item.id)`), using
  `cover.jpg`; a placeholder surface is shown meanwhile.
- **Recent document**: preference `last_opened_document_id`, set by `openDocument(id)` and when a
  new document is created in the library (first page of a scan or import). It is device-local (not
  written to the folder, so opening a document never modifies it nor triggers sync). `LibraryUiState`
  exposes `recent: LibraryItem?` and `others` (all but the recent, by `updatedAt` desc); a recent id
  not found in the library yields `recent = null`.
- **Recent quick actions**: Add pages = `openDocument(id)` + camera (back returns to the editor, by
  the camera back rule). Share = `openDocument(id)` (no-op when it is already the working document),
  then `ExportViewModel` prepares every page with the settings' format and quality and emits the
  existing `ExportEvent.Share`; the button shows a small progress indicator meanwhile and the other
  actions stay enabled. Export = `openDocument(id)` + export screen with every page selected.
- **Dates** formatted with `DateUtils.formatDateTime` (`FORMAT_SHOW_DATE|FORMAT_SHOW_TIME|
  FORMAT_ABBREV_MONTH`), locale-aware. Page count via the existing plural `pageCountText`.
- **Navigation**: `NavigationState.initial(root)` takes the root; `Library` in `NORMAL` (after
  `LibrarySetup` from 02 when needed), `Camera` in `EXTERNAL_SCAN_TO_PDF`. Back rules:
  `Document → Library`, `Camera → Document if pages > 0 else Library` (needs the page count,
  so `navigateBack(hasPages: Boolean)`), `Export → Document`, `EditImage → Document` (unchanged),
  `Library → system`. Unit-tested in `NavigationStateTest`.
- **Scan**: `startNewDocument()` then `toCameraScreen`. **Import**: `startNewDocument()`, navigate
  to camera and immediately launch the existing image picker of `CameraScreen` (a
  `Screen.Main.Camera(importOnOpen = true)` flag), reusing its import progress UI. Alternative:
  importing from the library screen itself (rejected: duplicates import progress UI).
- **Open**: `openDocument(id)` then `Screen.Main.Document(initialPage = 0)`; a progress indicator is
  shown while copying the entry into the working directory.
- **Export from row**: `openDocument(id)` then `toExportScreen`; back from export goes to the
  editor of that document, which is consistent with the other back rules.
- **Delete open document**: `LibraryRepository.delete(id)` on the open document also clears the
  working copy (no autosave re-creates it).
- **Export "New document" button** (`onCloseScan`): keeps the document (no deletion since 01),
  calls `startNewDocument()` and opens the camera. Its label stays "New document"; the confirmation
  dialog about losing the scan is removed since nothing is lost.
- **`ResumeScanScreen`** and `Screen.Main.ResumeScan` are deleted.

## Risks / Trade-offs

- [Users used to opening straight on the camera need one extra tap] → The Scan button is the
  primary action, in the same place as the camera's main button. A "Open camera at launch" setting
  could be added later if requested.
- [Long libraries load many covers] → `LazyColumn` + lazy cover loading + small `cover.jpg`.
- [Opening a big document takes time (copy + lazy re-render)] → Progress indicator; thumbnails come
  from the cover/thumbnail cache first.
