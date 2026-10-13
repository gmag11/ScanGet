# Tasks

## 1. Application id

- [ ] 1.1 Set `applicationId = "net.gmartin.scanget"` in `app/build.gradle.kts`, keeping `namespace`; verify that the merged manifest of a debug build has package `net.gmartin.scanget` and the provider authority `net.gmartin.scanget.fileprovider`, and that the unit tests still compile and pass

- [ ] 1.2 Rename the app to ScanGet: `app_name`, the texts of `values/strings.xml` and `values-es/strings.xml` where the app speaks of itself (keeping the credits to the upstream project and "FairScan document" as a concept), the e-mail subject and the diagnostics report, and the APK file names; verify by listing every remaining "FairScan" in resources and code, and on a device by the launcher label and the library setup screen in English and Spanish

## 2. Signing

- [ ] 2.1 Read `../keys/key.properties` (entries and `storeFile` relative to its directory) with the fallback to the `RELEASE_*` properties, fail with the name of a missing entry or keystore, and print only the source used; verify with `./gradlew :app:assembleRelease` signed by the key file (check with `apksigner verify --print-certs`), with only the `RELEASE_*` properties, and with neither (unsigned)
- [ ] 2.2 Sign the debug build type with the same key when one was found; verify that a debug APK installs over a release APK on a device without uninstalling (data kept) and that a build without `../keys` still uses the standard debug key

## 3. Verification

- [ ] 3.1 Verify on a device that the app installs next to `org.fairscan.app` as "ScanGet", asks for the library folder and finds the documents already in `FairScan`, and that sharing a file works (provider authority)
- [ ] 3.2 Verify the module compiles and all unit tests pass (`./gradlew :app:assembleDebug :app:testDebugUnitTest`)
