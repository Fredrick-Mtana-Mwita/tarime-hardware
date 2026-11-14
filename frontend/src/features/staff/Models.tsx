import { lazy, Suspense, useState } from 'react'
import type { FormEvent } from 'react'
import { builtInKinds, roomFinishes, zones } from '../../experiences/manifest'
import type { ModelKind, RoomSurface } from '../../experiences/manifest'
import { isClientPreview } from '../../shared/preview'
import { staffError, staffUpload, staffWrite } from './api'
import type { StaffRole } from './api'
import { WorkspaceError } from './Inventory'
import { useWorkspaceData } from './useWorkspaceData'
import './models.css'

const ModelStage = lazy(() => import('../../experiences/ModelStage').then(module => ({ default: module.ModelStage })))

type Status = 'DRAFT' | 'IN_REVIEW' | 'PUBLISHED' | 'RETIRED'
interface Report { meshes: number; materials: number; textures: number; images: number; nodes: number; binaryBytes: number; extensions: string[]; warnings: string[] }
interface Asset {
  id: string; productId: string; productName: string; productSku: string; productArchived: boolean; version: number; status: Status; sourceKind: 'BUILT_IN' | 'GLB'
  modelKind: ModelKind | null; fileSha256: string | null; fileBytes: number | null; fileName: string | null; validation: Report | null; sourceDescription: string
  licenseTerms: string; attribution: string; rightsConfirmed: boolean; verifiedExact: boolean; widthMm: number | null; heightMm: number | null; depthMm: number | null
  showroomZone: string; roomSurface: RoomSurface | null; roomFinish: string | null; tileMm: number | null; variantScales: Record<string, number>; revision: number
  createdByName: string | null; updatedAt: string; submittedByName: string | null; reviewedByName: string | null; reviewNote: string; publishedAt: string | null
}
interface AssetPage { items: Asset[]; total: number; inReview: number; page: number; size: number }
interface ProductChoice { id: string; sku: string; name: string; published: boolean; variants: { id: string; sku: string; label: string }[]; publishedVersion: number | null }

