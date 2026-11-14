# Payments, receipts and refunds

Covers FR-19, FR-20, BR-08, C2 and C4 in the requirements (v0.5).

## Business rules approved on 13 November 2026

| Question (requirements) | Decision |
|---|---|
| Accepted methods (OD-11) | **Cash** and **mobile money** (M-Pesa, Mixx by Yas, Airtel Money, HaloPesa). No bank or cheque. |
| When payments may be recorded | **After the order is confirmed**, so the agreed total is fixed. Part-payments (deposits) are allowed from then on. |
| Overpayment | **Refused.** Staff can record at most the balance due and hand back any change. |
| When a sale counts (OD-10) | **On delivery or collection.** Money received is reported separately. |

## Who can do what

| Action | Owner | Sales | Inventory | Delivery |
|---|---|---|---|---|
| See balances, receipts, day summary | ✓ | ✓ | – | – |
| Record a payment (issues a receipt) | ✓ | ✓ | – | – |
| Ask to cancel a wrong receipt, or ask for a refund | ✓ | ✓ | – | – |
| Approve or reject those requests | ✓ | – | – | – |

A driver who collects cash hands it to Sales, who records it. Drivers cannot touch money records.

## Records

- **`app.payments`**: one row per receipt. It holds the receipt number (`RCT-2026-000123`), method, mobile-money service and
  transaction ID, amount, staff member and time. Rows cannot be changed or deleted, and the same mobile-money
  transaction can never be recorded twice.
- **`app.payment_adjustments`**: requests that start as `PENDING` and are decided once by the Owner.
  - **Reversal**: cancels one receipt that was saved by mistake (wrong order, wrong amount, money never received),
    always for the receipt's full amount. Staff then record the correct payment.
  - **Refund**: money given back to the customer in cash or by mobile money. For a mobile-money refund the Owner enters
    the transaction ID of the money sent when approving.
- **`app.order_balances`** (view): total, received, cancelled, refunded, net paid, still owed and owed back, all
  counted from approved records only. **A pending request never changes a balance.**

Balance labels: Unpaid, Part paid, Paid, Refund due (the shop owes the customer, for example a cancelled order with a
deposit), Partly refunded, Refunded.

Every money change locks the order row first, so two staff saving at once cannot push an order over its total.
Retrying a save with the same request ID changes nothing. Each change goes to `app.audit_events`.

## Customer side

- The order page shows the balance and every receipt. The customer can open each receipt and print it or save it as PDF.
- The customer gets an update for each payment, cancelled receipt and refund. Staff names and staff notes are not shown.

## Staff side

**Payments** in the staff menu (Owner and Sales) has:
- **Money for the day**: cash and mobile money received, cancelled receipts, refunds paid, net money in, and completed
  sales (delivered or collected that day). Days follow Tanzania time. Use the cash figure to check the till.
- **Waiting for the Owner**: pending requests. Only the Owner sees the Approve and Reject buttons.
- **Orders**: orders where money is still owed either way, or all confirmed orders, with search.
- **Each order**: receipts with Print and "Saved by mistake?", the payment form, the refund form and a history of
  requests and decisions. The staff order page links to it.

## Important limits

- **Receipts are not TRA fiscal (EFD) receipts.** Each receipt says so. If the shop must issue EFD receipts, it keeps
  using its EFD machine; tax handling is still open (OD-12).
- No online payments. The `source` column only allows `STAFF_RECORDED`. A future provider integration must add a verified
  source from signed provider callbacks, never from the browser (BR-08, FR-20).
- Returns of goods (inspection, restocking) are not built yet. A refund here only moves money.
- Payment time is the time it is saved. Record payments when they arrive so the day summary matches the till.
- Fuller reports (weekly or monthly, by method, unpaid balances) come with the reports work (E1).
