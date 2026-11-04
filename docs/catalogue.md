# Catalogue slice

The application now provides a database-backed homepage, catalogue/search results, category/price/availability filters, stable sorting/pagination, and product details with variant prices, image selection, specifications and availability.

## Run in IntelliJ

Stop and restart **Full Stack - local** once after this update, then refresh the browser. The launcher now explicitly selects the `dev` Spring profile. This enables the development-only repeatable sample-data migration; ordinary versioned schema migrations run in every environment. The regular **Backend - local** configuration already uses `dev`.

The existing prototype is unchanged. Its photography and amber/charcoal styling are reused. Photos are illustrative, as noted in the original prototype's Unsplash attribution; they do not prove branded product fidelity. No new paid services or assets were used.

## Public API

| Endpoint | Behavior |
| --- | --- |
| `GET /api/categories` | Published, non-archived categories with public product counts |
| `GET /api/products` | Public products with a published category and at least one published variant |
| `GET /api/products/{uuid}` | Visible product, published variants, images and specifications; hidden/unknown products return 404 |

List parameters: `q` (literal case-insensitive name/brand/SKU search, at most 150 characters), `category` (slug), `minPrice`, `maxPrice`, `inStock`, `featured`, `sort`, `page` (zero-based), `size` (1-48, default 12). Sort values: `featured`, `name`, `price-asc`, `price-desc`. Every ordering has stable name/UUID tie-breakers. Invalid ranges, page sizes and sort values return 400. Query values are bound parameters; sort expressions come from a fixed allowlist.

List prices/filtering use the first published variant in configured sort order, not an unlabelled minimum across different units. The detail page displays the selected variant's price and unit. Quote-only pricing is representable as null price with QUOTE mode; no quotation workflow or purchasing flow is implemented here.

Availability is derived from inventory-owned on-hand minus reserved balances, never supplied by the browser. A product is in stock if any published variant is available. The public projection does not grant catalogue or stock mutation access. All writes and staff endpoints remain denied until authenticated feature use cases are implemented.

## Schema and seed policy

`V2__catalogue_and_stock_balances.sql` adds categories, units, products, variants, images, specifications, stock balances and a shared public-visibility view. Read projections use JDBC behind the catalogue application interface; operational writes and inventory concurrency will have their own application/domain use cases.

`db/dev/R__sample_catalogue.sql` contains 8 categories, 12 sample products and 13 variants with illustrative TZS prices/balances. It is loaded only by the `dev` profile, with insert-on-conflict preservation of existing records. Production does not load that location. Integration tests explicitly execute this fixture against the guarded test database. Sample stock is not an approved opening-stock import or a real financial record.

## Verification

Backend verification passed 2 architecture and 7 PostgreSQL/HTTP integration tests. Catalogue-specific cases cover literal search, filters, stable pagination, default-variant prices, invalid input, hidden product/category access, variants, reservation-adjusted availability, and rejected public writes. Frontend unit tests, lint and TypeScript/Vite build pass. Five real Chromium journeys cover homepage/category data, search/empty states, phone filtering/sorting/URL restoration, variant/gallery details and unknown products, and network failure/retry. Desktop and phone screenshots were inspected.

Accounts, staff editing, cart/checkout, order handling, real opening-stock imports and 3D scenes remain subsequent slices. Do not treat the browsing preview as a production launch.
