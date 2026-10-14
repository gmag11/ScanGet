# Proposal

## Why

To keep its library in a `FairScan` folder at the root of the storage, the app asks for the
"All files access" permission (`MANAGE_EXTERNAL_STORAGE`): a very broad permission, one that Google
Play only grants after a policy declaration, and that gives the app access to everything the user
has for the sake of one folder. The system folder picker already lets the user **create** a folder
and gives the app access to **that folder only**. The "default folder" convenience (`FairScan` at the
root, proposed with one tap) exists only because of the broad permission, so it goes too.

## What Changes

- The app **no longer requests** "All files access": the permission is removed from the manifest,
  with the build flag `-Pfairscan.noAllFilesAccess` and everything that asked for it. The library
  needs no storage permission at all. (`WRITE_EXTERNAL_STORAGE`, limited to Android 10 and lower, stays
  only because exporting a file to the Downloads folder on those versions needs it; it is not
  related to the library and its manifest comment stops saying so.)
- The **default folder is removed**: the setup screen no longer proposes `FairScan` at the root, has
  no "Use the FairScan folder" button and no "Recommended" card, and the settings no longer offer
  going back to it.
- Setting up the library is **always done with the system folder picker**: the setup screen
  explains that the user chooses an existing folder or creates a new one in the picker (a
  "create new folder" button there) and that the app only gets access to that folder. The picker
  opens at the root of the storage; there is no folder pre-selected.
- Paths to the storage (`LibraryLocation.Direct`) disappear: a library is always a folder granted
  through the picker (`Saf`). A library saved by a development build as a path is no longer
  reachable: the setup screen is shown, and choosing the same folder again brings all its documents
  back (the confirmation for a non-empty folder applies).
- Documents, their format, the confirmation for non-empty folders, moving documents between
  folders, and the library folder setting do not change.

**Assumptions** (cheap to revert): a library saved as a path by an earlier development build is not
migrated (it only exists on the owner's test devices); the picker starts at the root of the
internal storage.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `library-folder`: where the library may be, how it is set up (picker only, no default folder, no
  broad permission), reusing documents and the settings entry.
- `app-packaging`: the statement that the default library folder name does not change (from change
  07, to be archived first) no longer applies. This delta only applies once 07 is archived.

## Impact

- `app/src/allFilesAccess/AndroidManifest.xml` (deleted) and `app/build.gradle.kts` (source sets,
  `-Pfairscan.noAllFilesAccess`, `ALL_FILES_ACCESS_AVAILABLE`).
- `data/library`: `LibraryLocation` (`Direct`, `StoredLibraryLocation.Kind.DIRECT`),
  `LibraryPlatform`/`AndroidLibraryPlatform` (`hasAllFilesAccess`, `isFolderUsable`,
  `defaultFolderPath`, `existingDirectStore`, `directStore`, `DEFAULT_LIBRARY_FOLDER_NAME`),
  `LibraryLocations` (`defaultLocation`, `defaultFolderExists`, `Direct` branches).
- `ui/screens/setup` (`LibraryFolderViewModel`, `LibrarySetupScreen`), `ui/screens/settings`
  (`SettingsScreen`, `SettingsRepository` DIRECT value), `MainActivity` (all-files launchers,
  `pickerInitialUri`).
- Strings (EN + ES): remove the ones for the default folder and the permission, reword the setup
  and folder texts. Tests of `LibraryLocations`, `LibraryFolderViewModel` and the fake platforms.
- README and store descriptions do not mention the permission (checked).
- **Behavior on Android:** the system picker does not allow choosing the root of the storage nor
  folders such as Downloads (the "use this folder" button is disabled there): the user picks or
  creates a subfolder, which the setup text says. Reading and writing through the picker is slower
  than through a path, as already happens with picked folders today.
