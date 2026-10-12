# Handoff: ideas from opendocscanner for evolving FairScan

Written for the agent that takes over this FairScan clone. It compares FairScan (at commit
`c1517c5`) with **opendocscanner**, a project the owner built first and has now stopped in favour of
evolving FairScan. Everything below was checked against the code; file and line references point to
this repository unless they start with `opendocscanner/`.

opendocscanner lives at `/home/gmartin/Proyectos/Android/opendocscanner`.

---

## 1. What the owner wants

- A CamScanner replacement for personal use: no ads, **everything processed and stored locally**, no
  cloud, and no dependency on Google services.
- What matters most: **automatic edge detection that rarely needs touching up**, **easy corner
  adjustment with a magnifier that the finger never hides**, and good correction filters.
- Weak spot measured on the owner's own photos: low-contrast documents (white paper on a light table),
  shadows and skewed shots. opendocscanner's classic OpenCV detector failed exactly there, which is
  why the owner looked for an ML-based detector and found FairScan.

## 2. Working agreements with the owner

- Converse in **Spanish**. Write commits, code comments, docs and planning artifacts in **English**.
- Commits: Conventional Commits, in English. The owner's commit skill (`git-commit-organizer`) shows
  the plan and waits for approval, never pushes, and adds **no AI attribution trailers**.
- Devices: an emulator AVD `Pixel_8` (`emulator-5554`, API 37, **16 KB pages**, has Google Play) and the
  owner's real phone (Xiaomi `2201123G`, serial `a4280105`, Android 15, arm64, 4 KB pages). The phone
  is in daily use:
  - Export `ANDROID_SERIAL=emulator-5554` for every Gradle/adb command by default. Gradle's
    `installDebug` and `connected*AndroidTest` otherwise target **every** attached device.
  - Ask before installing anything on the phone. UI tests on it get killed when the owner touches the
    phone, so prefer manual checks by the owner there and read crashes with
    `adb -s a4280105 logcat -b crash -d`.
  - Never change device settings without reading and reporting the original value first.

## 3. Before changing anything: fork or contribute?

- FairScan is **GPLv3**, the segmentation model is **GPLv3** and the dataset is **CC BY-NC-SA 4.0**
  (non-commercial). A fork must stay GPLv3. Using the dataset commercially is not allowed.
- `CONTRIBUTING.md`: the project has a single maintainer, "large or architectural changes are unlikely
  to be accepted", significant work should be discussed first, and small focused PRs are preferred.
- FairScan's stated philosophy is "no manual adjustments" and "not intended to provide fine-grained
  manual control". The owner's priorities (manual corner adjustment, quality over speed) are
  compatible with the existing crop screen, but anything adding options may not be welcome upstream.
- If the owner plans to install a fork alongside the official app or distribute it, change
  `applicationId` (`org.fairscan.app`) and the app name.
- **Ask the owner** which route they want (private fork, public fork, or upstream PRs) before
  making structural changes. It decides how far each item below can go.

## 4. What FairScan already does better (do not port these from opendocscanner)

| Topic | FairScan | opendocscanner |
| --- | --- | --- |
| Detection | Segmentation model (DeepLabV3+/MobileNetV2, LiteRT, 256x256 input, [-1, 1] input range), multi-threshold probability maps, contour split into 4 dominant segments, line fitting and intersection, candidate scoring against the probability map, min-area-rect fallback (`imageprocessing/.../DocumentDetection.kt`, `quad/*`) | Classic Canny + `approxPolyDP`; 31 % "no touch-up" on the owner's photos |
| Live preview detection | `ImageAnalysis` + `QuadStabilizer` | None |
| Output size | Aspect ratio from vanishing points and focal length; physical size snapped to A4/Letter/etc. when the camera reports subject distance (`Perspective.kt:115`) | Longest-edge heuristic |
| Enhancement | Multi-scale Retinex, Sauvola binarization, automatic color detection (`PostProcessing.kt`, `ColorDetection.kt`) | Only planned |
| Magnifier and corner editing | Exists: relative drag, convexity guard, "lift wiggle" rollback, loupe above the finger | Similar; see section 5 for the differences that matter |
| Also | Multipage, PDF, OCR, reordering, scan-to-PDF intent, translations | None |

