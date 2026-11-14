import { useState } from 'react'
import type { FormEvent } from 'react'
import { assetUrl } from '../../shared/assetUrl'
import { isClientPreview } from '../../shared/preview'
import { money } from '../catalogue/format'
import { staffError, staffUpload, staffWrite } from './api'
import type { StaffRole } from './api'
import { WorkspaceError } from './Inventory'
import { useWorkspaceData } from './useWorkspaceData'
import './catalogue-admin.css'

type Filter = '' | 'draft' | 'published' | 'archived' | 'sample' | 'price-waiting'
interface Options { categories: { id: string; name: string; published: boolean }[]; units: { code: string; label: string; fractional: boolean }[] }
interface Summary { id: string; sku: string; name: string; brand: string; categoryName: string; status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'; sample: boolean; variants: number; image: string; lowestPrice: number | null; pendingPrices: number; updatedAt: string }
interface Page { items: Summary[]; total: number; pendingPrices: number; liveSamples: number; page: number; size: number }
interface Pending { id: string; requestedMode: 'FIXED' | 'QUOTE'; requestedPrice: number | null; reason: string; requestedByName: string; requestedAt: string }
interface Variant { id: string; sku: string; label: string; unitCode: string; unitLabel: string; fractional: boolean; pricingMode: 'FIXED' | 'QUOTE'; price: number | null; currency: string; published: boolean; sortOrder: number; unitLocked: boolean; pending: Pending | null }
interface Photo { id: string; path: string; alt: string; sortOrder: number; width: number | null; height: number | null; uploaded: boolean }
interface Spec { label: string; value: string }
interface Product { id: string; sku: string; slug: string; name: string; brand: string; categoryId: string; categoryName: string; description: string; published: boolean; archived: boolean; featured: boolean; sample: boolean; revision: number; updatedAt: string; variants: Variant[]; images: Photo[]; specifications: Spec[] }
interface PriceChange { id: string; productId: string; productName: string; variantLabel: string; sku: string; previousMode: string; previousPrice: number | null; requestedMode: string; requestedPrice: number | null; reason: string; requestedByName: string; requestedAt: string }

const filters: { id: Filter; name: string }[] = [{ id: '', name: 'All' }, { id: 'draft', name: 'Not published' }, { id: 'published', name: 'Published' }, { id: 'price-waiting', name: 'Price waiting' }, { id: 'sample', name: 'Sample products' }, { id: 'archived', name: 'Archived' }]
const statusName = { DRAFT: 'Not published', PUBLISHED: 'Published', ARCHIVED: 'Archived' }
const when = (value: string) => new Date(value).toLocaleString('en-TZ', { dateStyle: 'medium', timeStyle: 'short' })
const priceText = (mode: string, price: number | null) => mode === 'QUOTE' ? 'Ask for a quote' : money(price)
/** Uploaded photos of unpublished products are only reachable through the staff address. */
const staffPhoto = (path: string) => path.startsWith('/api/product-images/') ? path.replace('/api/product-images/', '/api/staff/catalogue/photos/') : assetUrl(path)
const field = (form: FormData, name: string) => String(form.get(name) ?? '').trim()

function PriceForm({ owner, onSubmit, busy, label }: { owner: boolean; busy: boolean; label: string; onSubmit: (mode: 'FIXED' | 'QUOTE', price: number | null, reason: string) => void }) {
  const [mode, setMode] = useState<'FIXED' | 'QUOTE'>('FIXED')
  return <form className="ca-price-form" onSubmit={event => { event.preventDefault(); const form = new FormData(event.currentTarget); onSubmit(mode, mode === 'FIXED' ? Number(form.get('price')) : null, field(form, 'reason')) }}>
    <label>Price type<select value={mode} onChange={event => setMode(event.target.value as 'FIXED' | 'QUOTE')}><option value="FIXED">Fixed price (TSh)</option><option value="QUOTE">Ask for a quote (no fixed price)</option></select></label>
    {mode === 'FIXED' && <label>New price, TSh<input name="price" type="number" min="0" step="0.01" max="999999999999.99" required /></label>}
    <label>Reason<input name="reason" required minLength={3} maxLength={500} placeholder="For example: new supplier price list" /></label>
    <button className="button primary" disabled={busy}>{busy ? 'Saving…' : owner ? 'Set price' : 'Send to Owner'}</button>
    <p className="commerce-muted">{owner ? `${label}: the new price applies straight away and is recorded.` : `${label}: the Owner must approve this price before customers see it. Until then the current price stays.`}</p>
  </form>
}

function VariantRow({ variant, product, options, owner, run }: { variant: Variant; product: Product; options: Options; owner: boolean; run: (key: string, done: string, action: () => Promise<Product>) => Promise<void> }) {
  const [editing, setEditing] = useState(false), [pricing, setPricing] = useState(false), [busy, setBusy] = useState(false)
  async function act(key: string, done: string, action: () => Promise<Product>) { setBusy(true); await run(key, done, action); setBusy(false) }
  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget)
    void act('variant', 'Option saved.', () => staffWrite<Product>(`/api/staff/catalogue/products/${product.id}/variants/${variant.id}`, { sku: field(form, 'sku'), label: field(form, 'label'), unitCode: field(form, 'unitCode'),
      published: form.get('published') === 'on', sortOrder: Number(form.get('sortOrder')), revision: product.revision }, 'PUT')).then(() => setEditing(false))
  }
  return <li className="ca-variant">
    <div className="ca-variant-head"><div><strong>{variant.label}</strong><small>{variant.sku} · per {variant.unitLabel}{variant.published ? '' : ' · hidden from customers'}</small></div>
      <div className="ca-variant-price"><strong>{priceText(variant.pricingMode, variant.price)}</strong>{variant.pending && <span className="ca-badge ca-waiting">Waiting: {priceText(variant.pending.requestedMode, variant.pending.requestedPrice)}</span>}</div></div>
    {variant.pending && <p className="ca-note">Requested by {variant.pending.requestedByName} on {when(variant.pending.requestedAt)}: “{variant.pending.reason}”. {owner ? 'Approve or reject it in “Prices waiting for you” above.' : 'Waiting for the Owner.'}</p>}
    <div className="ca-actions"><button type="button" className="button secondary" aria-expanded={editing} onClick={() => setEditing(!editing)}>{editing ? 'Cancel' : 'Edit option'}</button>
      {(owner || !variant.pending) && <button type="button" className="button secondary" aria-expanded={pricing} onClick={() => setPricing(!pricing)}>{pricing ? 'Cancel' : owner ? 'Change price' : 'Request price change'}</button>}</div>
    {editing && <form className="ca-grid" onSubmit={save}>
      <label>Option name<input name="label" defaultValue={variant.label} required maxLength={120} /></label>
      <label>Option SKU<input name="sku" defaultValue={variant.sku} required maxLength={80} /></label>
      <label>Selling unit<select name="unitCode" defaultValue={variant.unitCode} disabled={variant.unitLocked}>{options.units.map(unit => <option key={unit.code} value={unit.code}>{unit.label}</option>)}</select>
        {variant.unitLocked && <><input type="hidden" name="unitCode" value={variant.unitCode} /><small>Locked: stock or orders already use this unit.</small></>}</label>
      <label>Order in list<input name="sortOrder" type="number" min="0" max="1000" defaultValue={variant.sortOrder} /></label>
      <label className="ca-check"><input name="published" type="checkbox" defaultChecked={variant.published} /><span>Customers can choose this option</span></label>
      <button className="button primary" disabled={busy}>{busy ? 'Saving…' : 'Save option'}</button></form>}
    {pricing && <PriceForm owner={owner} busy={busy} label={variant.label} onSubmit={(mode, price, reason) => void act('price', owner ? 'Price changed.' : 'Price sent to the Owner for approval.',
      () => staffWrite<Product>(`/api/staff/catalogue/products/${product.id}/variants/${variant.id}/price`, { id: crypto.randomUUID(), pricingMode: mode, price, reason, revision: product.revision })).then(() => setPricing(false))} />}
  </li>
}

