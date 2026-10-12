# Design

## Context

Change 01 introduced `LibraryStore`, `FileLibraryStore` (tests and debug builds only), the entry
layout (`<docId>/.nomedia`, `document.json`, `cover.jpg`, `sources/<pageId>.jpg`), the rule that an
entry is FairScan's only if it holds a valid `document.json` whose id matches the directory name,
deletes limited to FairScan's own files, and the internal working copy that is only a cache. 01 and
this change ship together. The export folder setting already uses
`ActivityResultContracts.OpenDocumentTree`, `takePersistableUriPermission` and a
`DirectorySettingItem` row, and shows "Folder access lost" in error color. `minSdk` is 26, so
`DocumentsContract.EXTRA_INITIAL_URI` is available. The initial screen today is the camera, or
`ResumeScan` when a document is in progress (the library screen comes in 03). See proposal.md for
motivation.

## Goals / Non-Goals

**Goals:**
- Documents only in a user-visible folder, `FairScan` at the storage root by default, created by
  the app when the user grants all files access.
- A reinstalled app finds and reuses its documents; a folder with other content is used safely,
  never touching that content, and only after the user confirms.
- Safe move between folders, and recovery when the folder disappears.

**Non-Goals:**
- Any private-storage fallback for documents.
- Live watching of the folder for external changes while the app is open.
- Merging conflicting edits of the same document made on two devices (last write wins).

## Platform constraints

- **Creating `/storage/emulated/0/FairScan`** requires broad storage access: `MANAGE_EXTERNAL_STORAGE`
  ("All files access") on Android 11+, `WRITE_EXTERNAL_STORAGE` on Android 8–10 (the manifest's
  `maxSdkVersion="28"` is raised to 29, with `requestLegacyExternalStorage="true"` for Android 10).
  "All files access" is not a runtime dialog: the app opens
  `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` for its package and checks
  `Environment.isExternalStorageManager()` when the user comes back. This is why the setup screen
  explains the purpose first (Google Play also requires that disclosure).
- **Google Play** only accepts `MANAGE_EXTERNAL_STORAGE` after a policy declaration. If it is refused,
  the permission is removed from the Play build (manifest `tools:node="remove"` in a build variant)
  and `BuildConfig.ALL_FILES_ACCESS_AVAILABLE = false` hides that option; F-Droid keeps it.
- **Without that permission** the folder comes from the **system folder picker** (SAF), which gives
  access to the chosen folder only. The picker is pre-positioned with `EXTRA_INITIAL_URI`:
  `content://com.android.externalstorage.documents/document/primary%3AFairScan` when
  `File(Environment.getExternalStorageDirectory(), "FairScan").isDirectory` (directories of shared
  storage are visible without permission), the storage root otherwise. The root itself cannot be
  picked on Android 11+, but the picker allows creating a folder.
- **MediaStore** (Downloads/Documents collections) was rejected: after a reinstall the app no longer
  owns the files created by the previous installation and cannot read them.

## Screen design

Setup screen (normal mode, initial screen while no folder is set up; centered content on
`surfaceVariant` 40% like the old resume screen, overflow menu only):

```
┌────────────────────────────────────┐
│                                  ⋮ │
│               📁                   │  outlined folder icon, 64dp, primary
│                                    │
│     Where should FairScan keep     │  headlineSmall
│          your documents?           │
│                                    │
│   FairScan keeps the original      │  bodyMedium, onSurfaceVariant
│   photos and your edits, so you    │
│   can edit and export them again   │
│   at any time. They stay on your   │
│   phone even if FairScan is        │
│   uninstalled.                     │
│                                    │
│  ┌──────────────────────────────┐  │  Card, outlined
│  │ 📁 Internal storage › FairScan│  │  titleMedium
│  │ Recommended                  │  │  labelSmall, primary
│  └──────────────────────────────┘  │
│   To create it, FairScan will ask  │  bodySmall, onSurfaceVariant
│   for access to your files. It     │  (permission disclosure; hidden
│   only uses its own folder.        │   in builds without the permission)
│                                    │
│   [ ✓ Use the FairScan folder ]    │  MainActionButton
│        Choose another folder       │  TextButton
└────────────────────────────────────┘
```

- **Use the FairScan folder** → opens the system "All files access" screen for FairScan; on return
  with the permission granted, the folder is created if missing (or inspected if it exists) and
  used. Returned without the permission → a line under the card says "Without this permission,
  choose an existing folder instead". When the permission is already granted (reinstall on
  Android 8–10, or granted earlier) no system screen is shown. Hidden when the build has no
  all-files permission; then "Choose an existing folder" becomes the main button.
- **Choose another folder** → system folder picker, opened inside `FairScan` if it exists.

Unavailable mode (same screen, different header): "⚠ FairScan can no longer open the folder
"FairScan". Choose it again or pick another one. Your open document is kept." The card shows the
last folder name.

Confirmation when the picked folder is not empty (`AlertDialog` built from the inspection result,
only the applicable lines shown):

