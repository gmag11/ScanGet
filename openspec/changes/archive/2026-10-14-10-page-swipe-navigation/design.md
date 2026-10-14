## Context

`DocumentScreen` shows one page: `MainViewModel` exposes `DocumentUiState` with the bitmap of the
current page only, and `onPageSelected(index)` changes the index. The page image uses the
`zoomable` library (2.11.1), which only consumes drags while the content is zoomed in, so it can
coexist with a pager.

## Decisions

- **HorizontalPager** (Compose foundation) around `PageImage`, with `pageCount` = pages of the
  document. The pager state is the source of the animation; the view model index stays the source of
  truth. Pager settled page → `onPageSelected`; a change of the view model index not caused by the
  pager (strip tap, deletion) → `animateScrollToPage`. This keeps one direction of flow at a time by
  comparing the two indexes. Pager handles RTL by itself through the layout direction.
- **Neighbour bitmaps**: `DocumentUiState` gets `previousPage` and `nextPage` bitmaps (nullable),
  loaded in the view model like the current one (`imageRepository.image(key)`, the same cache), so
  slides show real content. A pager page whose index is not current or adjacent shows nothing (it is
  never visible while settled). While a strip jump skips several pages, the intermediate pages are
  empty for the duration of the animation; accepted.
- **Zoom**: `ZoomState` is already keyed by page id, so each page has its own; the pager's
  `userScrollEnabled` is false while adjusting. Because `zoomable` leaves drags alone at scale 1,
  no extra gesture code is needed; to be checked on a device.
- Overlays (counter, edit buttons, rotation, delete) and the strip stay fixed: they are outside the pager, over it, as now. Only the page image is inside the pager; the buttons act on the page selected in the view model, which changes when the pager settles.
- The loading overlay and the adjustment preview apply to the current page only.

## Risks

- Pager vs `zoomable` gesture conflict when slightly zoomed: verify on a device; fall back to a
  threshold on `zoomState.scale` to enable the pager.
- Transient flicker when the view model index and pager disagree: guard with the settled/target
  comparison.
