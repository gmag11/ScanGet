# Tasks

Unless stated otherwise, run Gradle and adb with `ANDROID_SERIAL=emulator-5554`.

## 1. Filter and adjustment model (imageprocessing)

- [x] 1.1 Add `ORIGINAL`, `LIGHTEN`, `ENHANCE`, `NO_SHADOWS`, `NEGATIVE` to `ColorMode` with an
  `isBitonal` property and a per-mode set of exposed controls (D1, D2). Add a JVM test asserting
  that only `BLACK_AND_WHITE` is bitonal and that the control sets match the page-adjustments spec
  table. Verify with `./gradlew :imageprocessing:test`.
- [x] 1.2 Add the `Adjustments` value type with `isNeutral`, value ranges, a canonical string and a
  CRC32-based 8-hex `hash()` (D2, D7). Add tests: the neutral value has `isNeutral`, the hash is
  stable for equal values, differs for different values and never contains `-`. Verify with
  `./gradlew :imageprocessing:test`.
- [x] 1.3 Add a pure function that builds the 4x5 tone color matrix from brightness, contrast and
  saturation, plus an OpenCV function that applies it to a BGR `Mat` with clamping (D5). Add tests:
  a neutral matrix is the identity, saturation -100 gives R=G=B, and applying the matrix per pixel
  in Kotlin matches the OpenCV result on a synthetic image within ±1. Load OpenCV in tests through
  openpnp (`testImplementation(libs.opencvjava)` if needed). Verify with
  `./gradlew :imageprocessing:test`.

## 2. Rendering pipeline (imageprocessing)

- [x] 2.1 Change `enhanceCapturedImage` to take `Adjustments` (neutral default) and implement the
  D3 order: filter → negative inversion → tone → detail. Turn the Retinex `alpha` and Sauvola `k`
  into parameters driven by `shadowRemoval` and `threshold`. Add a test that neutral adjustments
  give byte-identical output to the previous code for Color, Grayscale and Black and white on a
  synthetic page. Verify with `./gradlew :imageprocessing:test`.
- [x] 2.2 Implement the Original, Lighten, Enhance, No shadows and Negative pipelines (D4). Add
  tests on synthetic images:
  - Original returns its input unchanged.
  - Negative equals 255 minus the Grayscale output.
  - No shadows reduces the brightness difference between a shaded and an unshaded paper area.
  - Enhance brings a yellow-tinted paper closer to neutral.
  - Lighten raises the paper level.

  Verify with `./gradlew :imageprocessing:test`.
- [x] 2.3 Implement the detail control as an unsharp mask with sigma proportional to the longest
  side (D3). Add a test that detail 0 is a no-op and that detail > 0 increases edge contrast on a
  step image. Verify with `./gradlew :imageprocessing:test`.
- [x] 2.4 Add `FilterEvaluator` to `evaluation` (D9). It renders every filter with neutral and
  sample adjustments for a folder of images and quads, into a side-by-side HTML page. Document how
  to run it in `evaluation/README.md`, or in a header comment if there is no README. Verify by
  running it on the public dataset sample and opening the HTML. Never commit the owner's photos.
- [x] 2.5 Tune the D4 constants with the evaluator on a sample of the public FairScan dataset (the
  owner chose not to use their own photos). Record the chosen values and the reasoning in comments
  next to the constants. Verify by reviewing the evaluator HTML with the owner.

## 3. Storage and cache (app data layer)

- [x] 3.1 Add `adjustments: AdjustmentsV1? = null` to `PageV2`, with mapping to and from
  `Adjustments` (neutral ↔ null). Add `adjustments` to `ScanPage` and `PageViewKey`. Extend
  `PageStoreTest` and `ImageRepositoryTest`:
  - metadata without the field loads as neutral;
  - neutral adjustments are saved without the field;
  - non-neutral adjustments round-trip.

  Verify with `./gradlew :app:testDebugUnitTest`.
