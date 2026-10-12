## Purpose

Shows the pages of the open document as a numbered grid, from where a page is picked to be edited, the pages are put in order, and the document is renamed, extended, exported or deleted.

## ADDED Requirements

### Requirement: Page grid
The document overview SHALL show every page of the document as a processed thumbnail (with its
crop, rotation, filter and adjustments applied), in document order, in a grid of at least two
columns, with the page number on each thumbnail.

#### Scenario: Grid shown
- **WHEN** the user opens a 7-page document
- **THEN** seven numbered thumbnails are shown in document order

#### Scenario: Thumbnails follow edits
- **WHEN** the user changes the filter of page 3 in the page editor and goes back
- **THEN** the thumbnail of page 3 shows the new filter

### Requirement: Opening a page
Tapping a thumbnail SHALL open the page editor on that page. Going back from the page editor SHALL
return to the overview.

#### Scenario: Edit a page
- **WHEN** the user taps page 4
- **THEN** the page editor opens showing page 4

#### Scenario: Back to the grid
- **WHEN** the user presses back in the page editor
- **THEN** the overview is shown

### Requirement: Reordering in the grid
A long press followed by a drag on a thumbnail SHALL move the page to the drop position. The new
order SHALL be saved.

#### Scenario: Move a page
- **WHEN** the user drags page 5 before page 2
- **THEN** the former page 5 becomes page 2 and the numbers update

### Requirement: Document name in the overview
The overview SHALL show the document name. Tapping it SHALL open the rename dialog.

#### Scenario: Rename from the overview
- **WHEN** the user taps the name and enters "Tax 2026"
- **THEN** the overview and the library show "Tax 2026"

### Requirement: Overview actions
The overview SHALL offer Add page (opens the camera to add pages to this document), Export (opens the
export screen for this document) and Delete document (with confirmation, then returns to the
library).

#### Scenario: Add page
- **WHEN** the user presses Add page, captures one page and goes back
- **THEN** the overview shows the document with one more page at the end

#### Scenario: Export
- **WHEN** the user presses Export
- **THEN** the export screen opens for this document, and back returns to the overview

#### Scenario: Delete document
- **WHEN** the user deletes the document from the overview and confirms
- **THEN** the library is shown without that document
