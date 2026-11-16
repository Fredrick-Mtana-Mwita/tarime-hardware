import './reports.css'
import type { FormEvent } from 'react'
import { money } from '../catalogue/format'
import { useWorkspaceData } from '../staff/useWorkspaceData'
import { presetRange, todayInShop } from './api'
import type { BalancesReport, Preset, SalesReport, StockReport } from './api'

type Tab = 'sales' | 'balances' | 'stock'
const tabsFor = (role: string): Tab[] => role === 'OWNER' ? ['sales', 'balances', 'stock'] : role === 'SALES' ? ['sales', 'balances'] : role === 'INVENTORY' ? ['stock'] : []
const tabLabels: Record<Tab, string> = { sales: 'Sales & money', balances: 'Balances', stock: 'Stock' }
const presets: [Preset, string][] = [['today', 'Today'], ['7d', 'Last 7 days'], ['30d', 'Last 30 days'], ['month', 'This month'], ['last-month', 'Last month']]
const qty = (n: number) => new Intl.NumberFormat('en-TZ', { maximumFractionDigits: 3 }).format(n)
const dayLabel = (iso: string) => new Intl.DateTimeFormat('en-TZ', { day: 'numeric', month: 'short', timeZone: 'UTC' }).format(new Date(iso + 'T00:00:00Z'))

export function ReportsPage({ role, query }: { role: string; query: URLSearchParams }) {
  const tabs = tabsFor(role)
  const tab = tabs.includes(query.get('tab') as Tab) ? query.get('tab') as Tab : tabs[0]
  const fallback = presetRange('7d')
  const from = query.get('from') || fallback.from, to = query.get('to') || fallback.to
  const link = (changes: Record<string, string>) => '#/staff/reports?' + new URLSearchParams({ tab, from, to, ...changes })
  if (!tab) return <p className="form-error" role="alert">Reports are not available for your role.</p>
  function custom(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const data = new FormData(event.currentTarget)
    window.location.assign(link({ from: String(data.get('from')), to: String(data.get('to')) }))
  }
  const range = new URLSearchParams({ from, to }).toString()
  return <>
    <div className="staff-page-heading"><div><p className="eyebrow">Reports</p><h1>{tabLabels[tab]}</h1>
      <p className="commerce-intro">Figures for Tarime, in Tanzania time. Each figure states which date it counts by.</p></div></div>
    {tabs.length > 1 && <nav className="report-tabs" aria-label="Reports">{tabs.map(t => <a key={t} href={link({ tab: t })} aria-current={t === tab ? 'page' : undefined}>{tabLabels[t]}</a>)}</nav>}
    {tab !== 'balances' && <form className="report-range" onSubmit={custom} aria-label="Date range">
      <div className="report-presets">{presets.map(([value, label]) => { const r = presetRange(value); const on = r.from === from && r.to === to
        return <a key={value} href={link(r)} aria-current={on ? 'true' : undefined}>{label}</a> })}</div>
      <label>From<input type="date" name="from" defaultValue={from} max={todayInShop()} required /></label>
      <label>To<input type="date" name="to" defaultValue={to} max={todayInShop()} required /></label>
      <button className="button secondary">Show</button>
    </form>}
    {tab === 'sales' ? <Sales key={range} range={range} /> : tab === 'balances' ? <Balances /> : <Stock key={range} range={range} />}
  </>
}

function Figure({ label, value, note, strong }: { label: string; value: string; note?: string; strong?: boolean }) {
  return <div className={strong ? 'report-figure strong' : 'report-figure'}><dt>{label}</dt><dd>{value}{note && <small>{note}</small>}</dd></div>
}
function Loading({ error, retry }: { error?: string; retry: () => void }) {
  return error ? <div className="commerce-panel"><p className="form-error" role="alert">{error}</p><button className="button secondary" onClick={retry}>Retry</button></div> : <p role="status">Loading the report…</p>
}

