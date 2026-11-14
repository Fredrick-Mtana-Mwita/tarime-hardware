-- 3D model records: provenance, review and Owner publication (FR-3D-08, FR-3D-09).
-- A model is either a built-in illustrative shape drawn by the storefront or an uploaded GLB file.
-- Rows are never deleted; replacing a model creates a new version and retires the old one,
-- so order lines that recorded an older version keep a valid reference (FR-3D-07).
CREATE TABLE app.model_assets (
 id UUID PRIMARY KEY,
 product_id UUID NOT NULL REFERENCES app.products(id),
 version INTEGER NOT NULL CHECK(version>0),
 status VARCHAR(12) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','IN_REVIEW','PUBLISHED','RETIRED')),
 source_kind VARCHAR(10) NOT NULL CHECK(source_kind IN ('BUILT_IN','GLB')),
 model_kind VARCHAR(40) CHECK(model_kind IS NULL OR model_kind ~ '^[a-z][a-z0-9-]{1,39}$'),
 -- Uploaded file, stored by its SHA-256 so the stored name can never be chosen by a user.
 file_sha256 CHAR(64) CHECK(file_sha256 IS NULL OR file_sha256 ~ '^[0-9a-f]{64}$'),
 file_bytes INTEGER CHECK(file_bytes IS NULL OR file_bytes>0),
 file_name VARCHAR(120),
 validation_report JSONB NOT NULL DEFAULT '{}'::jsonb CHECK(jsonb_typeof(validation_report)='object'),
 -- Provenance and rights evidence.
 source_description VARCHAR(300) NOT NULL DEFAULT '',
 license_terms VARCHAR(300) NOT NULL DEFAULT '',
 attribution VARCHAR(300) NOT NULL DEFAULT '',
 rights_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
 verified_exact BOOLEAN NOT NULL DEFAULT FALSE,
 width_mm INTEGER CHECK(width_mm IS NULL OR width_mm BETWEEN 1 AND 100000),
 height_mm INTEGER CHECK(height_mm IS NULL OR height_mm BETWEEN 1 AND 100000),
 depth_mm INTEGER CHECK(depth_mm IS NULL OR depth_mm BETWEEN 1 AND 100000),
 -- Storefront placement.
 showroom_zone VARCHAR(20) NOT NULL CHECK(showroom_zone IN ('building','finishes','electrical','tools')),
 room_surface VARCHAR(10) CHECK(room_surface IS NULL OR room_surface IN ('floor','wall','light')),
 room_finish VARCHAR(20) CHECK(room_finish IS NULL OR room_finish IN ('marble','gloss-white','glazed','pendant','bulb')),
 tile_mm INTEGER CHECK(tile_mm IS NULL OR tile_mm BETWEEN 10 AND 3000),
 variant_scales JSONB NOT NULL DEFAULT '{}'::jsonb CHECK(jsonb_typeof(variant_scales)='object'),
 -- Workflow. created_by is NULL only for development seed rows.
 revision BIGINT NOT NULL DEFAULT 0,
 created_by UUID REFERENCES app.staff_accounts(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 submitted_by UUID REFERENCES app.staff_accounts(id),
 submitted_at TIMESTAMPTZ,
 reviewed_by UUID REFERENCES app.staff_accounts(id),
 reviewed_at TIMESTAMPTZ,
 review_note VARCHAR(500) NOT NULL DEFAULT '',
 published_at TIMESTAMPTZ,
 retired_at TIMESTAMPTZ,
 UNIQUE(product_id,version),
 CHECK((source_kind='BUILT_IN' AND model_kind IS NOT NULL AND file_sha256 IS NULL)
    OR (source_kind='GLB' AND model_kind IS NULL)),
 CHECK((room_surface IS NULL)=(room_finish IS NULL)),
 CHECK(tile_mm IS NULL OR room_surface IN ('floor','wall')),
 CHECK(status IN ('DRAFT','IN_REVIEW') OR (rights_confirmed AND reviewed_at IS NOT NULL AND published_at IS NOT NULL)),
 CHECK(source_kind='BUILT_IN' OR status='DRAFT' OR file_sha256 IS NOT NULL),
 CHECK(status<>'RETIRED' OR retired_at IS NOT NULL)
);
-- At most one live model per product.
CREATE UNIQUE INDEX model_assets_one_published ON app.model_assets(product_id) WHERE status='PUBLISHED';
CREATE INDEX model_assets_status_idx ON app.model_assets(status,updated_at DESC,id);

-- Each order line records the model the shop was publishing for that product when the order was submitted.
ALTER TABLE app.customer_order_items
 ADD COLUMN model_asset_id UUID REFERENCES app.model_assets(id),
 ADD COLUMN model_asset_version INTEGER;
CREATE FUNCTION app.snapshot_order_item_model() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  SELECT a.id,a.version INTO NEW.model_asset_id,NEW.model_asset_version
  FROM app.model_assets a WHERE a.product_id=NEW.product_id AND a.status='PUBLISHED';
  RETURN NEW;
END;
$$;
CREATE TRIGGER customer_order_items_model_snapshot BEFORE INSERT ON app.customer_order_items
 FOR EACH ROW EXECUTE FUNCTION app.snapshot_order_item_model();
