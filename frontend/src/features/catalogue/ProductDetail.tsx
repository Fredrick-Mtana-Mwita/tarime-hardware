import { useT } from '../../shared/i18n'
import { assetUrl } from '../../shared/assetUrl'
import { useState } from 'react'
import type { Detail } from './types'
import { useResource } from './useResource'
import { money } from './format'
import { LoadState } from './LoadState'
import { addToCart, removeItem, updateQuantity, useCart } from '../cart/store'
import { openCartDrawer } from '../cart/drawer'
import { DeliveryPromise, RelatedProducts } from './ProductExtras'
import { lowStockText } from './stock'
import { pick, useLanguage } from '../../shared/i18n'
import type { FormEvent } from 'react'

export function ProductDetail({ id }: { id: string }) { const t = useT();
  const resource = useResource<Detail>('/api/products/' + encodeURIComponent(id))
  const [selected, setSelected] = useState(''); const [photo, setPhoto] = useState('')
  const [feedback, setFeedback] = useState('')
  const cart = useCart()
  const lang = useLanguage()
  const data = resource.data
  if (!data) return <div className="container"><LoadState error={resource.error} retry={resource.retry} />{resource.error && <a href="#/shop">{t("Back to catalogue")}</a>}</div>
  const { product, variants, images, specifications } = data
  const variant = variants.find(v => v.id === selected) || variants[0]
  const image = images.find(i => i.path === photo) || images[0]
  const categoryName = pick(lang, product.categoryName, product.categoryNameSw)
  const inCart = variant && cart.items.find(i => i.variantId === variant.id)
  const fewLeft = lowStockText(variant, lang)
  const step = (by: number) => { if (!inCart) return; const next = Math.round((Number(inCart.quantity) + by) * 1000) / 1000; if (next <= 0) { removeItem(inCart.variantId); setFeedback('') } else updateQuantity(inCart.variantId, String(next)) }
  const mainPhoto = <img className="main-product-image" src={assetUrl(image?.path || '/images/store-shelves.jpg')} alt={image?.alt || product.name} />
  function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!variant || !variant.inStock || variant.price === null) return
    try {
      addToCart({ productId: product.id, variantId: variant.id, name: product.name, label: variant.label, quantity: String(new FormData(event.currentTarget).get('quantity') || '1') })
      setFeedback('Added to your cart.')
    } catch (error) { setFeedback(error instanceof Error ? error.message : 'Unable to add this item.') }
  }
  return <div className="container detail-page"><nav className="breadcrumbs" aria-label={t("Breadcrumb")}><a href="#/">{t("Home")}</a><span>/</span><a href={`#/shop?category=${product.categorySlug}`}>{categoryName}</a><span>/</span><span>{product.name}</span></nav>
    <div className="product-detail"><section className="gallery" aria-label={t("Product photos")}>
        {mainPhoto}{images.length > 1 && <div className="thumbnails">{images.map(i => <button key={i.path} onClick={() => setPhoto(i.path)} aria-label={t('View {name}', { name: i.alt })} aria-pressed={image?.path === i.path}><img src={assetUrl(i.path)} alt="" /></button>)}</div>}</section>
      <section className="product-info"><p className="eyebrow">{categoryName}</p><p className="brand">{product.brand}</p><h1>{product.name}</h1>{product.nameSw && <p className="name-sw detail-name-sw" lang="sw">{product.nameSw}</p>}<p className="sku">{variant?.sku || product.sku}</p><p className="detail-price">{money(variant ? variant.price : product.price, variant?.currency || product.currency)}</p><p className="unit">{t('per {unit}', { unit: variant?.unit || product.unit })}</p><p className={`availability ${variant?.inStock ? 'available-text' : ''}`}>{variant?.inStock ? t('In stock') : t('Out of stock')}{fewLeft && <strong className="few-left"> · {fewLeft}</strong>}</p>
      {variants.length > 1 && <fieldset className="variant-options"><legend>{t("Choose an option")}</legend>{variants.map(v => <button className={v.id === variant?.id ? 'selected' : ''} key={v.id} onClick={() => setSelected(v.id)} aria-pressed={v.id === variant?.id}>{v.label}</button>)}</fieldset>}
      <p className="description">{product.description}</p>{variant?.inStock && <DeliveryPromise />}{variant && inCart ? <div className="in-cart-row"><div className="qty-stepper large-stepper" role="group" aria-label={t("Quantity in cart")}><button type="button" onClick={() => step(-1)} aria-label={Number(inCart.quantity) <= 1 ? t('Remove from cart') : t('One less')}>−</button><span aria-live="polite">{t('{n} in cart', { n: inCart.quantity })}</span><button type="button" onClick={() => step(1)} aria-label={t("One more")}>+</button></div><button type="button" className="button secondary" onClick={openCartDrawer}>{t("View cart")}</button></div> : variant && <form className="add-cart-form" onSubmit={add}><label htmlFor="product-quantity">{t('Quantity ({unit})', { unit: variant.unit })}<input key={variant.id} id="product-quantity" name="quantity" type="number" min={variant.fractional ? '.001' : '1'} max="1000000" step={variant.fractional ? '.001' : '1'} defaultValue="1" required /></label><button type="submit" className="button primary" disabled={!variant.inStock || variant.price === null}>{t("Add to cart")}</button></form>}
      {feedback && !inCart && <p role="status" className="add-cart-feedback">{t(feedback)}</p>}{feedback && inCart && <p role="status" className="add-cart-toast">{t(feedback)}</p>}<div className="ordering-note">{t("Submit a request for shop review. Stock is reserved after confirmation. Items marked “Ask for a quote” require contacting the shop.")}</div><a className="button secondary" href={`#/shop?category=${product.categorySlug}`}>{t('More in {category} →', { category: categoryName })}</a></section></div>
    <section className="specifications"><h2>{t("Specifications")}</h2>{specifications.length ? <dl>{specifications.map(s => <div key={s.label}><dt>{s.label}</dt><dd>{s.value}</dd></div>)}</dl> : <p>{t("Contact the shop to confirm product specifications.")}</p>}<p className="photo-note">{t("Illustrative photos. Confirm the actual finish, dimensions and suitability before purchase.")}</p></section>
    <RelatedProducts product={product} />
  </div>
}
