# Tasks

## 1. Data layer: delete and transfer pages

- [x] 1.1 Add `ImageRepository.deletePages(ids)` (one lock, one save, files removed) and a unit test checking that remaining pages keep order and the deleted pages' files are gone; verify with `./gradlew :app:testDebugUnitTest --tests "*ImageRepository*"`
- [x] 1.2 Add `LibraryRepository.appendPages(pageIds, targetId?)` copying source, processed image, OCR and page record (new page id) to an existing or new document, metadata written last, names synced; add tests in the library repository test class for: copy to existing, copy to new, order preserved, independence after editing, failure leaves the target consistent; verify the tests pass
- [x] 1.3 Add a repository query for the destination list (other documents, with name and page count) and test that the current document is excluded

## 2. View model

- [x] 2.1 Add view-model operations `deletePages`, `copyPages`, `movePages` (move = append then delete only on success; deleting/moving every page removes the document and navigates to the library) with a transfer state (idle/running/error) and tests for: move keeps originals on failure, move all pages removes the document; verify the tests pass
- [x] 2.2 Wire the new callbacks and the destination list into `MainActivity`'s `Screen.Main.Overview` and verify the project compiles with `./gradlew :app:assembleDebug`

## 3. UI

- [x] 3.1 Add selection state to `DocumentOverviewScreen`: long press enters selection, tap toggles, back/close/empty ends it, drag-reorder disabled while selecting, selected cells visibly marked; verify with a Compose UI test for enter, toggle, back, and that a long press + drag still reorders
- [x] 3.2 Add the selection top bar (close, count, three-dot menu with Move/Copy/Delete) and disable the bottom/side actions while selecting; verify with a UI test that the menu shows the three entries
- [x] 3.3 Add the delete confirmation dialog (with page count) and the `PageDestinationDialog` (other documents + new document); verify with UI tests for confirm/cancel and for choosing an existing and a new destination
- [x] 3.4 Show progress while a transfer runs and a snackbar with the result or error; verify manually on a device with a 10+ page document (copy, move, delete, move all pages)
- [x] 3.5 Add the new strings to every locale folder and update the previews with a selection state; verify there is no lint "MissingTranslation" error with `./gradlew :app:lintDebug`

## 4. Documentation

- [x] 4.1 Not applicable: the project keeps no user documentation for this (changelogs are written per release)
