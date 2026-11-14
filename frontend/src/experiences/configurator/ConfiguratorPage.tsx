import { useId, useState } from 'react'
import type { FormEvent } from 'react'
import { assetUrl } from '../../shared/assetUrl'
import { addToCart } from '../../features/cart/store'
import { money } from '../../features/catalogue/format'
import { LoadState } from '../../features/catalogue/LoadState'
import type { Detail } from '../../features/catalogue/types'
import { useResource } from '../../features/catalogue/useResource'
import { useModelManifest } from '../useModelManifest'
import { ModelStage } from '../ModelStage'
import { useModelProducts } from '../useModelProducts'
import '../experiences.css'

function Chooser() {
  const { items, error, retry } = useModelProducts()
  return <div className="container x3-page">
    <nav className="breadcrumbs" aria-label="Breadcrumb"><a href="#/">Home</a><span>/</span><a href="#/3d">3D experience</a><span>/</span><span>Configurator</span></nav>
    <p className="eyebrow">Product configurator</p><h1>Choose a product to configure</h1>
    <p className="intro">Pick the option you want and watch the model change. Only options the shop actually sells are offered.</p>
    {!items ? <LoadState error={error} retry={retry} /> : items.length ? <ul className="x3-cards">{items.map(({ product }) => <li key={product.id}>
      <a href={`#/3d/configure/${product.id}`}><img src={assetUrl(product.imagePath || '/images/store-shelves.jpg')} alt="" loading="lazy" />
        <span><strong>{product.name}</strong><small>{money(product.price, product.currency)} per {product.unit}</small></span></a>
    </li>)}</ul> : <p>No products have a 3D model yet. <a className="text-link" href="#/shop">Browse the catalogue</a>.</p>}
  </div>
}

function Configure({ id, initialVariant }: { id: string; initialVariant: string | null }) {
  const resource = useResource<Detail>('/api/products/' + encodeURIComponent(id))
  const [selected, setSelected] = useState(initialVariant || '')
  const [feedback, setFeedback] = useState('')
  const field = useId()
  const manifest = useModelManifest()
  const data = resource.data
  if (!data) return <div className="container"><LoadState error={resource.error} retry={resource.retry} />{resource.error && <a className="text-link" href="#/3d/configure">Back to the configurator</a>}</div>
  const { product, variants, images } = data
  const entry = manifest.entries?.get(product.id)
  const variant = variants.find(option => option.id === selected) || variants[0]
  const blocked = !variant ? 'This product has no option on sale at the moment.' : variant.price === null ? 'This option is priced by quotation. Contact the shop; it cannot be added to an online request.'
    : !variant.inStock ? 'This option is out of stock. Choose another option or ask the shop when it will return.' : ''
  function choose(variantId: string) {
    setSelected(variantId); setFeedback('')
    // Keep the choice in the address so a refresh or a shared link opens the same option.
    window.history.replaceState(null, '', `#/3d/configure/${product.id}?variant=${variantId}`)
  }
  function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!variant || blocked) return
    try {
      addToCart({ productId: product.id, variantId: variant.id, name: product.name, label: variant.label, quantity: String(new FormData(event.currentTarget).get('quantity') || '1') })
      setFeedback('Added to your cart.')
    } catch (error) { setFeedback(error instanceof Error ? error.message : 'Unable to add this item.') }
  }
  const photo = <img src={assetUrl(images[0]?.path || product.imagePath || '/images/store-shelves.jpg')} alt={images[0]?.alt || product.name} />
  return <div className="container x3-page">
    <nav className="breadcrumbs" aria-label="Breadcrumb"><a href="#/">Home</a><span>/</span><a href="#/3d">3D experience</a><span>/</span><a href="#/3d/configure">Configurator</a><span>/</span><span>{product.name}</span></nav>
    <div className="x3-split">
      <section aria-label="3D preview">{entry ? <ModelStage model={entry} verified={entry.verified} scale={variant ? entry.variantScales[variant.id] ?? 1 : 1} name={`${product.name}${variant ? ', ' + variant.label : ''}`} fallback={photo} />
        : <div className="x3-canvas x3-canvas-light">{photo}</div>}</section>
      <section className="x3-panel" aria-label="Options and price">
        <p className="eyebrow">Product configurator</p><p className="brand">{product.brand}</p><h1>{product.name}</h1>
        <fieldset className="x3-options"><legend>{variants.length > 1 ? 'Choose an option' : 'Available option'}</legend>
          {variants.map(option => <label key={option.id} className={option.id === variant?.id ? 'selected' : ''}>
            <input type="radio" name="variant" value={option.id} checked={option.id === variant?.id} onChange={() => choose(option.id)} />
            <span><strong>{option.label}</strong><small>{option.sku}</small></span>
            <span className="x3-option-price">{money(option.price, option.currency)}<small className={option.inStock ? 'available-text' : ''}>{option.price === null ? 'Quote only' : option.inStock ? 'In stock' : 'Out of stock'}</small></span>
          </label>)}
        </fieldset>
        {variant && <p className="x3-price">{money(variant.price, variant.currency)} <span>per {variant.unit}</span></p>}
        {blocked ? <p className="x3-blocked" role="status">{blocked}</p> : variant && <form className="add-cart-form" onSubmit={add}>
          <label htmlFor={field}>Quantity ({variant.unit})<input key={variant.id} id={field} name="quantity" type="number" min={variant.fractional ? '.001' : '1'} max="1000000" step={variant.fractional ? '.001' : '1'} defaultValue="1" required /></label>
          <button type="submit" className="button primary">Add to cart</button>
        </form>}
        {feedback && <p role="status" className="add-cart-feedback">{feedback}<a className="text-link" href="#/cart">View cart</a></p>}
        <div className="ordering-note">The price and stock shown come from the shop's catalogue and are checked again in your cart and at checkout. The 3D picture does not set the price.</div>
        <a className="text-link" href={`#/product/${product.id}`}>Full product details →</a>
        <p className="x3-version">{entry ? `3D model version ${entry.version} · model set ${manifest.version}` : 'No 3D model is published for this product.'}</p>
      </section>
    </div>
  </div>
}

/** FR-3D-03. The options are the product's real variants; the 3D model and the HTML selectors stay in step. */
export function ConfiguratorPage({ id, params }: { id?: string; params: URLSearchParams }) {
  return id ? <Configure key={id} id={id} initialVariant={params.get('variant')} /> : <Chooser />
}
