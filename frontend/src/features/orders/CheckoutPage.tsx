import { useT } from '../../shared/i18n'
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
import { AddressFields } from './AddressFields'
import type { Location } from './AddressFields'
import { composeAddress } from '../../shared/contact'

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

function CheckoutForm({ items, quote, branch }: { items: CartItem[]; quote: Quote; branch: Branch }) { const t = useT();
  const { user, refresh } = useAuth()
  const [fulfilment, setFulfilment] = useState<'DELIVERY' | 'PICKUP'>('DELIVERY')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState<{ body: string; key: string } | null>(null)
  const [receipt, setReceipt] = useState<Order | null>(null)
  const [location, setLocation] = useState<Location>(null)
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy || !user) return
    const data = new FormData(event.currentTarget)
    const body = { branch, items: quote.items.map(line => ({ variantId: line.variantId, quantity: line.quantity, expectedPrice: line.unitPrice, currency: line.currency })), fulfilment,
      name: String(data.get('name') || '').trim(), phone: String(data.get('phone') || '').trim(), address: fulfilment === 'DELIVERY' ? composeAddress({ area: String(data.get('area') || ''), details: String(data.get('address') || ''), landmark: String(data.get('landmark') || ''), location }) : '', instructions: String(data.get('instructions') || '').trim() }
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
  if (receipt) return <div role="status">{t('Order request received.')} <a className="text-link" href={'#/orders/' + receipt.summary.id}>{t("View your order")}</a></div>
  return <div className="commerce-grid"><section className="commerce-panel"><h2>{t('Contact & fulfilment')}</h2>
    <form className="checkout-form" onSubmit={event => void submit(event)} aria-label={t("Checkout")}>
      <fieldset disabled={busy} className="checkout-fields"><legend className="sr-only">{t("Order details")}</legend>
        <div className="checkout-pair">
          <div className="checkout-field"><label htmlFor="order-name">{t("Contact name")}</label><input id="order-name" name="name" autoComplete="name" defaultValue={user?.name} required minLength={2} maxLength={100} /></div>
          <div className="checkout-field"><label htmlFor="order-phone">{t("Contact phone")}</label><input id="order-phone" name="phone" autoComplete="tel" type="tel" defaultValue={user?.phone} required maxLength={40} /></div>
        </div>
        <fieldset className="fulfilment-options"><legend>{t("How would you like your materials?")}</legend><label><input type="radio" name="fulfilment" value="DELIVERY" checked={fulfilment === 'DELIVERY'} onChange={() => setFulfilment('DELIVERY')} /> {t('Request delivery')}</label><label><input type="radio" name="fulfilment" value="PICKUP" checked={fulfilment === 'PICKUP'} onChange={() => setFulfilment('PICKUP')} /> {t('Shop pickup')}</label></fieldset>
        {fulfilment === 'DELIVERY' ? <AddressFields location={location} onLocation={setLocation} /> : <p className="commerce-muted">{t("The shop will confirm collection arrangements. Wait for confirmation before travelling.")}</p>}
        <label htmlFor="order-instructions">{t('Instructions')} <span>{t("(optional)")}</span></label><textarea id="order-instructions" name="instructions" maxLength={500} rows={2} />
        <label className="checkout-consent"><input type="checkbox" required /> {t('I understand this is a request for shop review. Stock and final arrangements require confirmation.')}</label>
      </fieldset>
      {error && <div className="form-error" role="alert"><p>{t(error)}</p><a href="#/cart">{t("Review cart and refresh prices")}</a><button type="button" className="text-link" onClick={refresh}>{t("Refresh sign-in status")}</button></div>}
      <button className="button primary" type="submit" disabled={busy}>{busy ? t("Submitting request…") : t("Submit order request")}</button>
    </form>
  </section><aside className="commerce-panel commerce-summary"><h2>{t("Your request")}</h2><ul className="checkout-lines">{quote.items.map(line => <li key={line.variantId}><span>{line.productName} · {line.variantLabel}<small>{line.quantity} {line.unit} × {money(line.unitPrice, line.currency)}</small></span><strong>{money(line.lineTotal, line.currency)}</strong></li>)}</ul><dl><div><dt>{t("Items subtotal")}</dt><dd>{money(quote.subtotal, quote.currency)}</dd></div><div><dt>{fulfilment === 'PICKUP' ? t("Pickup fee") : t("Delivery fee")}</dt><dd>{fulfilment === 'PICKUP' ? money(0, quote.currency) : t("To be agreed")}</dd></div></dl><p className="commerce-muted">{t("No payment is taken here. Prices are rechecked when you submit. No tax or discount adjustments have been added.")}</p><a href="#/cart" className="text-link">{t("Edit cart")}</a></aside></div>
}
export function CheckoutPage() { const t = useT();
  const cart = useCart()
  const [branch, setBranch] = useState<Branch | ''>(() => { try { const saved = localStorage.getItem('tarime.checkout.branch'); return saved === 'TARIME' ? saved : 'TARIME' } catch { return '' } })
  const quote = useCartQuote(cart.items, branch || undefined)
  const { user, loading, unavailable, refresh } = useAuth()
  if (!cart.items.length) return <EmptyCart />
  return <div className="container commerce-page checkout-page"><nav className="breadcrumbs" aria-label={t("Breadcrumb")}><a href="#/cart">{t("Your cart")}</a><span>/</span><span>{t("Checkout")}</span></nav><p className="eyebrow">{t("Almost ready")}</p><h1>{t("Request your order")}</h1><p className="commerce-intro">{t("Send your materials list to the shop for review and confirmation.")}</p>
    <div className="branch-choice"><label htmlFor="checkout-branch">{t("Choose your branch")}</label><select id="checkout-branch" value={branch} onChange={event => { const value = event.target.value as Branch | ''; setBranch(value); try { localStorage.setItem('tarime.checkout.branch', value) } catch { /* Selection still works for this visit. */ } }}><option value="">{t("Select Tarime")}</option><option value="TARIME">{t("Tarime (main branch)")}</option></select><p className="commerce-muted">{t("Delivery and pickup use stock from your selected branch.")}</p></div>
    {loading ? <p role="status">{t("Checking your account…")}</p> : unavailable ? <div className="commerce-panel"><p role="alert">{t("We could not check your account.")}</p><button className="button secondary" onClick={refresh}>{t("Try again")}</button></div> : !user ? <div className="commerce-panel checkout-signin"><h2>{t("Sign in to place your request")}</h2><p>{t("Your cart will be here when you return.")}</p><a className="button primary" href="#/login?next=checkout">{t("Sign in to checkout")}</a><a className="text-link" href="#/register?next=checkout">{t("Create an account")}</a></div>
      : !branch ? <p className="commerce-panel">{t("Choose a branch to check its stock and continue.")}</p> : quote.error ? <div className="commerce-panel"><p className="form-error" role="alert">{t(quote.error)}</p><a className="button secondary" href="#/cart">{t("Review cart")}</a><button className="button secondary" onClick={quote.retry}>{t("Refresh prices")}</button></div> : !quote.data ? <p role="status">{t("Checking prices and stock…")}</p> : <CheckoutForm key={user.id + branch} items={cart.items} quote={quote.data} branch={branch} />}
  </div>
}
