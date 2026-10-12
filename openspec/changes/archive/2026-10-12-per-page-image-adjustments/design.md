# Design

## Context

See proposal.md for motivation and the specs for the required behavior.

Current pipeline: `ImageRepository` keeps the source JPEG per page and re-renders it on every
change through `ImageTransformations.process` → `processedImage` → `extractDocument` (warp) →
`enhanceCapturedImage(colorMode)`. The result is cached on disk as
`<id>[.<mode>][.q<N>].jpg|png` and keyed in memory by `PageViewKey(pageId, rotation, colorMode,
quadVersion)`. `ExportQuality.HIGH` re-renders from the source at a higher resolution; the other
qualities reuse the cached rendering. A full render takes about a second at 2 MP (8 MP for black
and white) on the owner's phone.

Code that branches on the mode today: `enhanceCapturedImage`, `ExportQuality.maxPixels`,
`encodeImage` in `ImageProcessor`, `AndroidPdfWriter` (CCITT for black and white),
`ExportViewModel` (bitonal warning), `ImageRepository.processedImageFileName`,
`DefaultColorMode`, `DocumentScreen` labels and `ColorDetectionEvaluator`.

The fork should stay easy to rebase on upstream, so the diff in shared code should stay small.

## Goals / Non-Goals

**Goals:**
- One rendering function that takes filter and adjustments and is used for display, preview,
  every export quality and "apply to all".
- A preview that is visually the same as the confirmed result.
- Unchanged output and file names for pages without adjustments.

**Non-Goals:**
- Changing the automatic Color/Grayscale detection or adding new automatic choices.
- Per-region adjustments, curves, or manual white balance.
- Undo history beyond cancelling the panel.
- Translating new strings into languages other than English and Spanish.

## Decisions

### D1. Extend `ColorMode` instead of a new filter type
Add `ORIGINAL`, `LIGHTEN`, `ENHANCE`, `NO_SHADOWS` and `NEGATIVE` to `ColorMode`, plus a property
`isBitonal` (true only for `BLACK_AND_WHITE`). Replace every `== BLACK_AND_WHITE` check that is
about storage or encoding with `isBitonal`.

`ColorMode` is already serialized by name in `PageV2`, used in file names and in every layer, so
new values are backward compatible and the diff stays local.
- *Alternative:* a new `PageFilter` type mapped to `ColorMode`. Cleaner naming, but it touches
  every layer and makes upstream rebases harder. Rejected.

### D2. Adjustments model
`imageprocessing` gets a value type:

```
data class Adjustments(
    val brightness: Int = 0,        // -100..100
    val contrast: Int = 0,          // -100..100
    val saturation: Int = 0,        // -100..100 (-100 = no color)
    val detail: Int = 0,            // 0..100
    val shadowRemoval: Int = 0,     // -100..100, relative to the filter's default strength
    val threshold: Int = 0,         // -100..100, relative to the filter's default threshold
) { val isNeutral get() = this == Adjustments() }
```

Each `ColorMode` declares which controls it exposes (table in the page-adjustments spec). Values
for controls a filter does not expose are ignored when rendering and reset when the filter
changes. Integers keep the stored JSON and the cache hash stable.

- `PageV2` gets `adjustments: AdjustmentsV1? = null`, a serializable mirror; `null` means
  neutral, and neutral adjustments are stored as `null`.
- `ScanPage` and `PageViewKey` get `adjustments: Adjustments` (neutral by default).

### D3. Rendering order
```
source + quad --warp--> filter(mode, shadowRemoval, threshold)
              --> [Negative: invert]
              --> tone(brightness, contrast, saturation)    (skipped when neutral)
              --> detail(unsharp mask)                      (skipped when 0)
              --> encode (PNG if bitonal, else JPEG)
```
- Black and white only uses `threshold`, which shifts Sauvola's `k` (0.25 − 0.15 · threshold/100, so
  raising the threshold blackens more of the page). Tone and detail
  are not applied after binarization because they have no effect on 0/255 pixels.
- `shadowRemoval` maps to the Retinex blend `alpha` for Color (0.6 by default) and to the blend
  strength of the division for No shadows (0.9 by default). Both use the same mapping: 0 keeps the
  default, +100 reaches 1 and -100 reaches 0, linearly on each side.
- Detail is an unsharp mask whose sigma is proportional to the image's longest side, so the
  preview and the full-resolution page look alike.
- `enhanceCapturedImage(img, mode, adjustments, maxPixels)` gets the new parameter, with a neutral
  default so existing callers compile.

### D4. New filter pipelines
All in `ColorFilters.kt`, reusing existing building blocks:
- **Original:** returns the warped image unchanged.
- **Lighten:** white balance on the paper (the brighter half of the page is taken as paper), then a
  linear stretch that maps a black point to 0 and the paper level (histogram mode among the bright
  values) to ~250, then a gentle gamma. The black point is capped at 60 % of the paper level so a
  nearly blank page cannot explode. No Retinex, no flattening.
- **Enhance:** Lighten, then CLAHE on L in Lab (clip 1.2, mixed 35 % with the untouched lightness)
  and +15 % saturation. CLAHE alone left the paper a dull gray, hence Lighten first; at clip 2.0 and
  full strength it turned plain paper into a blotchy texture on the public dataset, hence the mix.