function Specs({ initial }: { initial: Spec[] }) {
  const [rows, setRows] = useState(() => initial.map(spec => ({ ...spec, key: crypto.randomUUID() })))
  return <fieldset className="ca-specs"><legend>Specifications (optional)</legend>
    {rows.map((row, index) => <div key={row.key} className="ca-spec">
      <label>Name<input name="specLabel" defaultValue={row.label} maxLength={100} placeholder="For example: Strength" /></label>
      <label>Value<input name="specValue" defaultValue={row.value} maxLength={500} placeholder="For example: 42.5N" /></label>
      <button type="button" className="button secondary" aria-label={`Remove specification ${index + 1}`} onClick={() => setRows(rows.filter(item => item.key !== row.key))}>Remove</button></div>)}
    {rows.length < 30 && <button type="button" className="text-link" onClick={() => setRows([...rows, { label: '', value: '', key: crypto.randomUUID() }])}>+ Add a specification</button>}
  </fieldset>
}
function readSpecs(form: FormData): Spec[] {
  const labels = form.getAll('specLabel').map(String), values = form.getAll('specValue').map(String)
  return labels.map((label, index) => ({ label: label.trim(), value: (values[index] ?? '').trim() })).filter(spec => spec.label || spec.value)
}

function DetailsFields({ product, options }: { product?: Product; options: Options }) {
  return <>
    <div className="ca-grid">
      <label>Product name<input name="name" required minLength={2} maxLength={200} defaultValue={product?.name} placeholder="As written on the shelf label" /></label>
      <label>Product SKU or shop code<input name="sku" required maxLength={80} defaultValue={product?.sku} placeholder="For example: TWG-CEM-425" /></label>
      <label>Brand<input name="brand" maxLength={100} defaultValue={product?.brand} /></label>
      <label>Category<select name="categoryId" required defaultValue={product?.categoryId}>{options.categories.map(category => <option key={category.id} value={category.id}>{category.name}{category.published ? '' : ' (hidden)'}</option>)}</select></label>
    </div>
    <label>Description for customers<textarea name="description" rows={4} maxLength={4000} defaultValue={product?.description} placeholder="What it is, what it is used for, and anything to check before buying (at least 10 characters to publish)" /></label>
    <label className="ca-check"><input name="featured" type="checkbox" defaultChecked={product?.featured} /><span>Feature on the home page</span></label>
  </>
}

