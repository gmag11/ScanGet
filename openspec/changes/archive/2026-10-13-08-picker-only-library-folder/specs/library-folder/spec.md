## MODIFIED Requirements

### Requirement: Library outside the app's private storage
Documents SHALL be stored only in a folder of the user's shared storage, never in the app's private
storage: a folder chosen or created through the system folder picker, to which the app has access
and to nothing else. The app SHALL NOT ask for "All files access", nor for any permission to keep
the library.
Data the app keeps privately SHALL only be caches that can be rebuilt from the library folder.

#### Scenario: Uninstall
- **WHEN** the user uninstalls the app
- **THEN** every saved document remains in the library folder

#### Scenario: No broad permission
- **WHEN** the user sets up the library and uses the app
- **THEN** no "All files access" or other permission is requested for the library, and the app can only reach the
  folder the user chose

### Requirement: Folder setup at first launch
When the app is launched normally and no library folder is set up, it SHALL show a setup screen
before any other screen, asking the user to choose an existing folder or create a new one with the
system folder picker. No folder SHALL be proposed or chosen beforehand. Setup SHALL NOT be
skippable. Once a folder is set up, the setup screen SHALL NOT be shown again unless the folder
becomes unavailable.

#### Scenario: First launch
- **WHEN** the user opens the app for the first time
- **THEN** the setup screen explains that the user chooses or creates a folder for the documents and
  that the app only gets access to that folder, and offers a button to open the folder picker

#### Scenario: Update from a previous version
- **WHEN** the app is updated from a version without a library and opened
- **THEN** the setup screen is shown first, and an unfinished scan is kept and saved into the chosen
  folder

#### Scenario: Scan to PDF
- **WHEN** another app starts the app to get a PDF and no folder is set up
- **THEN** the camera is shown without the setup screen

### Requirement: Reusing documents of a previous installation
Once a folder is confirmed, every FairScan document in it SHALL be part of the library and be
editable and exportable, including documents created by another installation or another device.
Incomplete or unreadable FairScan entries SHALL be ignored without affecting the others.

#### Scenario: Reinstall
- **WHEN** the app is reinstalled and the folder used before is picked and confirmed
- **THEN** the documents saved there before are in the library and can be edited and exported

#### Scenario: Corrupted entry
- **WHEN** a document entry in the folder has an unreadable description file
- **THEN** that entry is not part of the library, it is not modified, and the other documents are

### Requirement: Library folder setting
The settings SHALL show the current library folder and let the user change it with the system folder
picker, with the same confirmation for non-empty folders as the setup screen.

#### Scenario: Current location
- **WHEN** the user opens the settings
- **THEN** the library folder name is shown

#### Scenario: Change the folder
- **WHEN** the user picks and confirms another folder
- **THEN** new documents are saved there

### Requirement: Unavailable folder
If the library folder can no longer be accessed, the app SHALL show the setup screen explaining that
the folder is not accessible, so that it can be chosen again or replaced. The open document SHALL be
kept and SHALL be saved to the library as soon as a folder is accessible again.

#### Scenario: Permission revoked
- **WHEN** the access to the library folder is lost (access to the folder revoked, or the folder
  removed) and the app is opened
- **THEN** the setup screen says the folder is no longer accessible and offers to choose it again

#### Scenario: Edits kept
- **WHEN** the user had unsaved edits when the folder became unavailable and then chooses the folder
  again
- **THEN** the document in the folder contains those edits

#### Scenario: Library saved by an earlier build as a path
- **WHEN** the library was saved by an earlier build as a path of the storage, which needed "All
  files access"
- **THEN** the setup screen is shown, and choosing the same folder again brings its documents back

## REMOVED Requirements

### Requirement: Two ways to set up the folder
**Reason**: The "All files access" way, which created the `FairScan` folder at the root of the
storage, is removed together with the default folder.
**Migration**: Choose or create a folder with the system folder picker (see "Folder chosen with the
picker").

## ADDED Requirements

### Requirement: Folder chosen with the picker
The setup screen SHALL let the user open the system folder picker, in which the user can choose an
existing folder or create a new one. The picker SHALL open at the root of the storage. The chosen
folder SHALL be used after the confirmation if it is not empty, and the app SHALL only gain access
to that folder.

#### Scenario: Create a folder
- **WHEN** the user opens the picker, creates a folder with the picker's own button and confirms it
- **THEN** that folder is used as the library folder

#### Scenario: Existing folder
- **WHEN** the user picks an existing folder
- **THEN** that folder is used, after the confirmation if it is not empty

#### Scenario: Empty folder
- **WHEN** the selected folder is empty
- **THEN** it becomes the library folder without further questions

#### Scenario: Picker cancelled
- **WHEN** the user cancels the picker
- **THEN** the setup screen is still shown

#### Scenario: Folders the system does not allow
- **WHEN** the user is in the root of the storage or in a protected folder such as Downloads
- **THEN** the setup screen has told the user to choose or create a subfolder, as the picker does
  not offer those