- **No shadows:** divide the image by an estimate of the illumination. The estimate is computed on
  a downscaled luminance image:
  1. thin dark strokes (text, ruled lines) are removed with a small closing;
  2. pixels below 50 % of the paper level (90th percentile) are treated as ink, whatever their
     size, and excluded; soft shadows keep most of the paper's brightness and are followed;
  3. holes left by the ink are filled by normalized convolution, from coarse to fine blur radii,
     a finer radius replacing a coarser one wherever enough paper pixels fall inside its window;
  4. the result gets the color of the paper, so dividing also balances the whites.

  `shadowRemoval` blends between the original and the flattened image. A first version used a
  per-channel morphological closing with a large kernel; it washed out large dark or colored
  blocks (they are indistinguishable from a shadow by size) and tinted the paper around saturated
  colors, which is why the estimate is built on luminance and ink is separated by intensity.
- **Negative:** `enhanceGrayscaleImage` followed by inversion.

The exact constants were tuned by eye with an evaluator (D9) on a sample of the public FairScan
dataset (v2.1, CC BY-NC-SA; the owner's private photos were not used). The specs only fix the
qualitative behavior.

### D5. Tone adjustments are a shared color matrix
Brightness, contrast and saturation are defined once as a 4x5 RGBA color matrix built by a pure
Kotlin function in `imageprocessing`:
- contrast: scale around 128;
- brightness: offset;
- saturation: luminance-preserving saturation matrix (Rec. 601 weights).

The preview passes that matrix to Compose `ColorFilter.colorMatrix` on the displayed bitmap. The
final render applies the same matrix with OpenCV `Core.transform` (with the BGR/RGB channel order
handled) and clamping. Same math in both places is what makes the preview match the result.
- *Alternative:* re-render with OpenCV on every slider event. Too slow for dragging. Rejected.

### D6. Preview session for the reprocessing controls
When the adjustments panel opens, `MainViewModel` starts an editing session:
1. On a background thread, decode the source and warp it to about 1 MP (screen size). Keep this
   unfiltered warp in memory while the panel is open.
2. Render `filter + shadowRemoval/threshold + negative + detail` from that warp. The result is the
   preview base bitmap. Tone is not applied here; it is the Compose color filter on top (D5).
3. Changes to detail, shadow removal or threshold re-render the preview base, debounced by
   ~200 ms, cancelling the previous job. Brightness, contrast and saturation only change the
   color filter.
4. Confirm re-renders at full resolution through `ImageRepository` (D7) and closes the session.
   Cancel and reset only touch session state. The session releases its Mats when it ends.

### D7. Cache key and file names
`processedImageFileName` appends `.a<hash>` before the extension when the adjustments are not
neutral. `<hash>` is 8 hex digits of a CRC32 over a canonical string of the six values.
- Neutral pages keep their current file names, so existing documents need no migration.
- No `-` is used, because `normalizeLegacyFiles` treats `-` as a legacy rotation separator.
- `ImageRepository.setColorMode` becomes `setRendering(id, mode, adjustments)`, through the same
  `updatePage` job deduplication. After a successful commit, other processed files of that page
  with a different `.a<hash>` are deleted, so files do not pile up while the user experiments.
  Neutral files of other modes are kept so switching filters stays fast, as it is today.

### D8. UI
- `ColorModeButton` keeps opening the filter dropdown, which now lists all eight filters.
- A new `Tune` button next to it opens the adjustments bottom panel with:
  - one slider per control the current filter exposes;
  - Reset, Apply to all, Cancel and Done.
- The page image above the panel shows the preview (D6) with a progress indicator while the
  preview base is re-rendering.
- "Apply to all" commits the current page first, then queues the other pages one by one. Each page
  shows its existing loading state (`loadingPageId` becomes a set of ids).
- `DefaultColorMode` gets one entry per filter, so Settings lists them.
- Strings in `values/` and `values-es/`. Other locales fall back to English.

### D9. Filter evaluator
Add `FilterEvaluator.kt` to the `evaluation` module (JVM, openpnp OpenCV, already used there). It
renders every filter, with neutral and a few non-neutral adjustments, for a folder of photos with
known quads and writes a side-by-side HTML page. It is used to tune the D4 constants on the owner's
photos, which are never committed. Unit tests for the deterministic parts go in
`imageprocessing/src/test`:
- color matrix math;
- `isNeutral` and the hash;
- Original equals the warp;
- neutral adjustments leave Color, Grayscale and B/W output byte-identical;
- Negative inverts.

## Risks / Trade-offs

- [The preview at ~1 MP differs from full resolution for detail and threshold] → Sigma and the
  Sauvola window already scale with image size. Check with the evaluator that the full-resolution
  result matches the preview at screen size.
- [Compose color filter and OpenCV disagree on rounding or gamma] → Both apply the matrix in sRGB
  8-bit space with no gamma conversion. A unit test compares the Kotlin matrix applied per pixel
  with the OpenCV result on a synthetic image.
- [The new filter constants look bad on some documents] → Tune them with the evaluator on the
  owner's photos before release. Adjustments give a manual way out.
- [`ColorMode` growing beyond a "color mode"] → Accepted for rebase friendliness. Revisit if
  upstream ever adds filters of its own.
- [Apply to all on long documents takes several seconds per page] → Pages render sequentially in
  the background with per-page loading indicators. Export waits on pending jobs, as it already
  does for `updatePage`.
- [Older app versions cannot read pages that use the new modes] → Not a concern: there is no
  downgrade path for a personal fork, and unknown enum values only affect that page.

## Migration Plan

No data migration: `adjustments` is optional and neutral pages keep their file names. Rollback is
reverting the change. Pages saved with a new filter would then fail to parse that field, and
`ImageRepository` already skips pages it cannot parse.