function NewProduct({ options, owner, onCreated, onClose }: { options: Options; owner: boolean; onCreated: (product: Product) => void; onClose: () => void }) {
  const [request] = useState(() => ({ product: crypto.randomUUID(), variant: crypto.randomUUID() }))
  const [mode, setMode] = useState<'FIXED' | 'QUOTE'>('FIXED'), [busy, setBusy] = useState(false), [error, setError] = useState('')
  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return
    const form = new FormData(event.currentTarget); setBusy(true); setError('')
    try {
      onCreated(await staffWrite<Product>('/api/staff/catalogue/products', { id: request.product, sku: field(form, 'sku'), name: field(form, 'name'), brand: field(form, 'brand'), categoryId: field(form, 'categoryId'),
        description: field(form, 'description'), featured: form.get('featured') === 'on', specifications: readSpecs(form),
        variants: [{ id: request.variant, sku: field(form, 'variantSku'), label: field(form, 'variantLabel'), unitCode: field(form, 'unitCode'), pricingMode: mode, price: mode === 'FIXED' ? Number(form.get('price')) : null, published: true, sortOrder: 0 }] }))
    } catch (problem) { setError(staffError(problem)) } finally { setBusy(false) }
  }
  return <form className="commerce-panel ca-panel" onSubmit={create} aria-labelledby="ca-new">
    <div className="ca-heading"><h2 id="ca-new">Add a product from the shop</h2><button type="button" className="button secondary" onClick={onClose}>Cancel</button></div>
    {error && <WorkspaceError message={error} />}
    <DetailsFields options={options} />
    <fieldset className="ca-grid"><legend>First selling option (you can add more after saving)</legend>
      <label>Option name<input name="variantLabel" required maxLength={120} placeholder="For example: 50 kg bag" /></label>
      <label>Option SKU<input name="variantSku" required maxLength={80} placeholder="For example: TWG-CEM-425-50" /></label>
      <label>Selling unit<select name="unitCode" defaultValue="piece">{options.units.map(unit => <option key={unit.code} value={unit.code}>{unit.label}</option>)}</select></label>
      <label>Price type<select value={mode} onChange={event => setMode(event.target.value as 'FIXED' | 'QUOTE')}><option value="FIXED">Fixed price (TSh)</option><option value="QUOTE">Ask for a quote (no fixed price)</option></select></label>
      {mode === 'FIXED' && <label>Price, TSh<input name="price" type="number" min="0" step="0.01" required /></label>}
    </fieldset>
    {!owner && mode === 'FIXED' && <p className="ca-note">The Owner approves prices. Until then customers see “Ask for a quote” for this option.</p>}
    <Specs initial={[]} />
    <div className="ca-actions"><button className="button primary" disabled={busy}>{busy ? 'Saving…' : 'Save as not published'}</button></div>
    <p className="commerce-muted">After saving, add photos and check the details, then publish it. Receive its stock on the Inventory page.</p>
  </form>
}

