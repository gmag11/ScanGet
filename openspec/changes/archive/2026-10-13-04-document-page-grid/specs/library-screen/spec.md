## MODIFIED Requirements

### Requirement: Opening a document
Tapping a document SHALL open its overview, showing its pages with their saved crop and filters.

#### Scenario: Open
- **WHEN** the user taps a 5-page document
- **THEN** the document overview opens with 5 pages

### Requirement: Navigation between library, editor and camera
Back from the document overview SHALL return to the library. Back from the page editor SHALL return
to the document overview. Back from the camera SHALL return to the document overview if the open
document has pages, and to the library otherwise. Back from the export screen SHALL return to the
document overview. The export screen's "New document" action SHALL open the camera on a new
document, keeping the exported document in the library.

#### Scenario: Back from the overview
- **WHEN** the user presses back in the document overview
- **THEN** the library is shown

#### Scenario: Back from the editor
- **WHEN** the user presses back in the page editor
- **THEN** the document overview is shown

#### Scenario: Back from the camera without pages
- **WHEN** the user presses Scan in the library and then back without capturing
- **THEN** the library is shown and no document was added

#### Scenario: Back from the camera with pages
- **WHEN** the user captures two pages and presses back
- **THEN** the document overview is shown with those two pages

#### Scenario: New document after export
- **WHEN** the user presses "New document" on the export screen
- **THEN** the camera opens on an empty document and the exported document is still in the library
