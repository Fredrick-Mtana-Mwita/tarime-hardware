-- Retain historical Mwanza balances and orders, but reject new operational changes.
ALTER TABLE app.branches ADD COLUMN active boolean NOT NULL DEFAULT true;
UPDATE app.branches SET active=false WHERE code='MWANZA';
CREATE FUNCTION app.require_active_branch() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.branch_code IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM app.branches WHERE code=NEW.branch_code AND active
  ) THEN
    RAISE EXCEPTION 'This branch is closed. Use Tarime for new operations.';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER customer_orders_active_branch BEFORE INSERT OR UPDATE ON app.customer_orders
FOR EACH ROW EXECUTE FUNCTION app.require_active_branch();
CREATE TRIGGER stock_balances_active_branch BEFORE INSERT OR UPDATE ON app.stock_balances
FOR EACH ROW EXECUTE FUNCTION app.require_active_branch();
CREATE OR REPLACE VIEW app.public_catalogue_products AS
SELECT p.*, c.slug AS category_slug, c.name AS category_name,
 v.unit_price AS price, v.currency, v.label AS default_variant, u.label AS unit,
 COALESCE(img.path,'') AS image_path, COALESCE(img.alt_text,p.name) AS image_alt,
 EXISTS(SELECT 1 FROM app.product_variants pv JOIN app.stock_balances s ON s.variant_id=pv.id
        WHERE s.branch_code='TARIME' AND pv.product_id=p.id AND pv.published AND s.on_hand-s.reserved>0) AS in_stock
FROM app.products p JOIN app.categories c ON c.id=p.category_id
JOIN LATERAL (SELECT pv.* FROM app.product_variants pv WHERE pv.product_id=p.id AND pv.published ORDER BY pv.sort_order,pv.id LIMIT 1) v ON TRUE
JOIN app.units u ON u.code=v.unit_code
LEFT JOIN LATERAL (SELECT pi.path,pi.alt_text FROM app.product_images pi WHERE pi.product_id=p.id ORDER BY pi.sort_order,pi.id LIMIT 1) img ON TRUE
WHERE p.published AND NOT p.archived AND c.published AND NOT c.archived;

