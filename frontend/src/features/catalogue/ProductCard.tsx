import { assetUrl } from '../../shared/assetUrl'
import type { Product } from './types'
import { money } from './format'

export function ProductCard({ product }: { product: Product }) {
  return <a className="product-card" href={`#/product/${product.id}`}>
    <div className="product-photo"><img src={assetUrl(product.imagePath || '/images/store-shelves.jpg')} alt={product.imageAlt} loading="lazy" />
      <span className={`stock-badge ${product.inStock ? 'available' : ''}`}>{product.inStock ? 'In stock' : 'Out of stock'}</span></div>
    <div className="product-copy"><p className="brand">{product.brand}</p><h3>{product.name}</h3>
      <p className="product-price">{money(product.price, product.currency)}</p><p className="unit">per {product.unit}</p>
      <span className="view-product">View product <span aria-hidden="true">→</span></span>
    </div>
  </a>
}