FairScan's native libraries are already **16 KB page-size compatible**. The arm64 `.so` files of
OpenCV 4.12.0, LiteRT 1.4.1 and tesseract4android 4.9.0 all have `p_align 0x4000`, verified with
`llvm-readelf`.

## 5. Opportunities, by priority

### P1. Quantitative detection benchmark with ground-truth corners
**Finding.** `evaluation/` only produces visual before/after HTML (`QuadDetectionEvaluator.kt:43`,
`DatasetEvaluator.kt`): there are no ground-truth corners and no metric. So a detection change can't
be shown to be better or worse, and the model card only reports a segmentation Dice score (> 0.94),
which is not the same as "the user does not need to fix the corners".

**What opendocscanner has** (`opendocscanner/tools/detection-lab/`, Python, documented in its README):
- An annotation format: one JSON per photo with the 4 corners in pixels of the EXIF-oriented image,
  TL/TR/BR/BL, plus categories (`detlab/annotations.py`).
- An annotation tool with a magnifier, detector-proposed corners, autosave and categories
  (`scripts/annotate.py`, `detlab/annotator.py`).
- Metrics (`detlab/metrics.py`): quad IoU, max corner error normalized by the document diagonal, and
  the **"no touch-up needed" rate** (max corner error ≤ 2 % of the diagonal), which is the number that
  matches the owner's goal. It also gives a per-category breakdown, median latency, and an HTML
  failure report sorted worst first (`scripts/evaluate.py`, `detlab/report.py`).
- **The owner's annotated photos: 41 real photos with corners and categories** in
  `opendocscanner/tools/detection-lab/data/private/`. These are private documents: never commit,
  upload or share them. `evaluation/.gitignore` here already ignores `dataset/images`, `dataset/masks`
  and `dataset/metadata`.

**Suggested approach.** Add the metrics to the Kotlin `evaluation` module, which already runs
`detectDocumentQuad` on the JVM: read the same JSON format and compute the same three numbers per
image and per category. Then:
1. Generate masks for the owner's photos with `evaluation/python/generate_masks.py`.
2. Measure FairScan's detector on them. This is the first number the owner will want, to compare with
   the 31 % of opendocscanner's classic detector (that figure came from the first 29 photos).
3. Use the benchmark to accept or reject every detection change below.

Ground-truth corners for the public dataset could be derived from its LabelMe masks (fit a quad to
each mask), but check the result by hand on a sample first.

### P1. Privacy: documents can reach Google's cloud backup
**Finding.** `app/src/main/AndroidManifest.xml:18` sets `android:allowBackup="true"`, and
`res/xml/data_extraction_rules.xml` / `backup_rules.xml` are still the empty templates (`TODO`). Auto
Backup includes the app's `files/` directory, which holds the source photos (`sources/`) and the
processed pages (`scanned_pages/`). On a device with backup enabled, scans can be uploaded to the
user's Google Drive backup. That contradicts the owner's "local only" requirement and arguably
FairScan's own privacy promise.

**Fix.** Use `allowBackup="false"` plus extraction rules that exclude everything (see
`opendocscanner/app/src/main/res/xml/data_extraction_rules.xml`), or exclude only the document
folders if settings should survive a device transfer.

**Related**
- `INTERNET` (`AndroidManifest.xml:12`) exists only to download Tesseract language data. For a strictly
  offline fork: bundle the needed `traineddata` files or let the user import them, then remove
  `INTERNET` with `tools:node="remove"`. Ask the owner first: OCR language download is a real feature.
- `res/xml/file_paths.xml:6` exposes `external-path path="."` (the whole shared storage) through the
  FileProvider. Only explicitly granted URIs are readable, so this is least-privilege hygiene, not a
  leak. opendocscanner shares through a dedicated `cache/shared/` folder holding one file at a time.
