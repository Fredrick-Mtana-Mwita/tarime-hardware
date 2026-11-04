import { useState } from 'react'
import type { FormEvent } from 'react'
import { useAuth } from '../identity/useAuth'
import { useCart, clearPurchased } from '../cart/store'
import type { CartItem } from '../cart/store'
import { useCartQuote } from '../cart/useCartQuote'
import { EmptyCart } from '../cart/CartPage'
import { money } from '../catalogue/format'
import { commercePost, orderError } from './api'
import type { Order, Quote, Branch } from './api'

async function retryKey(body: unknown) {
  const bytes = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(JSON.stringify(body)))
  const fingerprint = Array.from(new Uint8Array(bytes), b => b.toString(16).padStart(2, '0')).join('')
  const name = 'finishers.order-attempt.v1'
  try {
    const stored = JSON.parse(sessionStorage.getItem(name) || 'null') as { fingerprint: string; key: string } | null
    if (stored?.fingerprint === fingerprint && /^[0-9a-f-]{36}$/.test(stored.key)) return stored.key
  } catch { /* In-memory retry protection remains available when storage is blocked. */ }
  const key = crypto.randomUUID()
  try { sessionStorage.setItem(name, JSON.stringify({ fingerprint, key })) } catch { /* No contact details are stored here. */ }
  return key
}

