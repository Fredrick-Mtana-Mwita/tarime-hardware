import { useT } from '../../shared/i18n'
import { useEffect, useRef } from 'react'
import { money } from '../catalogue/format'
import { useCart, updateQuantity, removeItem } from './store'
import { useCartQuote } from './useCartQuote'
import { closeCartDrawer, useCartDrawer } from './drawer'

const step = (value: string, by: number) => String(Math.round((Number(value) + by) * 1000) / 1000)

/** Slide-in cart: quick changes without leaving the page. The full cart page stays for detailed edits. */
export function CartDrawer() { const t = useT();
  const open = useCartDrawer()
  const cart = useCart()
  const quote = useCartQuote(open ? cart.items : [])
  const panel = useRef<HTMLDivElement>(null)
  const opener = useRef<Element | null>(null)
  useEffect(() => {
    if (!open) return
    opener.current = document.activeElement
    panel.current?.focus()
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closeCartDrawer()
      if (event.key === 'Tab' && panel.current) {
        const focusable = panel.current.querySelectorAll<HTMLElement>('a[href],button:not([disabled])')
        const first = focusable[0], last = focusable[focusable.length - 1]
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
      }
    }
    const close = () => closeCartDrawer()
    document.addEventListener('keydown', onKey)
    window.addEventListener('hashchange', close)
    document.body.classList.add('drawer-open')
    return () => {
      document.removeEventListener('keydown', onKey); window.removeEventListener('hashchange', close)
      document.body.classList.remove('drawer-open')
      if (opener.current instanceof HTMLElement) opener.current.focus()
    }
  }, [open])
  if (!open) return null
  return <div className="cart-drawer-layer">
    <button className="cart-drawer-backdrop" aria-label={t("Close cart")} tabIndex={-1} onClick={closeCartDrawer} />
    <div className="cart-drawer" role="dialog" aria-modal="true" aria-labelledby="cart-drawer-title" tabIndex={-1} ref={panel}>
      <div className="cart-drawer-head"><h2 id="cart-drawer-title">{t('Your cart ({n})', { n: cart.items.length })}</h2><button className="cart-drawer-close" onClick={closeCartDrawer} aria-label={t("Close cart")}>×</button></div>
      {!cart.items.length ? <div className="cart-drawer-empty"><p>{t("Your cart is empty.")}</p><a className="button primary" href="#/shop">{t("Browse products")}</a></div> : <>
        <ul className="cart-drawer-items">{cart.items.map(item => {
          const line = quote.data?.items.find(l => l.variantId === item.variantId)
          const qty = Number(item.quantity)
          return <li key={item.variantId}>
            <div><a href={`#/product/${item.productId}`}>{line?.productName || item.name}</a><small>{line?.variantLabel || item.label}{line && <> · {money(line.unitPrice, line.currency)} / {line.unit}</>}</small></div>
            <div className="qty-stepper" role="group" aria-label={t('Quantity of {name}', { name: item.name })}>
              <button onClick={() => qty <= 1 ? removeItem(item.variantId) : updateQuantity(item.variantId, step(item.quantity, -1))} aria-label={qty <= 1 ? t('Remove {name}', { name: item.name }) : t('One less {name}', { name: item.name })}>−</button>
              <span aria-live="polite">{item.quantity}</span>
              <button onClick={() => updateQuantity(item.variantId, step(item.quantity, 1))} aria-label={t('One more {name}', { name: item.name })}>+</button>
            </div>
            <strong>{line ? money(line.lineTotal, line.currency) : ''}</strong>
          </li>
        })}</ul>
        <div className="cart-drawer-foot">
          {quote.error ? <p className="form-error" role="alert">{quote.error} <button className="text-link" onClick={quote.retry}>{t("Retry")}</button></p>
            : <p className="cart-drawer-total"><span>{t("Items subtotal")}</span><strong>{quote.data ? money(quote.data.subtotal, quote.data.currency) : t("Checking…")}</strong></p>}
          <p className="commerce-muted">{t("Delivery fee is agreed with the shop. Nothing is reserved until the shop confirms.")}</p>
          <a className="button primary" href="#/checkout">{t("Continue to checkout")}</a>
          <a className="button secondary" href="#/cart">{t("View full cart")}</a>
        </div></>}
    </div>
  </div>
}
