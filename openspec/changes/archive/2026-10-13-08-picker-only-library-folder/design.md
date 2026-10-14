# Design

## Context

Change 02 gave the library two kinds of location: `LibraryLocation.Direct` (a path such as
`/storage/emulated/0/FairScan`, reached with `File` through `FileLibraryStore`, needing "All files
access") and `LibraryLocation.Saf` (a tree URI granted by `OpenDocumentTree`, reached through
`ContentResolverDocumentTree`). Both sit behind `LibraryStore`/`TreeLibraryStore`, so the library
logic does not care which one it is on. The permission comes from `src/allFilesAccess/AndroidManifest.xml`,
added to the debug and release source sets unless the build is made with
`-Pfairscan.noAllFilesAccess`; that flag already produces the picker-only app
(`BuildConfig.ALL_FILES_ACCESS_AVAILABLE = false`, `LibraryFolderUiState.allFilesAccessOffered = false`),
which is the one verified on devices during change 02. This change makes that variant the only one.

## Goals / Non-Goals

**Goals:**
- No "All files access" and no permission for the library; access to the chosen folder only.
- No default folder: the user always chooses or creates the folder with the picker.

**Non-Goals:**
- Changing the document format, the confirmation for non-empty folders, moving documents between
  folders, the library folder setting or the stores (`TreeLibraryStore` stays as is).
- Migrating a library saved as a path (only owner test builds have one).
- Offering a different place to keep documents (private storage stays excluded).

## Decisions

- **One kind of location.** `LibraryLocation` keeps only `Saf`; `Direct`, `StoredLibraryLocation.Kind.DIRECT`
  and the platform functions that serve paths (`hasAllFilesAccess`, `isFolderUsable`,
  `defaultFolderPath`, `existingDirectStore`, `directStore`) are deleted. `LibraryLocations` loses
  `defaultLocation`, `defaultFolderExists` and the `Direct` branches.
- **Old settings.** A stored `DIRECT` location is read as "no location": `SettingsRepository` maps
  it to null (and the next save overwrites it), so the status is `NotSetUp` and the setup screen
  appears. Not `Unavailable`: that state needs a location to name and to offer moving documents
  from, and a path cannot be opened any more. Choosing the same folder brings its documents back
  through the existing confirmation. The working document is kept and saved as with any first
  setup.
- **Setup screen.** One explanation, one button. Content: where the documents live and why ("so
  you can edit and export them again"), "choose an existing folder or create a new one", that the
  app only gets access to that folder, and a hint that the picker cannot use the root of the
  storage or Downloads (the picker disables its confirm button there), so a subfolder has to be
  chosen or created. Removed: `DefaultFolderCard`, "Recommended", "Use the FairScan folder", the
  all-files explanation, the permission-denied message. The `Unavailable` variant keeps its text and
  its single "Choose a folder" action.
- **Picker.** `OpenDocumentTree` with an initial URI at the root of the primary storage
  (`primary:`), as the existing code already does when no default folder exists; the system picker
  has its own "Create new folder" button. `FLAG_GRANT_*` and `takePersistableUriPermission` stay.
- **ViewModel/events.** `LibraryFolderEvent.RequestAllFilesAccess` and `PickFolder(defaultFolderExists)`
  become just `PickFolder`; `onUseDefaultFolder`, `onAllFilesAccessResult`, `permissionDenied`,
  `allFilesAccessOffered` and `defaultFolderName` go. `MainActivity` drops the two permission
  launchers and `allFilesAccessIntent`.
- **Settings.** The library folder item shows the folder and "Change folder"; the "use the FairScan
  folder" button disappears with the default.
- **Build.** Delete `src/allFilesAccess`, the source set lines, the `allFilesAccess` property, and
  `BuildConfig.ALL_FILES_ACCESS_AVAILABLE`. The merged manifests must not contain
  `MANAGE_EXTERNAL_STORAGE`, and that `WRITE_EXTERNAL_STORAGE` is only the one limited to Android 10
  and lower (`maxSdkVersion="29"`) that exporting to Downloads needs on those versions; its comment
  in the manifest no longer mentions the library.
- **Text and spec ordering.** Change 07 (app name) states that the default library folder name does
  not change; that sentence is obsolete here. The delta for `app-packaging` therefore only applies
  after 07 is archived (the archive command refuses it before): archive 07 first, then this one.

## Risks / Trade-offs

- [Picker restrictions: root and Downloads cannot be chosen] → the setup text says so; the picker
  itself explains it, and creating a subfolder is one tap.
- [Reading and writing through a tree URI is slower than through a path] → already the case for any
  picked folder and verified on devices; the library keeps its cached list (`LibraryIndex`) and
  covers.
- [Losing the one-tap default] → accepted by the owner: the permission and the "recommended" card
  go together.
- [`FileLibraryStore` and `FileDocumentTree` remain in the code] → they are still used by the tests,
  the legacy private-storage migration (`legacyDirs`) and the library mover from private storage;
  they are not removed.
- [Documents saved by a development build as a path] → not migrated; choosing the same folder
  brings them back.
