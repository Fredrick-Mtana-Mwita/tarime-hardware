import { PreviewFields } from '../../preview/PreviewFields'
import { isClientPreview } from '../../shared/preview'
import { NavigationGlow, WorkspaceTheme } from './WorkspaceMotion'
import { useWorkspaceDepth } from './useWorkspaceDepth'
import { Inventory } from './Inventory'
import { Overview } from './Overview'
import { ConfirmOrder } from './ConfirmOrder'
import { branchName } from '../orders/api'
import { useEffect, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { getJson } from '../../shared/api/http'
import { money } from '../catalogue/format'
import type { Order, OrderSummary } from '../orders/api'
import { staffError, staffSession, staffWrite } from './api'
import type { Staff, StaffAccount, StaffRole } from './api'

const roles: Record<StaffRole, string> = { OWNER: 'Owner', SALES: 'Sales', INVENTORY: 'Inventory', DELIVERY: 'Delivery' }
function useStaffData<T>(url: string) {
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState<{ key: string; data?: T; error?: string }>({ key: '' })
  const key = url + '|' + attempt
  useEffect(() => {
    const controller = new AbortController()
    void getJson(url, controller.signal).then(data => { if (!controller.signal.aborted) setState({ key, data: data as T }) })
      .catch((error: unknown) => { if (!controller.signal.aborted) setState({ key, error: staffError(error) }) })
    return () => controller.abort()
  }, [url, key])
  return { data: state.key === key ? state.data : undefined, error: state.key === key ? state.error : undefined, retry: () => setAttempt(n => n + 1) }
}
function ErrorBox({ message, retry }: { message: string; retry?: () => void }) {
  return <div className="form-error" role="alert"><p>{message}</p>{retry && <button className="text-link" onClick={retry}>Try again</button>}</div>
}
function StaffLogin({ onSignedIn }: { onSignedIn: (staff: Staff) => void }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return
    const data = new FormData(event.currentTarget); setBusy(true); setError('')
    try { onSignedIn(await staffWrite<Staff>('/api/staff/auth/login', { username: data.get('username'), password: data.get('password'), code: data.get('code') })); window.location.hash = '#/staff/overview' }
    catch (problem) { setError(staffError(problem)) } finally { setBusy(false) }
  }
  return <main className="staff-login-shell"><section className="staff-login-card"><p className="eyebrow">Tarime Hardware · Staff</p><h1>Staff sign-in</h1><p className="commerce-intro">Your named account. Your assigned tools.</p><form onSubmit={event => void submit(event)} aria-label="Staff sign-in" className="staff-form">{isClientPreview && <p className="client-preview-form-note">Design preview only. Staff sign-in is disabled.</p>}<PreviewFields><label htmlFor="staff-username">Staff username</label><input id="staff-username" name="username" autoComplete="username" required maxLength={60} autoCapitalize="none" spellCheck={false} /><label htmlFor="staff-password">Password</label><input id="staff-password" name="password" autoComplete="current-password" type="password" required maxLength={128} /><label htmlFor="staff-code">Authenticator or recovery code <span>(Owner only)</span></label><input id="staff-code" name="code" autoComplete="one-time-code" maxLength={80} spellCheck={false} aria-describedby="staff-code-help" /><small id="staff-code-help">Owner sign-in requires a fresh authenticator code. Recovery codes work only when enabled during private setup.</small>{error && <ErrorBox message={error} />}<button className="button primary" disabled={busy}>{busy ? 'Signing in…' : 'Sign in to staff workspace'}</button></PreviewFields></form><a className="text-link" href="#/shop">Return to the store</a><p className="staff-setup-note">Staff access is issued by the Owner. There is no public staff registration.</p></section></main>
}
function ChangePassword({ user, onChanged }: { user: Staff; onChanged: (staff: Staff) => void }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return
    const data = new FormData(event.currentTarget)
    if (data.get('newPassword') !== data.get('confirmPassword')) { setError('The new passwords do not match.'); return }
    setBusy(true); setError('')
    try { onChanged(await staffWrite<Staff>('/api/staff/auth/password', { currentPassword: data.get('currentPassword'), newPassword: data.get('newPassword') })) }
    catch (problem) { setError(staffError(problem)) } finally { setBusy(false) }
  }
  return <section className="commerce-panel staff-password"><p className="eyebrow">Secure your account</p><h1>{user.passwordChangeRequired ? 'Choose your private password' : 'Change password'}</h1><p className="commerce-intro">{user.passwordChangeRequired ? 'Change the temporary password before accessing staff tools.' : 'Changing your password signs out your other staff sessions.'}</p><form className="staff-form" onSubmit={event => void submit(event)} aria-label="Change staff password"><label htmlFor="staff-current">Current password</label><input id="staff-current" name="currentPassword" type="password" autoComplete="current-password" required maxLength={128} /><label htmlFor="staff-new">New password</label><input id="staff-new" name="newPassword" type="password" autoComplete="new-password" required minLength={12} maxLength={128} /><small>Use 12–128 characters and choose a different password.</small><label htmlFor="staff-confirm">Repeat new password</label><input id="staff-confirm" name="confirmPassword" type="password" autoComplete="new-password" required minLength={12} maxLength={128} />{error && <ErrorBox message={error} />}<button className="button primary" disabled={busy}>{busy ? 'Saving…' : 'Save private password'}</button></form></section>
}
interface PendingPage { items: { summary: OrderSummary; contactName: string; itemCount: number }[]; total: number; pendingCount: number; page: number; size: number }
interface StaffNotice { id: string; orderId: string; reference: string; createdAt: string; readAt: string | null }
const date = (value: string) => new Intl.DateTimeFormat('en-TZ', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
function StaffUpdates() {
  const resource = useStaffData<StaffNotice[]>('/api/staff/notifications')
  const [error, setError] = useState('')
  const [visible, setVisible] = useState(5)
  async function read(id: string) { try { await staffWrite(`/api/staff/notifications/${id}/read`, {}); resource.retry() } catch (problem) { setError(staffError(problem)) } }
  return <aside className="commerce-panel staff-updates"><h2>Order updates</h2><p className="commerce-muted">For Owner and Sales. Read status is private to your staff account.</p>{error && <ErrorBox message={error} />}{resource.error ? <ErrorBox message={resource.error} retry={resource.retry} /> : !resource.data ? <p role="status">Loading updates…</p> : !resource.data.length ? <p className="commerce-muted">New requests will appear here.</p> : <><ul>{resource.data.slice(0, visible).map(notice => <li key={notice.id}><a href={`#/staff/orders/${notice.orderId}`}>{!notice.readAt && <span className="order-status">New</span>}<strong>Order request received</strong><small>{notice.reference}</small></a>{!notice.readAt && <button className="text-link" onClick={() => void read(notice.id)}>Mark as read</button>}</li>)}</ul>{resource.data.length > visible && <button className="text-link" onClick={() => setVisible(n => n + 5)}>Show more updates</button>}</>}</aside>
}
function PendingOrders({ query }: { query: URLSearchParams }) {
  const q = query.get('q') || '', fulfilment = query.get('fulfilment') || 'ALL', page = Math.max(0, Number(query.get('page')) || 0)
  const params = new URLSearchParams({ q, fulfilment, page: String(page), size: '10' })
  const resource = useStaffData<PendingPage>('/api/staff/orders?' + params)
  function search(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const data = new FormData(event.currentTarget); window.location.hash = '#/staff?' + new URLSearchParams({ q: String(data.get('q') || '').trim(), fulfilment: String(data.get('fulfilment') || 'ALL') }) }
  function pageLink(next: number) { const changed = new URLSearchParams(params); changed.set('page', String(next)); return '#/staff?' + changed }
  return <><div className="staff-page-heading"><div><p className="eyebrow">Order desk</p><h1>Pending requests</h1><p className="commerce-intro">Review submitted materials and contact details. Oldest requests appear first.</p></div><button className="button secondary" onClick={resource.retry}>Refresh requests</button></div><div className="staff-review-note">Open a request to review its branch and record customer agreement before confirming.</div><form className="staff-filters" onSubmit={search}><label htmlFor="staff-order-search">Find a request<input key={q} id="staff-order-search" name="q" defaultValue={q} maxLength={100} placeholder="Reference or contact name" /></label><label htmlFor="staff-order-fulfilment">Fulfilment<select key={fulfilment} id="staff-order-fulfilment" name="fulfilment" defaultValue={fulfilment}><option value="ALL">All requests</option><option value="DELIVERY">Delivery</option><option value="PICKUP">Shop pickup</option></select></label><button className="button primary">Apply filters</button><a className="text-link" href="#/staff">Clear</a></form>
    <div className="staff-work-grid"><section aria-label="Pending order requests">{resource.error ? <ErrorBox message={resource.error} retry={resource.retry} /> : !resource.data ? <p role="status">Loading requests…</p> : <><p className="staff-result-count">{resource.data.total} matching · {resource.data.pendingCount} pending in total</p>{!resource.data.items.length ? <div className="commerce-panel"><h2>No matching pending requests</h2><p className="commerce-muted">Refresh later or clear the filters to review other requests.</p></div> : <div className="staff-request-list">{resource.data.items.map(({ summary: order, contactName, itemCount }) => <article className="commerce-panel" key={order.id}><div className="order-card-heading"><span className="order-status">Pending</span><time dateTime={order.createdAt}>{date(order.createdAt)}</time></div><h2>{contactName}</h2><a className="order-reference" href={`#/staff/orders/${order.id}`}>{order.reference}</a><div className="staff-request-meta"><span>{branchName(order.branch)} · {order.fulfilment === 'PICKUP' ? 'Shop pickup' : 'Delivery request'} · {itemCount} option{itemCount === 1 ? '' : 's'}</span><strong>{money(order.subtotal, order.currency)}</strong></div><a className="text-link" href={`#/staff/orders/${order.id}`}>Review request</a></article>)}</div>}<nav className="pagination" aria-label="Pending request pages">{page > 0 && <a className="button secondary" href={pageLink(page - 1)}>Previous</a>}<span>Page {page + 1}</span>{(page + 1) * 10 < resource.data.total && <a className="button secondary" href={pageLink(page + 1)}>Next</a>}</nav></>}</section><StaffUpdates /></div></>
}
function ReviewOrder({ id }: { id: string }) {
  const resource = useStaffData<Order>('/api/staff/orders/' + encodeURIComponent(id))
  const order = resource.data
  return <><a className="text-link" href="#/staff">Back to pending requests</a>{resource.error ? <ErrorBox message={resource.error} retry={resource.retry} /> : !order ? <p role="status">Loading request…</p> : <><div className="staff-page-heading"><div><p className="eyebrow">Order review · {order.summary.status.toLowerCase().replaceAll('_', ' ')}</p><h1>Request details</h1><p className="order-reference">{order.summary.reference}</p></div></div><div className="staff-work-grid"><section className="commerce-panel"><h2>Requested materials</h2><ul className="checkout-lines">{order.items.map(item => <li key={item.variantId}><span>{item.productName} · {item.variantLabel}<small>{item.sku} · {item.quantity} {item.unit} × {money(item.unitPrice, order.summary.currency)}</small></span><strong>{money(item.lineTotal, order.summary.currency)}</strong></li>)}</ul><h2>Fulfilment contact</h2><dl className="contact-details"><div><dt>Name</dt><dd>{order.name}</dd></div><div><dt>Phone</dt><dd>{order.phone}</dd></div><div><dt>Branch</dt><dd>{branchName(order.summary.branch)}</dd></div><div><dt>Requested method</dt><dd>{order.summary.fulfilment === 'PICKUP' ? 'Shop pickup' : 'Delivery'}</dd></div>{order.address && <div><dt>Address</dt><dd>{order.address}</dd></div>}{order.instructions && <div><dt>Instructions</dt><dd>{order.instructions}</dd></div>}<div><dt>Submitted</dt><dd>{date(order.summary.createdAt)}</dd></div></dl></section><aside className="commerce-panel commerce-summary"><h2>Submitted estimate</h2><dl><div><dt>Materials subtotal</dt><dd>{money(order.summary.subtotal, order.summary.currency)}</dd></div><div><dt>{order.summary.fulfilment === 'PICKUP' ? 'Pickup fee' : 'Delivery fee'}</dt><dd>{order.summary.deliveryFee === null ? 'To be agreed' : money(order.summary.deliveryFee, order.summary.currency)}</dd></div></dl><p className="commerce-muted">These are the customer-submitted snapshots. Confirmation must recheck current prices and availability.</p><ConfirmOrder order={order} onConfirmed={resource.retry} /></aside></div></>}</>
}
function StaffAccountEditor({ account, refresh }: { account: StaffAccount; refresh: () => void }) {
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy || account.profile.role === 'OWNER') return
    const data = new FormData(event.currentTarget); setBusy(true); setError('')
    try { await staffWrite('/api/staff/accounts/' + account.profile.id, { revision: account.revision, name: data.get('name'), role: data.get('role'), active: data.get('active') === 'on' }, 'PUT'); refresh() }
    catch (problem) { setError(staffError(problem)) } finally { setBusy(false) }
  }
  const owner = account.profile.role === 'OWNER'
  return <article className="commerce-panel staff-account"><h3>{account.profile.name}</h3><p className="commerce-muted">{account.profile.username} · {roles[account.profile.role]} · {account.active ? 'Active' : 'Disabled'}{account.profile.passwordChangeRequired ? ' · Password setup required' : ''}</p>{owner ? <p className="commerce-muted">Owner enrollment and recovery are protected separately from ordinary role editing.</p> : <form className="staff-account-form" onSubmit={event => void submit(event)}><label htmlFor={'name-' + account.profile.id}>Name<input id={'name-' + account.profile.id} name="name" defaultValue={account.profile.name} required minLength={2} maxLength={100} /></label><label htmlFor={'role-' + account.profile.id}>Role<select id={'role-' + account.profile.id} name="role" defaultValue={account.profile.role}><option value="SALES">Sales</option><option value="INVENTORY">Inventory</option><option value="DELIVERY">Delivery</option></select></label><label className="staff-active"><input type="checkbox" name="active" defaultChecked={account.active} /> Active account</label><button className="button secondary" disabled={busy}>{busy ? 'Saving…' : 'Save access'}</button></form>}{error && <ErrorBox message={error} />}</article>
}
function StaffAccountsPage() {
  const resource = useStaffData<StaffAccount[]>('/api/staff/accounts')
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [message, setMessage] = useState('')
  const [filter, setFilter] = useState('')
  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return
    const form = event.currentTarget, data = new FormData(form); setBusy(true); setError(''); setMessage('')
    try { await staffWrite('/api/staff/accounts', { name: data.get('name'), username: data.get('username'), role: data.get('role'), temporaryPassword: data.get('temporaryPassword') }); form.reset(); setMessage('Staff account created. Share the username and temporary password privately. The staff member must choose a private password at first sign-in.'); resource.retry() }
    catch (problem) { setError(staffError(problem)) } finally { setBusy(false) }
  }
  return <><p className="eyebrow">Owner controls</p><h1>Staff accounts</h1><p className="commerce-intro">Named access with clear responsibilities. Role changes and disabling accounts invalidate existing sessions.</p><section className="commerce-panel staff-create"><h2>Create a staff account</h2><form className="staff-form" onSubmit={event => void create(event)} aria-label="Create staff account"><label htmlFor="new-staff-name">Full name</label><input id="new-staff-name" name="name" required minLength={2} maxLength={100} /><label htmlFor="new-staff-username">Username</label><input id="new-staff-username" name="username" required minLength={3} maxLength={60} pattern="[a-zA-Z0-9][a-zA-Z0-9._-]{2,59}" autoComplete="off" spellCheck={false} /><label htmlFor="new-staff-role">Role</label><select id="new-staff-role" name="role"><option value="SALES">Sales</option><option value="INVENTORY">Inventory</option><option value="DELIVERY">Delivery</option></select><label htmlFor="new-staff-password">Temporary password</label><input id="new-staff-password" name="temporaryPassword" type="password" required minLength={12} maxLength={128} autoComplete="new-password" /><small>12–128 characters. Give it privately to this staff member; do not reuse a shared staff password.</small>{error && <ErrorBox message={error} />}<button className="button primary" disabled={busy}>{busy ? 'Creating…' : 'Create staff account'}</button></form>{message && <p className="staff-success" role="status">{message}</p>}</section><h2 className="staff-accounts-title">Named team</h2><label className="staff-team-search" htmlFor="staff-team-search">Find staff by name or username<input id="staff-team-search" type="search" value={filter} onChange={event => setFilter(event.target.value)} maxLength={100} /></label>{resource.error ? <ErrorBox message={resource.error} retry={resource.retry} /> : !resource.data ? <p role="status">Loading staff accounts…</p> : <div className="staff-account-list">{resource.data.filter(account => (account.profile.name + ' ' + account.profile.username).toLowerCase().includes(filter.toLowerCase())).map(account => <StaffAccountEditor key={account.profile.id + ':' + account.revision} account={account} refresh={resource.retry} />)}</div>}</>
}
function StaffLayout({ user, onLogout, children, path }: { path: string; user: Staff; onLogout: () => Promise<void>; children: ReactNode }) {
  const depth = useWorkspaceDepth()
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  async function logout() { if (busy) return; setBusy(true); try { await onLogout() } catch (problem) { setError(staffError(problem)) } finally { setBusy(false) } }
  return <div className="staff-shell" ref={depth}><header className="staff-header"><a href="#/staff/overview" className="staff-brand">TARIME HARDWARE <small>STAFF WORKSPACE</small></a><div><WorkspaceTheme /><strong>{user.name}</strong><span>{roles[user.role]}</span><button className="button secondary" disabled={busy} onClick={() => void logout()}>{busy ? 'Signing out…' : isClientPreview ? 'Exit preview' : 'Sign out'}</button></div></header><div className="staff-frame"><nav className="staff-nav" aria-label="Staff workspace"><NavigationGlow path={path} /><div className="sidebar-brand">TARIME<small>HARDWARE</small></div><span className="sidebar-label">WORKSPACE</span>{!user.passwordChangeRequired && <><a href="#/staff/overview" aria-current={path === '/staff/overview' ? 'page' : undefined}><svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round"><path d="M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z" /></svg><span>Overview</span></a>{['OWNER', 'INVENTORY'].includes(user.role) && <><a href="#/staff/inventory" aria-current={path === '/staff/inventory' ? 'page' : undefined}><svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round"><path d="M3 7l9-5 9 5v10l-9 5-9-5z M3 7l9 5 9-5 M12 12v10" /></svg><span>Inventory</span></a><a href="#/staff/receipts" aria-current={path === '/staff/receipts' ? 'page' : undefined}><svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round"><path d="M5 3h14v18l-3-2-4 2-4-2-3 2z M8 8h8 M8 12h6" /></svg><span>Stock receipts</span></a></>}{['OWNER', 'SALES'].includes(user.role) && <a href="#/staff" aria-current={path === '/staff' || path.startsWith('/staff/orders/') ? 'page' : undefined}><svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round"><path d="M4 5h16v15H4z M8 9h8 M8 13h5" /></svg><span>Pending requests</span></a>}{user.role === 'OWNER' && <a href="#/staff/accounts" aria-current={path === '/staff/accounts' ? 'page' : undefined}><svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round"><path d="M16 20v-2a5 5 0 00-10 0v2 M11 4a4 4 0 100 8 4 4 0 000-8z" /></svg><span>Staff accounts</span></a>}<a href="#/staff/password" aria-current={path === '/staff/password' ? 'page' : undefined}><svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round"><path d="M5 10h14v11H5z M8 10V7a4 4 0 018 0v3" /></svg><span>Change password</span></a></>}<a href="#/shop"><svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round"><path d="M3 10l2-6h14l2 6z M5 10v11h14V10 M9 21v-7h6v7" /></svg><span>Storefront</span></a></nav><main id="main" tabIndex={-1} className="staff-content">{error && <ErrorBox message={error} />}<div key={path} className="workspace-page">{children}</div></main></div></div>
}
export function StaffApp({ path, query }: { path: string; query: URLSearchParams }) {
  const [state, setState] = useState<{ loading: boolean; user: Staff | null; error?: string }>({ loading: true, user: null })
  const [attempt, setAttempt] = useState(0)
  const revision = useRef(0)
  useEffect(() => {
    const focus = () => setAttempt(n => n + 1)
    window.addEventListener('focus', focus); return () => window.removeEventListener('focus', focus)
  }, [])
  useEffect(() => {
    const controller = new AbortController(), current = revision.current
    void staffSession(controller.signal).then(user => { if (!controller.signal.aborted && current === revision.current) setState({ loading: false, user }) })
      .catch((problem: unknown) => { if (!controller.signal.aborted && current === revision.current) setState({ loading: false, user: null, error: staffError(problem) }) })
    return () => controller.abort()
  }, [attempt, path])
  function signedIn(user: Staff) { revision.current += 1; setState({ loading: false, user }) }
  async function logout() { if (isClientPreview) { window.location.hash = '#/'; return } await staffWrite('/api/staff/auth/logout', {}); revision.current += 1; setState({ loading: false, user: null }); window.location.hash = '#/staff/login' }
  if (isClientPreview && path === '/staff/login') return <StaffLogin onSignedIn={signedIn} />
  if (state.loading) return <main className="load-state" role="status">Checking staff access…</main>
  if (state.error) return <main className="staff-login-shell"><ErrorBox message={state.error} retry={() => setAttempt(n => n + 1)} /></main>
  if (!state.user) return <StaffLogin onSignedIn={signedIn} />
  const user = state.user, reviewer = ['OWNER', 'SALES'].includes(user.role)
  return <StaffLayout key={user.id + ':' + user.role} user={user} onLogout={logout} path={path}>{user.passwordChangeRequired || path === '/staff/password' ? <ChangePassword key={user.id + ':' + user.passwordChangeRequired} user={user} onChanged={updated => { signedIn(updated); window.location.hash = '#/staff/overview' }} /> : path === '/staff/overview' ? <Overview user={user} /> : path === '/staff/inventory' || path === '/staff/receipts' ? ['OWNER', 'INVENTORY'].includes(user.role) ? <Inventory key={path + query.get('branch')} query={query} history={path === '/staff/receipts'} /> : <ErrorBox message="Stock receiving is available to Owner and Inventory staff only." /> : path === '/staff/accounts' ? user.role === 'OWNER' ? <StaffAccountsPage /> : <ErrorBox message="Staff account management is available to the Owner only." /> : path.startsWith('/staff/orders/') ? reviewer ? <ReviewOrder key={path} id={path.split('/')[3]} /> : <ErrorBox message="Your role cannot access customer order requests." /> : path === '/staff' || path === '/staff/login' ? reviewer ? <PendingOrders query={query} /> : <Overview user={user} /> : <ErrorBox message="This staff page is not available." />}</StaffLayout>
}
