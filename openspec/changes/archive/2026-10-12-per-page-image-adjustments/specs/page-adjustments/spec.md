# Spec Delta

## Purpose

Lets the user tune the rendering of each scanned page on top of its filter, with live preview,
and keeps those settings with the page.

## ADDED Requirements

### Requirement: Adjustments per filter
Each page SHALL have a set of adjustments that tune its filter. The adjustments panel SHALL show
only the controls that apply to the page's current filter, as listed in the scenarios.

#### Scenario: Color-preserving filters
- **WHEN** the current filter is Original, Lighten or Enhance
- **THEN** the panel shows brightness, contrast, saturation and detail

#### Scenario: Flattening filters
- **WHEN** the current filter is Color or No shadows
- **THEN** the panel shows brightness, contrast, saturation, detail and shadow-removal strength

#### Scenario: Grayscale filters
- **WHEN** the current filter is Grayscale or Negative
- **THEN** the panel shows brightness, contrast and detail, and no saturation control

#### Scenario: Black and white
- **WHEN** the current filter is Black and white
- **THEN** the panel shows only the threshold control

### Requirement: Neutral adjustments by default
Every control SHALL start at a neutral value that reproduces the filter's unadjusted output.
Pages with no stored adjustments, including pages created before this change, SHALL behave as if
all controls were neutral.

#### Scenario: Neutral values reproduce the filter
- **WHEN** a page's adjustments are all neutral
- **THEN** its output is identical to the filter's output without adjustments

### Requirement: Adjustment effects
Brightness SHALL make the page lighter or darker; contrast SHALL widen or narrow the tonal range;
saturation SHALL strengthen or weaken colors; detail SHALL sharpen edges and text; shadow-removal
strength SHALL control how strongly illumination is flattened; threshold SHALL control how much of
the page turns black in Black and white.

#### Scenario: Raising the threshold
- **WHEN** the user raises the threshold on a Black and white page
- **THEN** light strokes and faint marks that were white before become black

#### Scenario: Lowering saturation to the minimum
- **WHEN** the user moves saturation to its minimum on a color page
- **THEN** the page shows no color

### Requirement: Switching filter resets adjustments
When the user selects a different filter for a page, that page's adjustments SHALL reset to
neutral.

#### Scenario: New filter starts neutral
- **WHEN** a Color page has brightness raised and the user switches it to Grayscale
- **THEN** the page is rendered with Grayscale and all adjustments neutral

### Requirement: Live preview
While the user moves a control, the page view SHALL update to show its effect without waiting for
full-resolution processing. Brightness, contrast and saturation SHALL update on every slider
movement; detail, shadow-removal strength and threshold SHALL update within about one second after
the slider stops, using a reduced-resolution preview.

#### Scenario: Instant tone preview
- **WHEN** the user drags the brightness slider
- **THEN** the page view gets lighter or darker as the slider moves

#### Scenario: Reprocessing preview
- **WHEN** the user moves the threshold slider and releases it
- **THEN** the page view shows the new threshold result shortly after, while the full-resolution
  page is not yet produced

### Requirement: Preview matches the result
The full-resolution page produced on confirmation SHALL look the same as the last preview, apart
from resolution.

#### Scenario: Confirmed page matches preview
- **WHEN** the user confirms the adjustments
- **THEN** the stored page shows the same brightness, contrast, colors, sharpness and threshold as
  the preview did

### Requirement: Confirm and cancel
The adjustments panel SHALL offer a confirm action that stores the adjustments and produces the
full-resolution page, and a cancel action that discards every change made since the panel was
opened.

#### Scenario: Confirming
- **WHEN** the user changes contrast and confirms
- **THEN** the page, its thumbnail and later exports use the new contrast

#### Scenario: Cancelling
- **WHEN** the user changes contrast and cancels
- **THEN** the page keeps the adjustments it had before the panel was opened

### Requirement: Reset adjustments
The adjustments panel SHALL offer a reset action that sets every control of the current filter back
to neutral.

#### Scenario: Resetting
- **WHEN** the user has changed several controls and taps reset
- **THEN** every control returns to neutral and the preview shows the unadjusted filter output

### Requirement: Apply to all pages
The adjustments panel SHALL offer an action that copies the current page's filter and adjustments
to every page of the document and re-renders them.

#### Scenario: Applying to the whole document
- **WHEN** a document has three pages and the user applies No shadows with raised contrast to all
  pages
- **THEN** all three pages use No shadows with the same contrast, and each page shows a loading
  state until its rendering is ready

### Requirement: Adjustments are persistent
A page's filter and adjustments SHALL be stored with the page, survive app restarts, and be used
for every export quality, including exports that re-render the page from its source photo.

#### Scenario: Restart keeps adjustments
- **WHEN** the user adjusts a page, closes the app and reopens the document
- **THEN** the page shows the same adjustments and the panel shows the stored control values

#### Scenario: High quality export
- **WHEN** the user exports an adjusted page at high quality
- **THEN** the exported page is re-rendered at high resolution with the same filter and
  adjustments

### Requirement: Rendering never reuses a stale result
The page view, thumbnails and exports SHALL never show a rendering produced with a different
filter, crop or adjustments than the page currently has.

#### Scenario: Adjusting after cropping
- **WHEN** the user crops a page and then changes its brightness
- **THEN** the page shows the new crop with the new brightness
