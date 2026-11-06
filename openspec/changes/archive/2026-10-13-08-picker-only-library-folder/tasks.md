# Tasks

## 1. Locations

- [x] 1.1 Remove `LibraryLocation.Direct`, the `DIRECT` stored kind (read as no location), the path functions of `LibraryPlatform`/`AndroidLibraryPlatform`, `DEFAULT_LIBRARY_FOLDER_NAME`, and `defaultLocation`/`defaultFolderExists`/`hasAllFilesAccess` of `LibraryLocations`; update the tests and fake platforms; verify with unit tests that a stored `DIRECT` location gives `NotSetUp`, that a picked folder is set up, moved and reported unavailable as before, and that the legacy private-storage migration still works

## 2. Setup and settings

- [x] 2.1 Reduce `LibraryFolderViewModel` and its events to picking a folder (no default folder, no permission request, no `permissionDenied`); verify with unit tests that picking an empty folder, a folder with documents and a folder with other content, cancelling, and the move offer behave as before
- [x] 2.2 Rework `LibrarySetupScreen` (explanation, one button, subfolder hint, the unavailable variant), the library folder item of the settings and the strings in English and Spanish (removing the ones for the default folder and the permission); verify the Compose previews in portrait and landscape

## 3. Build

- [x] 3.1 Delete `src/allFilesAccess`, the source set lines, the `fairscan.noAllFilesAccess` property and `BuildConfig.ALL_FILES_ACCESS_AVAILABLE`, and the permission launchers and the initial URI hint in `MainActivity`; verify that the merged manifests of debug and release do not contain `MANAGE_EXTERNAL_STORAGE`, that `WRITE_EXTERNAL_STORAGE` is still limited to `maxSdkVersion="29"` (exporting on Android 10 and lower), and that its manifest comment no longer mentions the library

## 4. Verification

- [x] 4.1 Verify on a device with the data cleared: the setup screen has no default folder, the picker opens at the root of the storage, creating a new folder in it sets the library up, no permission is requested for the library (`dumpsys package` lists no `MANAGE_EXTERNAL_STORAGE`), a scan is saved there, and picking an existing folder with documents asks for the confirmation and brings them back
- [x] 4.2 Verify the module compiles and all unit tests pass (`./gradlew :app:assembleDebug :app:testDebugUnitTest`)
