# Tasks

## 1. Navigation

- [ ] 1.1 Add `Screen.Main.Library`, make `NavigationState.initial` take the root (Library in normal mode, Camera in external mode) and implement the back rules from design.md; verify with unit tests in `NavigationStateTest` for every screen, with and without pages
- [ ] 1.2 Remove `Screen.Main.ResumeScan` and `ResumeScanScreen.kt`, and set the initial screen in `MainViewModel`; verify the module compiles (`./gradlew :app:assembleDebug`)

## 2. Library state

- [ ] 2.1 Add `LibraryUiState`, `LibraryItem` and `LibraryViewModel` (documents sorted by last-modified, refresh after autosave, rename, delete); verify with unit tests using a fake `LibraryStore` (order, rename, blank name rejected, delete of the open document clears the working copy)
- [ ] 2.2 Track `last_opened_document_id` (set on open and on creation of a new document) and split the state into `recent` and `others`; verify with unit tests (no recent, recent first and not repeated, recent deleted, new scan becomes recent)

## 3. Library screen

- [ ] 3.1 Implement `LibraryScreen` (top bar with overflow menu, list items with lazy cover, row menu, empty state, Import/Scan bottom bar, landscape layout) with Compose previews for empty, few and many documents
- [ ] 3.2 Implement the Recent card (headings, larger cover, Add pages / Share / Export quick actions with share progress); verify Compose previews with and without a recent document
- [ ] 3.3 Add the rename dialog and the delete confirmation; add EN and ES strings
- [ ] 3.4 Wire the screen in `MainActivity`: open, export from row, recent quick actions, scan, import (camera with `importOnOpen`); verify on a device the scenarios in `specs/library-screen/spec.md`

## 4. Other screens

- [ ] 4.1 Change the export screen's "New document" action to keep the document and open the camera, removing the loss-of-scan confirmation; verify on a device that the exported document is still in the library
- [ ] 4.2 Verify on a device that the scan-to-PDF flow from another app still opens on the camera and returns the PDF

## 5. Verification

- [ ] 5.1 Verify the module compiles and all unit tests pass (`./gradlew :app:assembleDebug :app:testDebugUnitTest`)