const statusNames: Record<Status, string> = { DRAFT: 'Draft', IN_REVIEW: 'Waiting for Owner', PUBLISHED: 'Published', RETIRED: 'Retired' }
const words = (value: string) => value.replace(/-/g, ' ')
const megabytes = (bytes: number) => bytes < 1048576 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1048576).toFixed(1)} MB`
const when = (value: string) => new Date(value).toLocaleString('en-TZ', { dateStyle: 'medium', timeStyle: 'short' })
const optional = (value: FormDataEntryValue | null) => { const text = String(value ?? '').trim(); return text ? Number(text) : null }

function NewModel({ onCreated, onClose }: { onCreated: (asset: Asset) => void; onClose: () => void }) {
  const [query, setQuery] = useState('')
  const products = useWorkspaceData<ProductChoice[]>('/api/staff/models/products?q=' + encodeURIComponent(query))
  const [request] = useState(() => crypto.randomUUID())
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return
    const fields = new FormData(event.currentTarget), source = String(fields.get('sourceKind'))
    setBusy(true); setError('')
    try {
      onCreated(await staffWrite<Asset>('/api/staff/models', { id: request, productId: fields.get('productId'), sourceKind: source, modelKind: source === 'BUILT_IN' ? fields.get('modelKind') : null,
        showroomZone: fields.get('showroomZone'), rightsConfirmed: false, variantScales: {} }))
    } catch (problem) { setError(staffError(problem)) } finally { setBusy(false) }
  }
  return <form className="commerce-panel sm-panel" onSubmit={create} aria-labelledby="sm-new">
    <div className="sm-heading"><h2 id="sm-new">New 3D model</h2><button type="button" className="button secondary" onClick={onClose}>Cancel</button></div>
    {error && <WorkspaceError message={error} />}
    <label>Find the product<input type="search" value={query} maxLength={100} onChange={event => setQuery(event.target.value)} placeholder="Name or SKU" /></label>
    {products.error ? <WorkspaceError message={products.error} retry={products.retry} /> : !products.data ? <p role="status">Searching…</p> : products.data.length === 0 ? <p className="commerce-muted">No active product matches.</p>
      : <fieldset className="sm-choices"><legend>Product</legend>{products.data.map((product, index) => <label key={product.id}><input type="radio" name="productId" value={product.id} required defaultChecked={index === 0} />
        <span><strong>{product.name}</strong><small>{product.sku} · {product.published ? 'On sale' : 'Not on sale'} · {product.publishedVersion ? `3D version ${product.publishedVersion} is live` : 'No 3D model yet'}</small></span></label>)}</fieldset>}
    <div className="sm-grid">
      <label>Model type<select name="sourceKind" defaultValue="GLB"><option value="GLB">Uploaded GLB file</option><option value="BUILT_IN">Built-in illustrative shape</option></select></label>
      <label>Built-in shape (if chosen)<select name="modelKind" defaultValue={builtInKinds[0]}>{builtInKinds.map(kind => <option key={kind} value={kind}>{words(kind)}</option>)}</select></label>
      <label>Showroom area<select name="showroomZone">{zones.map(zone => <option key={zone.id} value={zone.id}>{zone.name}</option>)}</select></label>
    </div>
    <button className="button primary" disabled={busy || !products.data?.length}>{busy ? 'Creating…' : 'Create draft'}</button>
  </form>
}

function Editor({ initial, role, onChanged, onClose }: { initial: Asset; role: StaffRole; onChanged: () => void; onClose: () => void }) {
  const [asset, setAsset] = useState(initial)
  const [busy, setBusy] = useState(''), [error, setError] = useState(''), [notice, setNotice] = useState(''), [preview, setPreview] = useState(false)
  const [source, setSource] = useState(asset.sourceKind), [surface, setSurface] = useState<RoomSurface | ''>(asset.roomSurface ?? '')
  const product = useWorkspaceData<ProductChoice[]>('/api/staff/models/products?q=' + encodeURIComponent(asset.productSku)).data?.find(item => item.id === asset.productId)
  const owner = role === 'OWNER', draft = asset.status === 'DRAFT'
  async function run(label: string, done: string, action: () => Promise<Asset>) {
    if (busy) return
    setBusy(label); setError(''); setNotice('')
    try { setAsset(await action()); setNotice(done); onChanged() } catch (problem) { setError(staffError(problem)) } finally { setBusy('') }
  }
  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const fields = new FormData(event.currentTarget)
    const variantScales = Object.fromEntries((product?.variants ?? []).flatMap(variant => { const value = optional(fields.get('scale:' + variant.id)); return value ? [[variant.id, value]] : [] }))
    void run('save', 'Draft saved.', () => staffWrite<Asset>('/api/staff/models/' + asset.id, { sourceKind: source, modelKind: source === 'BUILT_IN' ? fields.get('modelKind') : null,
      sourceDescription: fields.get('sourceDescription'), licenseTerms: fields.get('licenseTerms'), attribution: fields.get('attribution'), rightsConfirmed: fields.get('rightsConfirmed') === 'on',
      widthMm: optional(fields.get('widthMm')), heightMm: optional(fields.get('heightMm')), depthMm: optional(fields.get('depthMm')), showroomZone: fields.get('showroomZone'),
      roomSurface: surface || null, roomFinish: surface ? fields.get('roomFinish') : null, tileMm: surface && surface !== 'light' ? optional(fields.get('tileMm')) : null, variantScales, revision: asset.revision }, 'PUT'))
  }
  function decide(action: 'submit' | 'return' | 'publish' | 'retire', done: string, form?: HTMLFormElement) {
    const fields = form ? new FormData(form) : null
    void run(action, done, () => staffWrite<Asset>(`/api/staff/models/${asset.id}/${action}`, { revision: asset.revision, note: fields?.get('note') ?? '', verifiedExact: fields?.get('verifiedExact') === 'on' }))
  }
  const hasFile = asset.sourceKind === 'GLB' && asset.fileSha256
  const canPreview = asset.sourceKind === 'BUILT_IN' ? Boolean(asset.modelKind) : Boolean(hasFile)
  return <section className="commerce-panel sm-panel" aria-labelledby="sm-edit">
    <div className="sm-heading"><div><p className="eyebrow">Version {asset.version} · <span className={`sm-status sm-${asset.status}`}>{statusNames[asset.status]}</span></p><h2 id="sm-edit">{asset.productName}</h2>
      <p className="commerce-muted">{asset.productSku}{asset.productArchived ? ' · product archived' : ''}{asset.verifiedExact ? ' · approved as the exact product' : ''}</p></div>
      <button type="button" className="button secondary" onClick={onClose}>Close</button></div>
    {error && <WorkspaceError message={error} />}{notice && <p className="sm-notice" role="status">{notice}</p>}
    {asset.reviewNote && <p className="sm-note"><strong>{asset.status === 'DRAFT' ? 'Returned by' : 'Note from'} {asset.reviewedByName ?? 'the Owner'}:</strong> {asset.reviewNote}</p>}

    {draft ? <form onSubmit={save} className="sm-form">
      <div className="sm-grid">
        <label>Model type<select value={source} onChange={event => setSource(event.target.value as Asset['sourceKind'])}><option value="GLB">Uploaded GLB file</option><option value="BUILT_IN">Built-in illustrative shape</option></select></label>
        {source === 'BUILT_IN' && <label>Built-in shape<select name="modelKind" defaultValue={asset.modelKind ?? builtInKinds[0]}>{builtInKinds.map(kind => <option key={kind} value={kind}>{words(kind)}</option>)}</select></label>}
        <label>Showroom area<select name="showroomZone" defaultValue={asset.showroomZone}>{zones.map(zone => <option key={zone.id} value={zone.id}>{zone.name}</option>)}</select></label>
        <label>Room visualizer use<select value={surface} onChange={event => setSurface(event.target.value as RoomSurface | '')}><option value="">Not used in rooms</option><option value="floor">Floor finish</option><option value="wall">Wall finish</option><option value="light">Light fitting</option></select></label>
        {surface && <label>Room finish<select name="roomFinish" key={surface} defaultValue={asset.roomSurface === surface ? asset.roomFinish ?? undefined : undefined}>{roomFinishes[surface].map(finish => <option key={finish} value={finish}>{words(finish)}</option>)}</select></label>}
        {surface && surface !== 'light' && <label>Real tile edge, mm (if known)<input name="tileMm" type="number" min="10" max="3000" defaultValue={asset.tileMm ?? ''} /></label>}
      </div>
      <fieldset className="sm-grid"><legend>Measured size in millimetres (needed to approve as the exact product)</legend>
        <label>Width<input name="widthMm" type="number" min="1" max="100000" defaultValue={asset.widthMm ?? ''} /></label>
        <label>Height<input name="heightMm" type="number" min="1" max="100000" defaultValue={asset.heightMm ?? ''} /></label>
        <label>Depth<input name="depthMm" type="number" min="1" max="100000" defaultValue={asset.depthMm ?? ''} /></label></fieldset>
      {product && product.variants.length > 1 && <fieldset className="sm-grid"><legend>Relative size of each option (1 = full size; leave empty for 1)</legend>
        {product.variants.map(variant => <label key={variant.id}>{variant.label}<input name={'scale:' + variant.id} type="number" min="0.2" max="3" step="0.05" defaultValue={asset.variantScales[variant.id] ?? ''} /></label>)}</fieldset>}
      <fieldset className="sm-stack"><legend>Where the model came from</legend>
        <label>Source (who made it, or where it was obtained)<input name="sourceDescription" maxLength={300} defaultValue={asset.sourceDescription} /></label>
        <label>Licence or permission terms<input name="licenseTerms" maxLength={300} defaultValue={asset.licenseTerms} /></label>
        <label>Attribution to show, if the licence requires it<input name="attribution" maxLength={300} defaultValue={asset.attribution} /></label>
        <label className="sm-check"><input name="rightsConfirmed" type="checkbox" defaultChecked={asset.rightsConfirmed} /><span>I confirm the shop is allowed to show this model on its website.</span></label></fieldset>
      <div className="sm-actions"><button className="button primary" disabled={Boolean(busy)}>{busy === 'save' ? 'Saving…' : 'Save draft'}</button>
        <button type="button" className="button secondary" disabled={Boolean(busy)} onClick={() => decide('submit', 'Sent to the Owner for review.')}>{busy === 'submit' ? 'Sending…' : 'Send for review'}</button></div>
      <p className="commerce-muted">Save your changes before uploading a file or sending for review.</p>
    </form> : <dl className="sm-facts">
      <div><dt>Model</dt><dd>{asset.sourceKind === 'BUILT_IN' ? `Built-in shape: ${words(asset.modelKind ?? '')}` : 'Uploaded GLB file'}</dd></div>
      <div><dt>Showroom area</dt><dd>{zones.find(zone => zone.id === asset.showroomZone)?.name}</dd></div>
      <div><dt>Room use</dt><dd>{asset.roomSurface ? `${asset.roomSurface}: ${words(asset.roomFinish ?? '')}${asset.tileMm ? `, ${asset.tileMm} mm tile` : ''}` : 'Not used in rooms'}</dd></div>
      <div><dt>Measured size</dt><dd>{asset.widthMm && asset.heightMm && asset.depthMm ? `${asset.widthMm} × ${asset.heightMm} × ${asset.depthMm} mm` : 'Not recorded'}</dd></div>
      <div><dt>Source</dt><dd>{asset.sourceDescription || 'Not recorded'}</dd></div><div><dt>Licence</dt><dd>{asset.licenseTerms || 'Not recorded'}</dd></div>
      {asset.attribution && <div><dt>Attribution</dt><dd>{asset.attribution}</dd></div>}
      <div><dt>Prepared by</dt><dd>{asset.submittedByName ?? asset.createdByName ?? 'Development sample'}</dd></div>
    </dl>}

    {asset.sourceKind === 'GLB' && <div className="sm-file"><h3>Model file</h3>
      {hasFile ? <p>{asset.fileName} · {megabytes(asset.fileBytes ?? 0)} · {asset.validation ? `${asset.validation.meshes} mesh${asset.validation.meshes === 1 ? '' : 'es'}, ${asset.validation.materials} material${asset.validation.materials === 1 ? '' : 's'}, ${asset.validation.images} image${asset.validation.images === 1 ? '' : 's'}` : ''}</p> : <p className="commerce-muted">No file uploaded yet.</p>}
      {asset.validation?.warnings.map(warning => <p key={warning} className="sm-warning">{warning}</p>)}
      {draft && <label className="sm-upload">{hasFile ? 'Replace the file' : 'Upload a .glb file'} (binary glTF 2.0, up to 15 MB, textures embedded, no Draco or Meshopt compression)
        <input type="file" accept=".glb,model/gltf-binary" disabled={Boolean(busy) || isClientPreview} onChange={event => { const file = event.target.files?.[0]; event.target.value = ''
          if (file) void run('upload', 'File checked and stored.', () => staffUpload<Asset>(`/api/staff/models/${asset.id}/file?revision=${asset.revision}&name=${encodeURIComponent(file.name.slice(-120))}`, file)) }} /></label>}
      {busy === 'upload' && <p role="status">Checking and storing the file…</p>}
      <p className="commerce-muted">The upload check confirms the file is a self-contained GLB the storefront can draw. It does not replace the Khronos glTF Validator or a visual check against the real product.</p></div>}

    {canPreview && <div className="sm-preview"><button type="button" className="button secondary" aria-expanded={preview} onClick={() => setPreview(!preview)}>{preview ? 'Hide 3D preview' : 'Show 3D preview'}</button>
      {preview && <Suspense fallback={<p role="status">Loading the 3D preview…</p>}><ModelStage name={asset.productName} verified={asset.verifiedExact}
        model={{ kind: asset.sourceKind === 'BUILT_IN' ? asset.modelKind : null, modelUrl: hasFile ? `/api/staff/models/${asset.id}/file#${asset.fileSha256}` : null }} /></Suspense>}</div>}

    {asset.status === 'IN_REVIEW' && (owner ? <form className="sm-form sm-decision" onSubmit={event => { event.preventDefault(); decide('publish', 'Published. It is now live on the storefront.', event.currentTarget) }}>
      <h3>Owner decision</h3>
      <label>Note (required when returning)<textarea name="note" maxLength={500} rows={2} /></label>
      {asset.sourceKind === 'GLB' && <label className="sm-check"><input name="verifiedExact" type="checkbox" /><span>Approve as the exact product. Tick only after comparing the model with the real item and its measured size. Otherwise it is labelled illustrative.</span></label>}
      <div className="sm-actions"><button className="button primary" disabled={Boolean(busy)}>{busy === 'publish' ? 'Publishing…' : 'Publish'}</button>
        <button type="button" className="button secondary" disabled={Boolean(busy)} onClick={event => decide('return', 'Returned to draft.', event.currentTarget.form ?? undefined)}>{busy === 'return' ? 'Returning…' : 'Return to draft'}</button></div>
      <p className="commerce-muted">Publishing replaces this product's current live model. The old version is kept as retired so earlier orders still point to it.</p>
    </form> : <p className="sm-note">Waiting for the Owner to review. Only the Owner can publish or return a model.</p>)}
    {asset.status === 'PUBLISHED' && owner && <form className="sm-form sm-decision" onSubmit={event => { event.preventDefault(); decide('retire', 'Retired. The product no longer shows a 3D model.', event.currentTarget) }}>
      <h3>Retire this model</h3><label>Reason<textarea name="note" required minLength={5} maxLength={500} rows={2} /></label>
      <div className="sm-actions"><button className="button secondary" disabled={Boolean(busy)}>{busy === 'retire' ? 'Retiring…' : 'Retire model'}</button></div></form>}
  </section>
}

