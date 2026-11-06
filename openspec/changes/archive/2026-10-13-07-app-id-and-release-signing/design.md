# Design

## Context

`app/build.gradle.kts` has `namespace = "org.fairscan.app"` and `applicationId = "org.fairscan.app"`.
Signing is optional and only reads the Gradle properties `RELEASE_STORE_FILE`,
`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD` (`hasSigning`); the
release build type uses it, the debug build type uses Android's debug key. The owner's key lives in
`../keys` (outside the repository): `key.properties` (entries `storePassword`, `keyPassword`,
`keyAlias`, `storeFile`, where `storeFile` is a relative name, `keystore.jks`, that sits next to
it). Other files in that folder (`.pem`, base64 copies, an F-Droid folder) are not used here.
The manifest's FileProvider uses `${applicationId}.fileprovider` and `FileUris.kt` builds the same
authority from `context.packageName`, so both follow the id. The only other mention of
`org.fairscan.app` outside Kotlin packages is the public intent action
`org.fairscan.app.action.SCAN_TO_PDF` (README, manifest), which is an action name, not an id.

## Goals / Non-Goals

**Goals:**
- Own application id, installable next to upstream.
- Release and debug builds signed with the owner's key without ever putting it in the repository.

**Non-Goals:**
- Renaming the Kotlin package, the theme or other internal names.
- Changing the intent action (documented external contract), version numbers, F-Droid/Play
  metadata or links, the default library folder name or the translations that do not mention the
  app (they use the `app_name` string or none).
- Migrating data from `org.fairscan.app` (another app for Android; the shared `FairScan` folder
  already carries the documents over).

## Decisions

- **Name:** `app_name` becomes "ScanGet" (it is `translatable="false"`, so one value serves every
  language). The 11 mentions in `values/strings.xml` and the 9 in `values-es/strings.xml` are
  reviewed: texts where the app speaks of itself ("Where should FairScan keep your documents?")
  say ScanGet; texts that credit or link to the project (About, licenses) keep FairScan; "FairScan
  document" as a concept in code, specs and error messages stays. The diagnostics report and the
  e-mail subject that identify the app and its version say ScanGet. `DEFAULT_LIBRARY_FOLDER_NAME`
  stays `FairScan`, which is what lets the new app reuse the documents already saved.
- **`applicationId`.** `namespace` stays, so `R`, `BuildConfig` and every `package` line are
  untouched and the diff is one line.
- **Key lookup order**, evaluated at configuration time, first match wins:
  1. `../keys/key.properties` relative to `rootDir` (`rootProject.file("../keys/key.properties")`);
  2. the four `RELEASE_*` Gradle properties (today's behavior, used by CI/F-Droid);
  3. nothing: release unsigned, debug with the standard key.
- **`storeFile`** in `key.properties` is resolved against the directory of that file; an absolute
  path is accepted as is. A key file that exists but lacks an entry or points to a missing
  keystore fails the configuration with a message that names the entry (never its value).
- **One `signingConfig` "owner"** shared by `release` and `debug` build types when a key was found.
  Rationale: one key, so a debug APK updates a release one and vice versa (the Pixel 8 problem).
  Trade-off: a debug build is then signed with the release key; acceptable for a personal build, and
  it never happens on machines without `../keys`.
- **Secrets stay out of the repository:** nothing under `../keys` is read by anything but this
  Gradle code, the file is outside the git root, and the build prints only which source it used
  ("Signing with ../keys/key.properties"). The existing `.gitignore` does not need an entry because
  the folder is not inside the project.
- **About screen:** the developer name, the e-mail (`EMAIL_ADDRESS`, one constant used for the
  contact and the support e-mails) and the repository URL are changed in `AboutScreen.kt` and
  `Emails.kt`; the website row stays as a link row, now to the repository. The tagline is a string
  (`app_tagline`) in English and Spanish; the other translations keep the old text until they are
  translated, so those would still show the old tagline: the translations of `app_tagline` are
  removed so that they fall back to the new English text. License and libraries are untouched.
- **ABI split APKs** are named `ScanGet-<version>-<abi>.apk` (the one line that builds the name).
  Anything that referred to the old file name (scripts, notes) is updated.

## Risks / Trade-offs

- [Existing install of `org.fairscan.app` stays on the device, with its own data] → expected; the
  user can uninstall it by hand. The new app asks for permissions and OCR languages again, and finds
  the documents in the `FairScan` folder.
- [A wrong password only fails when signing] → the configuration check catches missing entries and
  missing keystore; wrong passwords fail in `assembleRelease` with Gradle's own message.
- [`../keys` is a relative path to something outside the repository] → documented in the spec and
  fallback to the Gradle properties keeps other machines and CI working.
- [Signing debug builds with the owner's key] → see the decision above; reverted by removing the
  `debug` line.
