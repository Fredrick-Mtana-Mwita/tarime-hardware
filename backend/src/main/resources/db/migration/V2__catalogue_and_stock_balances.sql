CREATE TABLE app.categories (
 id UUID PRIMARY KEY, slug VARCHAR(100) NOT NULL UNIQUE, name VARCHAR(150) NOT NULL,
 description TEXT NOT NULL DEFAULT '', image_path TEXT NOT NULL DEFAULT '',
 published BOOLEAN NOT NULL DEFAULT FALSE, archived BOOLEAN NOT NULL DEFAULT FALSE,
 sort_order INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE app.units (
 code VARCHAR(32) PRIMARY KEY, label VARCHAR(80) NOT NULL, fractional BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE app.products (
 id UUID PRIMARY KEY, sku VARCHAR(80) NOT NULL UNIQUE, slug VARCHAR(160) NOT NULL UNIQUE,
 category_id UUID NOT NULL REFERENCES app.categories(id), name VARCHAR(200) NOT NULL,
 brand VARCHAR(100) NOT NULL DEFAULT '', description TEXT NOT NULL,
 published BOOLEAN NOT NULL DEFAULT FALSE, archived BOOLEAN NOT NULL DEFAULT FALSE,
 featured BOOLEAN NOT NULL DEFAULT FALSE, sample_data BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE app.product_variants (
 id UUID PRIMARY KEY, product_id UUID NOT NULL REFERENCES app.products(id),
 sku VARCHAR(100) NOT NULL UNIQUE, label VARCHAR(120) NOT NULL,
 unit_code VARCHAR(32) NOT NULL REFERENCES app.units(code),
 pricing_mode VARCHAR(10) NOT NULL DEFAULT 'FIXED' CHECK(pricing_mode IN ('FIXED','QUOTE')),
 unit_price NUMERIC(19,2), currency CHAR(3) NOT NULL,
 published BOOLEAN NOT NULL DEFAULT FALSE, sort_order INTEGER NOT NULL DEFAULT 0,
 CHECK ((pricing_mode='FIXED' AND unit_price IS NOT NULL AND unit_price>=0) OR (pricing_mode='QUOTE' AND unit_price IS NULL))
);
CREATE TABLE app.product_images (
 id UUID PRIMARY KEY, product_id UUID NOT NULL REFERENCES app.products(id),
 path TEXT NOT NULL, alt_text TEXT NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE app.product_specifications (
 product_id UUID NOT NULL REFERENCES app.products(id), label VARCHAR(100) NOT NULL,
 value VARCHAR(500) NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0,
 PRIMARY KEY(product_id,label)
);
-- Inventory-owned balances. Future reservation/movement use cases own all mutations.
CREATE TABLE app.stock_balances (
 variant_id UUID PRIMARY KEY REFERENCES app.product_variants(id),
 on_hand NUMERIC(19,3) NOT NULL DEFAULT 0 CHECK(on_hand>=0),
 reserved NUMERIC(19,3) NOT NULL DEFAULT 0 CHECK(reserved>=0 AND reserved<=on_hand),
 low_stock_threshold NUMERIC(19,3) NOT NULL DEFAULT 0 CHECK(low_stock_threshold>=0),
 version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX products_public_category_idx ON app.products(category_id,name,id) WHERE published AND NOT archived;
CREATE INDEX variants_product_idx ON app.product_variants(product_id,sort_order,id) WHERE published;
CREATE INDEX images_product_idx ON app.product_images(product_id,sort_order,id);

-- Read-only public projection; all list/detail/filter queries share the same visibility policy.
CREATE VIEW app.public_catalogue_products AS
SELECT p.*, c.slug AS category_slug, c.name AS category_name,
 v.unit_price AS price, v.currency, v.label AS default_variant, u.label AS unit,
 COALESCE(img.path,'') AS image_path, COALESCE(img.alt_text,p.name) AS image_alt,
 EXISTS(SELECT 1 FROM app.product_variants pv JOIN app.stock_balances s ON s.variant_id=pv.id
        WHERE pv.product_id=p.id AND pv.published AND s.on_hand-s.reserved>0) AS in_stock
FROM app.products p JOIN app.categories c ON c.id=p.category_id
JOIN LATERAL (SELECT pv.* FROM app.product_variants pv WHERE pv.product_id=p.id AND pv.published ORDER BY pv.sort_order,pv.id LIMIT 1) v ON TRUE
JOIN app.units u ON u.code=v.unit_code
LEFT JOIN LATERAL (SELECT pi.path,pi.alt_text FROM app.product_images pi WHERE pi.product_id=p.id ORDER BY pi.sort_order,pi.id LIMIT 1) img ON TRUE
WHERE p.published AND NOT p.archived AND c.published AND NOT c.archived;
