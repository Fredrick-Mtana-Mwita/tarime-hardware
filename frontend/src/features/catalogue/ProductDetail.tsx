import { assetUrl } from '../../shared/assetUrl'
import { useState } from 'react'
import type { Detail } from './types'
import { useResource } from './useResource'
import { money } from './format'
import { LoadState, SampleNotice } from './LoadState'
import { addToCart } from '../cart/store'
import type { FormEvent } from 'react'

export function ProductDetail({ id }: { id: string }) {
  const resource = useResource<Detail>('/api/products/' + encodeURIComponent(id))
  const [selected, setSelected] = useState(''); const [photo, setPhoto] = useState('')
  const [feedback, setFeedback] = useState('')
  const data = resource.data
  if (!data) return <div className="container"><LoadState error={resource.error} retry={resource.retry} />{resource.error && <a href="#/shop">Back to catalogue</a>}</div>
  const { product, variants, images, specifications } = data
  const variant = variants.find(v => v.id === selected) || variants[0]
  const image = images.find(i => i.path === photo) || images[0]
  function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!variant || !variant.inStock || variant.price === null) return
    try {
      addToCart({ productId: product.id, variantId: variant.id, name: product.name, label: variant.label, quantity: String(new FormData(event.currentTarget).get('quantity') || '1') })
      setFeedback('Added to your cart.')
    } catch (error) { setFeedback(error instanceof Error ? error.message : 'Unable to add this item.') }
  }
  return <div className="container detail-page"><nav className="breadcrumbs" aria-label="Breadcrumb"><a href="#/">Home</a><span>/</span><a href={`#/shop?category=${product.categorySlug}`}>{product.categoryName}</a><span>/</span><span>{product.name}</span></nav>{product.sampleData && <SampleNotice />}
    <div className="product-detail"><section className="gallery" aria-label="Product photos"><img className="main-product-image" src={assetUrl(image?.path || '/images/store-shelves.jpg')} alt={image?.alt || product.name} />{images.length > 1 && <div className="thumbnails">{images.map(i => <button key={i.path} onClick={() => setPhoto(i.path)} aria-label={`View ${i.alt}`} aria-pressed={image?.path === i.path}><img src={assetUrl(i.path)} alt="" /></button>)}</div>}</section>
      <section className="product-info"><p className="eyebrow">{product.categoryName}</p><p className="brand">{product.brand}</p><h1>{product.name}</h1><p className="sku">{variant?.sku || product.sku}</p><p className="detail-price">{money(variant ? variant.price : product.price, variant?.currency || product.currency)}</p><p className="unit">per {variant?.unit || product.unit}</p><p className={`availability ${variant?.inStock ? 'available-text' : ''}`}>{variant?.inStock ? 'In stock' : 'Out of stock'} · Across branches; choose your branch at checkout</p>
      {variants.length > 1 && <fieldset className="variant-options"><legend>Choose an option</legend>{variants.map(v => <button className={v.id === variant?.id ? 'selected' : ''} key={v.id} onClick={() => setSelected(v.id)} aria-pressed={v.id === variant?.id}>{v.label}</button>)}</fieldset>}
      <p className="description">{product.description}</p>{variant && <form className="add-cart-form" onSubmit={add}><label htmlFor="product-quantity">Quantity ({variant.unit})<input key={variant.id} id="product-quantity" name="quantity" type="number" min={variant.fractional ? '.001' : '1'} max="1000000" step={variant.fractional ? '.001' : '1'} defaultValue="1" required /></label><button type="submit" className="button primary" disabled={!variant.inStock || variant.price === null}>Add to cart</button></form>}
      {feedback && <p role="status" className="add-cart-feedback">{feedback}<a className="text-link" href="#/cart">View cart</a></p>}<div className="ordering-note">Submit a request for shop review. Stock is reserved after confirmation. Items marked “Ask for a quote” require contacting the shop.</div><a className="button secondary" href={`#/shop?category=${product.categorySlug}`}>More in {product.categoryName} →</a></section></div>
    <section className="specifications"><h2>Specifications</h2>{specifications.length ? <dl>{specifications.map(s => <div key={s.label}><dt>{s.label}</dt><dd>{s.value}</dd></div>)}</dl> : <p>Contact the shop to confirm product specifications.</p>}<p className="photo-note">Illustrative photos. Confirm the actual finish, dimensions and suitability before purchase.</p></section>
  </div>
}