- **Automate the permission check.** FairScan removes `ACCESS_NETWORK_STATE` (added by CameraX through
  `media3-common`) by hand. opendocscanner has a Gradle task that fails the build when the merged
  manifest contains a forbidden permission: `VerifyNoInternetTask` in
  `opendocscanner/app/build.gradle.kts`, hooked into `check` for every variant. Extending it to an
  allow-list (only `CAMERA`, plus `WRITE_EXTERNAL_STORAGE` ≤ 28) would catch the next dependency
  that sneaks a permission in.

### P2. Crop screen memory: the full photo is decoded twice
**Finding.** `MainViewModel.kt:263` decodes the whole source JPEG with
`BitmapFactory.decodeByteArray`, and `MainViewModel.kt:266` creates a rotated full-size copy. At
12 MP that is about 48 MB per ARGB bitmap, ~96 MB at the peak, and roughly four times more on
50 MP sensors. The magnifier needs full resolution only around the dragged corner.

**What opendocscanner has**
- `opendocscanner/core/imaging/.../RegionDecoder.kt`: decodes only a rectangle of the photo with
  `BitmapRegionDecoder`, expressed in **oriented** coordinates, handling all 8 EXIF orientations.
- `opendocscanner/core/geometry/.../ExifGeometry.kt`: the pure mapping between stored and oriented
  coordinates, with exhaustive JVM tests.
- `opendocscanner/app/.../ui/editor/MagnifierSource.kt`: decodes a window twice the visible area and
  reuses it while the corner stays inside it, with an automatic power-of-two downscale for very large
  photos.

The editor itself then only needs a screen-sized bitmap.

