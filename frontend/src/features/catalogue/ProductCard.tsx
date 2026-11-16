import { useT } from '../../shared/i18n'
import { assetUrl } from '../../shared/assetUrl'
import { motion } from 'motion/react'
import { revealProps, revealTransition, useColumnDelay } from '../../shared/motion/reveal'
import type { Product } from './types'
import { money } from './format'

/** Cards fade and rise into place as they scroll into view, a little after their neighbour (like selcom.bank's reveals). */
export function ProductCard({ product }: { product: Product }) { const t = useT();
  const [card, delay] = useColumnDelay<HTMLAnchorElement>()
  return <motion.a ref={card} className="product-card" href={`#/product/${product.id}`} {...revealProps} transition={revealTransition(delay)}>
    <div className="product-photo"><img src={assetUrl(product.imagePath || '/images/store-shelves.jpg')} alt={product.imageAlt} loading="lazy" />
      <span className={`stock-badge ${product.inStock ? 'available' : ''}`}>{product.inStock ? t('In stock') : t('Out of stock')}</span></div>
    <div className="product-copy"><p className="brand">{product.brand}</p><h3>{product.name}</h3>{product.nameSw && <p className="name-sw" lang="sw">{product.nameSw}</p>}
      <p className="product-price">{money(product.price, product.currency)}</p><p className="unit">{t('per {unit}', { unit: product.unit })}</p>
      <span className="view-product">{t("View product")} <span aria-hidden="true">→</span></span>
    </div>
  </motion.a>
}
