## MODIFIED Requirements

### Requirement: Hidden from galleries
The raw captures, processed images and OCR data stored by FairScan SHALL not be indexed by media
galleries. The user's own files in the library folder SHALL remain visible to galleries.

#### Scenario: Gallery
- **WHEN** the user opens the phone's gallery app after scanning into a folder that also holds
  their photos
- **THEN** the captures and processed pages are not shown and the user's photos are

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
