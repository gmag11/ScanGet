# Proposal

## Why

The app is now the owner's own build, not the upstream FairScan one: it is installed next to (or
instead of) the F-Droid and Google Play versions, and today it carries their application id
(`org.fairscan.app`) and cannot be updated over them because it is signed with another key (the
Pixel 8 refused the install with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`). It needs its own identity
and to be signed with the owner's key, kept outside the repository.

## What Changes

- The **application id** becomes `net.gmartin.scanget`. The Kotlin package (`namespace`,
  `org.fairscan.app`) is **not** renamed: it is invisible to users and moving every source file
  would be a huge, risk-bearing diff for no benefit.
- The Gradle build reads the **signing key** from `../keys/key.properties` (outside the repository,
  next to the keystore it names) when that file exists, and falls back to the existing
  `RELEASE_*` Gradle properties otherwise. Release builds are signed with it.
- **Debug builds are signed with the same key** when it is available, so that a debug APK can update
  a release one (and the other way round) without uninstalling and losing the app's data. Without
  the key file, debug builds keep the standard debug key (a clone of the repository still builds).
- The key, the keystore and their passwords are never copied into the repository, the build
  output or logs; the build says which source it used without printing secrets.
- Nothing else changes: the app name shown to the user (`FairScan`), the intent action
  `org.fairscan.app.action.SCAN_TO_PDF` (documented in the README) and the library folder.

**Assumptions** (cheap to revert): "name" means the application id only, not the label shown under
the icon; debug builds share the key.

## Capabilities

### New Capabilities
- `app-packaging`: the identity of the installed app and how builds are signed.

### Modified Capabilities
<!-- None: no existing requirement changes. -->

## Impact

- `app/build.gradle.kts`: `applicationId`, key loading, `signingConfigs` for debug and release.
- The FileProvider authority (`${applicationId}.fileprovider` in the manifest and
  `context.packageName` in `FileUris.kt`) follows the id by itself; nothing to edit.
- **Consequence for existing installs:** `net.gmartin.scanget` is a different app for Android: it
  installs next to `org.fairscan.app`, starts with no settings, no downloaded OCR languages and no
  storage permissions, and the user must grant access again. Its library is still the `FairScan`
  folder of the shared storage, so the documents already saved there are found again (spec
  `library-folder`, "Reusing documents of a previous installation").
- README and F-Droid/Play links keep pointing to the upstream app, on purpose.
