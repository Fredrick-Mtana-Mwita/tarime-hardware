import { useT } from '../../shared/i18n'
import { useEffect, useState } from 'react'
import { getJson } from '../../shared/api/http'
import { useAuth } from '../identity/useAuth'
import { currentLanguage, locale } from '../../shared/i18n'
import { money } from '../catalogue/format'
import { branchName, commercePost, orderError } from './api'
import type { Order, OrderPage, OrderSummary, Notice } from './api'
import { PaymentsPanel } from '../finance/CustomerPayments'
import { ReceiptSheet } from '../finance/Receipt'
import type { CustomerPayments } from '../finance/api'

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
const date = (value: string) => new Intl.DateTimeFormat(locale(currentLanguage()), { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
function SignInPrompt() { const t = useT(); return <div className="commerce-panel"><h2>{t("Sign in to view your orders")}</h2><p className="commerce-intro">{t("Order details are private to your account.")}</p><a className="button primary" href="#/login?next=orders">{t("Sign in")}</a></div> }
function Totals({ summary }: { summary: OrderSummary }) { const t = useT();
  return <><dl className="order-totals"><div><dt>{t("Items subtotal")}</dt><dd>{money(summary.subtotal, summary.currency)}</dd></div><div><dt>{summary.fulfilment === 'PICKUP' ? t("Pickup fee") : t("Delivery fee")}</dt><dd>{summary.deliveryFee === null ? t("To be agreed") : money(summary.deliveryFee, summary.currency)}</dd></div><div><dt>{t("Requested total")}</dt><dd>{summary.total === null ? t("Awaiting delivery fee") : money(summary.total, summary.currency)}</dd></div></dl><p className="commerce-muted">{t("Final agreement and payment arrangements follow shop review. This request is not a receipt or completed sale.")}</p></>
}
function Notifications({ enabled }: { enabled: boolean }) { const t = useT();
  const resource = usePrivateResource<Notice[]>('/api/notifications', enabled)
  const [error, setError] = useState('')
  async function read(notice: Notice) {
    try { await commercePost(`/api/notifications/${notice.id}/read`, {}); resource.retry() } catch (problem) { setError(orderError(problem)) }
  }
  return <section className="commerce-panel notifications"><h2>{t("Updates")}</h2>{error && <p role="alert" className="form-error">{t(error)}</p>}{resource.error ? <><p role="alert">{t(resource.error)}</p><button className="text-link" onClick={resource.retry}>{t("Retry updates")}</button></> : !resource.data ? <p role="status">{t("Loading updates…")}</p> : !resource.data.length ? <p className="commerce-muted">{t("Your order updates will appear here.")}</p> : <ul>{resource.data.map(notice => <li key={notice.id} className={notice.readAt ? '' : 'notice-unread'}><strong>{t(notice.title)}{!notice.readAt && <span className="order-status">{t("New")}</span>}</strong><p>{notice.message}</p><a className="text-link" href={`#/orders/${notice.orderId}`}>{t("View order")}</a>{!notice.readAt && <button className="text-link" onClick={() => void read(notice)}>{t("Mark as read")}</button>}</li>)}</ul>}</section>
}
export function MyOrdersPage() { const t = useT();
  const { user, loading, unavailable, refresh } = useAuth()
  const [page, setPage] = useState(0)
  const resource = usePrivateResource<OrderPage>('/api/orders?page=' + page + '&size=10', !!user)
  return <div className="container commerce-page"><nav className="breadcrumbs" aria-label={t("Breadcrumb")}><a href="#/account">{t("My account")}</a><span>/</span><span>{t("My orders")}</span></nav><p className="eyebrow">{t("Your projects")}</p><h1>{t("My orders")}</h1><p className="commerce-intro">{t("Follow your requests and shop updates.")}</p>
    {loading ? <p role="status">{t("Checking your account…")}</p> : unavailable ? <div><p role="alert">{t("We could not load your account.")}</p><button className="button secondary" onClick={refresh}>{t("Try again")}</button></div> : !user ? <SignInPrompt /> : <div className="commerce-grid"><section className="order-list" aria-label={t("Your orders")}>{resource.error ? <div className="commerce-panel"><p role="alert">{t(resource.error)}</p><button className="button secondary" onClick={resource.retry}>{t("Retry orders")}</button></div> : !resource.data ? <p role="status">{t("Loading orders…")}</p> : !resource.data.items.length ? <div className="commerce-panel"><h2>{t("No orders yet")}</h2><p className="commerce-intro">{t("Start with the materials for your next build.")}</p><a className="button primary" href="#/shop">{t("Browse products")}</a></div> : <>{resource.data.items.map(order => <article className="commerce-panel order-list-card" key={order.id}><div className="order-card-heading"><span className="order-status">{t(status(order.status))}</span><time dateTime={order.createdAt}>{date(order.createdAt)}</time></div><a className="order-reference" href={`#/orders/${order.id}`}>{order.reference}</a><p className="commerce-muted">{t(branchName(order.branch))} · {order.fulfilment === 'PICKUP' ? t("Shop pickup") : t("Delivery requested")}</p><p>{t('{amount} in materials', { amount: money(order.subtotal, order.currency) })}</p><a className="text-link" href={`#/orders/${order.id}`}>{t("View order details")}</a></article>)}<nav className="pagination" aria-label={t("Order pages")}><button className="button secondary" disabled={page === 0} onClick={() => setPage(n => n - 1)}>{t("Previous")}</button><span>{t('Page {page}', { page: page + 1 })}</span><button className="button secondary" disabled={(page + 1) * 10 >= resource.data.total} onClick={() => setPage(n => n + 1)}>{t("Next")}</button></nav></>}</section><Notifications enabled={!!user} /></div>}
  </div>
}
export function OrderDetailPage({ id }: { id: string }) { const t = useT();
  const { user, loading, unavailable, refresh } = useAuth()
  const resource = usePrivateResource<Order>('/api/orders/' + encodeURIComponent(id), !!user)
  const order = resource.data
  const payments = usePrivateResource<CustomerPayments>('/api/orders/' + encodeURIComponent(id) + '/payments', !!order && !['PENDING', 'REJECTED'].includes(order.summary.status))
  return <div className="container commerce-page"><nav className="breadcrumbs" aria-label={t("Breadcrumb")}><a href="#/orders">{t("My orders")}</a><span>/</span><span>{t("Order details")}</span></nav>
    {loading ? <p role="status">{t("Checking your account…")}</p> : unavailable ? <><p role="alert">{t("We could not check your account.")}</p><button className="button secondary" onClick={refresh}>{t("Try again")}</button></> : !user ? <SignInPrompt /> : resource.error ? <div className="commerce-panel"><h1>{t("Unable to load this order")}</h1><p className="commerce-intro" role="alert">{t(resource.error)}</p><button className="button secondary" onClick={resource.retry}>{t("Try again")}</button></div> : !order ? <p role="status">{t("Loading your order…")}</p> : <><p className="eyebrow">{t(status(order.summary.status))}</p><h1>{order.decision ? (order.decision.decision === 'REJECTED' ? t("Request not accepted") : t("Order cancelled")) : order.summary.status === 'PENDING' ? t("Order request received") : t("Your order")}</h1><p className="order-reference">{order.summary.reference}</p>{order.decision ? <div className="commerce-panel order-decision" role="status"><h2>{t("Reason from the shop")}</h2><p>{order.decision.reason}</p><p className="commerce-muted">{order.decision.decision === 'REJECTED' ? t("Nothing is due for this request. You can contact the shop or send a new request.") : t("Any stock held for you has been released. If you paid anything for this order, contact the shop about a refund.")} {t('Decided {date}.', { date: date(order.decision.decidedAt) })}</p><a className="button secondary" href="#/shop">{t("Browse products")}</a></div> : <p className="commerce-intro">{order.summary.status === 'PENDING' ? t("The shop will review your request and contact you. Stock has not been reserved yet.") : t("Check your latest order status and contact the shop for any changes.")}</p>}<div className="commerce-grid"><section className="commerce-panel"><h2>{t("Your materials")}</h2><ul className="checkout-lines">{order.items.map(line => <li key={line.variantId}><span>{line.productName} · {line.variantLabel}<small>{line.quantity} {line.unit} × {money(line.unitPrice, order.summary.currency)} · {line.sku}</small></span><strong>{money(line.lineTotal, order.summary.currency)}</strong></li>)}</ul><h2>{order.summary.fulfilment === 'PICKUP' ? t("Shop pickup") : t("Delivery request")}</h2><dl className="contact-details"><div><dt>{t("Contact")}</dt><dd>{order.name} · {order.phone}</dd></div>{order.address && <div><dt>{t("Address")}</dt><dd>{order.address}</dd></div>}{order.instructions && <div><dt>{t("Instructions")}</dt><dd>{order.instructions}</dd></div>}<div><dt>{t("Branch")}</dt><dd>{t(branchName(order.summary.branch))}</dd></div><div><dt>{t("Requested")}</dt><dd>{date(order.summary.createdAt)}</dd></div></dl></section><aside className="commerce-panel commerce-summary"><h2>{t("Request summary")}</h2><Totals summary={order.summary} /><a className="button secondary" href="#/orders">{t('My orders & updates')}</a><a className="text-link" href="#/shop">{t("Continue shopping")}</a></aside></div>{!['PENDING', 'REJECTED'].includes(order.summary.status) && <PaymentsPanel orderId={order.summary.id} data={payments.data} error={payments.error} retry={payments.retry} />}</>}
  </div>
}
export function OrderReceiptPage({ id, receiptNumber }: { id: string; receiptNumber: string }) { const t = useT();
  const { user, loading } = useAuth()
  const order = usePrivateResource<Order>('/api/orders/' + encodeURIComponent(id), !!user)
  const payments = usePrivateResource<CustomerPayments>('/api/orders/' + encodeURIComponent(id) + '/payments', !!user)
  const receipt = payments.data?.receipts.find(r => r.receiptNumber === receiptNumber)
  return <div className="container commerce-page">{loading ? <p role="status">{t("Checking your account…")}</p> : !user ? <SignInPrompt />
    : order.error || payments.error ? <p role="alert">{t(order.error || payments.error || '')}</p> : !order.data || !payments.data ? <p role="status">{t("Loading receipt…")}</p>
    : !receipt ? <p role="alert">{t("This receipt is not on your order.")}</p>
    : <ReceiptSheet receipt={receipt} orderReference={order.data.summary.reference} customerName={order.data.name} balance={payments.data.balance} back={`#/orders/${id}`} />}</div>
}
