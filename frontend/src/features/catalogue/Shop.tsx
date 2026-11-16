import { pick, useLanguage, useT } from '../../shared/i18n'
import type { FormEvent } from 'react'
import type { Category, ProductPage } from './types'
import { useResource } from './useResource'
import { ProductCard } from './ProductCard'
import { LoadState } from './LoadState'

/** Ready-made price bands for hardware, in TZS. */
const priceBands: { label: string; min?: string; max?: string }[] = [
  { label: 'Under 10,000', max: '10000' }, { label: '10,000 – 50,000', min: '10000', max: '50000' }, { label: '50,000 – 200,000', min: '50000', max: '200000' },
  { label: '200,000 – 1,000,000', min: '200000', max: '1000000' }, { label: 'Over 1,000,000', min: '1000000' }]

export function Shop({ params, categories }: { params: URLSearchParams; categories: Category[] }) { const t = useT(); const lang = useLanguage();
  const allowed = new URLSearchParams(params); allowed.set('size', '12')
  const products = useResource<ProductPage>('/api/products?' + allowed.toString())
  const category = categories.find(c => c.slug === params.get('category'))
  function update(key: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value); else next.delete(key)
    if (key !== 'page') next.delete('page')
    window.location.hash = '#/shop?' + next.toString()
  }
  function bandLink(min = '', max = '') {
    const next = new URLSearchParams(params)
    if (min) next.set('minPrice', min); else next.delete('minPrice')
    if (max) next.set('maxPrice', max); else next.delete('maxPrice')
    next.delete('page'); return '#/shop?' + next.toString()
  }
  const custom = (params.get('minPrice') || params.get('maxPrice')) && !priceBands.some(b => (b.min || '') === (params.get('minPrice') || '') && (b.max || '') === (params.get('maxPrice') || ''))
  function filter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const data = new FormData(event.currentTarget); const next = new URLSearchParams(params)
    for (const key of ['category', 'minPrice', 'maxPrice', 'inStock']) {
      const value = String(data.get(key) || '')
      if (value) next.set(key, value); else next.delete(key)
    }
    next.delete('page'); window.location.hash = '#/shop?' + next.toString()
  }
  return <div className="container shop-page"><nav className="breadcrumbs" aria-label={t("Breadcrumb")}><a href="#/">{t("Home")}</a><span>/</span><span>{(category ? pick(lang, category.name, category.nameSw) : t('All products'))}</span></nav>
    <p className="eyebrow">{t("Shop")}</p><h1>{params.get('q') ? t('Results for “{q}”', { q: params.get('q') || '' }) : (category ? pick(lang, category.name, category.nameSw) : t('All products'))}</h1>{category && <p className="intro">{category.description}</p>}
    <div className="shop-layout"><aside><details className="filter-panel" open><summary>{t("Filters")}</summary><form key={params.toString() + categories.length} onSubmit={filter}>
      <label htmlFor="category">{t("Category")}</label><select id="category" name="category" defaultValue={params.get('category') || ''}><option value="">{t("All categories")}</option>{categories.map(c => <option key={c.id} value={c.slug}>{pick(lang, c.name, c.nameSw)}</option>)}</select>
      <fieldset><legend>{t("Price (TSh)")}</legend><div className="price-bands">{priceBands.map(b => { const on = (b.min || '') === (params.get('minPrice') || '') && (b.max || '') === (params.get('maxPrice') || ''); return <a key={b.label} href={on ? bandLink() : bandLink(b.min, b.max)} aria-current={on ? 'true' : undefined}>{t(b.label)}</a> })}</div><details className="custom-price" open={!!custom}><summary>{t("Custom range")}</summary><label htmlFor="min-price">{t("Minimum")}</label><input id="min-price" name="minPrice" type="number" min="0" step="0.01" defaultValue={params.get('minPrice') || ''} placeholder="0" /><label htmlFor="max-price">{t("Maximum")}</label><input id="max-price" name="maxPrice" type="number" min="0" step="0.01" defaultValue={params.get('maxPrice') || ''} placeholder={t("Any price")} /></details></fieldset>
      <label className="checkbox"><input name="inStock" value="true" type="checkbox" defaultChecked={params.get('inStock') === 'true'} /> {t("In stock only")}</label><button className="button primary" type="submit">{t("Apply filters")}</button><a className="reset" href="#/shop">{t("Clear all filters")}</a>
    </form></details></aside><section aria-label={t("Products")}><div className="results-toolbar"><p role="status">{products.data ? t(products.data.total === 1 ? '{n} product' : '{n} products', { n: products.data.total }) : t('Loading…')}</p><label>{t("Sort")} <select aria-label={t("Sort")} value={params.get('sort') || 'featured'} onChange={e => update('sort', e.target.value)}><option value="featured">{t("Featured first")}</option><option value="price-asc">{t("Price: low to high")}</option><option value="price-desc">{t("Price: high to low")}</option><option value="name">{t("Name: A to Z")}</option></select></label></div>
      {!products.data ? <LoadState error={products.error} retry={products.retry} /> : <>{products.data.items.length ? <div className="product-grid shop-products">{products.data.items.map(p => <ProductCard key={p.id} product={p} />)}</div> : <div className="load-state"><h2>{t("No products found")}</h2><p>{t("Try another search or a wider price range.")}</p><a className="button primary" href="#/shop">{t("Clear filters")}</a></div>}
      {products.data.total > products.data.size && <nav className="pagination" aria-label={t("Product pages")}><button className="button secondary" disabled={products.data.page === 0} onClick={() => update('page', String(products.data!.page - 1))}>{t("Previous")}</button><span>{t('Page {page} of {pages}', { page: products.data.page + 1, pages: Math.ceil(products.data.total / products.data.size) })}</span><button className="button secondary" disabled={(products.data.page + 1) * products.data.size >= products.data.total} onClick={() => update('page', String(products.data!.page + 1))}>{t("Next")}</button></nav>}</>}
    </section></div></div>
}
