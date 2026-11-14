import { useState } from 'react'
import type { FormEvent } from 'react'
import type { Order } from '../orders/api'
import { staffWrite, staffError } from './api'

/** Orders whose goods have not left the shop. The server applies the same rule. */
const cancellable = ['PENDING', 'CONFIRMED', 'PREPARING', 'READY_FOR_PICKUP', 'READY_FOR_DELIVERY']

export function OrderDecision({ order, onDecided }: { order: Order; onDecided: () => void }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [open, setOpen] = useState(false)
  const status = order.summary.status
  if (order.decision) return <div className="staff-review-note staff-decision"><strong>{order.decision.decision === 'REJECTED' ? 'Request rejected' : 'Order cancelled'}</strong><p>Reason shown to the customer: {order.decision.reason}</p></div>
  if (!cancellable.includes(status)) return null
  const reject = status === 'PENDING'
  const action = reject ? 'reject' : 'cancel'
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return
    const data = new FormData(event.currentTarget)
    setBusy(true); setError('')
    try { await staffWrite(`/api/staff/orders/${order.summary.id}/${action}`, { reason: data.get('reason') }); onDecided() }
    catch (problem) { setError(staffError(problem)) } finally { setBusy(false) }
  }
  if (!open) return <button className="button secondary staff-decision-open" onClick={() => setOpen(true)}>{reject ? 'Reject this request…' : 'Cancel this order…'}</button>
  return <form className="staff-confirm staff-decision" onSubmit={event => void submit(event)} aria-label={reject ? 'Reject request' : 'Cancel order'}>
    <h2>{reject ? 'Reject request' : 'Cancel order'}</h2>
    <p className="commerce-muted">{reject ? 'The request closes and nothing is reserved.' : 'The order closes and any stock held for it goes back on sale straight away.'} This cannot be undone.</p>
    <label htmlFor="decision-reason">Reason for the customer</label>
    <textarea id="decision-reason" name="reason" rows={3} required minLength={10} maxLength={500} placeholder={reject ? 'For example: These tiles are discontinued. We can suggest similar ones by phone.' : 'For example: The customer asked by phone on 12 November to cancel.'} />
    <label><input type="checkbox" required /> I understand the customer will see this reason.</label>
    {error && <p className="form-error" role="alert">{error}</p>}
    <button className="button primary" disabled={busy}>{busy ? 'Saving…' : reject ? 'Reject request' : 'Cancel order'}</button>
    <button type="button" className="text-link" onClick={() => setOpen(false)}>Keep the order</button>
  </form>
}
