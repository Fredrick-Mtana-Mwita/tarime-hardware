# 3D model records and approval

Added 12 November 2026 for requirements FR-3D-07, FR-3D-08 and FR-3D-09. It replaces the hand-edited frontend table: the storefront now shows only the 3D models the Owner has published.

## After pulling this change

1. Restart **Full Stack - local**. Flyway applies `V12__model_assets.sql`, and in development also `R__sample_model_assets.sql`, which publishes the built-in shapes for the 12 sample products so the 3D pages keep working.
2. No new packages were added.

## Who can do what

| Action | Owner | Inventory | Sales, Delivery |
| --- | --- | --- | --- |
| See the **3D models** page, create and edit drafts, upload a file, preview, send for review | Yes | Yes | No |
| Publish, return to draft, retire, approve as the exact product | Yes | No | No |

The server enforces this on every call; hiding the buttons is only a convenience. Sales staff and customers see published models through the storefront.

## Life of a model

`DRAFT` → `IN_REVIEW` → `PUBLISHED` → `RETIRED`, one row per version of a product's model.

- A draft is either a **built-in shape** (one of the 12 drawn in code) or an **uploaded GLB file**.
- Before review the preparer must record the source, the licence terms and tick the rights confirmation. A GLB draft also needs its file.
- The Owner publishes or returns it with a note. Publishing retires the product's previous live version in the same transaction, so a product never has two live models.
- **Approve as the exact product** is possible only for an uploaded GLB with measured width, height and depth. Everything else is labelled illustrative on the storefront.
- Rows are never deleted. Each action writes an audit event (`MODEL_DRAFT_CREATED`, `MODEL_SUBMITTED`, `MODEL_RETURNED`, `MODEL_PUBLISHED`, `MODEL_REPLACED`, `MODEL_RETIRED`).
- Edits carry the record's `revision`; a stale one is refused with "changed by someone else".

## Upload checks

A file is refused (HTTP 422, nothing stored) unless it is:

- binary glTF 2.0 with a consistent header, one JSON chunk and at most one binary chunk, no trailing bytes;
- at most 15 MB, read with a hard limit;
- self-contained: no buffer or image that points to another file or address;
- free of required extensions the storefront cannot draw (Draco, Meshopt and Basis compression are refused);
- not empty: at least one mesh.

Optional unknown extensions, animations and cameras are accepted with a warning shown to staff. Files are stored outside the web root in `.local/model-assets` (override with `fpa.models.dir`), named by their SHA-256, never by the uploaded name, and served as `model/gltf-binary` with `nosniff`. Draft files are readable by Owner and Inventory only; published and retired files are public so that old order references still resolve.

**This check is not the Khronos glTF Validator and does not judge appearance or accuracy.** AT-3D-02 still requires running the validator and comparing the model with the real product before ticking "exact product".

## Storefront

- `GET /api/models/manifest` (public) lists published models of products that are currently on sale: asset ID, version, built-in kind or file address, verified flag, showroom area, room use, tile size and per-option sizes keyed by variant ID.
- The frontend reads it in `useModelManifest.ts`. Entries with a shape, area or finish this build does not know are skipped, and a file address is accepted only in the exact form `/api/models/<id>/file`.
- Uploaded models are loaded with three.js `GLTFLoader`, centred, stood on the ground and scaled to about one metre, so they fit the viewer, configurator and showroom. If a file fails to load a plain block is shown and buying still works.
- The client preview build has no server and keeps using the sample table in `manifest.ts`.

## Orders

`customer_order_items` gained `model_asset_id` and `model_asset_version`. A database trigger fills them at insert with the product's live model, so an order line records which 3D version the customer could see. The order code was not changed. These columns are not yet shown on any staff or customer screen.

## Higgsfield

Higgsfield's free plan allowed image-to-3D. One unbranded paint tin picture was generated and converted to a GLB (1.88 MB, one mesh, embedded texture, no required extensions, so it meets the upload checks). `.\scripts\fetch-model-samples.ps1` saves it to `.local\higgsfield-models\paint-tin.glb` for upload through the staff page. It was generated from an invented picture, so it is an illustrative model and must not be approved as the exact product. Cost: 2.5 credits; 2.88 remained.

## Checks run

On a copy of the project, not inside the user's IntelliJ run. The backend was compiled with JDK 21 against the project's own downloaded libraries and tested on a throwaway PostgreSQL 16.

- `ModelAssetsIT`: 5 tests passed (roles, full workflow, replacement and retirement, 10 kinds of bad file, draft-field validation, public/private file access, order snapshot, stale revision, idempotent create). Architecture tests passed.
- Whole backend suite: 55 of 62 passed. The 7 failures are the same 7 that fail without this change: tests in `InventoryIT`, `CustomerOrdersIT` and `StaffAccessIT` that still use the Mwanza branch, which uncommitted `V11__retire_mwanza.sql` now blocks.
- A clean database migrated V1 to V12 with the development seeds, and the real application started and served the manifest.
- Browser run against that real backend: Inventory signed in, created a draft, had a fake file refused, uploaded a test GLB, was blocked from sending without the rights tick, saved, previewed and sent for review; a direct publish call by Inventory returned 403; the Owner signed in with an authenticator code and published as exact; the storefront product page and showroom then drew the uploaded model with the "approved" note.
- Frontend: `tsc`, ESLint, 17 unit tests and both builds passed; the 3D pages were re-run on the preview build.

Not checked: Maven itself (`mvnw verify`) on this machine, the Higgsfield GLB drawn in the app (it could not be downloaded into the test environment), real phones, and large real-world GLB files.

## Not done

- No screen to edit which products appear in the showroom beyond the area field, and no texture or environment asset types; a model is one GLB.
- No automatic Khronos validation, mesh or texture compression, or thumbnail generation.
- The order's recorded model version is stored but not displayed.
- Production storage: files are on the application's local disk. A hosted deployment needs a persistent volume or object storage and a backup plan.
