# Design

## Context

After change 03, the library opens a document directly in `DocumentScreen` (large page + horizontal
`CommonPageList` strip + edit buttons + Add page/Export bar). `CommonPageList` draws thumbnails from
`DocumentUiModel` (thumbnails already rendered with filter/crop/rotation) and reorders with
`sh.calvin.reorderable` 3.0.0, which also provides `rememberReorderableLazyGridState`. Rename
dialog and delete confirmation exist in the library (03). See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- Overview of all pages at a glance, a single tap to edit any page.
- Reordering many pages comfortably.

**Non-Goals:**
- Multi-page selection in the grid (bulk delete/filter): could come later; export already has
  page selection.
- Page sizes / capture dates on each thumbnail (CamScanner shows them; not useful enough here).
- Changes to the page editor besides its back target.

## Screen design

Portrait:

```
┌────────────────────────────────────┐
│ ←  Invoice March  ✎             ⋮  │  TopAppBar; title tappable → rename
├────────────────────────────────────┤  background surfaceContainerLow
│ ┌──────────────┐ ┌──────────────┐  │
│ │              │ │              │  │  LazyVerticalGrid,
│ │   (page 1)   │ │   (page 2)   │  │  GridCells.Adaptive(150.dp),
│ │              │ │              │  │  12dp spacing, 16dp padding
│ │              │ │              │  │
│ │     [ 1 ]    │ │     [ 2 ]    │  │  number badge like the strip:
│ └──────────────┘ └──────────────┘  │  inverseSurface 70%, bottom-center
│ ┌──────────────┐ ┌──────────────┐  │
│ │   (page 3)   │ │   (page 4)   │  │  thumbnail: aspect ratio of the
│ │              │ │              │  │  page, 4dp radius, elevation 1dp
│ │     [ 3 ]    │ │     [ 4 ]    │  │
│ └──────────────┘ └──────────────┘  │
├────────────────────────────────────┤
│ ( + Add page )        [ ✓ Export ] │  same bottom bar as the editor
└────────────────────────────────────┘
```

Overflow menu: 🗑 Delete document · ⚙ Settings · ⓘ About.
While dragging, the lifted thumbnail is scaled to 1.05 with a primary-color outline, like the strip.
Landscape: grid on the left (weight 2), Add page/Export stacked in the right column (weight 1).

Updated flow:

```
Library ──tap──▶ Overview ──tap page──▶ Page editor ──Crop──▶ Crop
   ▲               │  ▲  ▲                   │
   └────back───────┘  │  └───────back────────┘
                      │
       Camera ◀─Add page─┘   (back / "N pages" → Overview)
       Export ◀─Export───┘   (back → Overview)
```

## Decisions

- **New `Screen.Main.Overview`**; the existing `Screen.Main.Document(initialPage)` stays as the
  page editor. Back rules: `Overview → Library`, `Document → Overview`, `Camera → Overview if
  pages > 0 else Library`, `Export → Overview`. The camera's "N pages" button opens the overview.
  Opening from the library goes to `Overview`.
- **State**: the overview reads `MainViewModel.documentUiModel` (thumbnails) and the document name
  from `LibraryRepository`; no new ViewModel. Tapping page *i* calls `onPageSelected(i)` and
  navigates to `Document(initialPage = i)`.
- **Thumbnail size**: grid cells (~150–180dp) are bigger than the strip's thumbnails
  (`THUMBNAIL_SIZE_DP`). Grid thumbnails use a second, larger size from `ImageRepository`
  (`getThumbnail(key, size = GRID)`), cached separately, instead of upscaling small thumbnails.
  Alternative: always render the large size and downscale in the strip (rejected: more memory for
  the strip in long documents).
- **Reorder** with `rememberReorderableLazyGridState` and `longPressDraggableHandle`, calling the
  existing `movePage(id, index)`.
- **Shared thumbnail composable**: extract the thumbnail + number badge from `CommonPageList` into
  `PageThumbnail` so the strip and the grid look the same.
- **Rename** reuses the dialog from 03; **Delete document** reuses the confirmation from 03, then
  `LibraryRepository.delete` + navigate to Library.

## Risks / Trade-offs

- [One more screen before editing] → Tap target is the whole thumbnail; for 1-page documents the
  overview still makes sense (name, export, add page). Could auto-skip later if users complain.
- [Grid thumbnails for 30+ pages cost memory] → `LazyVerticalGrid` only composes visible items;
  bitmaps come from the LRU thumbnail cache.
- [Long-press drag in a grid conflicts with scrolling] → The library handles it; verify on device.
