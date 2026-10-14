# Spec Delta

## MODIFIED Requirements

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
