# page-filters Specification

## Purpose

Defines the filters a scanned page can be rendered with, what each filter produces, and how a new
page gets its initial filter.

## Requirements

### Requirement: Available filters
The system SHALL offer these filters for every page that has a source photo: Original, Color,
Lighten, Enhance, No shadows, Grayscale, Black and white, and Negative. The user SHALL be able to
switch the current page to any of them from the document screen.

#### Scenario: Filter list is shown
- **WHEN** the user opens the filter picker for a page
- **THEN** all eight filters are listed and the page's current filter is marked as selected

#### Scenario: Switching filter
- **WHEN** the user selects a different filter for the current page
- **THEN** the page is re-rendered from its source photo with that filter, and the page view,
  thumbnail and later exports use the new rendering

### Requirement: Original filter
The Original filter SHALL produce the perspective-corrected crop of the source photo without any
illumination flattening, white balance or contrast enhancement.

#### Scenario: Original keeps photo colors
- **WHEN** a page is rendered with the Original filter and neutral adjustments
- **THEN** each output pixel matches the perspective-corrected source within JPEG encoding error

### Requirement: Lighten filter
The Lighten filter SHALL push the paper background toward white and increase contrast while
keeping color, without the strong illumination flattening of No shadows.

#### Scenario: Background becomes lighter
- **WHEN** a page with a light gray paper background is rendered with Lighten
- **THEN** the background is brighter than in Original and the page keeps its colored content

### Requirement: Enhance filter
The Enhance filter SHALL apply automatic white balance, automatic contrast and a mild saturation
increase, keeping color.

#### Scenario: Color cast is removed
- **WHEN** a page photographed under warm light (yellowish paper) is rendered with Enhance
- **THEN** the paper background comes out close to neutral gray or white and colored content
  stays colored

### Requirement: No shadows filter
The No shadows filter SHALL flatten uneven illumination strongly enough to remove shadows and
lighting gradients from the paper, while keeping color.

#### Scenario: Shadow is removed
- **WHEN** a page with a shadow across part of the paper is rendered with No shadows
- **THEN** the shadowed and unshadowed paper areas come out at a similar brightness and colored
  content stays colored

### Requirement: Negative filter
The Negative filter SHALL produce the inverted grayscale rendering of the page: dark ink becomes
light and the paper becomes dark.

#### Scenario: Page is inverted
- **WHEN** a page with black text on white paper is rendered with Negative
- **THEN** the output shows light text on a dark background, in grayscale

### Requirement: Existing filters keep their output
Color, Grayscale and Black and white SHALL produce the same output as before this change when the
page has neutral adjustments.

#### Scenario: Unadjusted page is unchanged
- **WHEN** a page created before this change, with no stored adjustments, is displayed or exported
- **THEN** its output is identical to what the previous version produced

### Requirement: Black and white output stays bitonal
Black and white SHALL remain the only bitonal filter: its pages SHALL be stored losslessly and
exported to PDF with bitonal compression. All other filters SHALL be stored and exported as
continuous-tone images.

#### Scenario: Bitonal export
- **WHEN** a document with one Black and white page and one No shadows page is exported to PDF
- **THEN** the Black and white page is embedded as a 1-bit image and the No shadows page as a
  continuous-tone image

### Requirement: Default filter for new pages
A new page SHALL get the filter selected in the default filter setting. The setting SHALL offer
Automatic plus every available filter. Automatic SHALL keep choosing between Color and Grayscale
from the page content, as before.

#### Scenario: Automatic default
- **WHEN** the default filter setting is Automatic and the user captures a page with colored
  content
- **THEN** the new page uses the Color filter

#### Scenario: Explicit default
- **WHEN** the default filter setting is No shadows and the user captures a page
- **THEN** the new page uses the No shadows filter