function Photos({ product, run }: { product: Product; run: (key: string, done: string, action: () => Promise<Product>) => Promise<void> }) {
  const [order, setOrder] = useState(() => product.images.map(image => ({ id: image.id, alt: image.alt, path: image.path })))
  const [busy, setBusy] = useState(false)
  const changed = JSON.stringify(order.map(item => [item.id, item.alt])) !== JSON.stringify(product.images.map(image => [image.id, image.alt]))
  const move = (index: number, by: number) => { const next = [...order]; const [item] = next.splice(index, 1); next.splice(index + by, 0, item); setOrder(next) }
  async function act(done: string, action: () => Promise<Product>) { setBusy(true); await run('photos', done, action); setBusy(false) }
  return <section className="ca-section" aria-labelledby="ca-photos"><h3 id="ca-photos">Photos</h3>
    <p className="commerce-muted">Take clear photos of the real item in the shop. JPEG or PNG, at least 200 × 200 pixels, up to 10 MB. Photos are resized and any location or camera details are removed. The first photo is the main one.</p>
    {order.length === 0 ? <p className="ca-note">No photos yet. Customers will see a placeholder.</p> : <ol className="ca-photos">{order.map((image, index) => <li key={image.id}>
      <img src={staffPhoto(image.path)} alt="" />
      <label>Description<input value={image.alt} maxLength={200} onChange={event => setOrder(order.map(item => item.id === image.id ? { ...item, alt: event.target.value } : item))} placeholder="What the photo shows" /></label>
      <div className="ca-actions"><button type="button" className="button secondary" disabled={index === 0} aria-label={`Move photo ${index + 1} earlier`} onClick={() => move(index, -1)}>↑</button>
        <button type="button" className="button secondary" disabled={index === order.length - 1} aria-label={`Move photo ${index + 1} later`} onClick={() => move(index, 1)}>↓</button>
        <button type="button" className="button secondary" onClick={() => setOrder(order.filter(item => item.id !== image.id))}>Remove</button></div>
    </li>)}</ol>}
    {changed && <div className="ca-actions"><button type="button" className="button primary" disabled={busy} onClick={() => void act('Photos saved.', () => staffWrite<Product>(`/api/staff/catalogue/products/${product.id}/photos`, { revision: product.revision, images: order.map(item => ({ id: item.id, alt: item.alt })) }, 'PUT'))}>Save photo changes</button>
      <button type="button" className="button secondary" onClick={() => setOrder(product.images.map(image => ({ id: image.id, alt: image.alt, path: image.path })))}>Undo</button></div>}
    {!changed && order.length < 8 && <label className="ca-upload">Add a photo<input type="file" accept="image/jpeg,image/png" disabled={busy || isClientPreview} onChange={event => {
      const file = event.target.files?.[0]; event.target.value = ''
      if (file) void act('Photo added.', () => staffUpload<Product>(`/api/staff/catalogue/products/${product.id}/photos?revision=${product.revision}&photo=${crypto.randomUUID()}&alt=${encodeURIComponent(product.name.slice(0, 120))}`, file))
    }} /></label>}
    {busy && <p role="status">Working on the photos…</p>}
  </section>
}