**Lesson (a real crash on the owner's phone).** Never `recycle()` a bitmap the UI may still draw.
opendocscanner crashed with `Canvas: trying to use a recycled bitmap` when a long drag left the cached
window. The fix is commit `cadab59`, with a deterministic regression test in
`MagnifierSourceTest.kt`. The emulator was too fast to show the race; the real phone showed it at
once.

### P2. Detection: corners outside the frame make the whole detection fail
**Finding.** `DocumentDetection.kt:57` returns `null` when any vertex lies outside the image. The
line-intersection step can legitimately put a corner outside the frame (a corner cut off by the
image border or slightly over it), and then the result is "no document" instead of a nearly correct
quad. Only `Mode.CAPTURE` has the min-area-rect fallback, and the fallback only runs when no vertices
were found at all.

**Idea.** Clip slightly-outside corners to the image instead of rejecting them (opendocscanner's
validation clips sub-pixel overshoot, `opendocscanner/core/geometry/.../Validation.kt`), or allow a
margin. **Measure it with the P1 benchmark first**: clipping a corner that is really out of frame
changes the geometry, so it may help or hurt depending on the photos.

### P2. Turn the owner's corrections into labelled data
**Finding.** "Report a problem with last captured image" (`ui/screens/about/Emails.kt:31`) e-mails the
original photo to the maintainer. FairScan already stores the detected quad and the user-corrected
quad per page (`ImageRepository.setUserQuad`, `ImageRepository.kt:184`).

**What opendocscanner has.** A debug-only "Export test case" that zips the untouched original (EXIF
included) and a JSON in the benchmark's annotation format, with the corrected corners as ground truth
and the detector's answer kept in `detected_corners` (`opendocscanner/app/.../data/CaseExporter.kt`).
In a fork, every time the owner fixes corners, that photo can become a benchmark case or a training
example.

### P3. Smaller items
- **Preprocessing parity.** `ImageSegmentation.kt:72` has `NormalizeOp(127.5f, 127.5f) // TODO check
  if it's correct`. It is consistent with `evaluation/python/generate_masks.py:28`
  (`img / 127.5 - 1.0`). Turn that into a test: commit a few reference images with the expected
  probability maps and assert that Android's preprocessing and inference reproduce them within a
  tolerance. opendocscanner planned the same thing as "reference vectors".
- **EXIF on Android 8.0/8.1 imports.** `AndroidImageLoader.kt:46-49` uses `ImageDecoder` on API 28+
  (it applies EXIF orientation) and plain `BitmapFactory` below (it does not). Imported photos with an
  EXIF rotation may come out sideways on API 26-27. Verify on an API 26 emulator before fixing; the
  fix is to read the tag with `androidx.exifinterface` and rotate (see
  `opendocscanner/core/imaging/.../ImageLoader.kt`).
- **No instrumented tests** (`app/src/androidTest` does not exist; there are ~120 JVM tests). Compose
  UI tests for the crop screen would have caught the class of bug described in P2. Useful patterns
  from opendocscanner: Espresso-Intents to stub the photo picker, `GrantPermissionRule` for the
  camera, and `androidx.test.espresso:espresso-core:3.7.0` pinned explicitly, because the 3.5.0 pulled
  by Compose crashes on Android 16+ (`InputManager.getInstance`).
- **Touch radius in pixels.** `QuadEditingHandler.kt:26` `CORNER_TOUCH_RADIUS = 90f` is in pixels, so
  the touch target shrinks on denser screens (≈ 64 dp across at 440 dpi, ≈ 51 dp at 560 dpi). Use dp.
- **Magnifier.** It draws the quad edges but no mark on the exact corner position. opendocscanner
  draws a cross at the center and renders through a single transform for sub-pixel alignment
  (`opendocscanner/app/.../ui/editor/CornerMagnifier.kt`). This is a matter of taste: ask the owner.
- **Capture.** `CameraPreview.kt:208,217` targets about 4400x3300 with `CAPTURE_MODE_MINIMIZE_LATENCY`,
  so speed is chosen over quality. The owner prioritises quality: consider
  `CAPTURE_MODE_MAXIMIZE_QUALITY` and confirm on the real phone (its main camera tops out at
  4096x3072 for 4:3).
- **Do not upgrade to the OpenCV 5.0.0 AAR** without checking alignment. Its `libc++_shared.so` is
  4 KB aligned, and Android shows a "not 16 KB compatible" dialog on 16 KB devices. Verified while
  building opendocscanner; 4.12/4.14 are fine.
- `DocumentDetection.kt:193` uses `warpPerspective` with pixel-edge corners and the default bilinear
  interpolation. opendocscanner uses a half-pixel (pixel-center) correction and `INTER_CUBIC`
  (`PerspectiveWarp.kt`), but **the difference was never measured**. This is minor: only change it if
  the owner sees soft text in results.

## 6. What from opendocscanner is not worth porting
The classic OpenCV detector, the warp-size heuristic, the planned filters (FairScan's are far ahead),
the detector registry and detector selection in settings (FairScan has one model), and the
single-page result/share flow.

## 7. Where things are in opendocscanner
- `tools/detection-lab/`: Python benchmark, annotation tool, metrics, failure report, candidate review
  (`docs/decision.md`, `docs/detector-contract.md`).
- `core/geometry/`: `Quad`, `ExifGeometry`, `Validation`, `WarpSize`, with JVM tests.
- `core/imaging/`: `ImageLoader` (EXIF), `RegionDecoder`, `PerspectiveWarp`, `JpegWriter`, `ImageImporter`,
  with instrumented tests.
- `app/`: `QuadEditor`, `CornerMagnifier`, `MagnifierSource`, `CaseExporter`, `ShareHelper`,
  `ScanStore`; `app/build.gradle.kts` holds `VerifyNoInternetTask`.
- `openspec/specs/`: behaviour contracts that can serve as acceptance criteria for a fork:
  `corner-adjustment`, `local-only-operation`, `detection-diagnostics`, `perspective-correction`,
  `document-capture`, `document-detection`.
- The code there has no license yet. It is the owner's own work, so they can relicense it under GPLv3
  for this fork; confirm with them before copying files.

## 8. Suggested first steps
1. Ask the owner: fork or upstream PRs? Must OCR language downloads (`INTERNET`) stay?
2. Build and run FairScan on the emulator (`ANDROID_SERIAL=emulator-5554`). The build downloads the
   model from GitHub.
3. P1 benchmark: measure FairScan's detector on the owner's 41 annotated photos and report the
   "no touch-up" rate, overall and per category (especially `low-contrast`).
4. P1 privacy: disable cloud backup and add the manifest permission check.
5. Then P2, each change justified with the benchmark numbers or a reproducible test.
