import { pick, useLanguage, useT } from '../shared/i18n'
import { useState } from 'react'
import type { Category } from '../features/catalogue/types'
import { openCartDrawer } from '../features/cart/drawer'

const icon = (d: string) => <svg aria-hidden="true" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d={d} /></svg>

/** Phone-only bottom menu: Home, Categories, Cart, Account. */
export function MobileTabBar({ path, cartCount, signedIn, categories }: { path: string; cartCount: number; signedIn: boolean; categories: Category[] }) { const t = useT(); const lang = useLanguage();
  const [sheet, setSheet] = useState(false)
  const home = path === '/' || path === ''
  const account = ['/account', '/orders', '/login', '/register'].some(p => path === p || path.startsWith(p + '/'))
  return <>
    {sheet && <div className="tab-sheet-layer"><button className="cart-drawer-backdrop" aria-label={t("Close categories")} tabIndex={-1} onClick={() => setSheet(false)} />
      <nav className="tab-sheet" aria-label={t("Categories")}><div className="cart-drawer-head"><h2>{t("Categories")}</h2><button className="cart-drawer-close" onClick={() => setSheet(false)} aria-label={t("Close categories")}>×</button></div>
        <a href="#/shop" onClick={() => setSheet(false)}>{t("All products")}</a>
        {categories.map(c => <a key={c.id} href={`#/shop?category=${c.slug}`} onClick={() => setSheet(false)}>{pick(lang, c.name, c.nameSw)}<small>{c.productCount}</small></a>)}
        <a href="#/design-services" onClick={() => setSheet(false)}>{t('Interior & Exterior Design')}</a>
      </nav></div>}
    <nav className="mobile-tabbar" aria-label={t("Main")}>
      <a href="#/" aria-current={home ? 'page' : undefined}>{icon('M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z')}<span>{t("Home")}</span></a>
      <button aria-expanded={sheet} onClick={() => setSheet(!sheet)} aria-current={path === '/shop' ? 'page' : undefined}>{icon('M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z')}<span>{t("Categories")}</span></button>
      <button onClick={openCartDrawer} aria-label={t('Cart, {n} items', { n: cartCount })}>{icon('M3 3h2l2.4 12h11.2l2-8H6 M9 20h.01 M18 20h.01')}{cartCount > 0 && <span className="tab-count" aria-hidden="true">{cartCount}</span>}<span>{t("Cart")}</span></button>
      <a href={signedIn ? '#/account' : '#/login'} aria-current={account ? 'page' : undefined}>{icon('M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M4 21a8 8 0 0 1 16 0')}<span>{signedIn ? t("Account") : t("Sign in")}</span></a>
    </nav>
  </>
}