function Editor({ id, options, owner, onChanged, onClose }: { id: string; options: Options; owner: boolean; onChanged: () => void; onClose: () => void }) {
  const loaded = useWorkspaceData<Product>('/api/staff/catalogue/products/' + id)
  const [product, setProduct] = useState<Product>()
  const [busy, setBusy] = useState(''), [error, setError] = useState(''), [notice, setNotice] = useState(''), [adding, setAdding] = useState(false)
  const [newMode, setNewMode] = useState<'FIXED' | 'QUOTE'>('FIXED')
  const current = product ?? loaded.data
  if (loaded.error && !current) return <WorkspaceError message={loaded.error} retry={loaded.retry} />
  if (!current) return <p role="status">Loading the product…</p>
  const item = current
  async function run(key: string, done: string, action: () => Promise<Product>) {
    if (busy) return
    setBusy(key); setError(''); setNotice('')
    try { setProduct(await action()); setNotice(done); onChanged() } catch (problem) { setError(staffError(problem)) } finally { setBusy('') }
  }
  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget)
    void run('details', 'Details saved.', () => staffWrite<Product>('/api/staff/catalogue/products/' + item.id, { sku: field(form, 'sku'), name: field(form, 'name'), brand: field(form, 'brand'), categoryId: field(form, 'categoryId'),
      description: field(form, 'description'), featured: form.get('featured') === 'on', specifications: readSpecs(form), revision: item.revision }, 'PUT'))
  }
  function status(action: 'publish' | 'unpublish' | 'archive' | 'restore', done: string) { void run(action, done, () => staffWrite<Product>(`/api/staff/catalogue/products/${item.id}/${action}`, { revision: item.revision })) }
  function addVariant(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget)
    void run('add', owner || newMode === 'QUOTE' ? 'Option added.' : 'Option added. Its price is waiting for the Owner.', () => staffWrite<Product>(`/api/staff/catalogue/products/${item.id}/variants`, { id: crypto.randomUUID(), sku: field(form, 'sku'), label: field(form, 'label'),
      unitCode: field(form, 'unitCode'), pricingMode: newMode, price: newMode === 'FIXED' ? Number(form.get('price')) : null, published: true, sortOrder: item.variants.length, revision: item.revision })).then(() => setAdding(false))
  }
  const state = item.archived ? 'ARCHIVED' : item.published ? 'PUBLISHED' : 'DRAFT'
  return <section className="commerce-panel ca-panel" aria-labelledby="ca-edit">
    <div className="ca-heading"><div><p className="eyebrow"><span className={`ca-badge ca-${state}`}>{statusName[state]}</span>{item.sample && <span className="ca-badge ca-sample">Sample product</span>}</p><h2 id="ca-edit">{item.name}</h2>
      <p className="commerce-muted">{item.sku} · {item.categoryName} · last changed {when(item.updatedAt)}</p></div>
      <button type="button" className="button secondary" onClick={onClose}>Close</button></div>
    {error && <WorkspaceError message={error} />}{notice && <p className="ca-notice" role="status">{notice}</p>}
    {item.sample && <p className="ca-note">This is development sample data, not a real shop product. Archive it once the real product is entered{owner ? ', or use “Archive all sample products” above' : ''}.</p>}

    <div className="ca-status-bar">
      {!item.archived && !item.published && <button type="button" className="button primary" disabled={Boolean(busy)} onClick={() => status('publish', 'Published. Customers can now see this product.')}>{busy === 'publish' ? 'Publishing…' : 'Publish to the shop'}</button>}
      {item.published && <button type="button" className="button secondary" disabled={Boolean(busy)} onClick={() => status('unpublish', 'Hidden from customers.')}>Hide from customers</button>}
      {!item.archived && <button type="button" className="button secondary" disabled={Boolean(busy)} onClick={() => { if (window.confirm(`Archive ${item.name}? Customers will no longer see it. Past orders keep their details.`)) status('archive', 'Archived.') }}>Archive</button>}
      {item.archived && <button type="button" className="button secondary" disabled={Boolean(busy)} onClick={() => status('restore', 'Restored. It is not published yet.')}>Restore</button>}
      <a className="text-link" href="#/staff/inventory">Receive stock →</a><a className="text-link" href="#/staff/models">3D model →</a>
      {item.published && <a className="text-link" href={`#/product/${item.id}`}>View in the shop →</a>}
    </div>

    <form key={item.revision} className="ca-section" onSubmit={save} aria-labelledby="ca-details"><h3 id="ca-details">Details</h3>
      <DetailsFields product={item} options={options} /><Specs initial={item.specifications} />
      <div className="ca-actions"><button className="button primary" disabled={Boolean(busy)}>{busy === 'details' ? 'Saving…' : 'Save details'}</button></div></form>

    <section className="ca-section" aria-labelledby="ca-options"><h3 id="ca-options">Selling options and prices</h3>
      {!owner && <p className="commerce-muted">You can change names, SKUs and units. Price changes go to the Owner for approval and the current price stays until then.</p>}
      <ul className="ca-variants">{item.variants.map(variant => <VariantRow key={variant.id + ':' + item.revision} variant={variant} product={item} options={options} owner={owner} run={run} />)}</ul>
      {item.variants.length < 30 && (adding ? <form className="ca-grid ca-add" onSubmit={addVariant}>
        <label>Option name<input name="label" required maxLength={120} placeholder="For example: 25 kg bag" /></label>
        <label>Option SKU<input name="sku" required maxLength={80} /></label>
        <label>Selling unit<select name="unitCode" defaultValue={item.variants[0]?.unitCode ?? 'piece'}>{options.units.map(unit => <option key={unit.code} value={unit.code}>{unit.label}</option>)}</select></label>
        <label>Price type<select value={newMode} onChange={event => setNewMode(event.target.value as 'FIXED' | 'QUOTE')}><option value="FIXED">Fixed price (TSh)</option><option value="QUOTE">Ask for a quote (no fixed price)</option></select></label>
        {newMode === 'FIXED' && <label>Price, TSh<input name="price" type="number" min="0" step="0.01" required /></label>}
        <div className="ca-actions"><button className="button primary" disabled={Boolean(busy)}>Add option</button><button type="button" className="button secondary" onClick={() => setAdding(false)}>Cancel</button></div>
      </form> : <button type="button" className="text-link" onClick={() => setAdding(true)}>+ Add another option (size, colour, pack)</button>)}
    </section>

    <Photos key={'photos:' + item.revision} product={item} run={run} />
  </section>
}

