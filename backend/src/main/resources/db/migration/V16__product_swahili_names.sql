-- Optional Kiswahili product name, shown next to the main name and included in search
-- (for example "Portland Cement 42.5R" / "Saruji"). Customers search with the words they use.
ALTER TABLE app.products ADD COLUMN name_sw varchar(160)
 CHECK(name_sw IS NULL OR length(btrim(name_sw))>=2);
-- Kiswahili names for the shop's categories (fixed shop data from V6).
ALTER TABLE app.categories ADD COLUMN name_sw varchar(120)
 CHECK(name_sw IS NULL OR length(btrim(name_sw))>=2);
UPDATE app.categories c SET name_sw=v.sw FROM (VALUES
 ('cement','Vifaa vya Ujenzi'),('electrical','Vifaa vya Umeme'),('plumbing','Mabomba na Vifaa vya Usafi'),
 ('roofing','Vifaa vya Kuezekea'),('paint','Rangi na Vifaa vya Kupaka Rangi'),('tools','Zana za Mkono'),
 ('power-tools-machinery','Mashine na Zana za Umeme'),('fasteners-fixings','Misumari, Skrubu na Boliti'),
 ('steel-metal-products','Nondo na Bidhaa za Chuma'),('doors-windows-accessories','Vifaa vya Milango na Madirisha'),
 ('tiles','Vifaa vya Sakafu'),('ceilings-partitions','Dari na Kuta za Kugawa'),('welding-fabrication','Uchomeleaji na Uundaji'),
 ('security-safety-equipment','Vifaa vya Usalama'),('adhesives-sealants','Gundi na Vizibio'),
 ('gardening-outdoor','Bustani na Nje'),('cleaning-maintenance','Usafi na Matengenezo')
) AS v(slug,sw) WHERE c.slug=v.slug;
-- The public view lists p.* explicitly at creation time, so it is rebuilt to include the new column.
DROP VIEW app.public_catalogue_products;
CREATE VIEW app.public_catalogue_products AS
SELECT p.*, c.slug AS category_slug, c.name AS category_name, c.name_sw AS category_name_sw,
 v.unit_price AS price, v.currency, v.label AS default_variant, u.label AS unit,
 COALESCE(img.path,'') AS image_path, COALESCE(img.alt_text,p.name) AS image_alt,
 EXISTS(SELECT 1 FROM app.product_variants pv JOIN app.stock_balances s ON s.variant_id=pv.id
        WHERE s.branch_code='TARIME' AND pv.product_id=p.id AND pv.published AND s.on_hand-s.reserved>0) AS in_stock
FROM app.products p JOIN app.categories c ON c.id=p.category_id
JOIN LATERAL (SELECT pv.* FROM app.product_variants pv WHERE pv.product_id=p.id AND pv.published ORDER BY pv.sort_order,pv.id LIMIT 1) v ON TRUE
JOIN app.units u ON u.code=v.unit_code
LEFT JOIN LATERAL (SELECT pi.path,pi.alt_text FROM app.product_images pi WHERE pi.product_id=p.id ORDER BY pi.sort_order,pi.id LIMIT 1) img ON TRUE
WHERE p.published AND NOT p.archived AND c.published AND NOT c.archived;
