# Spec Delta

## Purpose

Lets the user select several pages of the open document and delete them, or copy or move them to
another document of the library or to a new one, without leaving the document overview.

## ADDED Requirements

### Requirement: Page selection mode
A long press on a page in the document overview SHALL enter selection mode with that page selected.
While in selection mode a tap SHALL toggle the selection of a page instead of opening it, and the
number of selected pages SHALL be shown. Selection mode SHALL end when no page remains selected, when
the user closes it, or on back.

#### Scenario: Enter selection mode
- **WHEN** the user long-presses page 3 and lifts the finger without moving
- **THEN** selection mode starts with page 3 selected and "1 selected" is shown

#### Scenario: Toggle
- **WHEN** selection mode is active and the user taps page 5, then taps page 3
- **THEN** page 5 is selected, page 3 is not, and the count is 1

#### Scenario: Leave with back
- **WHEN** selection mode is active and the user presses back
- **THEN** selection mode ends, nothing is selected and the user stays in the overview

#### Scenario: Last page deselected
- **WHEN** the only selected page is tapped
- **THEN** selection mode ends

### Requirement: Page actions menu
In selection mode the top bar SHALL show a three-dot menu with the actions Move, Copy and Delete,
which act on the selected pages. The usual overview actions (Add page, Export, document delete)
SHALL NOT act while selecting.

#### Scenario: Menu content
- **WHEN** selection mode is active and the user opens the three-dot menu
- **THEN** Move, Copy and Delete are offered

### Requirement: Delete selected pages
Delete SHALL ask for confirmation stating the number of pages, and then remove the selected pages
from the document. Cancelling SHALL change nothing. The change SHALL be saved like any other edit of
the document, and selection mode SHALL end.

#### Scenario: Delete two pages
- **WHEN** pages 2 and 4 of a 6-page document are selected and the user confirms the deletion
- **THEN** the document has 4 pages, numbered in order, and selection mode has ended

#### Scenario: Cancel
- **WHEN** the user cancels the confirmation
- **THEN** the document keeps all its pages and the selection is kept

#### Scenario: Delete every page
- **WHEN** all the pages are selected and the deletion is confirmed
- **THEN** the document is removed from the library and the library is shown

### Requirement: Destination dialog
Move and Copy SHALL open a dialog listing the other documents of the library, with their name and
page count, and an option to create a new document. The current document SHALL NOT be listed. The
dialog SHALL be dismissable without any effect. Choosing a document, or confirming the new one,
SHALL perform the action.

#### Scenario: Choose an existing document
- **WHEN** the user selects Copy, and picks the document "Invoices" in the dialog
- **THEN** the dialog closes and the action is performed on "Invoices"

#### Scenario: Create a new document
- **WHEN** the user selects Move and chooses the new-document option
- **THEN** a new document with the default name is created as destination, and the action is
  performed on it

#### Scenario: No other documents
- **WHEN** the library has no other document
- **THEN** the dialog offers only the new-document option

### Requirement: Copy pages
Copy SHALL add the selected pages at the end of the destination document, in their order in the
current document. Each copy SHALL keep the original capture, crop, rotation, filter, adjustments and
OCR data of its page, and SHALL be independent from it afterwards. The current document SHALL NOT
change.

#### Scenario: Copy to an existing document
- **WHEN** pages 1 and 3 of a 5-page document are copied to a 2-page document
- **THEN** the destination has 4 pages, the last two being those of pages 1 and 3 looking the same,
  and the current document still has its 5 pages

#### Scenario: Independence
- **WHEN** a page copied to another document is later cropped there
- **THEN** the page in the original document is unchanged

#### Scenario: Confirmation after copy
- **WHEN** the copy has finished
- **THEN** selection mode ends and the user is told how many pages were copied and to which document

### Requirement: Move pages
Move SHALL do as Copy and then remove the selected pages from the current document. If the pages
cannot be added to the destination, they SHALL NOT be removed from the current document.

#### Scenario: Move to a new document
- **WHEN** pages 2 and 3 of a 4-page document are moved to a new document
- **THEN** the current document has 2 pages, and the library has a new document with the 2 moved
  pages

#### Scenario: Move every page
- **WHEN** all the pages are moved to another document
- **THEN** the destination holds them and the emptied document is removed from the library, which is
  shown

#### Scenario: Failure keeps the originals
- **WHEN** the destination cannot be written (for example the library folder is no longer accessible)
- **THEN** an error is shown and the current document keeps all its pages

### Requirement: Progress and feedback
While pages are being copied or moved a progress indicator SHALL be shown and further actions on the
overview SHALL be blocked.

#### Scenario: Large selection
- **WHEN** 30 pages are being moved
- **THEN** a progress indicator is shown until the operation ends
