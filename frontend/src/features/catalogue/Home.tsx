import { assetUrl } from '../../shared/assetUrl'
import type { Category, ProductPage } from './types'
import { useResource } from './useResource'
import { ProductCard } from './ProductCard'
import { SampleNotice, LoadState } from './LoadState'

export function Home({ categories }: { categories: Category[] }) {
  const products = useResource<ProductPage>('/api/products?featured=true&size=8')
  return <>
    <section className="container hero"><img src={assetUrl('/images/hero-site.jpg')} alt="Builders working on a tiled roof" fetchPriority="high" /><div className="hero-overlay" />
      <div className="hero-copy"><p className="eyebrow">Build</p><h1>Cement, steel and roofing for every site.</h1><p>From the first foundation to the final finish. Find building materials, tools and supplies for your next project.</p><a className="button primary large" href="#/shop?category=cement">Shop building materials →</a></div></section>
    <div className="container promise-row"><div><strong>Building essentials</strong><span>For jobs big and small</span></div><div><strong>Prices by the unit</strong><span>Bags, metres, boxes and pieces</span></div><div><strong>Made for your project</strong><span>Tools, materials and finishing</span></div><div><strong>Browse anywhere</strong><span>At home or on site</span></div></div>
    {products.data?.items.some(p => p.sampleData) && <div className="container"><SampleNotice /></div>}
    <section className="container section"><div className="section-heading"><div><p className="eyebrow">Categories</p><h2>Everything to build and finish</h2></div><a href="#/shop">View all products →</a></div><div className="category-grid">{categories.map(c => <a className="category-tile" href={`#/shop?category=${c.slug}`} key={c.id}>{c.imagePath ? <img src={assetUrl(c.imagePath)} alt="" loading="lazy" /> : <span className="category-placeholder" aria-hidden="true">TH</span>}<div><h3>{c.name}</h3><span>{c.productCount} {c.productCount === 1 ? 'product' : 'products'} <span aria-hidden="true">→</span></span></div></a>)}</div></section>
    <section className="container section"><div className="section-heading"><div><p className="eyebrow">Featured products</p><h2>Start your next project</h2></div><a href="#/shop">Explore the catalogue →</a></div>{!products.data ? <LoadState error={products.error} retry={products.retry} /> : products.data.items.length ? <div className="product-grid">{products.data.items.map(p => <ProductCard key={p.id} product={p} />)}</div> : <p>No featured products yet. <a href="#/shop">Browse the catalogue</a>.</p>}</section>
    <section className="container section finish-banner"><div><p className="eyebrow">The finishing touches</p><h2>Make your space your own.</h2><p>Discover paint, tiles and lighting for a finish that feels right.</p><a className="button primary" href="#/shop?category=electrical">Explore electricals →</a></div><img src={assetUrl('/images/hero-interior.jpg')} alt="A finished room with warm lighting" loading="lazy" /></section>
  </>
}
