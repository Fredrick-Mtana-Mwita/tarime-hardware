import { AnimatedNumber } from './WorkspaceMotion'
import type { Staff } from './api'
import { WorkspaceError } from './Inventory'
import { useWorkspaceData } from './useWorkspaceData'
import { branchName } from '../orders/api'
import type { OrderSummary } from '../orders/api'
import { money } from '../catalogue/format'

function OrderOverview() {
  const resource = useWorkspaceData<{ pendingCount: number; items: { summary: OrderSummary; contactName: string; itemCount: number }[] }>('/api/staff/orders?size=5')
  return <>{resource.error ? <WorkspaceError message={resource.error} retry={resource.retry} /> : !resource.data ? <p role="status">Loading order overview…</p> : <>
    <div className="workspace-stats"><article className="amber-stat"><span>Pending requests</span><strong><AnimatedNumber value={resource.data.pendingCount} /></strong><small>Awaiting shop review</small></article><article><span>Branches</span><strong>02</strong><small>Tarime · Mwanza</small></article><article><span>Stock reservation</span><strong>On confirmation</strong><small>After customer agreement</small></article></div>
    <section className="commerce-panel overview-orders"><div className="staff-page-heading"><div><h2>Orders needing attention</h2><p className="commerce-muted">Oldest pending requests first.</p></div><a className="text-link" href="#/staff">View all requests</a></div>
      {!resource.data.items.length ? <p>No pending requests. New customer requests will appear here.</p> : <ul>{resource.data.items.map(({ summary: order, contactName, itemCount }) => <li key={order.id}><div><strong>{contactName}</strong><small>{branchName(order.branch)} · {order.fulfilment === 'PICKUP' ? 'Shop pickup' : 'Delivery'} · {itemCount} options</small></div><span className="order-status">Pending</span><strong>{money(order.subtotal, order.currency)}</strong><a className="text-link" href={`#/staff/orders/${order.id}`}>Review<span className="sr-only"> {order.reference}</span></a></li>)}</ul>}
    </section>
  </>}</>
}
export function Overview({ user }: { user: Staff }) {
  const reviewer = ['OWNER', 'SALES'].includes(user.role), stock = ['OWNER', 'INVENTORY'].includes(user.role)
  return <><div className="staff-page-heading"><div><p className="eyebrow">Your business, at a glance</p><h1>Ready for the next build.</h1><p className="commerce-intro">Welcome, {user.name}. A clear view of the work ahead.</p></div>{reviewer && <a href="#/staff" className="button primary">Review orders</a>}</div>
    {reviewer && <OrderOverview />}
    {stock && <section className="commerce-panel overview-branches"><h2>Branch inventory</h2><p className="commerce-muted">Review availability and record materials received at the right branch.</p><div><a href="#/staff/inventory?branch=TARIME"><span>Main branch</span><strong>Tarime</strong><small>View stock and receive materials →</small></a><a href="#/staff/inventory?branch=MWANZA"><span>Second branch</span><strong>Mwanza</strong><small>View stock and receive materials →</small></a></div><a className="text-link" href="#/staff/receipts">Browse receipt history</a></section>}
    {user.role === 'DELIVERY' && <section className="commerce-panel"><h2>Delivery workspace</h2><p>Assigned delivery jobs will appear here when the delivery workflow is available.</p></section>}
  </>
}