/** FR-3D-08 and FR-3D-09: Owner and Inventory prepare 3D models; only the Owner publishes, returns or retires them. */
export function Models({ role }: { role: StaffRole }) {
  const [status, setStatus] = useState(''), [query, setQuery] = useState(''), [page, setPage] = useState(0)
  const [open, setOpen] = useState<Asset | 'new' | null>(null)
  const list = useWorkspaceData<AssetPage>(`/api/staff/models?status=${status}&q=${encodeURIComponent(query)}&page=${page}&size=12`)
  const pages = list.data ? Math.max(1, Math.ceil(list.data.total / list.data.size)) : 1
  return <>
    <p className="eyebrow">3D models</p><h1>Product 3D models</h1>
    <p className="commerce-intro">Prepare a 3D model for a product, record where it came from, and send it to the Owner. Only models the Owner publishes appear on the storefront.</p>
    {isClientPreview && <p className="sm-note">Client preview: sample models are shown. Creating, uploading and approving need the full application.</p>}
    {open === 'new' ? <NewModel onClose={() => setOpen(null)} onCreated={asset => { setOpen(asset); list.retry() }} /> : open ? <Editor key={open.id} initial={open} role={role} onChanged={list.retry} onClose={() => setOpen(null)} /> : null}
    <div className="sm-toolbar">
      <label>Status<select value={status} onChange={event => { setStatus(event.target.value); setPage(0) }}><option value="">All</option>{(Object.keys(statusNames) as Status[]).map(key => <option key={key} value={key}>{statusNames[key]}</option>)}</select></label>
      <label>Search<input type="search" value={query} maxLength={100} placeholder="Product name or SKU" onChange={event => { setQuery(event.target.value); setPage(0) }} /></label>
      <button type="button" className="button primary" disabled={isClientPreview} onClick={() => setOpen('new')}>New 3D model</button>
    </div>
    {list.error ? <WorkspaceError message={list.error} retry={list.retry} /> : !list.data ? <p role="status">Loading 3D models…</p> : <>
      <p className="staff-result-count">{list.data.total} model record{list.data.total === 1 ? '' : 's'}{list.data.inReview ? ` · ${list.data.inReview} waiting for the Owner` : ''}</p>
      {list.data.items.length === 0 ? <p className="commerce-muted">No 3D model records match.</p> : <ul className="sm-list">{list.data.items.map(asset => <li key={asset.id} className="commerce-panel">
        <div><p className="eyebrow"><span className={`sm-status sm-${asset.status}`}>{statusNames[asset.status]}</span> · version {asset.version}</p><h3>{asset.productName}</h3>
          <p className="commerce-muted">{asset.productSku} · {asset.sourceKind === 'BUILT_IN' ? `built-in ${words(asset.modelKind ?? '')}` : asset.fileName ? `${asset.fileName}, ${megabytes(asset.fileBytes ?? 0)}` : 'GLB not uploaded'} · {asset.verifiedExact ? 'exact product' : 'illustrative'} · updated {when(asset.updatedAt)}</p></div>
        <button type="button" className="button secondary" onClick={() => { setOpen(asset); window.scrollTo({ top: 0 }) }}>Open<span className="sr-only"> {asset.productName} version {asset.version}</span></button>
      </li>)}</ul>}
      {pages > 1 && <nav className="sm-pages" aria-label="3D model pages"><button type="button" className="button secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button><span>Page {page + 1} of {pages}</span><button type="button" className="button secondary" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Next</button></nav>}
    </>}
  </>
}
