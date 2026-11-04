import { useState } from 'react'
import { useCart, updateQuantity, removeItem, quantity } from './store'
import type { CartItem } from './store'
import type { QuoteLine } from '../orders/api'
import { useCartQuote } from './useCartQuote'
import { money } from '../catalogue/format'

function CartRow({ item, line, onValidity }: { item: CartItem; line?: QuoteLine; onValidity: (valid: boolean) => void }) {
  const [draft, setDraft] = useState(item.quantity)
  const [error, setError] = useState('')
  function apply() {
    const value = quantity(draft)
    if (!value || line && !line.fractional && !Number.isInteger(Number(value))) {
      setError(line?.fractional ? 'Use a positive quantity with up to 3 decimals.' : 'Enter a valid positive quantity. This item may require whole units.'); onValidity(false); return
    }
    setError(''); onValidity(true); updateQuantity(item.variantId, value)
  }
  return <article className="cart-row">
    <div><a href={`#/product/${item.productId}`} className="cart-product">{line?.productName || item.name}</a><p className="commerce-muted">{line?.variantLabel || item.label}</p>
      {line && <p className="commerce-muted">{money(line.unitPrice, line.currency)} / {line.unit}</p>}</div>
    <div className="cart-quantity"><label htmlFor={'qty-' + item.variantId}>Quantity</label><input id={'qty-' + item.variantId} type="number" min={line?.fractional ? '.001' : '1'} max="1000000" step={line?.fractional ? '.001' : '1'} value={draft} onChange={event => setDraft(event.target.value)} onBlur={apply} onKeyDown={event => { if (event.key === 'Enter') event.currentTarget.blur() }} aria-invalid={!!error} aria-describedby={error ? 'qty-error-' + item.variantId : undefined} />
      {error && <small id={'qty-error-' + item.variantId} role="alert">{error}</small>}</div>
    <div className="cart-line-total"><strong>{line ? money(line.lineTotal, line.currency) : 'Refresh prices'}</strong><button className="text-link" onClick={() => removeItem(item.variantId)} aria-label={`Remove ${item.name}`}>Remove</button></div>
  </article>
}
export function EmptyCart() {
  return <div className="container commerce-page"><p className="eyebrow">Your materials</p><h1>Your cart is empty</h1><p className="commerce-intro">Find the right materials for your next project.</p><a className="button primary" href="#/shop">Browse products</a></div>
}
export function CartPage() {
  const cart = useCart()
  const quote = useCartQuote(cart.items)
  const [invalid, setInvalid] = useState<string[]>([])
  const hasInvalid = cart.items.some(item => invalid.includes(item.variantId))
  if (!cart.items.length) return <EmptyCart />
  return <div className="container commerce-page"><nav className="breadcrumbs" aria-label="Breadcrumb"><a href="#/shop">Shop</a><span>/</span><span>Your cart</span></nav><p className="eyebrow">Your next build</p><h1>Your cart</h1><p className="commerce-intro">Review your materials. Availability shown here is across branches; choose Tarime or Mwanza at checkout to check its stock.</p>
    {!cart.storageAvailable && <p className="form-error" role="alert">Browser storage is unavailable. Keep this tab open to retain your cart.</p>}
    <div className="commerce-grid"><section className="commerce-panel cart-items" aria-label="Cart items">
      {cart.items.map(item => <CartRow key={item.variantId + item.quantity} item={item} line={quote.data?.items.find(line => line.variantId === item.variantId)} onValidity={valid => setInvalid(ids => valid ? ids.filter(id => id !== item.variantId) : ids.includes(item.variantId) ? ids : [...ids, item.variantId])} />)}
      <a className="text-link" href="#/shop">Continue shopping</a></section>
      <aside className="commerce-panel commerce-summary"><h2>Order estimate</h2>
        {quote.error ? <><p className="form-error" role="alert">{quote.error}</p><button className="button secondary" onClick={quote.retry}>Refresh prices</button></> : !quote.data ? <p role="status">Checking prices and availability…</p> : <><dl><div><dt>Items subtotal</dt><dd>{money(quote.data.subtotal, quote.data.currency)}</dd></div><div><dt>Delivery</dt><dd>To be agreed</dd></div></dl><p className="commerce-muted">Pickup is available. Delivery fees are agreed with the shop after review. No online payment is taken.</p>{hasInvalid ? <button className="button primary" disabled>Fix quantities to continue</button> : <a className="button primary" href="#/checkout">Continue to checkout</a>}</>}
        <p className="commerce-muted">Items are reserved only after the shop confirms your order.</p>
      </aside></div>
  </div>
}
