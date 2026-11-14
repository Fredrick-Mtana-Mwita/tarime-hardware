import './finance.css'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { money } from '../catalogue/format'
import { useWorkspaceData } from '../staff/useWorkspaceData'
import { staffError, staffWrite } from '../staff/api'
import { isClientPreview } from '../../shared/preview'
import { dateTime, howPaid, payable, providers, statusLabels } from './api'
import type { Adjustment, DaySummary, Ledger, LedgerPage, Provider, Receipt } from './api'
import { ReceiptSheet } from './Receipt'

const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Dar_es_Salaam' }).format(new Date())
const providerOptions = Object.entries(providers) as [Provider, string][]

function useSubmit(after: () => void) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function run(action: () => Promise<unknown>) {
    if (busy) return
    setBusy(true); setError('')
    try { await action(); after() } catch (problem) { setError(staffError(problem)) } finally { setBusy(false) }
  }
  return { busy, error, run }
}

function Summary() {
  const [day, setDay] = useState(today())
  const summary = useWorkspaceData<DaySummary>('/api/staff/finance/summary?date=' + day)
  const s = summary.data
  return <section className="commerce-panel finance-summary" aria-label="Money for the day">
    <div className="finance-summary-head"><h2>Money for the day</h2><label>Day<input type="date" value={day} max={today()} onChange={event => event.target.value && setDay(event.target.value)} /></label></div>
    {summary.error ? <p role="alert">{summary.error}</p> : !s ? <p role="status">Loading…</p> : <>
      <dl className="finance-figures">
        <div><dt>Cash received</dt><dd>{money(s.cash, s.currency)}</dd></div>
        <div><dt>Mobile money received</dt><dd>{money(s.mobileMoney, s.currency)}</dd></div>
        <div><dt>Cancelled receipts</dt><dd>{money(s.reversed, s.currency)}</dd></div>
        <div><dt>Refunds paid</dt><dd>{money(s.refunded, s.currency)}</dd></div>
        <div className="finance-net"><dt>Net money in</dt><dd>{money(s.net, s.currency)}</dd></div>
        <div><dt>Completed sales</dt><dd>{money(s.completedSales, s.currency)}<small>{s.completedOrders} order{s.completedOrders === 1 ? '' : 's'} delivered or collected</small></dd></div>
      </dl>
      <p className="commerce-muted">{s.payments} receipt{s.payments === 1 ? '' : 's'} issued. Use the cash figure to check the till. Money received and completed sales are counted separately.</p>
    </>}
  </section>
}

function ApprovalCard({ item, owner, refresh }: { item: Adjustment; owner: boolean; refresh: () => void }) {
  const submit = useSubmit(refresh)
  const [mode, setMode] = useState<'approve' | 'reject' | null>(null)
  const mobileRefund = item.kind === 'REFUND' && item.refundMethod === 'MOBILE_MONEY'
  function decide(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    void submit.run(() => staffWrite(`/api/staff/finance/adjustments/${item.id}/${mode}`, { reference: data.get('reference') || null, note: data.get('note') || null }))
  }
  return <li className="finance-approval">
    <div><strong>{item.kind === 'REFUND' ? 'Refund' : 'Cancel receipt ' + item.receiptNumber} · {money(item.amount)}</strong>
      <small><a className="text-link" href={`#/staff/finance/${item.orderId}`}>{item.orderReference}</a> · asked by {item.requestedBy} · {dateTime(item.requestedAt)}</small>
      <p>{item.reason}</p>{item.kind === 'REFUND' && <small>Pay back by {howPaid(item.refundMethod, item.refundProvider)}</small>}</div>
    {owner && !mode && <div className="finance-approval-actions"><button className="button primary" onClick={() => setMode('approve')}>Approve…</button><button className="button secondary" onClick={() => setMode('reject')}>Reject…</button></div>}
    {owner && mode && <form className="staff-form" onSubmit={decide}>
      {mode === 'approve' && mobileRefund && <label>Transaction ID of the refund you sent<input name="reference" required minLength={6} maxLength={30} pattern="[A-Za-z0-9]{6,30}" autoComplete="off" /></label>}
      {mode === 'approve' && item.kind === 'REFUND' && !mobileRefund && <p className="commerce-muted">Approve only after the cash is handed back.</p>}
      <label>{mode === 'reject' ? 'Why are you rejecting it?' : 'Note (optional)'}<input name="note" maxLength={300} required={mode === 'reject'} minLength={mode === 'reject' ? 5 : undefined} /></label>
      {submit.error && <p className="form-error" role="alert">{submit.error}</p>}
      <div className="finance-approval-actions"><button className="button primary" disabled={submit.busy || isClientPreview}>{submit.busy ? 'Saving…' : mode === 'approve' ? 'Approve' : 'Reject'}</button><button type="button" className="text-link" onClick={() => setMode(null)}>Back</button></div>
    </form>}
  </li>
}

