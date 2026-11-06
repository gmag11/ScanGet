## MODIFIED Requirements

### Requirement: Page grid
The document overview SHALL show every page of the document as a processed thumbnail (with its
crop, rotation, filter and adjustments applied), in document order, in a grid of square cells, with
the page number on each. The thumbnail SHALL be scaled to fit inside its cell, whole and centered,
whatever the shape of the page. The grid SHALL have two columns on a normal phone in portrait. On
wider screens it SHALL fit more columns rather than enlarge the cells, which SHALL stay between about
150 dp and 230 dp on screens at least 344 dp wide. On narrower screens it SHALL keep two columns
with smaller cells.

#### Scenario: Grid shown
- **WHEN** the user opens a 7-page document
- **THEN** seven numbered thumbnails are shown in document order

#### Scenario: Thumbnails follow edits
- **WHEN** the user changes the filter of page 3 in the page editor and goes back
- **THEN** the thumbnail of page 3 shows the new filter

#### Scenario: Square cells
- **WHEN** a document has a portrait page, a landscape page and a square page
- **THEN** the three cells have the same square size and each page is shown whole inside its cell,
  with its own proportions

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

## ADDED Requirements

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
