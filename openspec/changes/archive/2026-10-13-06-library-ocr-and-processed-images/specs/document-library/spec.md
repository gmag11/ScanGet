## MODIFIED Requirements

### Requirement: Raw captures and editing data are kept
For each page, the library SHALL keep the original capture as it was taken (before cropping or
filtering) and the editing data needed to reproduce the page: crop outline, rotation, filter,
adjustments and capture metadata. The library SHALL also keep the processed image of each page and,
when available, its OCR data. The processed image and the OCR data are a cache: they SHALL NOT be
required to reopen a document, and when one is missing or does not match the page it SHALL be
produced again from the original capture.

#### Scenario: Re-crop after reopening
- **WHEN** the user reopens a saved document and opens the crop editor on a page
- **THEN** the full original capture is shown with the saved crop outline, and a different outline
  can be chosen

#### Scenario: Change filter after reopening
- **WHEN** the user reopens a saved document and changes a page from Black and white to Color
- **THEN** the page is rendered in color from the original capture

#### Scenario: Processed image saved
- **WHEN** a page is saved to the library
- **THEN** its processed image is stored in the document folder, and replaced when the page is edited

#### Scenario: One processed image per page
- **WHEN** a document has three pages
- **THEN** its folder holds three processed images, one for each page, and none for deleted pages

#### Scenario: Re-cropping replaces the processed image
- **WHEN** the user crops a page again
- **THEN** the processed image of that page is replaced and the folder still holds one image per page

#### Scenario: Names of the processed images
- **WHEN** a document named "Invoice March" has 12 pages
- **THEN** its processed images are named "Invoice March-page01" to "Invoice March-page12", with the
  extension of their format, in page order

#### Scenario: Names follow the document
- **WHEN** the document is renamed, or a page is deleted or moved
- **THEN** the processed images are renamed to match the new name and page numbers, and none is lost

#### Scenario: Reopen without processed images
- **WHEN** the processed images or OCR data of a document are missing from its folder
- **THEN** the document opens normally and its pages are rendered again from the original captures
