import './finance.css'
import { money } from '../catalogue/format'
import { dateTime, howPaid, statusLabels } from './api'
import type { CustomerPayments } from './api'

export function PaymentsPanel({ orderId, data, error, retry }: { orderId: string; data?: CustomerPayments; error?: string; retry: () => void }) {
  if (error) return <section className="commerce-panel"><h2>Payments</h2><p role="alert">{error}</p><button className="text-link" onClick={retry}>Retry payments</button></section>
  if (!data) return <section className="commerce-panel"><h2>Payments</h2><p role="status">Loading payments…</p></section>
  const { balance, receipts } = data
  return <section className="commerce-panel customer-payments"><h2>Payments</h2>
    <p><span className="order-status">{statusLabels[balance.status]}</span></p>
    <dl className="order-totals">
      <div><dt>Paid</dt><dd>{money(balance.netPaid, balance.currency)}</dd></div>
      {balance.due > 0 && <div><dt>Balance due</dt><dd>{money(balance.due, balance.currency)}</dd></div>}
      {balance.refundDue > 0 && <div><dt>Refund owed to you</dt><dd>{money(balance.refundDue, balance.currency)}</dd></div>}
      {balance.refunded > 0 && <div><dt>Refunded</dt><dd>{money(balance.refunded, balance.currency)}</dd></div>}
    </dl>
    {!receipts.length ? <p className="commerce-muted">No payments yet. Pay the shop in cash or by mobile money once your order is confirmed; you will get a receipt here.</p>
      : <ul className="receipt-list">{receipts.map(r => <li key={r.id} className={r.reversed ? 'receipt-reversed' : ''}><span><strong>{money(r.amount, r.currency)}</strong> · {howPaid(r.method, r.provider)}{r.reversed && ' · cancelled'}<small>{r.receiptNumber} · {dateTime(r.recordedAt)}</small></span><a className="text-link" href={`#/orders/${orderId}/receipts/${r.receiptNumber}`}>View receipt</a></li>)}</ul>}
  </section>
}
