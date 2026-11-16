import { pick, useLanguage, useT } from '../../shared/i18n'
import { useEffect, useState } from 'react'
import { shopSettings } from '../../shared/shopSettings'
import type { Product, ProductPage } from './types'
import { useResource } from './useResource'
import { ProductCard } from './ProductCard'

const SHOP_ZONE = 'Africa/Dar_es_Salaam'
function shopNow(now: Date) {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-GB', { timeZone: SHOP_ZONE, weekday: 'short', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(now).map(p => [p.type, p.value]))
  const day = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].indexOf(parts.weekday)
  return { day, minutes: Number(parts.hour) * 60 + Number(parts.minute) }
}
const clock = (hhmm: string) => { const [h, m] = hhmm.split(':').map(Number); return `${h % 12 || 12}${m ? ':' + String(m).padStart(2, '0') : ''} ${h < 12 ? 'am' : 'pm'}` }

/** Honest delivery line. Until the shop confirms its cut-off time, no time promise is shown. */
export function DeliveryPromise() { const t = useT();
  const d = shopSettings.delivery
  const [now, setNow] = useState(() => new Date())
  useEffect(() => { if (!d.confirmed) return; const t = setInterval(() => setNow(new Date()), 60000); return () => clearInterval(t) }, [d.confirmed])
  if (!d.confirmed) return <div className="delivery-promise"><strong>{t("Delivery or pickup")}</strong><span>{t('Delivery is available in {area} and nearby, or collect from the shop. The shop confirms the delivery fee and time before your order is final.', { area: t(d.area) })}</span></div>
  const { day, minutes } = shopNow(now)
  const [h, m] = d.sameDayCutoff.split(':').map(Number)
  const left = h * 60 + m - minutes
  const today = (d.deliveryDays as readonly number[]).includes(day) && left > 0
  return <div className="delivery-promise"><strong>{today ? t('Order within {time} for same-day delivery in {area}', { time: `${Math.floor(left / 60) ? Math.floor(left / 60) + 'h ' : ''}${left % 60}m`, area: t(d.area) }) : t('Order now for the next delivery day in {area}', { area: t(d.area) })}</strong>
    <span>{t('Same-day delivery applies to orders confirmed before {time}. The shop confirms the delivery fee before your order is final.', { time: clock(d.sameDayCutoff) })}</span></div>
}

/** Other products from the same category. */
export function RelatedProducts({ product }: { product: Product }) { const t = useT(); const lang = useLanguage();
  const related = useResource<ProductPage>('/api/products?' + new URLSearchParams({ category: product.categorySlug, size: '5' }))
  const items = related.data?.items.filter(p => p.id !== product.id).slice(0, 4) || []
  if (!items.length) return null
  return <section className="section related-products" aria-labelledby="related-title"><div className="section-heading"><h2 id="related-title">{t('More in {category}', { category: pick(lang, product.categoryName, product.categoryNameSw) })}</h2><a href={`#/shop?category=${product.categorySlug}`}>{t("See all →")}</a></div>
    <div className="product-grid">{items.map(p => <ProductCard key={p.id} product={p} />)}</div></section>
}
