# Storefront updates (November 2026)

Ideas taken from a study of xerinmarket.com, adapted to a single hardware shop that confirms every order.

## What changed

**Batch 1: shopping**
- Phone bottom menu: Home, Categories, Cart (with count), Account.
- Side cart from the header or the phone menu, with − / + per item.
- After adding, the product page shows "N in cart" with − / +.
- "Only N left" when 10 or fewer are available.
- Price bands in the shop filter; a custom minimum and maximum are still available.
- "More in [category]" on product pages.
- Delivery box on product pages.

**Batch 2: Kiswahili**
- First-visit language choice and an SW | EN switch, remembered in the browser.
- The whole customer side is translated. The text lives in `frontend/src/shared/i18n/sw.ts`, with English as the key.
  A unit test fails if any `t("…")` text has no Kiswahili version.
- Optional Kiswahili product names (staff catalogue → "Kiswahili name"), shown under the English name and included in search.
- Kiswahili names for all 17 categories (migration V16).
- Staff screens, database descriptions and the body text of server updates stay in English.

**Batch 3: contact and delivery**
- Footer with phone, WhatsApp, email, address and hours, plus a floating WhatsApp button.
- Returns, privacy and terms pages at `#/policies/returns`, `#/policies/privacy` and `#/policies/terms`.
  The draft text is in `frontend/src/shared/policies.ts`.
- Checkout address in parts: area, house/street/plot, nearest landmark, and an optional "Add my current location".
  These are combined into the order's single address field, for example:
  ```
  Area: Nyamisangura
  Plot 12, blue gate
  Landmark: Opposite the market
  GPS: -1.34917,34.36731 https://maps.google.com/?q=-1.34917,34.36731
  ```
  The staff order page and the driver's job card show it line by line, with an "Open location in maps" link.
  That link is built from the numbers only.

## What the shop must fill in before launch

Everything is in `frontend/src/shared/shopSettings.ts`. Each group stays hidden, or is shown in a safe generic form,
until it is set to `confirmed: true`.

| Setting | Until confirmed | Needed from the shop |
|---|---|---|
| `delivery` | Generic "delivery or pickup" text, no time promise | Same-day cut-off time, delivery days, area name |
| `contact` | No contact block, no WhatsApp button | Phone, WhatsApp number (format 2557…), email, address, opening hours |
| `policies` | Policy pages say "being prepared"; no footer links | Approve or rewrite the drafts in `policies.ts` (a lawyer should check) |
| `deliveryAreas` | Customers type their area | The real list of areas or wards served |
| `lowStockDisplay` | Shows "Only N left" at 10 or fewer | Change the number if wanted |

Also needed:
- A Kiswahili speaker should review `sw.ts`.
- Staff should add Kiswahili names to real products as they enter them.

## Not done, on purpose

- No map picker: it would need a paid maps service. The phone's own location is used instead.
- No "often bought together": the shop needs to decide which items go together first.

## Category and 3D hub photos

Free stock photos used under the Pexels and Unsplash licences (free use, no attribution required), saved in
`frontend/public/images`. Replace them with the shop's own photos when it has them.

- Category fallbacks for the "All categories" panel (`cat-power-tools.jpg` … `cat-cleaning.jpg`): Pexels numbers are
  listed in `frontend/src/shared/categoryImages.ts`. A category's own uploaded photo always wins.
- 3D hub cards (`#/3d`):

| File | Source |
| --- | --- |
| `x3-showroom.jpg` | Pexels photo 37184393 (pendant lights in a modern interior) |
| `x3-room.jpg` | Unsplash photo-1774199496664-a9690967be5a (modern living room with fireplace) |
| `x3-configurator.jpg` | Pexels photo 7828186 (stone tile samples) |
| `x3-product-view.jpg` | Pexels photo 8335680 (chrome socket wrenches, studio shot) |

## Realistic 3D models

The 3D pages (home banner, 3D hub, showroom, room visualizer, configurator and product 3D views) use realistic
models instead of the built-in shapes wherever a free model of that product type exists. All are CC0 assets from
Poly Haven (https://polyhaven.com/license), free for any use, converted to compact GLB files with WebP textures.
They show the type of product, not the exact brand. A model the Owner uploads for a product always wins.

| File | Poly Haven asset | Used for |
| --- | --- | --- |
| `public/models/cement-bag.glb` | `cement_bag` | Cement |
| `public/models/filament-bulb.glb` | `lightbulb_01` | Filament bulb |
| `public/models/pendant.glb` | `hanging_industrial_lamp` | Pendant light |
| `public/models/hammer.glb`, `pliers.glb` | `wooden_hammer_01`, `pliers` | Hammer and plier set |
| `public/models/breaker-board.glb` | `power_box_01` | Distribution board |
| `public/models/pipe.glb` | `modular_pipes_plastic_01` | PPR pipe |
| `public/models/sofa.glb`, `coffee-table.glb`, `armchair.glb`, `plant.glb` | `Sofa_01`, `modern_coffee_table_01`, `modern_arm_chair_01`, `potted_plant_02` | Room furniture |
| `public/textures/screed-*.jpg` | `concrete_floor_02` | Bare screed floor, showroom floor |
| `public/textures/plaster-*.jpg` | `plastered_wall` | Unpainted plaster walls |
| `public/textures/paint-normal.jpg` | `painted_plaster_wall` | Painted walls |
| `public/textures/ceiling-color.jpg` | `white_plaster_02` | Ceiling |
| `public/env/studio.hdr` | `studio_small_09` (HDRI) | Lighting and reflections |
| `public/textures/marble-photo.jpg` | Pexels photo 6634141 | Marble-look floor tiles |

The glazed wall uses the shop's own tile photo (`public/images/tiles-grid.jpg`). The paint tin, tiles, cable roll,
roller and roof sheet keep built-in shapes because no suitable free model exists; they get the new lighting and photos.
If a model file fails to load, the built-in shape is shown, so the pages keep working. Glass is drawn as clear,
slightly reflective glass instead of true see-through glass, which is slow on phones.

Download size: about 5.3 MB of models, 1.5 MB of textures and 1.6 MB of lighting, loaded only on the 3D pages and
the home banner, and only once per visit.
