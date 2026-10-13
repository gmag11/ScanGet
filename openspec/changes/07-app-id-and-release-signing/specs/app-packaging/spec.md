## ADDED Requirements

### Requirement: Application id
The app SHALL be installed with the application id `net.gmartin.scanget`, so that it can be
installed next to the upstream FairScan app (`org.fairscan.app`) without replacing it. The name
shown to the user and the public intent action SHALL NOT change.

#### Scenario: Installed next to upstream
- **WHEN** the app is installed on a device that already has `org.fairscan.app`
- **THEN** both apps are installed and each keeps its own data

#### Scenario: Share files of the app
- **WHEN** the app shares an exported file
- **THEN** the file is served by a provider whose authority is `net.gmartin.scanget.fileprovider`

#### Scenario: Documents of the same folder
- **WHEN** the app is first opened and the library folder it is given already holds FairScan
  documents saved by another installation
- **THEN** those documents are used, as the library folder rules say

### Requirement: Signing key kept outside the repository
Release builds SHALL be signed with the owner's key described by `../keys/key.properties`, a file
outside the repository whose `storeFile` is resolved relative to its own directory. The key,
keystore and passwords SHALL NOT be stored in the repository nor printed by the build.

#### Scenario: Signed with the key file
- **WHEN** `../keys/key.properties` exists and `assembleRelease` is run
- **THEN** the APKs are signed with the key it describes

#### Scenario: Fallback to Gradle properties
- **WHEN** there is no `../keys/key.properties` but the four `RELEASE_*` properties are set
- **THEN** the release APKs are signed with that key

#### Scenario: No key available
- **WHEN** there is neither the key file nor the properties
- **THEN** the build succeeds and the release APKs are unsigned

#### Scenario: Secrets not shown
- **WHEN** the build uses the key
- **THEN** it says which source it used and prints no password, alias or keystore content

#### Scenario: Incomplete key file
- **WHEN** `../keys/key.properties` exists but lacks a value or its keystore
- **THEN** the build fails saying which entry is missing, without showing any secret

### Requirement: Debug builds use the same key
When the signing key is available, debug builds SHALL be signed with it, so that a debug APK and a
release APK of the app can replace one another without uninstalling. Without a key they SHALL use
the standard debug key.

#### Scenario: Debug over release
- **WHEN** a release APK is installed and a debug APK built with the key is installed over it
- **THEN** the installation updates the app and keeps its data

#### Scenario: Clean checkout
- **WHEN** the project is built on a machine without the key
- **THEN** debug builds work with the standard debug key