function Approvals({ owner }: { owner: boolean }) {
  const list = useWorkspaceData<Adjustment[]>('/api/staff/finance/adjustments')
  if (list.error) return <section className="commerce-panel"><h2>Waiting for the Owner</h2><p role="alert">{list.error}</p></section>
  if (!list.data || !list.data.length) return null
  return <section className="commerce-panel finance-approvals"><h2>Waiting for the Owner ({list.data.length})</h2>
    {!owner && <p className="commerce-muted">Only the Owner can approve corrections and refunds. Balances change once approved.</p>}
    <ul>{list.data.map(item => <ApprovalCard key={item.id + item.status} item={item} owner={owner} refresh={list.retry} />)}</ul></section>
}

export function FinancePage({ role, query }: { role: string; query: URLSearchParams }) {
  const filter = query.get('filter') === 'ALL' ? 'ALL' : 'OUTSTANDING'
  const q = query.get('q') || ''
  const page = Math.max(0, Number(query.get('page')) || 0)
  const params = new URLSearchParams({ filter, q, page: String(page), size: '10' })
  const list = useWorkspaceData<LedgerPage>('/api/staff/finance/orders?' + params)
  const link = (changes: Record<string, string>) => '#/staff/finance?' + new URLSearchParams({ filter, q, page: '0', ...changes })
  function search(event: FormEvent<HTMLFormElement>) { event.preventDefault(); window.location.hash = link({ q: String(new FormData(event.currentTarget).get('q') || '') }) }
  return <>
    <div className="staff-page-heading"><div><p className="eyebrow">Finance</p><h1>Payments</h1><p className="commerce-intro">Record cash and mobile money, print receipts, and ask the Owner for corrections or refunds.</p></div></div>
    <Summary />
    <Approvals owner={role === 'OWNER'} />
    <form className="staff-filters" onSubmit={search} role="search"><label>Search orders<input name="q" defaultValue={q} placeholder="Order reference or customer name" maxLength={100} /></label>
      <label>Show<select value={filter} onChange={event => { window.location.hash = link({ filter: event.target.value }) }}><option value="OUTSTANDING">Money still owed (either way)</option><option value="ALL">All confirmed orders</option></select></label>
      <button className="button secondary">Search</button></form>
    {list.error ? <p role="alert" className="form-error">{list.error}</p> : !list.data ? <p role="status">Loading orders…</p> : !list.data.items.length
      ? <div className="commerce-panel"><h2>{filter === 'OUTSTANDING' ? 'Nothing outstanding' : 'No matching orders'}</h2><p className="commerce-muted">{filter === 'OUTSTANDING' ? 'Every confirmed order is settled.' : 'Try another search.'}</p></div>
      : <><p className="staff-result-count">{list.data.total} order{list.data.total === 1 ? '' : 's'}</p>
        <ul className="finance-orders">{list.data.items.map(row => <li key={row.orderId} className="commerce-panel"><a href={`#/staff/finance/${row.orderId}`}><span><strong>{row.reference}</strong><small>{row.customerName} · {row.orderStatus.toLowerCase().replaceAll('_', ' ')}</small></span>
          <span className={'order-status finance-status-' + row.balance.status.toLowerCase()}>{statusLabels[row.balance.status]}</span>
          <span className="finance-row-money">{row.balance.refundDue > 0 ? <>Refund {money(row.balance.refundDue, row.balance.currency)}</> : row.balance.due > 0 ? <>Due {money(row.balance.due, row.balance.currency)}</> : money(row.balance.netPaid, row.balance.currency)}<small>of {money(row.balance.total, row.balance.currency)}</small></span></a></li>)}</ul>
        <nav className="pagination" aria-label="Order pages">{page > 0 && <a className="button secondary" href={link({ page: String(page - 1) })}>Previous</a>}<span>Page {page + 1}</span>{(page + 1) * 10 < list.data.total && <a className="button secondary" href={link({ page: String(page + 1) })}>Next</a>}</nav></>}
  </>
}

