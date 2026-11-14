# 3D experiences

Added 12 November 2026 against requirements v0.3/v0.5 Appendix F. This slice delivers the five customer-facing 3D feature families (FR-3D-01 to FR-3D-05) with commerce continuity and HTML fallbacks (FR-3D-06, FR-3D-10). The staff approval pipeline (FR-3D-07 to FR-3D-09) was added later the same day; see [3D model records and approval](3d-model-assets.md), which supersedes the manifest and "Not done" notes below where they differ.

## After pulling this change

Two packages were added, so the frontend needs a fresh install before it will start:

```powershell
cd frontend
npm ci
```

Then restart **Full Stack - local**. No backend, migration or database change is involved.

## What exists

| Feature | Where | What it does |
| --- | --- | --- |
| Product viewer (FR-3D-01) | Product page, **3D view** button beside **Photos** | Drag or use buttons to turn, look higher/lower, zoom within limits and reset. The model follows the selected option. Photos stay the default. |
| Configurator (FR-3D-03) | `#/3d/configure`, and **Configure in 3D** on products with more than one option | The options are the product's real variants. Each shows SKU, price and stock. The model, the selectors and the address stay in step. An out-of-stock or quote-only option can be viewed but not added to the cart. |
| Room visualizer (FR-3D-02) | `#/3d/room` | Living room and bathroom templates. Floor, wall and lighting options are catalogue products. Pressing a surface in the picture jumps to its options. Reset, and a list of the selected products with prices and add-to-cart. The room is remembered for the browser tab. |
| Virtual showroom (FR-3D-04) | `#/3d/showroom` | Five guided viewpoints with bounded camera movement, selectable displays, a buying panel, and an ordinary list of every product on display. |
| Visual effects (FR-3D-05) | Home page banner and the `#/3d` hub hero | Slowly turning products beside the heading, never behind text. |

Navigation: **3D Experience** in the header bar and footer, a home page banner, and a link from the design page.

## Rules the code keeps

- **Prices, units, stock and variant IDs come only from the catalogue API.** The cart receives the same `productId`/`variantId` as the normal product page, and the cart and checkout re-check them. Nothing is read from a mesh.
- **Every model is illustrative.** `src/experiences/manifest.ts` marks all entries `verified: false`, and each view carries a note saying the model is not the exact branded product. The models are simple shapes written in code (`models.tsx`) and the surface pictures are painted in code (`textures.ts`), so no asset was downloaded, bought or licensed.
- **Only mapped products get 3D.** The manifest is keyed by product SKU. A product that is unpublished disappears from every scene, and a remembered room choice that is no longer published falls back to "none".
- **Three.js is never in the first download.** Each page and the product viewer load on request; the home banner loads its effect only when it is about to scroll into view. Checked: browsing the shop, a product's photos and the cart requests no 3D code.
- **3D is optional.** "3D views and effects: On/Off" (footer and hub) is remembered per browser. With it off, with no WebGL, or after the graphics context is lost, the same pages show their lists and buying controls, and a lost view can be reloaded without losing selections.
- **Motion.** Scenes draw only when something changes. The animated effect pauses off screen and in a hidden tab, and holds still under the operating system's reduced-motion setting, where camera moves also become instant.
- **Controls.** Every camera action has a visible button, so nothing depends on dragging, hovering or colour alone.

## Structure

```text
frontend/src/shared/three/      Canvas wrapper, orbit camera, camera buttons, 3D on/off preference
frontend/src/experiences/
  manifest.ts                   Versioned SKU -> model / room finish / showroom zone map
  models.tsx, textures.ts       Illustrative shapes and painted surfaces
  BuyBox.tsx                    HTML price, stock, option and add-to-cart block
  ExperienceRoute.tsx           Lazy routes under #/3d
  product-viewer/ configurator/ room-visualizer/ showroom/ effects/ hub/
```

Dependencies (exact versions, both MIT-licensed, free): `three` 0.187.0 and `@react-three/fiber` 9.8.2, plus `@types/three` 0.187.0 for development.

## Adding or changing a 3D product

1. Add the product SKU to `models` in `manifest.ts` with a `kind`, a showroom `zone` and, if it can dress a room, a `room` entry. Record `tileMetres` and set `scaleConfirmed: true` only when the real tile size is known.
2. For a product whose options differ in size, list each variant SKU under `variantScale`.
3. Change `manifestVersion`.
4. Run `npm test`; `manifest.test.ts` checks the entries.

The manifest currently covers the 12 sample products (`FPA-SAMPLE-001` to `-012`). **When the real catalogue replaces the samples, these SKUs must be remapped, or no product will show 3D.**

## Checks run

On a copy of the frontend, not inside the user's IntelliJ run:

- `tsc --noEmit` and ESLint (zero warnings) passed. 17 unit tests passed (12 existing, 5 new manifest tests). Standard and Pages builds succeeded. The main bundle grew from 335 kB to 343 kB; the 3D code is a separate 931 kB file (246 kB compressed) loaded on request.
- Scripted Chrome runs against the Pages preview build with software WebGL, at 1440 and 390 pixels wide: no script errors and no horizontal overflow on any 3D page. Verified: viewer buttons and option change; configurator option, address update, add to cart and the out-of-stock block; room options, product list, tab restore and surface press; showroom viewpoints, list-to-display selection and add to cart by keyboard; 3D off; reduced motion; forced context loss and reload; 24 repeated page mounts. Screenshots were inspected.

Not checked: a real phone or a low-end Android device, real GPU performance and frame pacing, the live Spring Boot backend (the preview build's sample API was used), Firefox and Safari, and screen-reader behaviour. The requirements' numeric performance targets (NFR-3D-03) are still unset. No Playwright spec was added to the project suites, because headless WebGL support on the development machine is unconfirmed.

## Not done

- **Staff asset management (FR-3D-08, FR-3D-09)**: upload, validation, review, Owner approval, publication and the `assets` backend module with its tables. Until then the manifest is a source file changed by a developer.
- **Real product models (GLB)** and the Khronos validation in AT-3D-02. There are no approved assets yet, so nothing can be labelled verified.
- **Configuration snapshot on the order (FR-3D-07)**: the order stores the variant as before; it does not yet store the manifest version.
- **Colour, finish and material options** in the configurator need catalogue products that sell those variants. Among the samples only the cement has two options.
- Open decisions OD-3D-01 to OD-3D-08 (launch coverage, room templates, showroom size, effect placement, device matrix and budgets) are still the client's to confirm. Saved designs, share links, room photos, AR and quantity estimates remain out of scope.
