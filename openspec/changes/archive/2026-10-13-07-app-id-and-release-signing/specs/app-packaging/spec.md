## ADDED Requirements

### Requirement: App name
The app SHALL call itself ScanGet: in the launcher, in the system lists of apps and in its own
texts that speak of the app by its name. The default library folder name and the public intent
action SHALL NOT change.

#### Scenario: Launcher
- **WHEN** the app is installed
- **THEN** its icon is labeled "ScanGet"

#### Scenario: Texts of the app
- **WHEN** the library setup screen asks where the app should keep the documents
- **THEN** it speaks of ScanGet, in English and in Spanish

#### Scenario: Library folder
- **WHEN** the library folder is offered for the first time
- **THEN** its default name is still `FairScan`, so that documents already saved there are found

### Requirement: About screen data
The About screen SHALL show the app as ScanGet with a tagline that says everything stays on the
device, the developer "Germán Martín", the contact e-mail `github@gmartin.net` and the repository
`https://github.com/gmag11/scanget`. The e-mails for contact and support (with the last image)
SHALL be addressed to that e-mail and carry the app name and version in their subject. The license
section (GNU GPL v3) and the list of libraries SHALL stay.

#### Scenario: Data shown
- **WHEN** the user opens the About screen
- **THEN** it shows ScanGet, the tagline, the developer, the e-mail and the repository link, in
  English and in Spanish

#### Scenario: Contact
- **WHEN** the user taps the e-mail
- **THEN** a new e-mail to `github@gmartin.net` is started

#### Scenario: Support e-mail
- **WHEN** the user sends the last image to support
- **THEN** the e-mail is addressed to `github@gmartin.net` and its subject starts with "ScanGet" and
  the version

#### Scenario: License
- **WHEN** the user opens the license
- **THEN** the full GPL v3 text is shown, as before

### Requirement: Application id
The app SHALL be installed with the application id `net.gmartin.scanget`, so that it can be
installed next to the upstream FairScan app (`org.fairscan.app`) without replacing it.

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
