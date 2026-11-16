import { useEffect, useRef, useState } from 'react'
import type { Category } from '../features/catalogue/types'
import { pick, useLanguage, useT } from '../shared/i18n'
import { assetUrl } from '../shared/assetUrl'
import { categoryImage } from '../shared/categoryImages'

/** "All categories" button that opens a panel with every category, like Xerin's "Aina Zote". */
export function AllCategories({ categories, current }: { categories: Category[]; current: string | null }) {
  const t = useT(); const lang = useLanguage()
  const [open, setOpen] = useState(false)
  const box = useRef<HTMLDivElement>(null)
  const button = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    if (!open) return
    box.current?.querySelector<HTMLAnchorElement>('.all-categories-panel a')?.focus()
    const outside = (event: PointerEvent) => { if (box.current && !box.current.contains(event.target as Node)) setOpen(false) }
    const key = (event: KeyboardEvent) => { if (event.key === 'Escape') { setOpen(false); button.current?.focus() } }
    const close = () => setOpen(false)
    document.addEventListener('pointerdown', outside); document.addEventListener('keydown', key); window.addEventListener('hashchange', close)
    return () => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', key); window.removeEventListener('hashchange', close) }
  }, [open])
  const total = categories.reduce((sum, c) => sum + c.productCount, 0)
  return <div className="all-categories" ref={box}>
    <button ref={button} type="button" className="all-categories-button" aria-expanded={open} aria-controls="all-categories-panel" onClick={() => setOpen(!open)}>
      <svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="M4 6h16M4 12h16M4 18h16" /></svg>
      {t('All categories')}
      <svg aria-hidden="true" className="chevron" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"><path d="m6 9 6 6 6-6" /></svg>
    </button>
    {open && <div className="all-categories-panel" id="all-categories-panel">
      <div className="all-categories-head"><p>{t('Shop by category')}</p><a href="#/shop">{t('All products')} <small>{total}</small> →</a></div>
      <ul>{categories.map(c => <li key={c.id}><a href={`#/shop?category=${c.slug}`} aria-current={current === c.slug ? 'page' : undefined}>
        <CategoryThumb slug={c.slug} imagePath={c.imagePath} />
        <span><strong>{pick(lang, c.name, c.nameSw)}</strong><small>{t(c.productCount === 1 ? '{n} product' : '{n} products', { n: c.productCount })}</small></span>
      </a></li>)}</ul>
    </div>}
  </div>
}

function CategoryThumb({ slug, imagePath }: { slug: string; imagePath: string }) {
  const [failed, setFailed] = useState(false)
  const src = categoryImage(slug, imagePath)
  if (!src || failed) return <span className="category-thumb empty" aria-hidden="true" />
  return <img className="category-thumb" src={assetUrl(src)} alt="" width={48} height={48} loading="lazy" decoding="async" onError={() => setFailed(true)} />
}
