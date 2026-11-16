-- The 3D experience was removed from the storefront on 16 Nov 2026 (client decision), so the 3D model
-- records added in V12 are no longer used. This removes the order-line model snapshot, its trigger,
-- and the model table. Product photos are unaffected.
DROP TRIGGER IF EXISTS customer_order_items_model_snapshot ON app.customer_order_items;
DROP FUNCTION IF EXISTS app.snapshot_order_item_model();
ALTER TABLE app.customer_order_items DROP COLUMN IF EXISTS model_asset_id, DROP COLUMN IF EXISTS model_asset_version;
DROP TABLE IF EXISTS app.model_assets;
