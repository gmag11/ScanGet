# Proposal

## Why

The app is now the owner's own build, called **ScanGet**, not the upstream FairScan one: it is
installed next to (or instead of) the F-Droid and Google Play versions, and today it carries their
name and application id (`org.fairscan.app`) and cannot be updated over them because it is signed
with another key (the
Pixel 8 refused the install with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`). It needs its own name,
identity and to be signed with the owner's key, kept outside the repository.

## What Changes

- The **name** of the app becomes **ScanGet**: the label under the icon and the texts of the app
  that speak of the app by its name (English and Spanish; the other translations do not mention it).
- The **About screen** shows the owner's data: developer "Germán Martín", contact e-mail
  `github@gmartin.net` (also where the support e-mails with the last image go), the repository
  `https://github.com/gmag11/scanget` in place of the website, and a new tagline that stresses that
  everything stays on the device. The license section (GPL v3) and the list of libraries stay.
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
- The APK files are named `ScanGet-<version>-<abi>.apk`.
- Nothing else changes: the intent action `org.fairscan.app.action.SCAN_TO_PDF` (documented in the
  README), the default library folder (`FairScan`, so that existing documents are found again),
  the GPL v3 license and its copyright notices (`LICENSE`, source headers), and the internal names
  (Kotlin package, theme, classes, "FairScan document" in code and specs).

**Assumptions** (cheap to revert): debug builds share the key; the new tagline is "Scan and
organize your documents. Everything stays on your device." (Spanish: "Escanea y organiza tus
documentos. Todo se queda en tu dispositivo."); the repository does not exist yet, so its link will
not open anything until it is published. The About screen no longer names the upstream author: the
original copyright remains in the license and source headers, as the GPL requires.

## Capabilities

### New Capabilities
- `app-packaging`: the identity of the installed app and how builds are signed.

### Modified Capabilities
<!-- None: no existing requirement changes. -->

## Impact

- `app/build.gradle.kts`: `applicationId`, key loading, `signingConfigs` for debug and release,
  APK file names.
- `res/values/strings.xml` and `res/values-es/strings.xml`: `app_name`, the tagline and the texts
  that mention the app (11 and 9 occurrences of "FairScan").
- `ui/screens/about/AboutScreen.kt` (developer, website row) and `Emails.kt` (`EMAIL_ADDRESS`,
  used for the contact and support e-mails); the e-mail subject and the diagnostics report say
  ScanGet. Other translations of the tagline fall back to English until they are translated.
- The FileProvider authority (`${applicationId}.fileprovider` in the manifest and
  `context.packageName` in `FileUris.kt`) follows the id by itself; nothing to edit.
- **Consequence for existing installs:** `net.gmartin.scanget` is a different app for Android: it
  installs next to `org.fairscan.app`, starts with no settings, no downloaded OCR languages and no
  storage permissions, and the user must grant access again. Its library is still the `FairScan`
  folder of the shared storage, so the documents already saved there are found again (spec
  `library-folder`, "Reusing documents of a previous installation").
- README and F-Droid/Play links keep pointing to the upstream app, on purpose. The launcher label
  and the app name in settings change at once, as does the name in the "Open with"/share lists.
