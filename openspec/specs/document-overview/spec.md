# document-overview Specification

## Purpose

Shows the pages of the open document as a numbered grid, from where a page is picked to be edited, the pages are put in order, and the document is renamed, extended, exported or deleted.

## Requirements

### Requirement: Page grid
The document overview SHALL show every page of the document as a processed thumbnail (with its
crop, rotation, filter and adjustments applied), in document order, in a grid of square cells, with
the page number on each. The thumbnail SHALL fill the cell: a portrait page SHALL be scaled so that
its width fills the width of the cell and be aligned to the top of the cell, and a landscape page
SHALL be scaled so that its height fills the height of the cell and be aligned to the left of the
cell. Whatever does not fit in the cell SHALL be cut off, and a square page SHALL be shown whole.
The grid SHALL have two columns on a normal phone in portrait. On wider screens it SHALL fit more
columns rather than enlarge the cells, which SHALL stay between about 150 dp and 230 dp on screens at
least 344 dp wide. On narrower screens it SHALL keep two columns with smaller cells.

#### Scenario: Grid shown
- **WHEN** the user opens a 7-page document
- **THEN** seven numbered thumbnails are shown in document order

#### Scenario: Thumbnails follow edits
- **WHEN** the user changes the filter of page 3 in the page editor and goes back
- **THEN** the thumbnail of page 3 shows the new filter

#### Scenario: Portrait page fills the width
- **WHEN** a page taller than wide (for example A4 in portrait) is shown
- **THEN** its width covers the whole width of the cell, its top edge touches the top of the cell
  and only the part of the page that fits in the square is visible

#### Scenario: Landscape page fills the height
- **WHEN** a page wider than tall (for example A4 in landscape) is shown
- **THEN** its height covers the whole height of the cell, its left edge touches the left of the
  cell and only the part of the page that fits in the square is visible

#### Scenario: Square page
- **WHEN** a page as wide as tall is shown
- **THEN** it covers the cell exactly and nothing is cut off

#### Scenario: Square cells
- **WHEN** a document has a portrait page, a landscape page and a square page
- **THEN** the three cells have the same square size and each page fills its cell as described, with
  its own proportions and no empty bands

#### Scenario: Two columns on a phone
- **WHEN** the overview is shown on a phone about 360 to 430 dp wide in portrait
- **THEN** the cells are in two columns of equal size

#### Scenario: More columns when there is room
- **WHEN** the overview is shown on a screen wider than a normal phone in portrait, or in landscape
- **THEN** more columns are shown, and the cells are about the same size as on the phone, growing
  only a little to use the whole width

#### Scenario: Tablet
- **WHEN** the overview is shown on a tablet about 800 dp wide
- **THEN** four or five columns are shown and no cell is bigger than about 230 dp

#### Scenario: No jump while loading
- **WHEN** the thumbnails are still being made
- **THEN** every cell already has its final square size, and the grid does not move when a
  thumbnail appears

#### Scenario: Page number and selection mark stay visible
- **WHEN** a page is shown in selection mode
- **THEN** its number and its selection mark are drawn over the thumbnail, at the same place as for a
  page that fits the cell

### Requirement: Opening a page
Tapping a thumbnail SHALL open the page editor on that page, unless page selection is active, in
which case a tap toggles the selection (see `document-page-selection`). Going back from the page
editor SHALL return to the overview.

#### Scenario: Edit a page
- **WHEN** the user taps page 4
- **THEN** the page editor opens showing page 4

#### Scenario: Back to the grid
- **WHEN** the user presses back in the page editor
- **THEN** the overview is shown

#### Scenario: Tap while selecting
- **WHEN** page selection is active and the user taps page 4
- **THEN** page 4 is toggled and the page editor does not open

### Requirement: Reordering in the grid
A long press followed by a drag on a thumbnail SHALL move the page to the drop position. The new
order SHALL be saved. A long press released without dragging SHALL instead start page selection (see
`document-page-selection`). While pages are selected, dragging SHALL NOT reorder.

#### Scenario: Move a page
- **WHEN** the user drags page 5 before page 2
- **THEN** the former page 5 becomes page 2 and the numbers update

#### Scenario: Long press without drag
- **WHEN** the user long-presses page 5 and releases without moving
- **THEN** page selection starts with page 5 selected and the order is unchanged

#### Scenario: No reordering while selecting
- **WHEN** page selection is active and the user drags a page
- **THEN** the order does not change

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

### Requirement: Thumbnails are reused
A thumbnail SHALL be made again only when the page was rendered differently (crop, rotation, filter
or adjustments) or no earlier thumbnail of the same rendering is available. A thumbnail made before
SHALL be kept between sessions, and the processed image kept by the library SHALL be used before
rendering a page again from its original capture.

#### Scenario: Reopening a document
- **WHEN** the user opens again a document whose thumbnails were shown before
- **THEN** the thumbnails appear without rendering any page from its original capture

#### Scenario: Page edited
- **WHEN** the user crops page 2 again and goes back to the overview
- **THEN** only the thumbnail of page 2 is made again

#### Scenario: Cache lost
- **WHEN** the private cache of thumbnails has been cleared
- **THEN** the thumbnails are made again from the processed images of the library, and a page is
  rendered from its original capture only if the library holds no processed image for it
