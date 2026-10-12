# Tasks

## 1. Search

- [ ] 1.1 Add `filterDocuments(items, query)` (case- and accent-insensitive contains) and `query` to `LibraryUiState`; verify with unit tests (case, accents, empty query, no results)
- [ ] 1.2 Add the search action and search top bar to `LibraryScreen`, the no-results message and back handling; add EN and ES strings; verify on a device the scenarios in `specs/library-search/spec.md`

## 2. Selection state

- [ ] 2.1 Add `selectedIds`, `onLongPress`, `onTap`, `selectAll`, `clearSelection` to `LibraryViewModel`, pruning on list changes and respecting the active search; verify with unit tests (enter, toggle, last unselect ends the mode, select all with a query)

## 3. Selection UI

- [ ] 3.1 Add the contextual top bar (count, Select all, close), selected row visuals and the Delete/Share bottom bar; add EN and ES strings (plurals); verify Compose previews in portrait and landscape

## 4. Actions

- [ ] 4.1 Implement multi-document delete with a plural confirmation; verify with a unit test that deleting the open document clears the working copy
- [ ] 4.2 Implement `LibraryExport` (temporary repository per document, existing export pipeline, unique names, cleanup) and Share with progress and cancel; verify with a unit test that the working document is unchanged and one file per document is produced
- [ ] 4.3 Verify on a device the scenarios in `specs/library-selection/spec.md` for PDF and JPEG formats

## 5. Verification

- [ ] 5.1 Verify the module compiles and all unit tests pass (`./gradlew :app:assembleDebug :app:testDebugUnitTest`)