function PaymentForm({ ledger, refresh }: { ledger: Ledger; refresh: () => void }) {
  const [id, setId] = useState(() => crypto.randomUUID())
  const [method, setMethod] = useState<'CASH' | 'MOBILE_MONEY'>('CASH')
  const submit = useSubmit(() => { setId(crypto.randomUUID()); refresh() })
  const due = ledger.balance.due
  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const body = { id, method, amount: data.get('amount'), note: data.get('note') || null, ...(method === 'MOBILE_MONEY' ? { provider: data.get('provider'), reference: data.get('reference') } : {}) }
    void submit.run(() => staffWrite(`/api/staff/finance/orders/${ledger.orderId}/payments`, body))
  }
  return <form className="staff-form finance-form" onSubmit={save} aria-label="Record a payment">
    <h2>Record a payment</h2>
    <fieldset className="finance-methods"><legend>How did the customer pay?</legend>
      <label><input type="radio" name="method" checked={method === 'CASH'} onChange={() => setMethod('CASH')} /> Cash</label>
      <label><input type="radio" name="method" checked={method === 'MOBILE_MONEY'} onChange={() => setMethod('MOBILE_MONEY')} /> Mobile money</label></fieldset>
    {method === 'MOBILE_MONEY' && <><label>Service<select name="provider" required defaultValue=""><option value="" disabled>Choose the service</option>{providerOptions.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label>Transaction ID<input name="reference" required minLength={6} maxLength={30} pattern="[A-Za-z0-9]{6,30}" autoComplete="off" placeholder="From the customer's confirmation SMS" /></label></>}
    <label>Amount ({ledger.balance.currency})<input name="amount" type="number" required min="0.01" max={due} step="0.01" inputMode="decimal" /><small>Still owed: {money(due, ledger.balance.currency)}. If the customer gives more, hand back the change.</small></label>
    <label>Note (optional, staff only)<input name="note" maxLength={300} /></label>
    {submit.error && <p className="form-error" role="alert">{submit.error}</p>}
    <button className="button primary" disabled={submit.busy || isClientPreview}>{submit.busy ? 'Saving…' : 'Save and issue receipt'}</button>
  </form>
}

function ReversalForm({ ledger, receipt, refresh, close }: { ledger: Ledger; receipt: Receipt; refresh: () => void; close: () => void }) {
  const [id] = useState(() => crypto.randomUUID())
  const submit = useSubmit(() => { close(); refresh() })
  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void submit.run(() => staffWrite(`/api/staff/finance/orders/${ledger.orderId}/adjustments`, { id, kind: 'REVERSAL', paymentId: receipt.id, reason: new FormData(event.currentTarget).get('reason') }))
  }
  return <form className="staff-form finance-inline-form" onSubmit={save}>
    <p className="commerce-muted">Use this when receipt {receipt.receiptNumber} was saved by mistake (wrong order, wrong amount, money never received). The Owner must approve. Then record the correct payment.</p>
    <label>What went wrong?<textarea name="reason" required minLength={10} maxLength={500} rows={2} /></label>
    {submit.error && <p className="form-error" role="alert">{submit.error}</p>}
    <div className="finance-approval-actions"><button className="button primary" disabled={submit.busy || isClientPreview}>Send to the Owner</button><button type="button" className="text-link" onClick={close}>Back</button></div>
  </form>
}