```
┌────────────────────────────────────┐
│ This folder already exists         │
│                                    │
│ "FairScan" is not empty.           │
│                                    │
│ ✓ 12 FairScan documents, probably  │  if fairScanDocuments > 0
│   from a previous installation.    │
│   They will be added to your       │
│   library.                         │
│                                    │
│ • 8 other files and folders. They  │  if otherItems > 0
│   will be ignored and not modified.│
│                                    │
│ Use this folder?                   │
│                                    │
│  [Choose another] [Use this folder]│
└────────────────────────────────────┘
```

Move dialog (settings only, when the current folder has documents): "Move your documents? 5
documents are in "FairScan". Move them to "Scans"? [Don't move] [Move]"; then a non-dismissable
progress dialog ("Moving 3 of 5…").

Settings, new section above "Export":

```
│ Library                            │
│   📁 Library folder                │
│      FairScan                      │  "Folder access lost" in red
```

No "Reset to default" button.

## Decisions

- **Location state**: preferences `library_location_kind` (`direct` | `saf`) and `library_location`
  (absolute path or tree URI; null = not set up). `LibraryRepository` holds
  `StateFlow<LibraryLocation>`: `NotSetUp`, `Direct(path)` (backed by `FileLibraryStore`, all files
  access), `Saf(uri, name)` (backed by `SafLibraryStore`), `Unavailable(previous)`. Availability:
  for `Direct`, `isExternalStorageManager()` (or `WRITE_EXTERNAL_STORAGE` on 8–10) + the directory
  exists; for `Saf`, persisted permission present + root query succeeds. Checked at launch, when the
  app returns to the foreground and before each autosave.
- **`FileLibraryStore`** (from 01) is the production store in `Direct` mode; it is also faster than
  SAF since it uses plain `File` I/O.
- **Initial screen** in normal mode: `LibrarySetup` while `NotSetUp` or `Unavailable`, else the
  usual first screen (camera / resume in this change, library from 03). If the folder becomes
  unavailable while editing, editing continues and the setup screen is shown the next time the app
  goes to its first screen.
- **Folder inspection** (`inspectFolder(store): FolderInspection`) lists the root once and
  classifies each child: `fairScanDocuments` (entries satisfying the 01 recognition rule) and
  `otherItems` (everything else, including unreadable FairScan-looking entries). Read-only. Empty →
  no dialog. Otherwise the confirmation is shown, and nothing is written before "Use this folder".
  "Choose another" relaunches the picker and releases the rejected folder's permission
  (`releasePersistableUriPermission`).
- **`SafLibraryStore(treeUri)`** implements `LibraryStore` with `DocumentsContract`
  (`buildChildDocumentsUriUsingTree` queries, `createDocument`, `openOutputStream("wt")`,
  `renameDocument`, `deleteDocument`). JSON writes go to `document.json.tmp` then rename (delete
  old first where rename-over is unsupported); readers fall back to `.tmp` if `document.json` is
  missing. Sources are written once and never rewritten. It applies the 01 rules: only recognized
  entries are listed; deletes remove only FairScan's own files, then the directory if empty; a new
  id that names an existing child is regenerated; the display name returned by `createDocument` is
  checked so the provider never silently renames into a clash.
- **Listing performance**: one children query on the root plus one read of `document.json` per
  entry. The index (id → name, dates, page count) is cached in memory and in
  `filesDir/library-index.json` (a rebuildable cache), refreshed when the library is shown.
- **Move**: only recognized entries. Copy sources, cover, `.nomedia`, then JSON last; verify the
  JSON reads back; then delete the entry from the old folder with the own-files-only delete. An
  entry exists only once its JSON exists, so an interruption leaves at worst a JSON-less directory
  in the target (ignored; reused only if it carries our id on retry) and the original intact. Same
  id in both folders: newer `updatedAt` wins, the other copy is left in place.
- **Legacy working document**: kept untouched in the working directory until setup completes, then
  saved into the chosen folder by the 01 migration.
- **Debug builds**: entries in the 01 debug `FileLibraryStore` directory are copied into the chosen
  folder after setup, then the directory is deleted.
- **Unavailable folder**: autosave keeps the internal working copy and marks it dirty; when the
  folder is available again, the dirty working document is written.
- **External mode** (scan to PDF) never reads the location and never shows the setup screen.

## Risks / Trade-offs

- [Users hesitate to grant "All files access"] → The setup screen explains why; the folder picker is
  always available as an alternative with access to one folder only.
- [Google Play refuses the permission declaration] → Build without it; the picker path covers
  everything, only the automatic creation of `FairScan` is lost.
- [A user confirms a folder full of unrelated files] → Allowed after an explicit warning; FairScan
  only adds its own directories and never touches the rest.
- [SAF I/O is much slower than `File`] → Only the canonical copy is on SAF; editing uses the
  internal working copy; autosave is debounced and writes only changed files.
- [Cloud-backed providers may not support rename or may be offline] → Fallback write strategy for
  JSON; errors are logged and the document stays dirty for the next attempt.
- [User deletes files of an entry by hand] → Entries without a valid JSON are ignored; missing
  sources make that page drop from the document on open, the other pages still load.
