import fs from 'node:fs/promises';
import { URL } from 'node:url';
import console from 'node:console';
// Read only the versioned, synthetic seed file. Never export a running database.
const sql = await fs.readFile(new URL('../../backend/src/main/resources/db/dev/R__sample_catalogue.sql', import.meta.url), 'utf8');
const tables = {};
for (const match of sql.matchAll(/INSERT INTO app\.(\w+)(?:\(([^)]+)\))?\s+VALUES\s*\((.*?)\) ON CONFLICT DO NOTHING;/g)) {
  const defaults = { units: ['code','label','fractional'], product_images: ['id','product_id','path','alt_text','sort_order'], product_specifications: ['product_id','label','value','sort_order'] };
  const columns = match[2]?.split(',') || defaults[match[1]];
  if (!columns) continue;
  const values = [...match[3].matchAll(/'((?:''|[^'])*)'|([^,\s]+)(?:\s*,|$)/g)].map(m => m[1] !== undefined ? m[1].replaceAll("''", "'") : m[2] === 'TRUE' ? true : m[2] === 'FALSE' ? false : m[2] === 'NULL' ? null : Number(m[2]));
  if (values.length !== columns.length) throw new Error(`Unrecognized seed row: ${match[1]}`);
  (tables[match[1]] ??= []).push(Object.fromEntries(columns.map((column, i) => [column.trim(), values[i]])));
}
const categories = tables.categories.map(c => ({ id:c.id, slug:c.slug, name:c.name, description:c.description, imagePath:c.image_path, productCount:tables.products.filter(p=>p.category_id===c.id).length }));
const details = tables.products.map(p => {
  const category=categories.find(c=>c.id===p.category_id);
  const variants=tables.product_variants.filter(v=>v.product_id===p.id).map(v=>{
    const stock=tables.stock_balances.find(s=>s.variant_id===v.id), unit=tables.units.find(u=>u.code===v.unit_code);
    return { id:v.id, sku:v.sku, label:v.label, unit:unit.label, price:v.unit_price, currency:v.currency, pricingMode:'FIXED', available:stock?.on_hand||0, inStock:(stock?.on_hand||0)>0, fractional:unit.fractional };
  });
  const images=tables.product_images.filter(i=>i.product_id===p.id).map(i=>({path:i.path,alt:i.alt_text}));
  return { product:{ id:p.id,sku:p.sku,slug:p.slug,name:p.name,brand:p.brand,description:p.description,categorySlug:category.slug,categoryName:category.name,price:variants[0].price,currency:'TZS',unit:variants[0].unit,imagePath:images[0]?.path||'',imageAlt:images[0]?.alt||p.name,inStock:variants.some(v=>v.inStock),featured:p.featured,sampleData:true }, variants, images, specifications:tables.product_specifications.filter(s=>s.product_id===p.id).map(s=>({label:s.label,value:s.value})) };
});
if(categories.length!==17 || details.length!==12) throw new Error('Unexpected preview seed size; review before publishing.');
await fs.mkdir(new URL('../src/preview/',import.meta.url),{recursive:true});
await fs.writeFile(new URL('../src/preview/catalogue.json',import.meta.url),JSON.stringify({categories,details},null,2)+'\n');
console.log(`Prepared ${details.length} synthetic products and ${categories.length} confirmed categories.`);
