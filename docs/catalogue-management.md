# Catalogue management (real shop products)

Added 12 November 2026 for FR-01 to FR-06, C4 and BR-10 in requirements v0.5. Staff page: **Products** (`#/staff/catalogue`), for Owner and Inventory.

## After installing

Restart **Full Stack - local**. Flyway applies `V13__catalogue_management.sql`. No new packages.

## Who can do what

| Action | Owner | Inventory | Sales, Delivery |
| --- | --- | --- | --- |
| Add products, edit details, specifications, options, units and photos | Yes | Yes | No |
| Publish, hide, archive, restore | Yes | Yes | No |
| Set a price | Applies at once | Sent to the Owner; the current price stays | No |
| Approve or reject a price request | Yes | No | No |
| Archive all development sample products at once | Yes | No | No |

The server enforces every rule; the buttons only mirror them.

## Prices (C4)

- An Inventory price is stored in `app.price_changes` as `PENDING`. A new option entered by Inventory starts as "Ask for a quote" until the Owner approves its price.
- One pending request per option. The Owner's direct change closes any waiting request.
- A request can only be approved if the price is still the one it was made against. If the price changed in between, the Owner must reject it and a new request is needed.
- Decided price history cannot be edited or deleted (database trigger).

## Products, options and units

- Products are never deleted. Archiving hides a product; past orders keep their own copy of name, SKU and price.
- Publishing needs a shown category, a description of at least 10 characters and at least one option customers can choose.
- An option's selling unit is locked once it has stock or appears in an order; add a new option instead.
- The migration adds standard units (piece, bag, box, roll, set, sheet, tin, length, kit, pair, metre, litre, kilogram, square metre, cubic metre, tonne). Until now units existed only in the development sample data, so production had none.
- New stock is received on the existing Inventory page.

## Photos

- JPEG or PNG only, decided from the file's content. At least 200 × 200 and at most 12000 pixels per side, 10 MB, 8 photos per product.
- Every photo is decoded and saved again as a new JPEG of at most 1600 pixels. This removes all metadata, including phone GPS location and camera model.
- Files are stored outside the web root in `.local/product-photos` (setting `fpa.photos.dir`), named by SHA-256. The public address `/api/product-images/<hash>.jpg` works only while a published product uses the photo; staff see unpublished photos through `/api/staff/catalogue/photos/<hash>.jpg`.

## Replacing the sample products

Sample products exist only in the development database (`db/dev`); production starts with an empty catalogue. To switch a development or demo database to real stock: enter the real products, publish them, then, as Owner, use **Archive all sample products** and type `ARCHIVE SAMPLES`.

When samples are archived, their 3D models disappear from the storefront too, because the 3D manifest lists only published products. Give the real products 3D models on the **3D models** page.

## Checks run

On a copy of the project, with JDK 21, the project's own libraries and a throwaway PostgreSQL 16:

- `CatalogueAdminIT`: 5 tests pass. Covered: pending prices that never take effect, Owner approval and rejection, stale requests, direct Owner prices, price history that cannot be changed, publish rules, archive and restore, unit lock, photo re-encoding with metadata removed, public and staff photo access, refused files, roles, sample archiving, duplicate SKUs, HTTP sign-in and CSRF.
- Whole backend suite: 60 of 67 pass. The 7 failures are the existing Mwanza tests blocked by uncommitted `V11__retire_mwanza.sql`.
- Browser run against the real backend:
  - Inventory added "Simba Cement 42.5N" with a 50 kg bag option at TSh 17,500 and uploaded a photo carrying GPS and camera details; the stored copy had none.
  - Inventory published it, and the shop showed "Ask for a quote".
  - The Owner approved the price, and the shop showed TSh 17,500.
  - The Owner archived the 12 samples, leaving the shop with only the real product.
  - At 390 px wide there was no sideways scrolling.
- Frontend: `tsc`, ESLint, 17 unit tests and both builds pass.

## Not done

- Category management (FR-01). The client-confirmed categories are used as they are.
- Excel/CSV import with dry run (D4).
- Bulk price changes.
- Product search for the staff 3D page by real SKU works, but each real product needs its own 3D model entry.
