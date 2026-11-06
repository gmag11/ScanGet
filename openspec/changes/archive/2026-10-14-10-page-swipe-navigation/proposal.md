# Proposal

## Why

In the page editor the user can only change page by tapping a thumbnail in the strip. When reviewing
a document, the natural gesture is to swipe sideways, as in any gallery or in the scanners the
library is modelled on.

## What Changes

- In the page editor, a horizontal swipe on the page moves to the next or previous page, with a
  sliding animation: the page follows the finger and the neighbour page comes in from the side.
- The strip, the page counter and the edit buttons follow the page that is shown.
- Swiping does not conflict with zooming: while the page is zoomed in, a drag pans the page; at
  normal size a drag changes page. Swiping is disabled while the adjustments panel is open.
- The neighbour pages are loaded ahead so they are already visible while sliding.

## Capabilities

### New Capabilities
- `page-swipe-navigation`: moving between the pages of the open document by swiping in the page editor.

### Modified Capabilities

## Impact

- `DocumentScreen.kt` (pager around the page image), `DocumentUiState.kt` and `MainViewModel.kt`
  (bitmaps of the previous and next page).
- No data format, dependency or permission changes.
