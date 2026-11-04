import catalogue from './catalogue.json'
import { ApiError } from '../shared/api/http'
import type { Detail } from '../features/catalogue/types'
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
  if (url.pathname === '/api/staff/me') return { id:'client-preview',username:'preview',name:'Client preview',role:'OWNER',passwordChangeRequired:false }
  if (url.pathname === '/api/staff/inventory') {
    const items = details.flatMap(d => d.variants.map(v => {
      const stock=p.get('branch')==='MWANZA'?0:v.available
      return {variantId:v.id,productName:d.product.name,variantLabel:v.label,sku:v.sku,unit:v.unit,fractional:v.fractional,category:d.product.categoryName,image:d.product.imagePath,onHand:stock,reserved:0,available:stock,lowStock:stock<=10}
    })).filter(i=>(i.productName+' '+i.variantLabel+' '+i.sku).toLowerCase().includes(q))
    return {items:items.slice(page*size,(page+1)*size),total:items.length,lowStockCount:items.filter(i=>i.lowStock).length,page,size}
  }
  if (url.pathname === '/api/staff/orders') return {items:[],total:0,pendingCount:0,page,size}
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
