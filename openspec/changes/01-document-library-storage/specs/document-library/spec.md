## ADDED Requirements

### Requirement: Automatic saving
A document SHALL be saved to the library as soon as its first page is added, without any user
action. Every later change to the document (adding, deleting, reordering or rotating pages,
changing a crop, a filter or adjustments, renaming) SHALL be saved automatically.

#### Scenario: First capture creates a document
- **WHEN** the user starts a new document and captures one page
- **THEN** the library contains a new document with that page

#### Scenario: Edits are kept
- **WHEN** the user changes the filter of page 2 and the app is then killed
- **THEN** after restarting the app the document shows page 2 with the new filter

#### Scenario: No empty documents
- **WHEN** the user starts a new document and leaves without capturing any page
- **THEN** no document is added to the library

### Requirement: Raw captures and editing data are kept
For each page, the library SHALL keep the original capture as it was taken (before cropping or
filtering) and the editing data needed to reproduce the page: crop outline, rotation, filter,
adjustments and capture metadata. Processed images SHALL NOT be required to reopen a document.

#### Scenario: Re-crop after reopening
- **WHEN** the user reopens a saved document and opens the crop editor on a page
- **THEN** the full original capture is shown with the saved crop outline, and a different outline
  can be chosen

#### Scenario: Change filter after reopening
- **WHEN** the user reopens a saved document and changes a page from Black and white to Color
- **THEN** the page is rendered in color from the original capture

### Requirement: Reproducible export
Exporting a reopened document SHALL produce the same pages, in the same order, with the same crop,
rotation, filter and adjustments as when it was last edited.

#### Scenario: Export after restart
- **WHEN** a document was exported, the app is restarted, and the same document is exported again
  with the same format and quality
- **THEN** both exports contain the same pages rendered identically

### Requirement: Document name and dates
Each document SHALL have a name, a creation date and a last-modified date. A new document SHALL get
a default name built like the default export file name. The user SHALL be able to rename a document,
and the export file name SHALL default to the document name.

#### Scenario: Default name
- **WHEN** a new document is created
- **THEN** it is named with the default style (for example "Scan 2026-10-12 20.33")

#### Scenario: Export uses the name
- **WHEN** a document named "Invoice March" is exported without typing a file name
- **THEN** the default file name is "Invoice March"

#### Scenario: Modification date
- **WHEN** a page of a document is edited
- **THEN** the document's last-modified date becomes the time of that edit

### Requirement: One working document
Exactly one library document SHALL be open for editing at a time. Starting a new document or opening
another one SHALL keep the previous document in the library unchanged.

#### Scenario: New document keeps the previous one
- **WHEN** a document with 3 pages is open and the user starts a new document
- **THEN** the 3-page document is still in the library and the new document is empty

#### Scenario: Open another document
- **WHEN** the user opens a saved document
- **THEN** it becomes the working document with its pages and editing data

### Requirement: Migration of the existing working document
After updating from a previous version, a non-empty working document SHALL be saved as a library
document as soon as the library is available, with its pages, order and editing data preserved, and
SHALL NOT be lost before that.

#### Scenario: Upgrade with an unfinished scan
- **WHEN** the app is updated while a 4-page document was in progress and the library folder is set
  up
- **THEN** the library contains that document with its 4 pages, and it is the working document

### Requirement: External scans are temporary
A scan started by another application to obtain a PDF SHALL NOT be saved to the library.

#### Scenario: Scan to PDF from another app
- **WHEN** another app asks FairScan for a PDF and the user scans and returns it
- **THEN** the library does not contain that document
