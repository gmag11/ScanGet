# Proposal

## Why

Change 01 keeps every document (raw captures and editing data) behind a storage abstraction, but
does not decide where. Scans must never live in the app's private storage: it is deleted when the
app is uninstalled or its data cleared, it cannot be backed up or synced with the user's own tools
(Syncthing, Nextcloud, a computer over USB), and a reinstalled app could not find them again. The
library has to be a folder the user can see, and by default an obvious one: `FairScan` at the top of
the phone's storage.

## What Changes

- On first launch (and on the first launch after updating to this version), before any other
  screen, a **setup screen** proposes the folder **`FairScan`** at the root of the user's storage
  (`/storage/emulated/0/FairScan`, shown as "Internal storage › FairScan"), never the app's private
  storage. It offers two ways:
  - **Allow access to create "FairScan"** (recommended): the app asks for the "All files access"
    permission (`MANAGE_EXTERNAL_STORAGE`; `WRITE_EXTERNAL_STORAGE` on Android 8–10), then creates
    the folder if needed and uses it directly.
  - **Choose an existing folder**: the system folder picker gives FairScan access to that folder
    only, with no broad permission. The picker opens in `FairScan` when it exists.
  - There is no option to keep documents in the app: setup cannot be skipped in normal mode.
- If the chosen folder **already exists with content**, the app says so and asks for
  **confirmation** before using it, describing what it found:
  - FairScan documents from a **previous installation** are **reused**: they appear in the library
    and can be edited and exported.
  - **Content that is not FairScan's** is **ignored and never modified**, moved or deleted.
  - The user can confirm or choose another folder.
- New setting **Library folder** to change the location later (same two ways), with the same
  confirmation and the option to **move** the documents to the new folder.
- An unfinished scan from the previous version is saved into the chosen folder after setup.
- Raw captures are hidden from galleries, without hiding the user's own files in that folder.
- If the folder becomes unavailable (permission revoked, folder deleted), the setup screen is shown
  again explaining the problem; the open document is kept and saved once a folder is available.
- The scan-to-PDF flow requested by other apps does not need a folder and never shows the setup.

## Capabilities

### New Capabilities
- `library-folder`: proposing, choosing, confirming, changing and recovering the location of the
  document library, and coexisting with content that is not FairScan's.

### Modified Capabilities

## Impact

- `AndroidManifest.xml`: `MANAGE_EXTERNAL_STORAGE`; `WRITE_EXTERNAL_STORAGE` `maxSdkVersion` raised
  from 28 to 29 with `requestLegacyExternalStorage` for Android 10.
- `FileLibraryStore` from 01 becomes a production store for the all-files-access mode.
- New `data/library/SafLibraryStore.kt` implementing `LibraryStore` over a tree URI.
- New `data/library/FolderInspection.kt`: classifies a folder's content (FairScan documents vs.
  other content).
- New `ui/screens/setup/LibrarySetupScreen.kt` and `Screen.Main.LibrarySetup`.
- `ui/screens/settings/SettingsRepository.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`:
  `library_dir_uri` preference, "Library" section.
- `MainActivity.kt`: all-files-access request, folder picker launcher with initial URI,
  confirmation and move dialogs.
- `MainViewModel.kt`: initial screen depends on the library location.
- `LibraryRepository`: SAF store, moving documents with progress, unavailable state, migration of
  the legacy working document after setup.
- Strings (EN + ES).
- Store listings: Google Play requires a declaration for `MANAGE_EXTERNAL_STORAGE`; if it is
  refused, the permission is removed from the Play build and only the folder picker remains.
