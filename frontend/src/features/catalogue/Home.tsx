import { pick, useLanguage, useT } from '../../shared/i18n'
import { DesignGallery } from './DesignGallery'
import { assetUrl } from '../../shared/assetUrl'
import type { Category, ProductPage } from './types'
import { useResource } from './useResource'
import { ProductCard } from './ProductCard'
import { LoadState } from './LoadState'

export function Home({ categories }: { categories: Category[] }) { const t = useT(); const lang = useLanguage();
  const products = useResource<ProductPage>('/api/products?size=12')
  return <>
    <section className="container hero"><img src={assetUrl('/images/hero-site.jpg')} alt={t("Builders working on a tiled roof")} fetchPriority="high" /><div className="hero-overlay" />
      <div className="hero-copy"><p className="eyebrow">{t("Build")}</p><h1>{t("Cement, steel and roofing for every site.")}</h1><p>{t("From the first foundation to the final finish. Find building materials, tools and supplies for your next project.")}</p><a className="button primary large" href="#/shop?category=cement">{t("Shop building materials →")}</a></div></section>
    <section className="container section"><div className="section-heading"><div><p className="eyebrow">{t("Categories")}</p><h2>{t("Everything to build and finish")}</h2></div><a href="#/shop">{t("View all products →")}</a></div><div className="category-grid">{categories.map(c => <a className="category-tile" href={`#/shop?category=${c.slug}`} key={c.id}>{c.imagePath ? <img src={assetUrl(c.imagePath)} alt="" loading="lazy" /> : <span className="category-placeholder" aria-hidden="true">TH</span>}<div><h3>{pick(lang, c.name, c.nameSw)}</h3><span>{t(c.productCount === 1 ? '{n} product' : '{n} products', { n: c.productCount })} <span aria-hidden="true">→</span></span></div></a>)}</div></section>
    <section className="container section"><div className="section-heading"><div><p className="eyebrow">{t("Featured products")}</p><h2>{t("Explore materials for every part of your project")}</h2></div><a href="#/shop">{t("Explore the catalogue →")}</a></div>{!products.data ? <LoadState error={products.error} retry={products.retry} /> : products.data.items.length ? <div className="product-grid">{products.data.items.map(p => <ProductCard key={p.id} product={p} />)}</div> : <p>{t('No featured products yet.')} <a href="#/shop">{t("Browse the catalogue")}</a>.</p>}</section>
<section className="container section"><div className="section-heading"><h2>{t("Deals and offers")}</h2></div><p>{t("Confirmed offers will appear here. Browse current product prices in our store.")}</p><a className="button secondary" href="#/shop">{t("Explore products")}</a></section><section className="container section"><h2>{t("New arrivals")}</h2><p>{t("Our latest additions will appear here as they arrive at the shop.")}</p></section>
    <section className="container section finish-banner"><div><p className="eyebrow">{t("The finishing touches")}</p><h2>{t("Your imagination. Our design expertise.")}</h2><p>{t("Have a vision for your space but unsure where to start? Tarime Hardware helps bring your ideas to life with interior and exterior design. Let us help you create a place you love.")}</p><a className="button primary" href="#/design-services">{t("Explore our design services →")}</a></div><DesignGallery /></section>
  </>
}
