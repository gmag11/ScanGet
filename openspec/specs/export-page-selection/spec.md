# export-page-selection Specification

## Purpose

Lets the user choose which pages of the document are exported from the export screen, share each selected page as its own file, and keeps each export under a distinct file name.

## Requirements

### Requirement: Page strip on the export screen
The export screen SHALL show a thumbnail for every page of the document, in document order, indicating which pages will be exported.

#### Scenario: Strip is shown
- **WHEN** the user opens the export screen for a document of several pages
- **THEN** a thumbnail of each page is displayed

#### Scenario: Active page is indicated when nothing is selected
- **WHEN** no page is selected
- **THEN** the active page's thumbnail is visibly marked as the page to be exported

### Requirement: Selection mode via long press
A long press without movement on a thumbnail SHALL enter selection mode and select that page. While in selection mode, a short press on any thumbnail SHALL toggle that page's selection. Selection mode SHALL end when no page remains selected.

#### Scenario: Entering selection mode
- **WHEN** the user long-presses a thumbnail while not in selection mode
- **THEN** selection mode starts and that page is selected

#### Scenario: Toggling a page
- **WHEN** selection mode is active and the user taps a thumbnail
- **THEN** the page becomes selected if it was not, and unselected if it was

#### Scenario: Leaving selection mode
- **WHEN** the user unselects the last selected page
- **THEN** selection mode ends and the export falls back to the active page

#### Scenario: Tap outside selection mode
- **WHEN** selection mode is not active and the user taps a thumbnail
- **THEN** that page becomes the active page, so the export contains that page only

### Requirement: Reordering outside selection mode
Dragging a thumbnail after a long press SHALL keep reordering pages when selection mode is not active, and SHALL have no effect while it is active.

#### Scenario: Drag to reorder
- **WHEN** selection mode is not active and the user long-presses a thumbnail and drags it
- **THEN** the page is moved and selection mode does not start

#### Scenario: Drag in selection mode
- **WHEN** selection mode is active and the user drags a thumbnail
- **THEN** pages are not reordered

### Requirement: Exported pages
The export SHALL contain exactly the selected pages, in document order. If no page is selected, it SHALL contain only the active page. This applies to every export format and to the displayed page count and size.

#### Scenario: Some pages selected
- **WHEN** pages 2 and 4 are selected and the user shares or saves
- **THEN** the exported file(s) contain pages 2 and 4 only

#### Scenario: No selection
- **WHEN** no page is selected and page 3 is the active page
- **THEN** the export contains page 3 only

#### Scenario: Selection changes after preparation
- **WHEN** the user changes the selection after the export was prepared
- **THEN** the export is prepared again for the new selection before it can be shared or saved

### Requirement: Select all
The export screen SHALL offer a button that selects every page. Using it SHALL enter selection mode.

#### Scenario: Select all
- **WHEN** the user presses "Select all"
- **THEN** every page is selected and the export contains the whole document

### Requirement: External export unchanged
An export requested by another application SHALL keep including every page of the document regardless of selection.

#### Scenario: External call
- **WHEN** another app requests a PDF of the document
- **THEN** the PDF contains all pages

### Requirement: Distinct file names per export
Each export SHALL be saved under a different file name, so that exporting several page selections of the same document never overwrites or collides with an earlier export. When the exported pages are not the whole document and the user has not typed a file name, the default name SHALL include the exported page numbers. When the chosen name already exists in the destination, the name SHALL be made unique.

#### Scenario: Default name for a partial export
- **WHEN** pages 2 and 4 of a 6-page document are exported with the default file name
- **THEN** the file name contains the page numbers (for example "… p2,4")

#### Scenario: Two exports of different pages
- **WHEN** the user exports page 1 and then page 2 of the same document
- **THEN** two files with different names are saved and the first is untouched

#### Scenario: Name already taken
- **WHEN** the user saves with a name that already exists in the destination
- **THEN** a numeric suffix is added instead of overwriting

### Requirement: Share each selected page separately
When two or more pages are selected, the export screen SHALL offer a "Share each page separately" action. It SHALL generate one file per selected page, in the configured export format (a single-page PDF or a JPEG per page), name each file with the page number, and share all of them in one share request.

#### Scenario: Button visibility
- **WHEN** fewer than two pages are selected
- **THEN** the "Share each page separately" action is not shown

#### Scenario: Separate PDFs
- **WHEN** pages 2, 3 and 5 are selected, the export format is PDF, and the user uses the action
- **THEN** the share request carries three single-page PDF files with different names

#### Scenario: Separate JPEGs
- **WHEN** the export format is JPEG and the user uses the action
- **THEN** the share request carries one JPEG per selected page
