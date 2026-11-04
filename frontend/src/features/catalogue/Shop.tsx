import type { FormEvent } from 'react'
import type { Category, ProductPage } from './types'
import { useResource } from './useResource'
import { ProductCard } from './ProductCard'
import { SampleNotice, LoadState } from './LoadState'

export function Shop({ params, categories }: { params: URLSearchParams; categories: Category[] }) {
  const allowed = new URLSearchParams(params); allowed.set('size', '12')
  const products = useResource<ProductPage>('/api/products?' + allowed.toString())
  const category = categories.find(c => c.slug === params.get('category'))
  function update(key: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value); else next.delete(key)
    if (key !== 'page') next.delete('page')
    window.location.hash = '#/shop?' + next.toString()
  }
  function filter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const data = new FormData(event.currentTarget); const next = new URLSearchParams(params)
    for (const key of ['category', 'minPrice', 'maxPrice', 'inStock']) {
      const value = String(data.get(key) || '')
      if (value) next.set(key, value); else next.delete(key)
    }
    next.delete('page'); window.location.hash = '#/shop?' + next.toString()
  }
  return <div className="container shop-page"><nav className="breadcrumbs" aria-label="Breadcrumb"><a href="#/">Home</a><span>/</span><span>{category?.name || 'All products'}</span></nav>
    <p className="eyebrow">Shop</p><h1>{params.get('q') ? `Results for “${params.get('q')}”` : category?.name || 'All products'}</h1>{category && <p className="intro">{category.description}</p>}
    <div className="shop-layout"><aside><details className="filter-panel" open><summary>Filters</summary><form key={params.toString() + categories.length} onSubmit={filter}>
      <label htmlFor="category">Category</label><select id="category" name="category" defaultValue={params.get('category') || ''}><option value="">All categories</option>{categories.map(c => <option key={c.id} value={c.slug}>{c.name}</option>)}</select>
      <fieldset><legend>Price range (TSh)</legend><label htmlFor="min-price">Minimum</label><input id="min-price" name="minPrice" type="number" min="0" step="0.01" defaultValue={params.get('minPrice') || ''} placeholder="0" /><label htmlFor="max-price">Maximum</label><input id="max-price" name="maxPrice" type="number" min="0" step="0.01" defaultValue={params.get('maxPrice') || ''} placeholder="Any price" /></fieldset>
      <label className="checkbox"><input name="inStock" value="true" type="checkbox" defaultChecked={params.get('inStock') === 'true'} /> In stock only</label><button className="button primary" type="submit">Apply filters</button><a className="reset" href="#/shop">Clear all filters</a>
    </form></details></aside><section aria-label="Products"><div className="results-toolbar"><p role="status">{products.data ? `${products.data.total} ${products.data.total === 1 ? 'product' : 'products'}` : 'Loading…'}</p><label>Sort <select aria-label="Sort" value={params.get('sort') || 'featured'} onChange={e => update('sort', e.target.value)}><option value="featured">Featured first</option><option value="price-asc">Price: low to high</option><option value="price-desc">Price: high to low</option><option value="name">Name: A to Z</option></select></label></div>
      {!products.data ? <LoadState error={products.error} retry={products.retry} /> : <>{products.data.items.some(p => p.sampleData) && <SampleNotice />}{products.data.items.length ? <div className="product-grid shop-products">{products.data.items.map(p => <ProductCard key={p.id} product={p} />)}</div> : <div className="load-state"><h2>No products found</h2><p>Try another search or a wider price range.</p><a className="button primary" href="#/shop">Clear filters</a></div>}
      {products.data.total > products.data.size && <nav className="pagination" aria-label="Product pages"><button className="button secondary" disabled={products.data.page === 0} onClick={() => update('page', String(products.data!.page - 1))}>Previous</button><span>Page {products.data.page + 1} of {Math.ceil(products.data.total / products.data.size)}</span><button className="button secondary" disabled={(products.data.page + 1) * products.data.size >= products.data.total} onClick={() => update('page', String(products.data!.page + 1))}>Next</button></nav>}</>}
    </section></div></div>
}
