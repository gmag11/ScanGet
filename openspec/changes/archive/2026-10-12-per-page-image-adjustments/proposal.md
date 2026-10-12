# Proposal

## Why

FairScan offers three fixed enhancement modes (color, grayscale, black and white) with no way to
tune the result. When a page comes out too dark, too washed out, or with the wrong threshold, the
user can only switch modes. There is also no way to get the plain cropped photo, because the color
mode always applies Retinex. This fork drops upstream's "no manual adjustments" stance: the owner
wants CamScanner-like control over post-processing, applied per page.

## What Changes

- Add new filters next to the existing ones:
  - **Original**: perspective-corrected crop with no enhancement.
  - **Lighten**: whiter background and more contrast, without strong illumination flattening.
  - **Enhance**: automatic white balance, contrast and a mild saturation boost.
  - **No shadows**: strong illumination flattening that keeps color.
  - **Negative**: inverted output.

  Color, Grayscale and Black and white stay as they are and remain the automatic defaults.
- Add per-page adjustments that are stored with the page and survive app restarts:
  - Cheap adjustments: brightness, contrast, saturation.
  - Filter-specific adjustments that need reprocessing: detail (sharpening), shadow-removal
    strength, black-and-white threshold.
  - Each filter shows only the adjustments that make sense for it. For example, black and white
    shows the threshold, not contrast or saturation.
- Live preview while moving a slider. Cheap adjustments update instantly, and reprocessing
  adjustments update on a reduced-size preview. The full-resolution page is produced when the user
  confirms.
- A reset action that restores the filter's default adjustments, and an "apply to all pages"
  action that copies the current page's filter and adjustments to every page in the document.
- Existing documents keep loading: pages without stored adjustments behave exactly as before.

## Capabilities

### New Capabilities
- `page-filters`: the set of filters a page can be rendered with, what each one produces, and how
  the default filter is chosen for a new page.
- `page-adjustments`: per-page tuning of the selected filter, including which adjustments each
  filter exposes, live preview, persistence, reset, and applying settings to all pages.

### Modified Capabilities
None: the project has no specs yet.

## Impact

- `imageprocessing`: `PostProcessing.kt` (new filter pipelines; Retinex, white-point and Sauvola
  constants become parameters), `ColorMode` (new filters, or a separate filter type that says
  whether its output is bitonal).
- `app` data layer: `PageV2` in `DocumentMetadata.kt` gets an optional adjustments field;
  `PageViewKey` and `ImageRepository.processedImageFileName` must include the adjustments so a
  cached image is never served for different settings.
- `app` domain/platform: `ImageTransformations.process`, `ImageProcessor.processedImage`,
  `ExportPreparation`, `ExportQuality.maxPixels` and `AndroidPdfWriter` (CCITT encoding for bitonal
  output) must take the new filters into account.
- `app` UI: `DocumentScreen` replaces the color-mode dropdown with a filter picker and an
  adjustments panel; `MainViewModel` gets actions to preview, commit, reset and apply to all.
  The default-color-mode setting may need to list the new filters.
- New strings in every supported language (at least English and Spanish, with English as the
  fallback for the others).
- No new dependencies: OpenCV and Compose `ColorMatrix` cover everything.