function RefundForm({ ledger, refresh }: { ledger: Ledger; refresh: () => void }) {
  const [open, setOpen] = useState(false)
  const [id, setId] = useState(() => crypto.randomUUID())
  const [method, setMethod] = useState<'CASH' | 'MOBILE_MONEY'>('CASH')
  const submit = useSubmit(() => { setId(crypto.randomUUID()); setOpen(false); refresh() })
  const suggested = ledger.balance.refundDue > 0 ? ledger.balance.refundDue : undefined
  if (!open) return <button className="button secondary" onClick={() => setOpen(true)}>Ask for a refund…</button>
  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    void submit.run(() => staffWrite(`/api/staff/finance/orders/${ledger.orderId}/adjustments`, { id, kind: 'REFUND', amount: data.get('amount'), reason: data.get('reason'), refundMethod: method, refundProvider: method === 'MOBILE_MONEY' ? data.get('provider') : null }))
  }
  return <form className="staff-form finance-form" onSubmit={save} aria-label="Ask for a refund">
    <h2>Ask for a refund</h2><p className="commerce-muted">The Owner approves refunds and records the payout. Nothing changes until then.</p>
    <label>Amount ({ledger.balance.currency})<input name="amount" type="number" required min="0.01" max={ledger.balance.netPaid} step="0.01" defaultValue={suggested} /></label>
    <fieldset className="finance-methods"><legend>Pay back by</legend>
      <label><input type="radio" checked={method === 'CASH'} onChange={() => setMethod('CASH')} /> Cash</label>
      <label><input type="radio" checked={method === 'MOBILE_MONEY'} onChange={() => setMethod('MOBILE_MONEY')} /> Mobile money</label></fieldset>
    {method === 'MOBILE_MONEY' && <label>Service<select name="provider" required defaultValue=""><option value="" disabled>Choose the service</option>{providerOptions.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>}
    <label>Reason<textarea name="reason" required minLength={10} maxLength={500} rows={2} placeholder="For example: order cancelled after a deposit." /></label>
    {submit.error && <p className="form-error" role="alert">{submit.error}</p>}
    <div className="finance-approval-actions"><button className="button primary" disabled={submit.busy || isClientPreview}>Send to the Owner</button><button type="button" className="text-link" onClick={() => setOpen(false)}>Back</button></div>
  </form>
}