function PriceQueue({ onDecided }: { onDecided: () => void }) {
  const queue = useWorkspaceData<{ items: PriceChange[]; total: number }>('/api/staff/catalogue/price-changes?status=PENDING&size=20')
  const [busy, setBusy] = useState(''), [error, setError] = useState('')
  async function decide(change: PriceChange, action: 'approve' | 'reject') {
    const note = action === 'reject' ? window.prompt(`Why is the price for ${change.productName}, ${change.variantLabel} not approved?`) : ''
    if (note === null) return
    setBusy(change.id); setError('')
    try { await staffWrite(`/api/staff/catalogue/price-changes/${change.id}/${action}`, { note }); queue.retry(); onDecided() } catch (problem) { setError(staffError(problem)) } finally { setBusy('') }
  }
  if (!queue.data?.items.length) return null
  return <section className="commerce-panel ca-panel ca-queue" aria-labelledby="ca-queue"><h2 id="ca-queue">Prices waiting for you ({queue.data.total})</h2>
    {error && <WorkspaceError message={error} />}
    <ul>{queue.data.items.map(change => <li key={change.id}>
      <div><strong>{change.productName}, {change.variantLabel}</strong><small>{change.sku} · from {priceText(change.previousMode, change.previousPrice)} to <b>{priceText(change.requestedMode, change.requestedPrice)}</b></small>
        <small>{change.requestedByName}, {when(change.requestedAt)}: “{change.reason}”</small></div>
      <div className="ca-actions"><button type="button" className="button primary" disabled={busy === change.id} onClick={() => void decide(change, 'approve')}>Approve</button>
        <button type="button" className="button secondary" disabled={busy === change.id} onClick={() => void decide(change, 'reject')}>Reject</button></div>
    </li>)}</ul></section>
}

