import { MotionConfig, motion } from 'motion/react'
import { useT } from '../shared/i18n'
import { isClientPreview } from '../shared/preview'
import { PreviewUnavailable } from '../preview/PreviewNotice'
import { useEffect, useSyncExternalStore, useState } from 'react'
import type { FormEvent } from 'react'
import { useResource } from '../features/catalogue/useResource'
import type { Category } from '../features/catalogue/types'
import { Home } from '../features/catalogue/Home'
import { Shop } from '../features/catalogue/Shop'
import { ProductDetail } from '../features/catalogue/ProductDetail'
import { AuthPage } from '../features/identity/AuthPage'
import { AccountPage } from '../features/identity/AccountPage'
import { useAuth } from '../features/identity/useAuth'
import { useCart } from '../features/cart/store'
import { CartPage } from '../features/cart/CartPage'
import { CartDrawer } from '../features/cart/CartDrawer'
import { openCartDrawer } from '../features/cart/drawer'
import { MobileTabBar } from './MobileTabBar'
import { useScrollBackHeader } from './useScrollBackHeader'
import { LanguageSwitch } from './Language'
import { Footer, WhatsAppButton } from './Footer'
import { PolicyPage } from './PolicyPage'
import { DeliverTo } from './DeliverTo'
import { SearchHint } from './SearchHint'
import { AllCategories } from './AllCategories'
import { CheckoutPage } from '../features/orders/CheckoutPage'
import { MyOrdersPage, OrderDetailPage, OrderReceiptPage } from '../features/orders/OrderPages'
import { StaffApp } from '../features/staff/StaffApp'
import { DesignPage } from '../features/design/DesignPage'

const subscribe = (notify: () => void) => { window.addEventListener('hashchange', notify); return () => window.removeEventListener('hashchange', notify) }
const snapshot = () => window.location.hash || '#/'
export function App() { const t = useT();
  const { user } = useAuth()
  const cart = useCart()
  const hash = useSyncExternalStore(subscribe, snapshot)
  const [path, query = ''] = hash.slice(1).split('?')
  const categories = useResource<Category[]>('/api/categories')
  const params = new URLSearchParams(query)
  const [searchActive, setSearchActive] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const headerHidden = useScrollBackHeader(menuOpen)
  useEffect(() => { window.scrollTo({ top: 0, behavior: 'instant' }) }, [hash])
  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const q = String(new FormData(event.currentTarget).get('q') || '').trim()
    window.location.hash = '#/shop?' + new URLSearchParams(q ? { q } : {}).toString()
  }
  if (isClientPreview && ['/checkout', '/account', '/orders', '/staff/password', '/staff/accounts'].includes(path)) return <PreviewUnavailable />
  if (path === '/staff' || path.startsWith('/staff/')) return <StaffApp path={path} query={params} />
  if (path === '/login' || path === '/register') {
    return <main id="main" className="auth-shell" tabIndex={-1}>
      <AuthPage mode={path === '/login' ? 'login' : 'register'} next={params.get('next')} />
      <div className="auth-language"><LanguageSwitch /></div>
    </main>
  }
  return <MotionConfig reducedMotion="user">
    <a className="skip-link" href="#main" onClick={event => { event.preventDefault(); document.getElementById('main')?.focus() }}>{t("Skip to content")}</a>
    <div className="topbar"><div className="container"><span>Tarime <b>{t("· Build with confidence")}</b></span><span>{t("Hardware. Building. Finishing.")}</span><LanguageSwitch /></div></div>
    <header className={`site-header${headerHidden ? ' is-hidden' : ''}`}><div className="container header-row">
      <button className="menu-button" aria-label={t("Toggle categories")} aria-expanded={menuOpen} onClick={() => setMenuOpen(!menuOpen)}>☰</button>
      <a href="#/" className="logo" aria-label={t("Tarime Hardware home")}><span className="logo-mark">T</span><span><strong>TARIME</strong><small>HARDWARE</small></span></a>
      <DeliverTo />
      <form onSubmit={search} className="search-form" role="search"><label className="sr-only" htmlFor="search">{t("Search products")}</label>
        <span className="search-field"><input key={params.get('q') || ''} id="search" name="q" type="search" maxLength={150} defaultValue={params.get('q') || ''} onFocus={() => setSearchActive(true)} onBlur={event => setSearchActive(event.currentTarget.value !== '')} onInput={event => setSearchActive(event.currentTarget.value !== '' || document.activeElement === event.currentTarget)} /><SearchHint hidden={searchActive || !!params.get('q')} /></span><button type="submit" aria-label={t("Search")}><svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg></button></form>
      <div className="header-actions">
        <a className="cart-header-link" href="#/cart" onClick={event => { if (path === '/cart' || path === '/checkout') return; event.preventDefault(); openCartDrawer() }} aria-label={t('Cart ({n})', { n: cart.items.length })} title={t("View cart")}><svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d="M3 3h2l2.4 12h11.2l2-8H6" /><circle cx="9" cy="20" r="1" /><circle cx="18" cy="20" r="1" /></svg>{cart.items.length > 0 && <span className="cart-count" aria-hidden="true">{cart.items.length}</span>}</a>
        <a className="account-link" href={user ? '#/account' : '#/login'}><svg aria-hidden="true" viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M4 21a8 8 0 0 1 16 0" /></svg><span>{user ? t("My account") : t("Sign in")}</span></a>
      </div>
    </div><nav aria-label={t("Product categories")} className={`container category-nav ${menuOpen ? 'open' : ''}`}>
      <AllCategories categories={categories.data || []} current={params.get('category')} />
      <a className="design-nav-link" href="#/design-services" onClick={() => setMenuOpen(false)} aria-current={path === '/design-services' ? 'page' : undefined}>{t('Interior & Exterior Design')}</a>
      {categories.error && <button onClick={categories.retry}>{t("Reload categories")}</button>}
    </nav></header>
    <main id="main" tabIndex={-1}><motion.div key={path} className="page-fade" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.35, ease: 'easeOut' }}>{path === '/' || path === '' ? <Home categories={categories.data || []} /> : path === '/shop' ? <Shop params={params} categories={categories.data || []} />
      : path.startsWith('/product/') ? <ProductDetail key={path} id={path.split('/')[2]} />
      : path === '/design-services' ? <DesignPage /> : path === '/account' ? <AccountPage />
      : path.startsWith('/policies/') ? <PolicyPage key={path} slug={path.split('/')[2]} />
      : path === '/cart' ? <CartPage /> : path === '/checkout' ? <CheckoutPage />
      : path === '/orders' ? <MyOrdersPage key={user?.id || 'guest'} />
      : /^\/orders\/[^/]+\/receipts\/[^/]+$/.test(path) ? <OrderReceiptPage key={(user?.id || 'guest') + path} id={path.split('/')[2]} receiptNumber={path.split('/')[4]} />
      : path.startsWith('/orders/') ? <OrderDetailPage key={(user?.id || 'guest') + path} id={path.split('/')[2]} />
      : <div className="container load-state"><h1>{t("Page not found")}</h1><a className="button primary" href="#/shop">{t("Browse products")}</a></div>}</motion.div></main>
    <Footer />
    <WhatsAppButton />
    <CartDrawer />
    
    <MobileTabBar path={path} cartCount={cart.items.length} signedIn={!!user} categories={categories.data || []} />
  </MotionConfig>
}
