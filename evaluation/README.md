# Tools to evaluate the image processing pipeline

## Preparing the environment

### Python

```bash
cd python
python -m venv venv
source venv/bin/activate         # venv\Scripts\activate on Windows
pip install -r requirements.txt
```

### Images

Put JPEG images in `dataset/images`.


## Comparing filters

`FilterEvaluator` renders every filter (Original, Color, Lighten, Enhance, No shadows, Grayscale,
Black and white, Negative) for each photo of a folder, with neutral adjustments and with a few
sample values for each control the filter exposes. It writes the images and a side-by-side page to
`reports/filters/index.html`, which is meant to tune the filters and their default constants by eye.

Run its `main` from the repository root (in the IDE, set the working directory to the repository
root), optionally passing:

- the folder with the JPEG photos (default `evaluation/dataset/images`). For each `IMG.jpg`, the
  document is cropped with the quad detected from `../masks/IMG.png` when that mask exists, and the
  whole photo is used otherwise, which is handy for photos that are already cropped;
- `--neutral-only` to render only the neutral version of each filter, which is much faster.

`reports/` is ignored by git. For private photos, keep them in a folder outside the repository and
pass its path: nothing outside `evaluation/dataset/images`, `masks`, `metadata` and `reports` is
ignored, so never leave them inside the project tree.
