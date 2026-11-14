import { useId, useState } from 'react'
import type { FormEvent } from 'react'
import { addToCart } from '../features/cart/store'
import { money } from '../features/catalogue/format'
import type { Detail } from '../features/catalogue/types'
import { useResource } from '../features/catalogue/useResource'

/**
 * Ordinary HTML buying controls for one product. Price, unit, stock and variant IDs come from the
 * catalogue API each time; nothing is read from a 3D scene. Give it a `key` of the product ID.
 */
export function BuyBox({ productId, compact = false }: { productId: string; compact?: boolean }) {
  const resource = useResource<Detail>('/api/products/' + encodeURIComponent(productId))
  const [selected, setSelected] = useState('')
  const [feedback, setFeedback] = useState('')
  const field = useId()
  const data = resource.data
  if (!data) return <div className="x3-buy" role={resource.error ? 'alert' : 'status'}>
    <p>{resource.error || 'Loading price and availability…'}</p>
    {resource.error && <button type="button" className="button secondary" onClick={resource.retry}>Try again</button>}
  </div>
  const { product, variants } = data
  const variant = variants.find(option => option.id === selected) || variants[0]
  function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!variant || !variant.inStock || variant.price === null) return
    try {
      addToCart({ productId: product.id, variantId: variant.id, name: product.name, label: variant.label, quantity: String(new FormData(event.currentTarget).get('quantity') || '1') })
      setFeedback('Added to your cart.')
    } catch (error) { setFeedback(error instanceof Error ? error.message : 'Unable to add this item.') }
  }
  return <div className={`x3-buy ${compact ? 'compact' : ''}`}>
    <p className="brand">{product.brand}</p>
    <h3><a href={`#/product/${product.id}`}>{product.name}</a></h3>
    <p className="sku">{variant?.sku || product.sku}</p>
    <p className="x3-price">{money(variant ? variant.price : product.price, variant?.currency || product.currency)} <span>per {variant?.unit || product.unit}</span></p>
    <p className={`availability ${variant?.inStock ? 'available-text' : ''}`}>{variant?.inStock ? 'In stock' : 'Out of stock'}</p>
    {variants.length > 1 && <fieldset className="variant-options"><legend>Choose an option</legend>
      {variants.map(option => <button type="button" key={option.id} className={option.id === variant?.id ? 'selected' : ''} aria-pressed={option.id === variant?.id} onClick={() => { setSelected(option.id); setFeedback('') }}>{option.label}</button>)}
    </fieldset>}
    {variant && <form className="add-cart-form" onSubmit={add}>
      <label htmlFor={field}>Quantity ({variant.unit})<input key={variant.id} id={field} name="quantity" type="number" min={variant.fractional ? '.001' : '1'} max="1000000" step={variant.fractional ? '.001' : '1'} defaultValue="1" required /></label>
      <button type="submit" className="button primary" disabled={!variant.inStock || variant.price === null}>Add to cart</button>
    </form>}
    {feedback && <p role="status" className="add-cart-feedback">{feedback}<a className="text-link" href="#/cart">View cart</a></p>}
    <a className="text-link" href={`#/product/${product.id}`}>Full product details →</a>
  </div>
}
