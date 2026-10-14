# page-swipe-navigation Specification

## Purpose

Lets the user move between the pages of the open document by swiping sideways in the page editor, with a sliding animation.

## Requirements

### Requirement: Swipe between pages
In the page editor, a horizontal swipe on the page SHALL move to the next or the previous page with
a sliding animation: the page follows the finger and the neighbour page enters from the side. Only
the page image SHALL move; the buttons, the page counter and the strip SHALL stay in place. Where
there is no next or previous page the page SHALL not leave its place.

#### Scenario: Next page
- **WHEN** the user swipes the page of a 5-page document, shown at page 2, towards the start
- **THEN** the page slides out, page 3 slides in, and the counter shows 3 / 5

#### Scenario: Previous page
- **WHEN** the user swipes towards the end on page 3
- **THEN** page 2 slides in and the counter shows 2 / 5

#### Scenario: Right-to-left language
- **WHEN** the app is shown in a right-to-left language
- **THEN** the directions are mirrored: swiping towards the end of the screen shows the next page

#### Scenario: Short swipe
- **WHEN** the user drags the page only a little and releases
- **THEN** the page slides back and the same page stays shown

#### Scenario: Limits
- **WHEN** the user swipes towards the start on the last page, or towards the end on the first page
- **THEN** the page stays in place

#### Scenario: Only the image moves
- **WHEN** the user swipes from page 2 to page 3
- **THEN** the filter, adjust, crop, rotate and delete buttons and the strip do not move, and only
  the page image slides

#### Scenario: Neighbour already visible
- **WHEN** the user starts dragging the page
- **THEN** the neighbour page is already visible while it slides in, not an empty space

### Requirement: Screen follows the shown page
After a swipe the page strip SHALL select (and scroll to, if needed) the thumbnail of the new page, and the filter, crop,
adjust, rotate and delete buttons SHALL act on it. Tapping a thumbnail of the strip SHALL also
move to that page with the same sliding animation.

#### Scenario: Strip follows
- **WHEN** the user swipes to page 4
- **THEN** page 4 is highlighted in the strip and the buttons act on page 4

#### Scenario: Tap on the strip
- **WHEN** the user taps page 5 in the strip while page 2 is shown
- **THEN** the page area slides to page 5

### Requirement: Swipe and zoom
A drag on a page that is zoomed in SHALL pan it and SHALL NOT change page. At normal size a drag
SHALL change page. Zooming with two fingers SHALL not change page. Page changes SHALL be disabled
while the adjustments panel is open.

#### Scenario: Panning a zoomed page
- **WHEN** the user has zoomed in on a page and drags it sideways
- **THEN** the page is panned and the page does not change

#### Scenario: Adjustments open
- **WHEN** the adjustments panel is open and the user swipes the page
- **THEN** the page does not change

#### Scenario: Zoom is per page
- **WHEN** the user zooms page 2, swipes to page 3 and back
- **THEN** page 3 is shown at normal size