/** FR-01 to FR-06: Owner and Inventory enter the shop's real products. Only the Owner changes prices (C4). */
export function Catalogue({ role }: { role: StaffRole }) {
  const owner = role === 'OWNER'
  const [filter, setFilter] = useState<Filter>(''), [query, setQuery] = useState(''), [page, setPage] = useState(0)
  const [open, setOpen] = useState<string | 'new' | null>(null), [refresh, setRefresh] = useState(0)
  const [archiving, setArchiving] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('')
  const options = useWorkspaceData<Options>('/api/staff/catalogue/options')
  const list = useWorkspaceData<Page>(`/api/staff/catalogue/products?filter=${filter}&q=${encodeURIComponent(query)}&page=${page}&size=12&r=${refresh}`)
  const changed = () => setRefresh(n => n + 1)
  const pages = list.data ? Math.max(1, Math.ceil(list.data.total / list.data.size)) : 1
  async function archiveSamples() {
    const typed = window.prompt(`This hides all ${list.data?.liveSamples} sample products from the shop. They stay in the records. Type ARCHIVE SAMPLES to continue.`)
    if (typed === null) return
    setArchiving(true); setError(''); setNotice('')
    try { const result = await staffWrite<{ archived: number }>('/api/staff/catalogue/samples/archive', { confirm: typed.trim() }); setNotice(`${result.archived} sample products archived.`); changed() }
    catch (problem) { setError(staffError(problem)) } finally { setArchiving(false) }
  }
  return <>
    <p className="eyebrow">Catalogue</p><h1>Products</h1>
    <p className="commerce-intro">Enter the products the shop really sells, with photos, options and specifications. {owner ? 'Your prices apply straight away.' : 'Prices you enter are sent to the Owner for approval.'}</p>
    {isClientPreview && <p className="ca-note">Client preview: products are shown read-only. Adding and editing need the full application.</p>}
    {error && <WorkspaceError message={error} />}{notice && <p className="ca-notice" role="status">{notice}</p>}
    {owner && !isClientPreview && <PriceQueue key={refresh} onDecided={changed} />}
    {owner && (list.data?.liveSamples ?? 0) > 0 && <div className="ca-note ca-samples"><span>{list.data?.liveSamples} development sample products are not archived.</span><button type="button" className="button secondary" disabled={archiving || isClientPreview} onClick={() => void archiveSamples()}>{archiving ? 'Archiving…' : 'Archive all sample products'}</button></div>}
    {options.error ? <WorkspaceError message={options.error} retry={options.retry} /> : options.data && (open === 'new'
      ? <NewProduct options={options.data} owner={owner} onClose={() => setOpen(null)} onCreated={product => { setOpen(product.id); changed() }} />
      : open ? <Editor key={open} id={open} options={options.data} owner={owner} onChanged={changed} onClose={() => setOpen(null)} /> : null)}
    <div className="ca-toolbar">
      <div className="ca-filters" role="group" aria-label="Show products">{filters.map(item => <button key={item.id} type="button" aria-pressed={filter === item.id} onClick={() => { setFilter(item.id); setPage(0) }}>{item.name}</button>)}</div>
      <label>Search<input type="search" value={query} maxLength={100} placeholder="Name, brand or SKU" onChange={event => { setQuery(event.target.value); setPage(0) }} /></label>
      <button type="button" className="button primary" disabled={isClientPreview || !options.data} onClick={() => { setOpen('new'); window.scrollTo({ top: 0 }) }}>Add a product</button>
    </div>
    {list.error ? <WorkspaceError message={list.error} retry={list.retry} /> : !list.data ? <p role="status">Loading products…</p> : <>
      <p className="staff-result-count">{list.data.total} product{list.data.total === 1 ? '' : 's'}{list.data.pendingPrices ? ` · ${list.data.pendingPrices} price change${list.data.pendingPrices === 1 ? '' : 's'} waiting for the Owner` : ''}</p>
      {list.data.items.length === 0 ? <p className="commerce-muted">No products match. Use “Add a product” to enter one from the shop.</p> : <ul className="ca-list">{list.data.items.map(product => <li key={product.id} className="commerce-panel">
        {product.image ? <img src={staffPhoto(product.image)} alt="" /> : <span className="ca-noimage" aria-hidden="true">No photo</span>}
        <div><p className="eyebrow"><span className={`ca-badge ca-${product.status}`}>{statusName[product.status]}</span>{product.sample && <span className="ca-badge ca-sample">Sample</span>}{product.pendingPrices > 0 && <span className="ca-badge ca-waiting">Price waiting</span>}</p>
          <h3>{product.name}</h3><p className="commerce-muted">{product.sku}{product.brand ? ` · ${product.brand}` : ''} · {product.categoryName} · {product.variants} option{product.variants === 1 ? '' : 's'} · from {money(product.lowestPrice)}</p></div>
        <button type="button" className="button secondary" onClick={() => { setOpen(product.id); window.scrollTo({ top: 0 }) }}>Open<span className="sr-only"> {product.name}</span></button>
      </li>)}</ul>}
      {pages > 1 && <nav className="ca-pages" aria-label="Product pages"><button type="button" className="button secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button><span>Page {page + 1} of {pages}</span><button type="button" className="button secondary" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Next</button></nav>}
    </>}
  </>
}