- [x] 3.2 Append `.a<hash>` to processed file names for non-neutral adjustments (D7) and pass
  adjustments through `ImageTransformations.process` and `processedImage`. Add an
  `ImageRepositoryTest` case showing that neutral pages keep their old file name and that changing
  adjustments produces a different file. Verify with `./gradlew :app:testDebugUnitTest`.
- [x] 3.3 Replace `setColorMode` with `setRendering(id, mode, adjustments)`. Switching mode resets
  adjustments to neutral. After a successful commit, delete that page's processed files for other
  non-neutral hashes. Add `ImageRepositoryTest` cases for the reset on mode change and for the
  cleanup. Verify with `./gradlew :app:testDebugUnitTest`.

## 4. Export and encoding

- [x] 4.1 Replace storage and encoding checks on `BLACK_AND_WHITE` with `isBitonal` in
  `ExportQuality.maxPixels`, `encodeImage`, `AndroidPdfWriter`, `ExportViewModel` and
  `ImageRepository`. Pass the page's adjustments in the `ExportQuality.HIGH` re-render path of
  `ExportPreparation`. Extend `AndroidPdfWriterTest` so a non-B/W new filter is embedded as
  continuous tone. Verify with `./gradlew :app:testDebugUnitTest`.

## 5. Preview session and view model

- [x] 5.1 Add an adjustments editing session to `MainViewModel` (D6):
  - open: background decode and warp to ~1 MP;
  - preview base render, debounced and cancellable on changes to detail, shadow removal or
    threshold;
  - tone matrix state for brightness, contrast and saturation;
  - confirm, cancel and reset;
  - release Mats on close.

  Unit-test the session logic that does not need Android (state transitions, debounce
  cancellation) with coroutine test utilities. Verify with `./gradlew :app:testDebugUnitTest`.
- [x] 5.2 Implement "apply to all": commit the current page, then call `setRendering` on every
  other page sequentially. Change `loadingPageId` into a set of loading ids. Verify with a
  view-model test that every page ends with the same mode and adjustments and that the loading set
  becomes empty.

## 6. UI

- [x] 6.1 Extend the filter dropdown in `DocumentScreen` to list all eight filters, with labels.
  Extend `DefaultColorMode` and the Settings list with one entry per filter. Add English strings in
  `values/strings.xml` and Spanish strings in `values-es/strings.xml`. Verify with
  `./gradlew :app:lintDebug` (no new errors) and by checking both menus on the emulator.
- [x] 6.2 Add the Tune button and the adjustments bottom panel:
  - sliders for the current filter's controls;
  - Reset, Apply to all, Cancel and Done;
  - a progress indicator while the preview base re-renders.

  Apply the tone matrix with `ColorFilter.colorMatrix` on the page image while the panel is open.
  Add English and Spanish strings. Verify on the emulator that brightness changes while dragging
  and that threshold updates shortly after release.

## 7. Integration checks

- [x] 7.1 On the emulator, run the page-adjustments and page-filters spec scenarios end to end:
  - every filter selectable;
  - controls per filter;
  - preview matches the confirmed page;
  - cancel restores;
  - reset;
  - apply to all on a 3-page document;
  - adjustments survive an app restart;
  - high-quality PDF export keeps the adjustments and keeps B/W bitonal.

  Report the result to the owner.
- [ ] 7.2 Open a document created before this change and confirm that its pages display and export
  unchanged, with the same processed file names in `files/scanned_pages/`. Verify with
  `adb shell run-as org.fairscan.app ls files/scanned_pages`.
- [x] 7.3 Run the full JVM test suite and lint: `./gradlew test :app:lintDebug`. Verify both pass.
- [x] 7.4 Ask the owner to try the build on their phone (`a4280105`) before installing. Collect any
  crash with `adb -s a4280105 logcat -b crash -d`. Verify that the owner confirms acceptable preview
  latency.

