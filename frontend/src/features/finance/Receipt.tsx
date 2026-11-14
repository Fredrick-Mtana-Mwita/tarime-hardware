import { money } from '../catalogue/format'
import { dateTime, howPaid } from './api'
import type { Balance, Receipt } from './api'

/** A printable payment receipt. It acknowledges money received; it is not a TRA fiscal (EFD) receipt. */
export function ReceiptSheet({ receipt, orderReference, customerName, balance, back }: { receipt: Receipt; orderReference: string; customerName: string; balance: Balance; back: string }) {
  return <div className="receipt-page">
    <div className="receipt-actions"><a className="text-link" href={back}>Back</a><button className="button primary" onClick={() => window.print()}>Print or save as PDF</button></div>
    <article className="receipt-sheet" aria-label={`Receipt ${receipt.receiptNumber}`}>
      <header><strong>TARIME HARDWARE</strong><span>Tarime (main branch)</span></header>
      <h1>Payment receipt</h1>
      {receipt.reversed && <p className="receipt-void" role="status">Cancelled — this receipt was recorded in error and no longer counts.</p>}
      <dl>
        <div><dt>Receipt number</dt><dd>{receipt.receiptNumber}</dd></div>
        <div><dt>Date</dt><dd>{dateTime(receipt.recordedAt)}</dd></div>
        <div><dt>Order</dt><dd>{orderReference}</dd></div>
        <div><dt>Customer</dt><dd>{customerName}</dd></div>
        <div><dt>Paid by</dt><dd>{howPaid(receipt.method, receipt.provider)}{receipt.reference && <> · {receipt.reference}</>}</dd></div>
        {receipt.recordedBy && <div><dt>Received by</dt><dd>{receipt.recordedBy}</dd></div>}
      </dl>
      <p className="receipt-amount"><span>Amount received</span><strong>{money(receipt.amount, receipt.currency)}</strong></p>
      <dl className="receipt-balance">
        <div><dt>Order total</dt><dd>{money(balance.total, balance.currency)}</dd></div>
        <div><dt>Paid so far</dt><dd>{money(balance.netPaid, balance.currency)}</dd></div>
        <div><dt>Balance due</dt><dd>{money(balance.due, balance.currency)}</dd></div>
      </dl>
      <footer>Payment acknowledgement from Tarime Hardware. This is not a TRA fiscal (EFD) receipt. Balances are as at printing.</footer>
    </article>
  </div>
}