export function FinanceOrder({ id, role }: { id: string; role: string }) {
  const ledger = useWorkspaceData<Ledger>('/api/staff/finance/orders/' + encodeURIComponent(id))
  const [reversing, setReversing] = useState<string | null>(null)
  const l = ledger.data
  if (ledger.error) return <><a className="text-link" href="#/staff/finance">Back to payments</a><p role="alert" className="form-error">{ledger.error}</p></>
  if (!l) return <p role="status">Loading the order's payments…</p>
  const b = l.balance
  const activeReversal = (receipt: Receipt) => l.adjustments.some(a => a.paymentId === receipt.id && a.status !== 'REJECTED')
  return <>
    <a className="text-link" href="#/staff/finance">Back to payments</a>
    <div className="staff-page-heading"><div><p className="eyebrow">Payments · {l.orderStatus.toLowerCase().replaceAll('_', ' ')}</p><h1>{l.reference}</h1><p className="commerce-intro">{l.customerName} · {l.customerPhone}</p></div>
      {role !== 'DELIVERY' && <a className="button secondary" href={`#/staff/orders/${l.orderId}`}>Open the order</a>}</div>
    <div className="staff-work-grid">
      <section className="commerce-panel"><h2>Receipts</h2>
        {!l.payments.length ? <p className="commerce-muted">No payments recorded yet.</p> : <ul className="receipt-list">{l.payments.map(r => <li key={r.id} className={r.reversed ? 'receipt-reversed' : ''}>
          <span><strong>{money(r.amount, r.currency)}</strong> · {howPaid(r.method, r.provider)}{r.reference && ' · ' + r.reference}{r.reversed && ' · cancelled'}
            <small>{r.receiptNumber} · {dateTime(r.recordedAt)} · by {r.recordedBy}{r.note && ' · ' + r.note}</small></span>
          <span className="receipt-links"><a className="text-link" href={`#/staff/finance/${l.orderId}/receipts/${r.id}`}>Print</a>
            {!r.reversed && !activeReversal(r) && reversing !== r.id && <button className="text-link" onClick={() => setReversing(r.id)}>Saved by mistake?</button>}</span>
          {reversing === r.id && <ReversalForm ledger={l} receipt={r} refresh={ledger.retry} close={() => setReversing(null)} />}
        </li>)}</ul>}
        {l.adjustments.length > 0 && <><h2>Corrections and refunds</h2><ul className="finance-history">{l.adjustments.map(a => <li key={a.id}>
          <strong>{a.kind === 'REFUND' ? 'Refund' : 'Cancel ' + a.receiptNumber} · {money(a.amount, b.currency)} · {a.status.toLowerCase()}</strong>
          <small>{a.reason} — asked by {a.requestedBy}, {dateTime(a.requestedAt)}{a.decidedBy && <>; {a.status.toLowerCase()} by {a.decidedBy}, {dateTime(a.decidedAt!)}</>}{a.refundReference && ' · transaction ' + a.refundReference}{a.decisionNote && ' · ' + a.decisionNote}</small></li>)}</ul></>}
      </section>
      <aside className="commerce-panel commerce-summary">
        <h2>Balance</h2><p><span className={'order-status finance-status-' + b.status.toLowerCase()}>{statusLabels[b.status]}</span></p>
        <dl><div><dt>Order total</dt><dd>{money(b.total, b.currency)}</dd></div><div><dt>Received</dt><dd>{money(b.paid, b.currency)}</dd></div>
          {b.reversed > 0 && <div><dt>Cancelled receipts</dt><dd>−{money(b.reversed, b.currency)}</dd></div>}{b.refunded > 0 && <div><dt>Refunded</dt><dd>−{money(b.refunded, b.currency)}</dd></div>}
          <div><dt>Still owed</dt><dd>{money(b.due, b.currency)}</dd></div>{b.refundDue > 0 && <div><dt>Shop owes back</dt><dd>{money(b.refundDue, b.currency)}</dd></div>}</dl>
        {payable.includes(l.orderStatus) && b.due > 0 ? <PaymentForm key={b.netPaid} ledger={l} refresh={ledger.retry} />
          : l.orderStatus === 'PENDING' ? <p className="staff-review-note">Confirm the order first. Payments are recorded once the total is agreed.</p> : null}
        {b.netPaid > 0 && <RefundForm ledger={l} refresh={ledger.retry} />}
      </aside>
    </div>
  </>
}

export function StaffReceipt({ orderId, paymentId }: { orderId: string; paymentId: string }) {
  const ledger = useWorkspaceData<Ledger>('/api/staff/finance/orders/' + encodeURIComponent(orderId))
  const receipt = ledger.data?.payments.find(p => p.id === paymentId)
  if (ledger.error) return <p role="alert" className="form-error">{ledger.error}</p>
  if (!ledger.data) return <p role="status">Loading receipt…</p>
  if (!receipt) return <p role="alert" className="form-error">This receipt is not on the order.</p>
  return <ReceiptSheet receipt={receipt} orderReference={ledger.data.reference} customerName={ledger.data.customerName} balance={ledger.data.balance} back={`#/staff/finance/${orderId}`} />
}