function Sales({ range }: { range: string }) {
  const report = useWorkspaceData<SalesReport>('/api/staff/reports/sales?' + range)
  const r = report.data
  if (!r) return <Loading error={report.error} retry={report.retry} />
  const c = r.currency
  const peak = Math.max(1, ...r.days.map(d => Math.max(d.completedSales, d.received)))
  return <>
    <div className="report-groups">
      <section className="commerce-panel"><h2>Order requests</h2><p className="report-basis">Counted by the day sent; decisions by the day decided.</p><dl>
        <Figure label="Requests sent" value={String(r.orders.submitted)} /><Figure label="Requested materials value" value={money(r.orders.requestedValue, c)} note="Before delivery fees" />
        <Figure label="Confirmed" value={String(r.orders.confirmed)} /><Figure label="Rejected" value={String(r.orders.rejected)} /><Figure label="Cancelled" value={String(r.orders.cancelled)} /></dl></section>
      <section className="commerce-panel"><h2>Completed sales</h2><p className="report-basis">Counted when goods are delivered or collected. Refunds by the day the Owner approved them.</p><dl>
        <Figure label="Orders completed" value={String(r.sales.completedOrders)} /><Figure label="Gross sales" value={money(r.sales.gross, c)} />
        <Figure label="Refunds" value={money(r.sales.refunds, c)} /><Figure label="Net sales" value={money(r.sales.net, c)} strong /></dl></section>
      <section className="commerce-panel"><h2>Money</h2><p className="report-basis">Counted by the day each receipt was saved; corrections and refunds by approval day.</p><dl>
        <Figure label="Cash received" value={money(r.money.cash, c)} /><Figure label="Mobile money received" value={money(r.money.mobileMoney, c)} note={`${r.money.receipts} receipt${r.money.receipts === 1 ? '' : 's'} in total`} />
        <Figure label="Cancelled receipts" value={money(r.money.reversed, c)} /><Figure label="Refunds paid" value={money(r.money.refunded, c)} /><Figure label="Net money in" value={money(r.money.net, c)} strong /></dl></section>
    </div>
    <section className="commerce-panel report-chart-panel"><div className="report-panel-head"><h2>Day by day</h2><a className="button secondary" href={'/api/staff/reports/sales.csv?' + range} download>Download CSV</a></div>
      <p className="report-legend"><span className="key sales" /> Completed sales <span className="key money" /> Money received</p>
      {r.days.length > 62 ? <p className="commerce-muted">Choose 62 days or fewer to see the chart. The CSV has every day.</p>
        : <div className="report-chart" role="img" aria-label={`Completed sales and money received per day from ${r.from} to ${r.to}`}>{r.days.map(d => <div key={d.date} className="report-bar-group" title={`${d.date}: sales ${money(d.completedSales, c)}, received ${money(d.received, c)}`}>
          <div className="report-bars"><span className="bar sales" style={{ height: `${(d.completedSales / peak) * 100}%` }} /><span className="bar money" style={{ height: `${(d.received / peak) * 100}%` }} /></div>
          {r.days.length <= 31 && <small>{dayLabel(d.date)}</small>}</div>)}</div>}
      <details className="report-table-toggle"><summary>Show the figures as a table</summary><div className="report-table-wrap"><table className="report-table"><thead><tr><th>Date</th><th>Requested</th><th>Completed sales</th><th>Received</th><th>Refunds</th></tr></thead>
        <tbody>{r.days.map(d => <tr key={d.date}><td>{d.date}</td><td>{money(d.requestedValue, c)}</td><td>{money(d.completedSales, c)}</td><td>{money(d.received, c)}</td><td>{money(d.refunded, c)}</td></tr>)}</tbody></table></div></details>
    </section>
    <section className="commerce-panel"><div className="report-panel-head"><h2>Top products</h2><a className="button secondary" href={'/api/staff/reports/products.csv?' + range} download>Download CSV</a></div>
      <p className="report-basis">From orders delivered or collected in this period, by value.</p>
      {!r.topProducts.length ? <p className="commerce-muted">No completed sales in this period.</p> : <div className="report-table-wrap"><table className="report-table"><thead><tr><th>Product</th><th>Quantity</th><th>Value</th></tr></thead>
        <tbody>{r.topProducts.map(p => <tr key={p.product + p.variant}><td>{p.product}<small>{p.variant}</small></td><td>{qty(p.quantity)} {p.unit}</td><td>{money(p.value, c)}</td></tr>)}</tbody></table></div>}
    </section>
  </>
}

