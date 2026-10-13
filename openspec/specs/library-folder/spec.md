# library-folder Specification

## Purpose

Lets the user choose where the document library is kept, a visible folder of their storage (`FairScan` by default) and never the app's private storage, reuses the documents a previous installation left there, and leaves any other content of that folder untouched.

## Requirements

### Requirement: Library outside the app's private storage
Documents SHALL be stored only in a folder of the user's shared storage, never in the app's private
storage: either the `FairScan` folder at the root of the user's storage, accessed with the "All files
access" permission, or a folder chosen through the system folder picker. Data the app keeps
privately SHALL only be caches that can be rebuilt from the library folder.

#### Scenario: Uninstall
- **WHEN** the user uninstalls FairScan
- **THEN** every saved document remains in the library folder

### Requirement: Folder setup at first launch
When the app is launched normally and no library folder is set up, it SHALL show a setup screen
before any other screen, proposing the folder `FairScan` at the root of the user's storage. Setup
SHALL NOT be skippable. Once a folder is set up, the setup screen SHALL NOT be shown again unless the
folder becomes unavailable.

#### Scenario: First launch
- **WHEN** the user opens FairScan for the first time
- **THEN** the setup screen proposes "Internal storage › FairScan"

#### Scenario: Update from a previous version
- **WHEN** the app is updated from a version without a library and opened
- **THEN** the setup screen is shown first, and an unfinished scan is kept and saved into the chosen
  folder

#### Scenario: Scan to PDF
- **WHEN** another app starts FairScan to get a PDF and no folder is set up
- **THEN** the camera is shown without the setup screen

### Requirement: Two ways to set up the folder
The setup screen SHALL offer allowing "All files access", after which the app creates the `FairScan`
folder if it does not exist and uses it, and choosing an existing folder with the system folder
picker, which gives the app access to that folder only.

#### Scenario: All files access granted, folder missing
- **WHEN** the user chooses to allow access, grants "All files access" and no `FairScan` folder exists
- **THEN** the app creates `FairScan` at the root of the storage and uses it as the library folder

#### Scenario: All files access granted, folder exists
- **WHEN** the user grants "All files access" and a `FairScan` folder already exists at the root
- **THEN** the app uses that folder, after the confirmation if it is not empty

#### Scenario: All files access denied
- **WHEN** the user returns from the system permission screen without granting access
- **THEN** the setup screen is still shown and explains that an existing folder can be chosen instead

#### Scenario: Existing folder
- **WHEN** the user chooses an existing folder and picks it in the system picker
- **THEN** that folder is used, after the confirmation if it is not empty, without "All files access"

#### Scenario: Picker opens in FairScan
- **WHEN** a `FairScan` folder exists at the root and the user chooses an existing folder
- **THEN** the system folder picker opens inside `FairScan`

#### Scenario: Empty folder
- **WHEN** the selected folder is empty
- **THEN** it becomes the library folder without further questions

#### Scenario: Picker cancelled
- **WHEN** the user cancels the picker
- **THEN** the setup screen is still shown

#### Scenario: Permission not offered
- **WHEN** the app is built without the "All files access" permission
- **THEN** the setup screen only offers choosing an existing folder

### Requirement: Confirmation for a non-empty folder
Whenever the user picks a library folder that is not empty, at setup or later in the settings, the
app SHALL tell the user that the folder already exists with content, state how many FairScan
documents it contains and whether it contains other content, and ask for confirmation. Nothing SHALL
be written to the folder before the user confirms. The user SHALL be able to choose another folder
instead.

#### Scenario: Folder with a previous installation
- **WHEN** the user picks a folder holding 12 FairScan documents
- **THEN** the app says the folder exists and contains 12 FairScan documents that will be added to
  the library, and asks for confirmation

#### Scenario: Folder with other content
- **WHEN** the user picks a folder holding only photos and PDFs not made by FairScan
- **THEN** the app says the folder exists and contains files that FairScan will not use or modify,
  and asks for confirmation

#### Scenario: Mixed content
- **WHEN** the folder holds FairScan documents and other files
- **THEN** the message mentions both, and that the other files will be left untouched

#### Scenario: Choose another folder
- **WHEN** the user chooses another folder in the confirmation
- **THEN** the picker opens again and the first folder is not used nor modified

