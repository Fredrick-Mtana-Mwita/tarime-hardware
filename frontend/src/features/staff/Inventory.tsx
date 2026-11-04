import { assetUrl } from '../../shared/assetUrl'
import { isClientPreview } from '../../shared/preview'
import { AnimatedNumber } from './WorkspaceMotion'
import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useWorkspaceData } from './useWorkspaceData'
import { StaffError, staffError, staffWrite } from './api'
import { branchName } from '../orders/api'
import type { Branch } from '../orders/api'

interface Item { variantId: string; productName: string; variantLabel: string; sku: string; unit: string; fractional: boolean; category: string; image: string; onHand: number; reserved: number; available: number; lowStock: boolean }
interface StockPage { items: Item[]; total: number; lowStockCount: number; page: number; size: number }
interface Receipt { id: string; branch: Branch; productName: string; variantLabel: string; sku: string; unit: string; quantity: number; sourceReference: string; reason: string; onHandAfter: number; reservedAfter: number; actorName: string; receivedAt: string }
interface History { items: Receipt[]; total: number; page: number; size: number }
export function WorkspaceError({ message, retry }: { message: string; retry?: () => void }) {
  return <div className="form-error" role="alert"><p>{message}</p>{retry && <button className="text-link" onClick={retry}>Try again</button>}</div>
}
const quantity = (value: number) => new Intl.NumberFormat('en-TZ', { maximumFractionDigits: 3 }).format(value)
function ReceiveStock({ item, branch, onClose, onReceived }: { item: Item; branch: Branch; onClose: () => void; onReceived: (receipt: Receipt) => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  const quantityInput = useRef<HTMLInputElement>(null)
  const requestId = useRef(crypto.randomUUID())
  const payload = useRef<object | null>(null)
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [attempted, setAttempted] = useState(false)
  useEffect(() => { const node = dialog.current; node?.showModal(); quantityInput.current?.focus(); return () => node?.close() }, [])
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return
    const fields = new FormData(event.currentTarget)
    payload.current ??= { id: requestId.current, variantId: item.variantId, branch, quantity: Number(fields.get('quantity')), sourceReference: fields.get('sourceReference'), reason: fields.get('reason') }
    setAttempted(true); setBusy(true); setError('')
    try { onReceived(await staffWrite<Receipt>('/api/staff/inventory/receipts', payload.current)) }
    catch (problem) {
      setError(staffError(problem))
      if (problem instanceof StaffError && [400, 404].includes(problem.status)) { payload.current = null; setAttempted(false) }
    } finally { setBusy(false) }
  }
  return <dialog ref={dialog} className="receive-dialog" aria-labelledby="receive-title" onCancel={event => { if (busy) event.preventDefault(); else onClose() }}>
    <div className="receive-heading"><div><p className="eyebrow">{branchName(branch)}</p><h2 id="receive-title">Receive stock</h2></div><button className="button secondary" type="button" disabled={busy} onClick={onClose}>Close</button></div>
    <h3>{item.productName}</h3><p className="commerce-muted">{item.variantLabel} · {item.sku} · {item.unit}</p>
    <p>Record materials physically received at this branch. Existing reservations stay unchanged.</p>
    <form className="staff-form" onSubmit={event => void submit(event)}>
      <fieldset disabled={busy || attempted || isClientPreview}><legend className="sr-only">Receipt details</legend>
        <label htmlFor="receive-quantity">Quantity received ({item.unit})</label><input ref={quantityInput} id="receive-quantity" name="quantity" type="number" min={item.fractional ? '0.001' : '1'} step={item.fractional ? '0.001' : '1'} max="999999999" required />
        <label htmlFor="receive-source">Supplier / delivery reference</label><input id="receive-source" name="sourceReference" required minLength={2} maxLength={160} />
        <label htmlFor="receive-reason">Receipt reason</label><input id="receive-reason" name="reason" required minLength={5} maxLength={500} placeholder="e.g. Supplier delivery checked and accepted" />
      </fieldset>
      {error && <WorkspaceError message={error} />}
      {attempted && error && <p className="commerce-muted">Retry keeps the same receipt details to prevent duplicate stock. Check receipt history before starting another receipt if the outcome is uncertain.</p>}
      <button className="button primary" disabled={busy || isClientPreview}>{isClientPreview ? 'Preview only — nothing is saved' : busy ? 'Recording receipt…' : attempted ? 'Retry this receipt' : 'Record stock receipt'}</button>
    </form>
  </dialog>
}
export function Inventory({ query, history = false }: { query: URLSearchParams; history?: boolean }) {
  const branch: Branch = query.get('branch') === 'MWANZA' ? 'MWANZA' : 'TARIME'
  const page = Math.max(0, Number(query.get('page')) || 0), q = query.get('q') || ''
  const params = new URLSearchParams({ branch, q, page: String(page), size: history ? '10' : '12' })
  const resource = useWorkspaceData<StockPage & History>('/api/staff/inventory' + (history ? '/receipts' : '') + '?' + params)
  const [selected, setSelected] = useState<Item | null>(null), [received, setReceived] = useState<Receipt | null>(null)
  const trigger = useRef<HTMLButtonElement | null>(null)
  useEffect(() => {
    if (!selected && trigger.current) {
      if (trigger.current.isConnected) trigger.current.focus()
      else document.getElementById('main')?.focus()
    }
  }, [selected])
  function link(changes: Record<string, string>) { return '#/staff/' + (history ? 'receipts' : 'inventory') + '?' + new URLSearchParams({ ...Object.fromEntries(params), ...changes }) }
  function close() { setSelected(null) }
  function search(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const data = new FormData(event.currentTarget); window.location.hash = link({ q: String(data.get('q') || '').trim(), branch: String(data.get('branch')), page: '0' }) }
  return <>
    <div className="staff-page-heading"><div><p className="eyebrow">Stock that stays in sync</p><h1>{history ? 'Stock receipts' : 'Inventory'}</h1><p className="commerce-intro">{history ? 'A clear record of received materials, their source and the staff member responsible.' : 'The right materials. The right branch. A clear record.'}</p></div><button className="button secondary" onClick={resource.retry}>Refresh {history ? 'receipts' : 'stock'}</button></div>
    <form className="staff-filters inventory-filters" onSubmit={search}>
      <label htmlFor="inventory-branch">Branch<select id="inventory-branch" name="branch" value={branch} onChange={event => { setReceived(null); window.location.hash = link({ branch: event.target.value, page: '0' }) }}><option value="TARIME">Tarime (main branch)</option><option value="MWANZA">Mwanza</option></select></label>
      {!history && <><label htmlFor="inventory-search">Find materials<input key={q} id="inventory-search" name="q" type="search" defaultValue={q} maxLength={100} placeholder="Product, option or SKU" /></label><button className="button primary">Search inventory</button></>}
      <a className="text-link" href={`#/staff/${history ? 'inventory' : 'receipts'}?branch=${branch}`}>{history ? 'View inventory' : 'View receipt history'}</a>
    </form>
    {received && <div className="staff-success" role="status">Received {quantity(received.quantity)} {received.unit} of {received.productName} at {branchName(received.branch)}. On hand after receipt: {quantity(received.onHandAfter)}; reserved: {quantity(received.reservedAfter)}. <a href={`#/staff/receipts?branch=${received.branch}`}>View receipt history</a></div>}
    {resource.error ? <WorkspaceError message={resource.error} retry={resource.retry} /> : !resource.data ? <p role="status">Loading {history ? 'receipts' : 'branch stock'}…</p> : <>
      {!history && <div className="workspace-stats"><article><span>Matching product options</span><strong><AnimatedNumber value={resource.data.total} /></strong></article><article><span>Selected branch</span><strong>{branch === 'TARIME' ? 'Tarime' : 'Mwanza'}</strong></article><article className="amber-stat"><span>At or below stock threshold</span><strong><AnimatedNumber value={resource.data.lowStockCount} /></strong></article></div>}
      {!resource.data.items.length ? <section className="commerce-panel"><h2>{history ? 'No receipts at this branch yet' : 'No matching materials'}</h2><p>{history ? 'New stock receipts will appear here after materials are received.' : 'Try another product name or SKU.'}</p></section> : history ? <div className="receipt-list">{(resource.data.items as Receipt[]).map(receipt => <article className="commerce-panel" key={receipt.id}><div className="receipt-heading"><h2>{receipt.productName} · {receipt.variantLabel}</h2><strong>+{quantity(receipt.quantity)} {receipt.unit}</strong></div><p>{receipt.sku} · {branchName(receipt.branch)}</p><dl className="receipt-details"><div><dt>Source / reference</dt><dd>{receipt.sourceReference}</dd></div><div><dt>Reason</dt><dd>{receipt.reason}</dd></div><div><dt>Received by</dt><dd>{receipt.actorName}</dd></div><div><dt>Received at</dt><dd>{new Date(receipt.receivedAt).toLocaleString('en-TZ')}</dd></div><div><dt>Balance after receipt</dt><dd>{quantity(receipt.onHandAfter)} on hand · {quantity(receipt.reservedAfter)} reserved</dd></div></dl><small>Receipt {receipt.id}</small></article>)}</div> : <div className="inventory-grid">{(resource.data.items as Item[]).map(item => <article className="inventory-card" key={item.variantId}>
        {item.image && <img src={assetUrl(item.image)} alt="" loading="lazy" />}<div className="inventory-card-body"><p className="inventory-category">{item.category}</p><h2>{item.productName}</h2><p>{item.variantLabel} · {item.unit}</p><small>{item.sku}</small><dl className="inventory-quantities"><div><dt>On hand</dt><dd>{quantity(item.onHand)}</dd></div><div><dt>Reserved</dt><dd>{quantity(item.reserved)}</dd></div><div><dt>Available</dt><dd>{quantity(item.available)}</dd></div></dl>{item.lowStock && <span className="inventory-warning">At or below stock threshold</span>}<button className="button secondary" onClick={event => { trigger.current = event.currentTarget; setSelected(item); setReceived(null) }}>Receive stock<span className="sr-only"> for {item.productName} {item.variantLabel}</span></button></div>
      </article>)}</div>}
      <nav className="pagination" aria-label={history ? 'Receipt pages' : 'Inventory pages'}>{page > 0 && <a className="button secondary" href={link({ page: String(page - 1) })}>Previous</a>}<span>Page {page + 1} · {resource.data.total} {history ? 'receipts' : 'options'}</span>{(page + 1) * Number(params.get('size')) < resource.data.total && <a className="button secondary" href={link({ page: String(page + 1) })}>Next</a>}</nav>
    </>}
    {selected && <ReceiveStock key={selected.variantId + branch} item={selected} branch={branch} onClose={close} onReceived={receipt => { setReceived(receipt); close(); resource.retry() }} />}
  </>
}
