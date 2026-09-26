# Prepare cloud spatial wallpapers

`scripts/prepare_cloud_spatial.py` downloads a CSV batch, runs server-side ML,
then creates portable spatial asset folders and local scene manifests. It only
generates files and SQLite metadata; it does not need a publishing URL, upload
files, or update your server APIs.

## Installation

Use a dedicated Python **3.11** environment on a Linux GPU machine. Install
PyTorch and torchvision using the matching command from
[PyTorch](https://pytorch.org/get-started/locally/), then install:

```sh
python3.11 -m venv .venv-spatial
. .venv-spatial/bin/activate
# Install a compatible torch/torchvision CUDA pair here.
python -m pip install -r scripts/requirements-spatial.txt
```

Minimal Linux containers may also need system packages `libgl1` and
`libglib2.0-0` for the OpenCV dependency used by DA3.

DA3 brings additional dependencies including xformers, Open3D and pycolmap.
Its dependency resolver can change your Torch installation: check CUDA
availability after installation (`python -c 'import torch; print(torch.cuda.is_available())'`).
The dependency file pins the model integration versions and DA3 source revision;
it is not a full transitive lockfile. Freeze the successfully validated environment
for repeatable production batches. The pipeline uses POSIX file locks (Linux/macOS).
CPU inference is supported by the adapter but is slow, and upstream dependency
availability varies by platform. MPS is not selected automatically.

First use downloads several large checkpoints, including the roughly 206 MB
LaMa checkpoint. Model files are cached under `OUTPUT/models`, or `--model-cache`.
Only one inference model occupies the accelerator at a time; inactive models
remain in CPU memory. Allow several GB of RAM and accelerator memory. Exact
hardware requirements depend on source dimensions; lower `--max-size` if
inpainting exhausts memory. Depth and mask inference use 1008 and 2048 pixel
processing resolutions respectively.

## Input and command

```csv
id,url
101,https://images.example.com/wallpapers/101.jpg
102,https://images.example.com/wallpapers/102.jpg
```

```sh
python scripts/prepare_cloud_spatial.py wallpapers.csv \
  --output spatial-output \
  --device cuda
```

No base URL is required. IDs may contain ASCII letters,
numbers, underscores and hyphens, up to 128 characters, starting with a letter
or number. Use the same numeric IDs as your wallpaper API. Duplicate identical
rows collapse; conflicting IDs and invalid rows reject the whole CSV before
processing. Sources must be unauthenticated HTTP(S) image URLs; do not put
credentials in URLs. Logs and SQLite preserve source URLs, including query strings.
Use trusted input CSVs: fetching arbitrary URLs is not an isolated web service.

Options:

- `--max-layers 2|3|4`: maximum total layers, default 4. Actual count is adaptive.
- `--max-size 1024|1536|2048`: maximum output dimension, default 2048; preserves aspect ratio without upscaling.
- `--db /path/wallpapers.sqlite3`: SQLite location (default inside output).
- `--model-cache /path/models`: share downloaded checkpoints between batches.
- `--refresh`: fetch source images again, detecting changes at an unchanged URL.
- `--device auto|cuda|cpu`: auto selects CUDA when available, otherwise CPU.

Use one writer per output/SQLite database. Shared model caches should be populated
before concurrent jobs; the LaMa cache download is not independently process-locked.

## Generated output

```text
spatial-output/
  assets/101/<version>/
    scene.json
    layer-0.png       # opaque, inpainted background
    layer-0.depth
    layer-1.png       # full-canvas RGBA foreground
    layer-1.depth
    ...              # at most four layers total
  sources/           # private downloaded originals
  models/            # private ML checkpoints
  wallpapers.sqlite3 # private state and metadata
  wallpapers-ready.csv
```

Copy `assets/` to your server when ready. `wallpapers-ready.csv` maps each
successful input ID and source URL to `scenePath`, relative to the output directory
(e.g. `assets/101/<version>/scene.json`). SQLite uses the same relative paths, so
moving the output directory does not require rewriting metadata.

Each local manifest has `version: 1` and back-to-front `layers` with relative
`imageUrl` and `depthUrl` filenames, resolved relative to its own folder:

```json
{"version": 1, "layers": [{"imageUrl": "layer-0.png", "depthUrl": "layer-0.depth"}]}
```

Your existing server handles public URLs. **The current Android loader requires
absolute HTTPS URLs inside the served manifest**: the server must resolve these
local filenames to its asset URLs when producing the API response/served manifest,
and provide that manifest's URL as `spatialSceneUrl`. The local manifest is a
portable generation artifact, not a directly consumable Android API response.

All layers share canvas dimensions. Meshes contain `48 96` followed by 4,753
normalized depth values (near = 1). Output bounds fit the app's 2048-pixel decoding
limit and asset byte limits. Directories are staged and renamed before SQLite
marks a scene ready. Existing generated versions are retained.

## SQLite, resume and failure behavior

`wallpapers` holds one current record per ID: input URL, source checksum/path,
status, configuration hash, relative manifest path, dimensions, layer count, quality
flags, model provenance, per-file SHA-256 checksums, last error and update time.
`attempts` stores attempt history, stage, status, timestamps and errors. Schema
version is tracked by `PRAGMA user_version`. The legacy `manifest_url` column is
retained for database compatibility and is NULL for newly generated scenes. Back up SQLite using its backup API
or after the process exits; copying only the database during WAL writes is unsafe.

Re-running the same command verifies generated file checksums and skips valid
completed scenes without loading models. Failed/interrupted items retry; intact
original downloads are reused. Changing a URL, generation settings or pipeline
version regenerates assets. By default, an unchanged source URL is assumed to
still identify the same image; use `--refresh` to detect changed contents.
Even after refresh, unchanged source bytes reuse valid assets. Corrupt assets
are rebuilt in a new version folder. Do not manually alter model files in cache.
Keep the output directory and SQLite together when relocating a batch.

Per-wallpaper errors do not stop subsequent rows. Exit code is 0 on success,
1 for failures, and 130 on Ctrl-C. The mapping CSV includes only ready rows
from the current input. Asset folders are finalized atomically on a local filesystem;
this is not an object-storage transaction or a guarantee against disk hardware
failure. No automatic cleanup of originals, checkpoints or old versions occurs.

## Model choices and quality

- [Depth Anything 3 MONO-LARGE](https://huggingface.co/depth-anything/DA3MONO-LARGE):
  Apache-2.0 monocular model, pinned to `f465978e618db8cc79c83b8bbf24964857db1875`.
  Its distance predictions are inverted and normalized for Comfer's near=1 convention.
- [BiRefNet HR](https://huggingface.co/ZhengPeng7/BiRefNet_HR): MIT foreground
  segmentation, pinned to `a7a562f6fd16021180f2f4348f4de003a2d3d1e1`.
  Hugging Face custom model code runs with `trust_remote_code=True` at that
  immutable revision. Review that pinned code before deploying in your infrastructure.
- [LaMa](https://github.com/advimman/lama): Apache-2.0 background inpainting,
  using the [community TorchScript conversion](https://github.com/enesmsahin/simple-lama-inpainting)
  from its v0.1.0 release. This is not an official DA3 or BiRefNet component.
  Its downloaded checksum is recorded in provenance; the release asset does not
  supply a pinned upstream digest. Preserve license notices with redistributed
  model code/weights and check rights to the input wallpapers separately.

The script dilates the foreground removal mask and inpaints the background so
motion can reveal plausible hidden pixels. Pixels outside that region are kept.
Separate substantial foreground components can occupy additional depth layers;
connected silhouettes are not cut into arbitrary depth bands. This is foreground
segmentation, not complete semantic scene decomposition: more layers are not
guaranteed for every wallpaper. Near and far layers use bounded depth ranges,
and mesh slopes are constrained to avoid foldovers.

Tiny/almost-full foreground masks produce a single depth mesh and a quality flag.
Large inpaint regions also receive a review flag. Model errors fail the row rather
than silently replacing ML output with a synthetic effect. Hidden backgrounds
are hallucinated: inspect representative portraits, hair, foliage, glass,
landscapes and large foreground subjects at maximum motion on a device before
publishing a collection. Very thin structures and overlapping subjects can still
show artifacts. No change to the launcher's apply-without-preview flow is involved.

## Verification

Pipeline tests use deterministic injected model outputs, exercising the output
contract, alpha preservation, depth orientation, caching, changed inputs,
interruption, corruption and failure recovery:

```sh
python -m unittest discover -s scripts/tests -p test_prepare_cloud_spatial.py -v
```

These tests require only NumPy, Pillow, OpenCV and requests. They do **not** establish
real model quality or CUDA compatibility. Run an actual small CSV on the deployment
machine and inspect its assets before scheduling a full production collection.
