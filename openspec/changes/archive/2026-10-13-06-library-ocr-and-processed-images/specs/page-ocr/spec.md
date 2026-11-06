## ADDED Requirements

### Requirement: OCR of saved pages in the background
When a page is saved to the library and the setting "Recognize text when saving a scan" is on and at
least one OCR language is available, the app SHALL recognize the text of the page in the background,
without delaying capture, editing or navigation, and SHALL store the result with the document.

#### Scenario: Scan with the setting on
- **WHEN** the user captures a page with the setting on
- **THEN** the camera and the document stay responsive and the recognized text of the page is stored
  in the library when the recognition finishes

#### Scenario: Setting off
- **WHEN** the setting is off and the user captures a page
- **THEN** no recognition runs in the background

#### Scenario: Recognition interrupted
- **WHEN** the app is closed before the recognition of a page finishes
- **THEN** the page has no OCR data and is recognized again the next time the document is saved
  with the setting on or when a PDF is exported

### Requirement: Stored OCR data
For each recognized page the library SHALL store the recognized words, each with its text, its
bounding box in the image, and the height and bottom position of its line (the data the PDF text
layer needs), the size in pixels of the image that was recognized, and the identity of the page
settings it was computed from (rotation, crop, color mode and adjustments). Positions are per word,
not per character.

#### Scenario: Data of a page
- **WHEN** a page has been recognized
- **THEN** its stored data lists its words with their boxes and the size of the recognized image

### Requirement: OCR data follows the page
The stored OCR data of a page SHALL be used only while the page settings match those it was computed
from. When the page is edited so that they differ, the data SHALL be discarded and, if the setting
is on, recognized again in the background. When the page is deleted its OCR data SHALL be deleted.

#### Scenario: Rotate a page
- **WHEN** the user rotates a recognized page
- **THEN** the old OCR data is not used and the page is recognized again

#### Scenario: Delete a page
- **WHEN** the user deletes a page
- **THEN** its OCR data is removed from the library

### Requirement: PDF built from stored OCR
When a PDF is generated, the text layer of a page SHALL be built from its stored OCR data when it is
valid, scaling the stored positions to the size of the exported page, without running the
recognition. Pages without valid data SHALL be recognized as before (or have no text layer if no OCR
language is available), and the result SHALL be stored when the page belongs to a library document.
PDFs SHALL still be generated on request and not stored.

#### Scenario: Export a recognized document
- **WHEN** a document whose pages all have valid OCR data is exported as PDF
- **THEN** the PDF has a text layer positioned like before and no recognition runs

#### Scenario: Export a partly recognized document
- **WHEN** a document with one page without OCR data is exported as PDF
- **THEN** only that page is recognized

### Requirement: Setting
The settings SHALL offer "Recognize text when saving a scan", on by default.

#### Scenario: Default
- **WHEN** the app is installed
- **THEN** the setting is on
