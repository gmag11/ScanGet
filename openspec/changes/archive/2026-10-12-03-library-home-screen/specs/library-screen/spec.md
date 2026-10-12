## Purpose

Home screen of the app: lists the saved documents with the one opened last on top as a card with quick actions, lets the user open, rename, export, share and delete them, and start a new scan or import.

## ADDED Requirements

### Requirement: Library as the home screen
When the app is launched normally and the library folder is set up, it SHALL open on the library
screen. When it is launched by
another app to scan to PDF, it SHALL open on the camera as before.

#### Scenario: Normal launch
- **WHEN** the user opens FairScan from the launcher
- **THEN** the library screen is shown

#### Scenario: Scan to PDF
- **WHEN** another app starts FairScan to get a PDF
- **THEN** the camera screen is shown and the library is not reachable

### Requirement: Document list
The library SHALL list every saved document. Each item SHALL show a thumbnail of the first page, the
document name, the last-modified date and time, and the number of pages. Documents other than the
recent one SHALL be ordered by last-modified date, newest first.

#### Scenario: Items shown
- **WHEN** the library contains three documents and none has been opened yet
- **THEN** three items are shown, ordered by last-modified date, newest first, with no Recent section

#### Scenario: Modification time updated
- **WHEN** the user edits a document and returns to the library
- **THEN** that document shows the new modification time

### Requirement: Recent document first
The most recently opened document (opened from the library, or created by scanning or importing)
SHALL be shown first, under a "Recent" heading, separated from the other documents, which follow
under "All documents". The recent item SHALL offer the quick actions Add pages, Share and Export. A
document SHALL appear only once in the screen. If the recent document is deleted or no document has
been opened, the Recent section SHALL not be shown.

#### Scenario: Last opened is first
- **WHEN** the user opens the oldest document, goes back to the library
- **THEN** that document is shown under "Recent", above "All documents", and not repeated in the list

#### Scenario: New scan becomes recent
- **WHEN** the user scans a new document with two pages and returns to the library
- **THEN** the new document is shown under "Recent"

#### Scenario: Add pages
- **WHEN** the user presses Add pages on the recent document
- **THEN** the camera opens to add pages to that document

#### Scenario: Share
- **WHEN** the user presses Share on the recent document
- **THEN** the whole document is shared in the export format and quality chosen in the settings,
  named after the document

#### Scenario: Export
- **WHEN** the user presses Export on the recent document
- **THEN** the export screen opens for that document

#### Scenario: Recent document deleted
- **WHEN** the user deletes the recent document
- **THEN** the Recent section is no longer shown

#### Scenario: Empty library
- **WHEN** the library has no documents
- **THEN** a message explains that scans will appear here, and the Scan action is available

### Requirement: Starting a new document
The library SHALL offer a Scan action that opens the camera on a new, empty document and an Import
action that imports images into a new document.

#### Scenario: Scan
- **WHEN** the user presses Scan
- **THEN** the camera opens with an empty document, and the previously open document is unchanged in
  the library

#### Scenario: Import
- **WHEN** the user presses Import and picks two images
- **THEN** a new document with those two pages is created

### Requirement: Opening a document
Tapping a document SHALL open it for editing, showing its pages with their saved crop and filters.

#### Scenario: Open
- **WHEN** the user taps a 5-page document
- **THEN** the document opens on its first page with 5 pages

### Requirement: Rename
Each document SHALL offer a Rename action. The new name SHALL be shown in the library and used as
the default export file name. An empty name SHALL NOT be accepted.

#### Scenario: Rename a document
- **WHEN** the user renames a document to "Car insurance"
- **THEN** the library shows "Car insurance" for it

#### Scenario: Empty name
- **WHEN** the user clears the name in the rename dialog
- **THEN** the confirm button is disabled

### Requirement: Delete
Each document SHALL offer a Delete action that asks for confirmation and then removes the document,
including its original captures, from the library. Exported files SHALL NOT be deleted.

#### Scenario: Confirmed delete
- **WHEN** the user deletes a document and confirms
- **THEN** the document disappears from the library and its storage is freed

#### Scenario: Cancelled delete
- **WHEN** the user cancels the confirmation
- **THEN** the document is kept

#### Scenario: Delete the open document
- **WHEN** the deleted document is the one currently open
- **THEN** it is removed and the next Scan starts with an empty document

### Requirement: Export from the library
Each document SHALL offer an Export action that opens the export screen for that document.

#### Scenario: Export
- **WHEN** the user chooses Export on a document
- **THEN** the export screen opens with that document's pages

### Requirement: Navigation between library, editor and camera
Back from the document editor SHALL return to the library. Back from the camera SHALL return to the
document editor if the open document has pages, and to the library otherwise. Back from the export
screen SHALL return to the document editor. The export screen's "New document" action SHALL open the
camera on a new document, keeping the exported document in the library.

#### Scenario: Back from the editor
- **WHEN** the user presses back in the document editor
- **THEN** the library is shown

#### Scenario: Back from the camera without pages
- **WHEN** the user presses Scan in the library and then back without capturing
- **THEN** the library is shown and no document was added

#### Scenario: Back from the camera with pages
- **WHEN** the user captures two pages and presses back
- **THEN** the document editor is shown with those two pages

#### Scenario: New document after export
- **WHEN** the user presses "New document" on the export screen
- **THEN** the camera opens on an empty document and the exported document is still in the library
