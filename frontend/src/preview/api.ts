import catalogue from './catalogue.json'
import { ApiError } from '../shared/api/http'
import type { Detail } from '../features/catalogue/types'
import { previewManifestVersion, previewModels } from '../experiences/manifest'
const details = catalogue.details as Detail[]
export function previewRead(path: string): unknown {
  const url = new URL(path, 'https://preview.invalid'), p = url.searchParams
  const q = (p.get('q') || '').toLowerCase()
  const page = Math.max(0, Number(p.get('page')) || 0), size = Math.min(48, Math.max(1, Number(p.get('size')) || 12))
  if (url.pathname === '/api/auth/me') throw new ApiError(401)
  if (url.pathname === '/api/categories') return catalogue.categories
  if (url.pathname === '/api/products') {
    let items = details.map(d => d.product).filter(product => (product.name + ' ' + product.brand + ' ' + product.sku).toLowerCase().includes(q)
      && (!p.get('category') || product.categorySlug === p.get('category'))
      && (p.get('featured') !== 'true' || product.featured) && (p.get('inStock') !== 'true' || product.inStock)
      && (!p.get('minPrice') || (product.price ?? 0) >= Number(p.get('minPrice'))) && (!p.get('maxPrice') || (product.price ?? 0) <= Number(p.get('maxPrice'))))
    items = items.sort((a,b) => p.get('sort') === 'price-asc' ? (a.price??0)-(b.price??0) : p.get('sort') === 'price-desc' ? (b.price??0)-(a.price??0) : p.get('sort') === 'name' ? a.name.localeCompare(b.name) : Number(b.featured)-Number(a.featured))
    return { items:items.slice(page*size,(page+1)*size), total:items.length, page,size }
  }
  if (url.pathname.startsWith('/api/products/')) {
    const detail = details.find(d => d.product.id === url.pathname.split('/').pop())
    if (!detail) throw new ApiError(404)
    return detail
  }
  if (url.pathname === '/api/models/manifest' || url.pathname === '/api/staff/models') {
    // The preview has no server: every sample product shows its built-in illustrative shape.
    const entries = details.flatMap((detail, index) => {
      const sample = previewModels[detail.product.sku]
      if (!sample) return []
      const variantScales = Object.fromEntries(detail.variants.flatMap(variant => sample.variantScale?.[variant.sku] ? [[variant.id, sample.variantScale[variant.sku]]] : []))
      return [{ assetId: `00000000-0000-4000-8000-0000000007${String(index).padStart(2, '0')}`, version: 1, productId: detail.product.id, sku: detail.product.sku, productName: detail.product.name,
        sourceKind: 'BUILT_IN', modelKind: sample.kind, modelUrl: null, verified: false, zone: sample.zone, roomSurface: sample.room?.surface ?? null,
        roomFinish: sample.room?.finish ?? null, tileMm: sample.room?.tileMetres ? sample.room.tileMetres * 1000 : null, variantScales }]
    })
    if (url.pathname === '/api/models/manifest') return { version: previewManifestVersion, entries }
    const items = entries.map(entry => ({ id: entry.assetId, productId: entry.productId, productName: entry.productName, productSku: entry.sku, productArchived: false, version: 1, status: 'PUBLISHED',
      sourceKind: 'BUILT_IN', modelKind: entry.modelKind, fileSha256: null, fileBytes: null, fileName: null, validation: null, sourceDescription: 'Built-in illustrative shape drawn by the storefront code',
      licenseTerms: 'Project-owned code', attribution: '', rightsConfirmed: true, verifiedExact: false, widthMm: null, heightMm: null, depthMm: null, showroomZone: entry.zone, roomSurface: entry.roomSurface,
      roomFinish: entry.roomFinish, tileMm: entry.tileMm, variantScales: entry.variantScales, revision: 0, createdByName: null, createdAt: '2026-11-12T00:00:00Z', updatedAt: '2026-11-12T00:00:00Z',
      submittedByName: null, submittedAt: null, reviewedByName: null, reviewedAt: '2026-11-12T00:00:00Z', reviewNote: 'Sample', publishedAt: '2026-11-12T00:00:00Z', retiredAt: null }))
      .filter(item => (item.productName + ' ' + item.productSku).toLowerCase().includes(q) && (!p.get('status') || p.get('status') === 'PUBLISHED'))
    return { items: items.slice(page * size, (page + 1) * size), total: items.length, inReview: 0, page, size }
  }
  if (url.pathname === '/api/staff/models/products') return []
  if (url.pathname === '/api/staff/catalogue/options') return { categories: catalogue.categories.map(category => ({ id: category.id, name: category.name, published: true })), units: [] }
  if (url.pathname === '/api/staff/catalogue/products') {
    const items = details.map(d => ({ id: d.product.id, sku: d.product.sku, name: d.product.name, brand: d.product.brand, categoryName: d.product.categoryName, status: 'PUBLISHED', sample: true,
      variants: d.variants.length, image: d.product.imagePath, lowestPrice: d.product.price, pendingPrices: 0, revision: 0, updatedAt: '2026-11-12T00:00:00Z' }))
      .filter(item => (item.name + ' ' + item.brand + ' ' + item.sku).toLowerCase().includes(q) && ['', 'published', 'sample'].includes(p.get('filter') || ''))
    return { items: items.slice(page * size, (page + 1) * size), total: items.length, pendingPrices: 0, liveSamples: 0, page, size }
  }
  if (url.pathname.startsWith('/api/staff/catalogue/products/')) {
    const d = details.find(item => item.product.id === url.pathname.split('/').pop())
    if (!d) throw new ApiError(404)
    return { id: d.product.id, sku: d.product.sku, slug: d.product.slug, name: d.product.name, brand: d.product.brand, categoryId: catalogue.categories.find(c => c.slug === d.product.categorySlug)?.id ?? '',
      categoryName: d.product.categoryName, description: d.product.description, published: true, archived: false, featured: d.product.featured, sample: true, revision: 0, updatedAt: '2026-11-12T00:00:00Z',
      variants: d.variants.map((v, index) => ({ id: v.id, sku: v.sku, label: v.label, unitCode: v.unit, unitLabel: v.unit, fractional: v.fractional, pricingMode: v.pricingMode, price: v.price, currency: v.currency,
        published: true, sortOrder: index, unitLocked: true, pending: null })),
      images: d.images.map((image, index) => ({ id: `preview-${index}`, path: image.path, alt: image.alt, sortOrder: index, width: null, height: null, uploaded: false })), specifications: d.specifications }
  }
  if (url.pathname === '/api/staff/catalogue/price-changes') return { items: [], total: 0, page, size }
  if (url.pathname === '/api/staff/me') return { id:'client-preview',username:'preview',name:'Client preview',role:'OWNER',passwordChangeRequired:false }
  if (url.pathname === '/api/staff/fulfilment') return {drivers:[],orders:[{id:'sample-pickup',reference:'Sample pickup',status:'READY_FOR_PICKUP',fulfilment:'PICKUP',branch_code:'TARIME',contact_name:'Sample customer',contact_phone:'',address:'',instructions:''},{id:'sample-delivery',reference:'Sample delivery',status:'OUT_FOR_DELIVERY',fulfilment:'DELIVERY',branch_code:'TARIME',contact_name:'Sample customer',contact_phone:'',address:'',instructions:''}]}
  if (url.pathname === '/api/staff/inventory') {
    const items = details.flatMap(d => d.variants.map(v => {
      const stock=p.get('branch')==='MWANZA'?0:v.available
      return {variantId:v.id,productName:d.product.name,variantLabel:v.label,sku:v.sku,unit:v.unit,fractional:v.fractional,category:d.product.categoryName,image:d.product.imagePath,onHand:stock,reserved:0,available:stock,lowStock:stock<=10}
    })).filter(i=>(i.productName+' '+i.variantLabel+' '+i.sku).toLowerCase().includes(q))
    return {items:items.slice(page*size,(page+1)*size),total:items.length,lowStockCount:items.filter(i=>i.lowStock).length,page,size}
  }
  if (url.pathname === '/api/staff/orders') return {items:[],total:0,pendingCount:0,page,size}
  if (url.pathname === '/api/staff/finance/orders') return {items:[],total:0,page,size}
  if (url.pathname === '/api/staff/finance/adjustments') return []
  if (url.pathname === '/api/staff/finance/summary') return {date:p.get('date')||'',currency:'TZS',cash:0,mobileMoney:0,received:0,payments:0,reversed:0,refunded:0,net:0,completedSales:0,completedOrders:0}
  if (url.pathname === '/api/staff/inventory/receipts') return {items:[],total:0,page,size}
  if (['/api/staff/accounts','/api/staff/notifications','/api/notifications'].includes(url.pathname)) return []
  throw new ApiError(404)
}
export function previewQuote(body: unknown) {
  const request = body as {items:{variantId:string;quantity:string}[];branch?:string}
  const items = request.items.map(selection => {
    const detail = details.find(d=>d.variants.some(v=>v.id===selection.variantId))
    const variant = detail?.variants.find(v=>v.id===selection.variantId)
    const quantity = Number(selection.quantity)
    if (!detail || !variant || !Number.isFinite(quantity) || quantity<=0 || (!variant.fractional&&!Number.isInteger(quantity))) throw new ApiError(400)
    return {variantId:variant.id,productId:detail.product.id,productName:detail.product.name,variantLabel:variant.label,sku:variant.sku,unit:variant.unit,fractional:variant.fractional,quantity,unitPrice:variant.price||0,currency:'TZS',lineTotal:Math.round(quantity*(variant.price||0)*100)/100,available:request.branch==='MWANZA'?0:variant.available}
  })
  return {items,currency:'TZS',subtotal:items.reduce((sum,item)=>sum+item.lineTotal,0)}
}
