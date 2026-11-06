# Tasks

## 1. Navigation

- [x] 1.1 Add `Screen.Main.Overview` and update the back rules (Overview → Library, Document → Overview, Camera → Overview/Library, Export → Overview) and the camera's "N pages" target; verify with unit tests in `NavigationStateTest`

## 2. Thumbnails

- [x] 2.1 Add a grid thumbnail size to `ImageRepository.getThumbnail` with its own cache; verify with a unit test that both sizes are generated and cached independently
- [x] 2.2 Extract `PageThumbnail` (image + number badge) from `CommonPageList` and use it in the strip; verify the document screen looks unchanged (Compose preview and device)

## 3. Overview screen

- [x] 3.1 Implement `DocumentOverviewScreen` (top bar with tappable name, adaptive grid, number badges, overflow menu with Delete document, Add page/Export bottom bar, landscape layout) with Compose previews
- [x] 3.2 Add drag-to-reorder with `rememberReorderableLazyGridState` calling `movePage`; verify on a device that dragging reorders and a tap still opens the page
- [x] 3.3 Wire rename and delete document (reusing the 03 dialogs), tap to open the page editor on the tapped page, Add page and Export in `MainActivity`; add EN and ES strings

## 4. Verification

- [x] 4.1 Verify on a device the scenarios in `specs/document-overview/spec.md` and the modified navigation in `specs/library-screen/spec.md`
- [x] 4.2 Verify the module compiles and all unit tests pass (`./gradlew :app:assembleDebug :app:testDebugUnitTest`)
