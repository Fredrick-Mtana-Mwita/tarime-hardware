import { useEffect, useState } from 'react'
import { getJson } from '../../shared/api/http'
import { useAuth } from '../identity/useAuth'
import { money } from '../catalogue/format'
import { branchName, commercePost, orderError } from './api'
import type { Order, OrderPage, OrderSummary, Notice } from './api'

function usePrivateResource<T>(url: string, enabled: boolean) {
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState<{ key: string; data?: T; error?: string }>({ key: '' })
  const key = url + '|' + enabled + '|' + attempt
  useEffect(() => {
    if (!enabled) return
    const controller = new AbortController()
    void getJson(url, controller.signal).then(data => { if (!controller.signal.aborted) setState({ key, data: data as T }) })
      .catch((error: unknown) => { if (!controller.signal.aborted) setState({ key, error: orderError(error) }) })
    return () => controller.abort()
  }, [url, enabled, key])
  return { data: state.key === key ? state.data : undefined, error: state.key === key ? state.error : undefined, retry: () => setAttempt(n => n + 1) }
}
const status = (value: string) => value.toLowerCase().replaceAll('_', ' ').replace(/^./, c => c.toUpperCase())
const date = (value: string) => new Intl.DateTimeFormat('en-TZ', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
function SignInPrompt() { return <div className="commerce-panel"><h2>Sign in to view your orders</h2><p className="commerce-intro">Order details are private to your account.</p><a className="button primary" href="#/login?next=orders">Sign in</a></div> }
function Totals({ summary }: { summary: OrderSummary }) {
  return <><dl className="order-totals"><div><dt>Items subtotal</dt><dd>{money(summary.subtotal, summary.currency)}</dd></div><div><dt>{summary.fulfilment === 'PICKUP' ? 'Pickup fee' : 'Delivery fee'}</dt><dd>{summary.deliveryFee === null ? 'To be agreed' : money(summary.deliveryFee, summary.currency)}</dd></div><div><dt>Requested total</dt><dd>{summary.total === null ? 'Awaiting delivery fee' : money(summary.total, summary.currency)}</dd></div></dl><p className="commerce-muted">Final agreement and payment arrangements follow shop review. This request is not a receipt or completed sale.</p></>
}
function Notifications({ enabled }: { enabled: boolean }) {
  const resource = usePrivateResource<Notice[]>('/api/notifications', enabled)
  const [error, setError] = useState('')
  async function read(notice: Notice) {
    try { await commercePost(`/api/notifications/${notice.id}/read`, {}); resource.retry() } catch (problem) { setError(orderError(problem)) }
  }
  return <section className="commerce-panel notifications"><h2>Updates</h2>{error && <p role="alert" className="form-error">{error}</p>}{resource.error ? <><p role="alert">{resource.error}</p><button className="text-link" onClick={resource.retry}>Retry updates</button></> : !resource.data ? <p role="status">Loading updates…</p> : !resource.data.length ? <p className="commerce-muted">Your order updates will appear here.</p> : <ul>{resource.data.map(notice => <li key={notice.id} className={notice.readAt ? '' : 'notice-unread'}><strong>{notice.title}{!notice.readAt && <span className="order-status">New</span>}</strong><p>{notice.message}</p><a className="text-link" href={`#/orders/${notice.orderId}`}>View order</a>{!notice.readAt && <button className="text-link" onClick={() => void read(notice)}>Mark as read</button>}</li>)}</ul>}</section>
}
export function MyOrdersPage() {
  const { user, loading, unavailable, refresh } = useAuth()
  const [page, setPage] = useState(0)
  const resource = usePrivateResource<OrderPage>('/api/orders?page=' + page + '&size=10', !!user)
  return <div className="container commerce-page"><nav className="breadcrumbs" aria-label="Breadcrumb"><a href="#/account">My account</a><span>/</span><span>My orders</span></nav><p className="eyebrow">Your projects</p><h1>My orders</h1><p className="commerce-intro">Follow your requests and shop updates.</p>
    {loading ? <p role="status">Checking your account…</p> : unavailable ? <div><p role="alert">We could not load your account.</p><button className="button secondary" onClick={refresh}>Try again</button></div> : !user ? <SignInPrompt /> : <div className="commerce-grid"><section className="order-list" aria-label="Your orders">{resource.error ? <div className="commerce-panel"><p role="alert">{resource.error}</p><button className="button secondary" onClick={resource.retry}>Retry orders</button></div> : !resource.data ? <p role="status">Loading orders…</p> : !resource.data.items.length ? <div className="commerce-panel"><h2>No orders yet</h2><p className="commerce-intro">Start with the materials for your next build.</p><a className="button primary" href="#/shop">Browse products</a></div> : <>{resource.data.items.map(order => <article className="commerce-panel order-list-card" key={order.id}><div className="order-card-heading"><span className="order-status">{status(order.status)}</span><time dateTime={order.createdAt}>{date(order.createdAt)}</time></div><a className="order-reference" href={`#/orders/${order.id}`}>{order.reference}</a><p className="commerce-muted">{branchName(order.branch)} · {order.fulfilment === 'PICKUP' ? 'Shop pickup' : 'Delivery requested'}</p><p><strong>{money(order.subtotal, order.currency)}</strong> in materials</p><a className="text-link" href={`#/orders/${order.id}`}>View order details</a></article>)}<nav className="pagination" aria-label="Order pages"><button className="button secondary" disabled={page === 0} onClick={() => setPage(n => n - 1)}>Previous</button><span>Page {page + 1}</span><button className="button secondary" disabled={(page + 1) * 10 >= resource.data.total} onClick={() => setPage(n => n + 1)}>Next</button></nav></>}</section><Notifications enabled={!!user} /></div>}
  </div>
}
export function OrderDetailPage({ id }: { id: string }) {
  const { user, loading, unavailable, refresh } = useAuth()
  const resource = usePrivateResource<Order>('/api/orders/' + encodeURIComponent(id), !!user)
  const order = resource.data
  return <div className="container commerce-page"><nav className="breadcrumbs" aria-label="Breadcrumb"><a href="#/orders">My orders</a><span>/</span><span>Order details</span></nav>
    {loading ? <p role="status">Checking your account…</p> : unavailable ? <><p role="alert">We could not check your account.</p><button className="button secondary" onClick={refresh}>Try again</button></> : !user ? <SignInPrompt /> : resource.error ? <div className="commerce-panel"><h1>Unable to load this order</h1><p className="commerce-intro" role="alert">{resource.error}</p><button className="button secondary" onClick={resource.retry}>Try again</button></div> : !order ? <p role="status">Loading your order…</p> : <><p className="eyebrow">{status(order.summary.status)}</p><h1>{order.summary.status === 'PENDING' ? 'Order request received' : 'Your order'}</h1><p className="order-reference">{order.summary.reference}</p><p className="commerce-intro">{order.summary.status === 'PENDING' ? 'The shop will review your request and contact you. Stock has not been reserved yet.' : 'Check your latest order status and contact the shop for any changes.'}</p><div className="commerce-grid"><section className="commerce-panel"><h2>Your materials</h2><ul className="checkout-lines">{order.items.map(line => <li key={line.variantId}><span>{line.productName} · {line.variantLabel}<small>{line.quantity} {line.unit} × {money(line.unitPrice, order.summary.currency)} · {line.sku}</small></span><strong>{money(line.lineTotal, order.summary.currency)}</strong></li>)}</ul><h2>{order.summary.fulfilment === 'PICKUP' ? 'Shop pickup' : 'Delivery request'}</h2><dl className="contact-details"><div><dt>Contact</dt><dd>{order.name} · {order.phone}</dd></div>{order.address && <div><dt>Address</dt><dd>{order.address}</dd></div>}{order.instructions && <div><dt>Instructions</dt><dd>{order.instructions}</dd></div>}<div><dt>Branch</dt><dd>{branchName(order.summary.branch)}</dd></div><div><dt>Requested</dt><dd>{date(order.summary.createdAt)}</dd></div></dl></section><aside className="commerce-panel commerce-summary"><h2>Request summary</h2><Totals summary={order.summary} /><a className="button secondary" href="#/orders">My orders &amp; updates</a><a className="text-link" href="#/shop">Continue shopping</a></aside></div></>}
  </div>
}