function Balances() {
  const report = useWorkspaceData<BalancesReport>('/api/staff/reports/balances')
  const r = report.data
  if (!r) return <Loading error={report.error} retry={report.retry} />
  return <>
    <div className="report-groups"><section className="commerce-panel"><h2>As of now</h2><p className="report-basis">Approved records only. Pending corrections and refunds do not change these.</p><dl>
      <Figure label="Customers owe the shop" value={money(r.owedToShop, r.currency)} strong /><Figure label="Shop owes customers" value={money(r.owedToCustomers, r.currency)} note="Refunds due" /><Figure label="Orders with money outstanding" value={String(r.orders)} /></dl></section></div>
    <section className="commerce-panel"><div className="report-panel-head"><h2>Orders</h2><a className="button secondary" href="/api/staff/reports/balances.csv" download>Download CSV</a></div>
      {!r.rows.length ? <p className="commerce-muted">Nothing outstanding. Every confirmed order is settled.</p> : <div className="report-table-wrap"><table className="report-table"><thead><tr><th>Order</th><th>Status</th><th>Total</th><th>Paid</th><th>Owed</th><th>Days open</th></tr></thead>
        <tbody>{r.rows.map(b => <tr key={b.orderId}><td><a className="text-link" href={`#/staff/finance/${b.orderId}`}>{b.reference}</a><small>{b.customer} · {b.phone}</small></td><td>{b.status.toLowerCase().replaceAll('_', ' ')}</td>
          <td>{money(b.total, r.currency)}</td><td>{money(b.netPaid, r.currency)}</td><td className={b.refundDue > 0 ? 'owed-back' : ''}>{b.refundDue > 0 ? 'Refund ' + money(b.refundDue, r.currency) : money(b.due, r.currency)}</td><td>{b.ageDays}</td></tr>)}</tbody></table></div>}
    </section>
  </>
}

function Stock({ range }: { range: string }) {
  const report = useWorkspaceData<StockReport>('/api/staff/reports/stock?' + range)
  const r = report.data
  if (!r) return <Loading error={report.error} retry={report.retry} />
  return <>
    <div className="report-groups"><section className="commerce-panel"><h2>Stock at {r.branch === 'TARIME' ? 'Tarime' : r.branch}</h2><p className="report-basis">Levels are as of now. Movements count within the dates chosen.</p><dl>
      <Figure label="Products tracked" value={String(r.items)} /><Figure label="At or below low-stock level" value={String(r.lowStock)} strong={r.lowStock > 0} />
      <Figure label="Stock value at selling price" value={money(r.valueAtPrice, r.currency)} note="Not cost; cost prices are not recorded" /></dl></section></div>
    <section className="commerce-panel"><div className="report-panel-head"><h2>Products</h2><a className="button secondary" href={'/api/staff/reports/stock.csv?' + range} download>Download CSV</a></div>
      {!r.rows.length ? <p className="commerce-muted">No stock records yet.</p> : <div className="report-table-wrap"><table className="report-table"><thead><tr><th>Product</th><th>On hand</th><th>Reserved</th><th>Available</th><th>Received</th><th>Dispatched</th><th>Released</th><th>Value</th></tr></thead>
        <tbody>{r.rows.map(s => <tr key={s.variantId} className={s.lowStock ? 'low' : ''}><td>{s.product}<small>{s.variant} · {s.sku}{s.lowStock && <strong> · low (level {qty(s.threshold)})</strong>}</small></td>
          <td>{qty(s.onHand)}</td><td>{qty(s.reserved)}</td><td>{qty(s.available)} {s.unit}</td><td>{qty(s.received)}</td><td>{qty(s.dispatched)}</td><td>{qty(s.released)}</td><td>{money(s.valueAtPrice, r.currency)}</td></tr>)}</tbody></table></div>}
    </section>
  </>
}
