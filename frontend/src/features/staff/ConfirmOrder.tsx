import { useState } from 'react'
import type { FormEvent } from 'react'
import type { Order } from '../orders/api'
import { branchName } from '../orders/api'
import { money } from '../catalogue/format'
import { staffWrite, staffError } from './api'

export function ConfirmOrder({ order, onConfirmed }: { order: Order; onConfirmed: () => void }) {
  const [fee, setFee] = useState(order.summary.fulfilment === 'PICKUP' ? '0' : '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return
    const data = new FormData(event.currentTarget)
    setBusy(true); setError('')
    try { await staffWrite(`/api/staff/orders/${order.summary.id}/confirm`, { deliveryFee: fee, note: data.get('agreement') }); onConfirmed() }
    catch (problem) { setError(staffError(problem)) } finally { setBusy(false) }
  }
  if (order.summary.status !== 'PENDING') return <p className="staff-review-note">Status: {order.summary.status.toLowerCase()}. Agreed total: {order.summary.total === null ? 'Pending' : money(order.summary.total, order.summary.currency)}.</p>
  if (!order.summary.branch) return <p className="staff-review-note">This older request has no selected branch. Ask the customer to submit a new request choosing Tarime or Mwanza before confirmation.</p>
  return <form className="staff-confirm" onSubmit={event => void submit(event)} aria-label="Confirm order">
    <h2>Confirm with customer</h2><p className="commerce-muted">Reserve materials at {branchName(order.summary.branch)}. Physical stock is deducted later on dispatch or collection.</p>
    <label htmlFor="confirm-fee">Agreed delivery fee ({order.summary.currency})</label><input id="confirm-fee" type="number" min="0" step="0.01" required value={fee} readOnly={order.summary.fulfilment === 'PICKUP'} onChange={event => setFee(event.target.value)} />
    <p className="commerce-muted">Final total: {fee !== '' && Number.isFinite(Number(fee)) ? money(order.summary.subtotal + Number(fee), order.summary.currency) : 'Enter the agreed fee'}</p>
    <label htmlFor="confirm-agreement">Customer agreement record</label><textarea id="confirm-agreement" name="agreement" rows={3} required minLength={10} maxLength={500} placeholder="Record who agreed, when and how, including branch, materials, delivery/pickup and final total." />
    <label><input type="checkbox" required /> The customer has agreed to these details and the final total.</label>
    {error && <p className="form-error" role="alert">{error}</p>}
    <button className="button primary" disabled={busy}>{busy ? 'Confirming…' : 'Confirm and reserve stock'}</button>
  </form>
}
