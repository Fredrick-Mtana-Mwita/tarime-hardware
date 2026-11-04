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
import { CheckoutPage } from '../features/orders/CheckoutPage'
import { MyOrdersPage, OrderDetailPage } from '../features/orders/OrderPages'
import { StaffApp } from '../features/staff/StaffApp'

const subscribe = (notify: () => void) => { window.addEventListener('hashchange', notify); return () => window.removeEventListener('hashchange', notify) }
const snapshot = () => window.location.hash || '#/'
export function App() {
  const { user } = useAuth()
  const cart = useCart()
  const hash = useSyncExternalStore(subscribe, snapshot)
  const [path, query = ''] = hash.slice(1).split('?')
  const categories = useResource<Category[]>('/api/categories')
  const params = new URLSearchParams(query)
  const [menuOpen, setMenuOpen] = useState(false)
  useEffect(() => { window.scrollTo({ top: 0 }) }, [hash])
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
    </main>
  }
  return <>
    <a className="skip-link" href="#main" onClick={event => { event.preventDefault(); document.getElementById('main')?.focus() }}>Skip to content</a>
    <div className="topbar"><div className="container"><span>Tarime (main branch) · Mwanza <b>· Build with confidence</b></span><span>Hardware. Building. Finishing.</span></div></div>
    <header className="site-header"><div className="container header-row">
      <button className="menu-button" aria-label="Toggle categories" aria-expanded={menuOpen} onClick={() => setMenuOpen(!menuOpen)}>☰</button>
      <a href="#/" className="logo" aria-label="Tarime Hardware home"><span className="logo-mark">T</span><span><strong>TARIME</strong><small>HARDWARE</small></span></a>
      <form onSubmit={search} className="search-form" role="search"><label className="sr-only" htmlFor="search">Search products</label>
        <input key={params.get('q') || ''} id="search" name="q" type="search" maxLength={150} defaultValue={params.get('q') || ''} placeholder="Search cement, paint, tiles, cables…" /><button type="submit">Search</button></form>
      <a className="button primary shop-link" href="#/shop">Browse products →</a>
      <a className="cart-header-link" href="#/cart" aria-label={`Cart (${cart.items.length})`} title="View cart"><svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d="M3 3h2l2.4 12h11.2l2-8H6" /><circle cx="9" cy="20" r="1" /><circle cx="18" cy="20" r="1" /></svg>{cart.items.length > 0 && <span className="cart-count" aria-hidden="true">{cart.items.length}</span>}</a>
      <a className="account-link" href={user ? '#/account' : '#/login'} target={user ? undefined : '_blank'} rel={user ? undefined : 'noopener noreferrer'}>{user ? 'My account' : 'Sign in'}</a>
    </div><nav aria-label="Product categories" className={`container category-nav ${menuOpen ? 'open' : ''}`}>
      {categories.data?.map(c => <a key={c.id} href={`#/shop?category=${c.slug}`} onClick={() => setMenuOpen(false)} aria-current={params.get('category') === c.slug ? 'page' : undefined}>{c.name}</a>)}
      {categories.error && <button onClick={categories.retry}>Reload categories</button>}
    </nav></header>
    <main id="main" tabIndex={-1}>{path === '/' || path === '' ? <Home categories={categories.data || []} /> : path === '/shop' ? <Shop params={params} categories={categories.data || []} />
      : path.startsWith('/product/') ? <ProductDetail key={path} id={path.split('/')[2]} />
      : path === '/account' ? <AccountPage />
      : path === '/cart' ? <CartPage /> : path === '/checkout' ? <CheckoutPage />
      : path === '/orders' ? <MyOrdersPage key={user?.id || 'guest'} />
      : path.startsWith('/orders/') ? <OrderDetailPage key={(user?.id || 'guest') + path} id={path.split('/')[2]} />
      : <div className="container load-state"><h1>Page not found</h1><a className="button primary" href="#/shop">Browse products</a></div>}</main>
    <footer className="site-footer"><div className="container footer-row"><div><strong>TARIME HARDWARE</strong><p>Everything to build and finish.</p></div><div><a href="#/shop">Browse all products</a><p>Tarime (main branch) · Mwanza</p></div></div>
      <div className="container footer-note">Order requests require shop confirmation. No online payment is taken. Photos reused from the supplied prototype (Unsplash). <a className="text-link" style={{ color: 'inherit', marginLeft: 16 }} href="#/staff/login">Staff access</a></div></footer>
  </>
}
