# Tasks

## 1. Neighbour pages

- [ ] 1.1 Add `previousPage` and `nextPage` bitmaps to `DocumentUiState` and load them in `MainViewModel` next to the current page; verify with a unit test (first and last page have no previous / next, bitmaps follow the index) and that existing tests compile

## 2. Pager

- [ ] 2.1 Wrap the page image in a `HorizontalPager` synchronised with `currentPageIndex` in both directions (swipe → `onPageSelected`; strip tap, deletion → animated scroll), disabled while adjusting, with zoom per page; verify with previews and on a device: swipe next/previous with animation, limits, short swipe, strip and counter follow, strip tap slides, RTL mirrored (`ar` locale)
- [ ] 2.2 Verify panning a zoomed page does not change page and pinch zoom does not either; adjust the gesture handling if the two conflict

## 3. Verification

- [ ] 3.1 Verify the module compiles and all unit tests pass (`./gradlew :app:assembleDebug :app:testDebugUnitTest`) and install on a device to check the whole flow
