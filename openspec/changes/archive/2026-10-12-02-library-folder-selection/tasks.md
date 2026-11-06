# Tasks

## 1. SAF store and folder inspection

- [x] 1.1 Implement `SafLibraryStore` (children query, read, temp-then-rename JSON write, own-files-only delete, id clash avoidance, `.nomedia` per entry) and make the `LibraryStore` contract tests from 01 run against both stores (Robolectric or instrumented test with a temp tree); verify they pass, including foreign files that stay untouched
- [x] 1.2 Implement `inspectFolder` (FairScan documents vs. other items, read-only); verify with tests for an empty folder, a previous installation, foreign content only, mixed content and a corrupted entry
- [x] 1.3 Add the library index cache (`filesDir/library-index.json`) refreshed when the library is read; verify with a unit test that it is rebuilt when deleted

## 2. Location state

- [x] 2.1 Add `library_location_kind`/`library_location` to `SettingsRepository` and `LibraryLocation` (`NotSetUp`, `Direct`, `Saf`, `Unavailable`) to `LibraryRepository`, with availability checks at launch, on foreground and before autosave; verify with unit tests using fake stores
- [x] 2.2 Keep the working document dirty while the folder is unavailable and write it when it is available again; verify with a unit test using a failing fake store
- [x] 2.3 Run the 01 legacy migration (and the debug-store copy) only once a folder is set up; verify with a unit test that the working document is untouched before setup and in the library after it

## 3. Moving documents

- [x] 3.1 Implement move with progress (only recognized entries; sources, cover, `.nomedia`, JSON last; verify; own-files-only delete; newer `updatedAt` wins); verify with unit tests including an interruption after the copy of sources and foreign files that are not moved

## 4. Setup flow

- [x] 4.1 Add `Screen.Main.LibrarySetup` as the initial screen in normal mode while the location is `NotSetUp` or `Unavailable`; verify with a unit test of the initial navigation (fresh install, updated install, unavailable folder, external mode)
- [x] 4.2 Implement `LibrarySetupScreen` (proposed folder card, main and secondary actions, permission-denied hint, unavailable mode, portrait and landscape, previews); add EN and ES strings
- [ ] 4.3 Add `MANAGE_EXTERNAL_STORAGE` and raise `WRITE_EXTERNAL_STORAGE` to `maxSdkVersion` 29 with `requestLegacyExternalStorage`; request all files access (system settings screen on 11+, runtime permission on 8–10), then create or inspect `/storage/emulated/0/FairScan`; verify on Android 9, 11 and 15 emulators granting and denying
- [x] 4.4 Add `BuildConfig.ALL_FILES_ACCESS_AVAILABLE` and a way to build without the permission (for Google Play if the declaration is refused); verify a build without it only offers the folder picker
- [x] 4.5 Launch the picker with the initial URI (`primary:FairScan` when the directory exists, storage root otherwise); verify on Android 11, 13 and 15 that the picker opens in the right place
- [x] 4.6 Implement the non-empty folder confirmation built from `inspectFolder` (Use this folder / Choose another, releasing the rejected permission); verify on a device the scenarios of "Folder setup at first launch" and "Confirmation for a non-empty folder"

## 5. Settings

- [x] 5.1 Add the "Library" section with the folder row to `SettingsScreen`, reusing the confirmation, move and progress dialogs; add EN and ES strings; verify on a device moving 10+ documents between two folders

## 6. Verification

- [x] 6.1 Verify on a device: fresh install granting all files access (`FairScan` created at the storage root); fresh install denying it and choosing a folder; uninstall and reinstall picking the same folder (documents reused and editable); a folder with unrelated photos (warning shown, photos unchanged after scanning, deleting and moving documents, still visible in the gallery while raw captures are not); revoking the permission shows the setup screen in unavailable mode
- [x] 6.2 Verify the module compiles and all unit tests pass (`./gradlew :app:assembleDebug :app:testDebugUnitTest`)