### Requirement: Reusing documents of a previous installation
Once a folder is confirmed, every FairScan document in it SHALL be part of the library and be
editable and exportable, including documents created by another installation or another device.
Incomplete or unreadable FairScan entries SHALL be ignored without affecting the others.

#### Scenario: Reinstall
- **WHEN** the app is reinstalled and the `FairScan` folder used before is confirmed
- **THEN** the documents saved there before are in the library and can be edited and exported

#### Scenario: Corrupted entry
- **WHEN** a document entry in the folder has an unreadable description file
- **THEN** that entry is not part of the library, it is not modified, and the other documents are

### Requirement: Other content is left untouched
Content of the library folder that is not a FairScan document SHALL never be listed, modified,
renamed, moved or deleted by the app, including when documents are deleted or moved. Inside a
FairScan document, only the files the app wrote (document description, cover, captures, processed
images and the list that identifies them, OCR data) SHALL be changed, renamed or deleted. New documents SHALL NOT overwrite existing files or
folders.

#### Scenario: Deleting a document
- **WHEN** a FairScan document is deleted from a folder that also holds the user's files
- **THEN** only that document's files are removed and the user's files are unchanged

#### Scenario: Moving the library
- **WHEN** the library is moved from that folder to another one
- **THEN** only FairScan documents are moved, with their processed images and OCR data, and the
  user's files stay where they were

#### Scenario: Name clash
- **WHEN** a new document would use the name of an existing folder that is not a FairScan document
- **THEN** the document is saved under another name and the existing folder is unchanged

#### Scenario: Foreign file inside a document folder
- **WHEN** the user has put a file of their own in a document folder and the document is edited or
  deleted
- **THEN** that file is unchanged

#### Scenario: Foreign file with the name of a processed image
- **WHEN** the user's own file in the processed images folder is named like the image the app wants
  to write for a page
- **THEN** the user's file is unchanged and the page keeps another name

### Requirement: Library folder setting
The settings SHALL show the current library folder and let the user change it with the same two ways
as the setup screen, with the same confirmation for non-empty folders.

#### Scenario: Current location
- **WHEN** the user opens the settings
- **THEN** the library folder name is shown

#### Scenario: Change the folder
- **WHEN** the user picks and confirms another folder
- **THEN** new documents are saved there

### Requirement: Moving existing documents
When the library folder changes and the current folder contains FairScan documents, the app SHALL
ask whether to move them to the new folder or to leave them where they are. Moving SHALL show
progress and SHALL remove a document from the old folder only after it is completely copied. If a
document with the same identity exists in both folders, the most recently modified one SHALL be
kept.

#### Scenario: Move
- **WHEN** the user confirms a new folder and chooses Move
- **THEN** the library contains the moved documents plus those found in the new folder, all stored
  in the new folder

#### Scenario: Don't move
- **WHEN** the user chooses not to move
- **THEN** the library contains only the documents of the new folder, and the old documents are kept
  in the old folder

#### Scenario: Interrupted move
- **WHEN** the app is killed while moving documents
- **THEN** no document is lost: each one is complete in the old or the new folder

### Requirement: Hidden from galleries
The raw captures, processed images and OCR data stored by FairScan SHALL not be indexed by media
galleries. The user's own files in the library folder SHALL remain visible to galleries.

#### Scenario: Gallery
- **WHEN** the user opens the phone's gallery app after scanning into a folder that also holds
  their photos
- **THEN** the captures and processed pages are not shown and the user's photos are

### Requirement: Unavailable folder
If the library folder can no longer be accessed, the app SHALL show the setup screen explaining that
the folder is not accessible, so that it can be chosen again or replaced. The open document SHALL be
kept and SHALL be saved to the library as soon as a folder is accessible again.

#### Scenario: Permission revoked
- **WHEN** the access to the library folder is lost (folder access or "All files access" revoked) and
  the app is opened
- **THEN** the setup screen says the folder "FairScan" is no longer accessible and offers to choose
  it again

#### Scenario: Edits kept
- **WHEN** the user had unsaved edits when the folder became unavailable and then chooses the folder
  again
- **THEN** the document in the folder contains those edits
