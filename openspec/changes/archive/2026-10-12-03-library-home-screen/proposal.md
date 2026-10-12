# Proposal

## Why

After change 01 every document is kept, but the only way back to one is the "resume scan" screen,
which shows just the last document. Users need a place to see all their scans, reopen any of them
to re-edit or export again, and remove the ones they no longer need: a file manager for scans, like
CamScanner's home screen, but with FairScan's look and its camera-first simplicity.

## What Changes

- New **Library** screen, shown at launch (after the folder setup of change 02 when needed): a list
  of documents, each with a cover thumbnail, name, modification date and page count.
- The **last opened document** comes first under a **"Recent"** heading, as a larger card with quick
  actions: **Add pages** (continue scanning it, which replaces the old "resume scan" screen),
  **Share** (the whole document with the export settings) and **Export**. The other documents
  follow under **"All documents"**, most recently modified first.
- The bottom bar keeps FairScan's style: an outlined **Import** button and a filled **Scan** button,
  both starting a new document.
- Tapping a document opens it in the page editor (the current document screen). Change 04 inserts a
  page grid in between.
- Each row has a menu with **Rename**, **Export** and **Delete** (with confirmation).
- An empty library shows a short explanation and the Scan button.
- The **resume scan** screen is removed: the library and its Recent card replace it.
- **BREAKING** navigation: the app opens on the library instead of the camera. Back from the page
  editor returns to the library; back from the camera returns to the page editor when the document
  has pages, to the library otherwise. The export screen's "New document" button returns to the
  camera with a new document, and back from export returns to the page editor.
- The scan-to-PDF flow requested by other apps is unchanged (opens on the camera, no library).

## Capabilities

### New Capabilities
- `library-screen`: browsing the saved documents and the actions on each of them.

### Modified Capabilities

## Impact

- New `ui/screens/library/` (`LibraryScreen`, `LibraryUiState`, `LibraryViewModel`).
- `ui/Navigation.kt`: `Screen.Main.Library` as root in normal mode, new back rules,
  `Screen.Main.ResumeScan` removed.
- `MainActivity.kt`: library wiring; `ResumeScanScreen.kt` deleted.
- `MainViewModel.kt`: initial screen, `openDocument`.
- `ui/screens/export/ExportScreen.kt`: back target, "New document" behaviour.
- Strings (EN + ES): library title, empty state, rename, delete confirmation, page count.