function CheckoutForm({ items, quote, branch }: { items: CartItem[]; quote: Quote; branch: Branch }) {
  const { user, refresh } = useAuth()
  const [fulfilment, setFulfilment] = useState<'DELIVERY' | 'PICKUP'>('DELIVERY')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState<{ body: string; key: string } | null>(null)
  const [receipt, setReceipt] = useState<Order | null>(null)
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy || !user) return
    const data = new FormData(event.currentTarget)
    const body = { branch, items: quote.items.map(line => ({ variantId: line.variantId, quantity: line.quantity, expectedPrice: line.unitPrice, currency: line.currency })), fulfilment,
      name: String(data.get('name') || '').trim(), phone: String(data.get('phone') || '').trim(), address: fulfilment === 'DELIVERY' ? String(data.get('address') || '').trim() : '', instructions: String(data.get('instructions') || '').trim() }
    setBusy(true); setError('')
    try {
      const serialized = JSON.stringify(body)
      const key = attempt?.body === serialized ? attempt.key : await retryKey(body)
      setAttempt({ body: serialized, key })
      const order = await commercePost<Order>('/api/orders', body, { 'Idempotency-Key': key })
      setReceipt(order)
      window.location.hash = '#/orders/' + order.summary.id
      clearPurchased(items)
    } catch (problem) { setError(orderError(problem)) } finally { setBusy(false) }
  }
  if (receipt) return <div role="status">Order request received. <a className="text-link" href={'#/orders/' + receipt.summary.id}>View your order</a></div>
  return <div className="commerce-grid"><section className="commerce-panel"><h2>Contact &amp; fulfilment</h2>
    <form className="checkout-form" onSubmit={event => void submit(event)} aria-label="Checkout">
      <fieldset disabled={busy} className="checkout-fields"><legend className="sr-only">Order details</legend>
        <label htmlFor="order-name">Contact name</label><input id="order-name" name="name" autoComplete="name" defaultValue={user?.name} required minLength={2} maxLength={100} />
        <label htmlFor="order-phone">Contact phone</label><input id="order-phone" name="phone" autoComplete="tel" type="tel" defaultValue={user?.phone} required maxLength={40} />
        <fieldset className="fulfilment-options"><legend>How would you like your materials?</legend><label><input type="radio" name="fulfilment" value="DELIVERY" checked={fulfilment === 'DELIVERY'} onChange={() => setFulfilment('DELIVERY')} /> Request delivery</label><label><input type="radio" name="fulfilment" value="PICKUP" checked={fulfilment === 'PICKUP'} onChange={() => setFulfilment('PICKUP')} /> Shop pickup</label></fieldset>
        {fulfilment === 'DELIVERY' ? <><label htmlFor="order-address">Delivery address</label><textarea id="order-address" name="address" autoComplete="street-address" required minLength={5} maxLength={500} rows={3} /><p className="commerce-muted">The shop will confirm delivery coverage and agree the fee with you before confirmation.</p></> : <p className="commerce-muted">The shop will confirm collection arrangements. Wait for confirmation before travelling.</p>}
        <label htmlFor="order-instructions">Instructions <span>(optional)</span></label><textarea id="order-instructions" name="instructions" maxLength={500} rows={2} />
        <label className="checkout-consent"><input type="checkbox" required /> I understand this is a request for shop review. Stock and final arrangements require confirmation.</label>
      </fieldset>
      {error && <div className="form-error" role="alert"><p>{error}</p><a href="#/cart">Review cart and refresh prices</a><button type="button" className="text-link" onClick={refresh}>Refresh sign-in status</button></div>}
      <button className="button primary" type="submit" disabled={busy}>{busy ? 'Submitting request…' : 'Submit order request'}</button>
    </form>
  </section><aside className="commerce-panel commerce-summary"><h2>Your request</h2><ul className="checkout-lines">{quote.items.map(line => <li key={line.variantId}><span>{line.productName} · {line.variantLabel}<small>{line.quantity} {line.unit} × {money(line.unitPrice, line.currency)}</small></span><strong>{money(line.lineTotal, line.currency)}</strong></li>)}</ul><dl><div><dt>Items subtotal</dt><dd>{money(quote.subtotal, quote.currency)}</dd></div><div><dt>{fulfilment === 'PICKUP' ? 'Pickup fee' : 'Delivery fee'}</dt><dd>{fulfilment === 'PICKUP' ? money(0, quote.currency) : 'To be agreed'}</dd></div></dl><p className="commerce-muted">No payment is taken here. Prices are rechecked when you submit. No tax or discount adjustments have been added.</p><a href="#/cart" className="text-link">Edit cart</a></aside></div>
}
export function CheckoutPage() {
  const cart = useCart()
  const [branch, setBranch] = useState<Branch | ''>(() => { try { const saved = localStorage.getItem('tarime.checkout.branch'); return saved === 'TARIME' || saved === 'MWANZA' ? saved : '' } catch { return '' } })
  const quote = useCartQuote(cart.items, branch || undefined)
  const { user, loading, unavailable, refresh } = useAuth()
  if (!cart.items.length) return <EmptyCart />
  return <div className="container commerce-page"><nav className="breadcrumbs" aria-label="Breadcrumb"><a href="#/cart">Your cart</a><span>/</span><span>Checkout</span></nav><p className="eyebrow">Almost ready</p><h1>Request your order</h1><p className="commerce-intro">Send your materials list to the shop for review and confirmation.</p>
    <div className="branch-choice"><label htmlFor="checkout-branch">Choose your branch</label><select id="checkout-branch" value={branch} onChange={event => { const value = event.target.value as Branch | ''; setBranch(value); try { localStorage.setItem('tarime.checkout.branch', value) } catch { /* Selection still works for this visit. */ } }}><option value="">Select Tarime or Mwanza</option><option value="TARIME">Tarime (main branch)</option><option value="MWANZA">Mwanza</option></select><p className="commerce-muted">Delivery and pickup use stock from your selected branch.</p></div>
    {loading ? <p role="status">Checking your account…</p> : unavailable ? <div className="commerce-panel"><p role="alert">We could not check your account.</p><button className="button secondary" onClick={refresh}>Try again</button></div> : !user ? <div className="commerce-panel checkout-signin"><h2>Sign in to place your request</h2><p>Your cart will be here when you return.</p><a className="button primary" href="#/login?next=checkout">Sign in to checkout</a><a className="text-link" href="#/register?next=checkout">Create an account</a></div>
      : !branch ? <p className="commerce-panel">Choose a branch to check its stock and continue.</p> : quote.error ? <div className="commerce-panel"><p className="form-error" role="alert">{quote.error}</p><a className="button secondary" href="#/cart">Review cart</a><button className="button secondary" onClick={quote.retry}>Refresh prices</button></div> : !quote.data ? <p role="status">Checking prices and stock…</p> : <CheckoutForm key={user.id + branch} items={cart.items} quote={quote.data} branch={branch} />}
  </div>
}
