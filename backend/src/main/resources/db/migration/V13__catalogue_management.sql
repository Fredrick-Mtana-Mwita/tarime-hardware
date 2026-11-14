-- Staff catalogue management (FR-01 to FR-06, C4, BR-10).
-- Owner and Inventory edit catalogue content. Prices change only through the Owner: an Inventory price is a
-- pending request that never alters the effective price until approved.

-- Standard selling units (FR-05). Development data may already contain some of these.
INSERT INTO app.units(code,label,fractional) VALUES
 ('piece','piece',FALSE),('bag','bag',FALSE),('box','box',FALSE),('roll','roll',FALSE),('set','set',FALSE),('sheet','sheet',FALSE),
 ('tin','tin',FALSE),('length','length',FALSE),('kit','kit',FALSE),('pair','pair',FALSE),('metre','metre',TRUE),('litre','litre',TRUE),
 ('kilogram','kilogram',TRUE),('square-metre','square metre',TRUE),('cubic-metre','cubic metre',TRUE),('tonne','tonne',TRUE)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE app.products
 ADD COLUMN revision BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 ADD CONSTRAINT products_name_present CHECK(length(trim(name))>=2),
 ADD CONSTRAINT products_archived_hidden CHECK(NOT (archived AND published));
ALTER TABLE app.product_variants
 ADD CONSTRAINT product_variants_label_present CHECK(length(trim(label))>=1);

-- Photos uploaded by staff are re-encoded and stored by content hash; older rows point at bundled images.
ALTER TABLE app.product_images
 ADD COLUMN file_sha256 CHAR(64) CHECK(file_sha256 IS NULL OR file_sha256 ~ '^[0-9a-f]{64}$'),
 ADD COLUMN width_px INTEGER CHECK(width_px IS NULL OR width_px>0),
 ADD COLUMN height_px INTEGER CHECK(height_px IS NULL OR height_px>0),
 ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE INDEX product_images_file_idx ON app.product_images(file_sha256) WHERE file_sha256 IS NOT NULL;

-- Every price change, requested or direct, with who asked and who decided.
CREATE TABLE app.price_changes (
 id UUID PRIMARY KEY,
 variant_id UUID NOT NULL REFERENCES app.product_variants(id),
 previous_mode VARCHAR(10) NOT NULL CHECK(previous_mode IN ('FIXED','QUOTE')),
 previous_price NUMERIC(19,2),
 requested_mode VARCHAR(10) NOT NULL CHECK(requested_mode IN ('FIXED','QUOTE')),
 requested_price NUMERIC(19,2),
 currency CHAR(3) NOT NULL,
 reason VARCHAR(500) NOT NULL CHECK(length(trim(reason))>=3),
 status VARCHAR(10) NOT NULL CHECK(status IN ('PENDING','APPROVED','REJECTED','WITHDRAWN')),
 requested_by UUID NOT NULL REFERENCES app.staff_accounts(id),
 requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 decided_by UUID REFERENCES app.staff_accounts(id),
 decided_at TIMESTAMPTZ,
 decision_note VARCHAR(500) NOT NULL DEFAULT '',
 CHECK((requested_mode='FIXED' AND requested_price IS NOT NULL AND requested_price>=0 AND requested_price<=999999999999.99) OR (requested_mode='QUOTE' AND requested_price IS NULL)),
 CHECK((status='PENDING' AND decided_by IS NULL AND decided_at IS NULL) OR (status<>'PENDING' AND decided_at IS NOT NULL))
);
CREATE UNIQUE INDEX price_changes_one_pending ON app.price_changes(variant_id) WHERE status='PENDING';
CREATE INDEX price_changes_status_idx ON app.price_changes(status,requested_at DESC,id);
-- Decided rows are history; only a pending request may change, and only once.
CREATE FUNCTION app.price_changes_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE' OR OLD.status<>'PENDING' THEN RAISE EXCEPTION 'Price history is append-only' USING ERRCODE='23000'; END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER price_changes_history BEFORE UPDATE OR DELETE ON app.price_changes
 FOR EACH ROW EXECUTE FUNCTION app.price_changes_immutable();
