# Order rejection and cancellation

Covers FR-14, BR-04, BR-07 and C1 in the requirements (v0.5).

## Who and when

| Action | Who | Allowed from | Result |
|---|---|---|---|
| Reject | Owner, Sales | `PENDING` | `REJECTED`. Nothing was reserved, so nothing is released. |
| Cancel | Owner, Sales | `PENDING`, `CONFIRMED`, `PREPARING`, `READY_FOR_PICKUP`, `READY_FOR_DELIVERY` | `CANCELLED`. Any stock still held for the order goes back on sale. |

After `OUT_FOR_DELIVERY`, `COLLECTED` or `DELIVERED` the goods have left the shop and the stock was already deducted, so
cancellation is refused. That case belongs to the returns workflow, which is not built yet.
Inventory and Delivery staff get `403`.

## API

- `POST /api/staff/orders/{id}/reject` with `{"reason": "..."}`
- `POST /api/staff/orders/{id}/cancel` with `{"reason": "..."}`

The reason must be 10 to 500 characters. **The customer sees it**, in the order page and in their update.
Both return the order, which now carries `decision: {decision, reason, decidedAt}` (also on `GET /api/orders/{id}`).

Retrying the same decision with the same reason returns `200` and changes nothing. A different decision or reason
for an order that already has one returns `409`.

## What happens, in one transaction

1. The order row is locked. Confirmation and handover lock the same row first, so they cannot interleave.
2. For each reservation with stock still held: `stock_balances.reserved` goes down by that amount, a row is added to
   `app.reservation_releases` (quantity, reason, order, actor, time) and the reservation's `remaining` becomes 0.
   If the balance does not hold enough reserved stock, nothing changes and the request returns `409`.
3. `app.order_decisions` records the decision, the status it came from, the reason, the actor and the time.
4. The order status changes, and the decision is written to `app.audit_events` (`ORDER_REJECTED` / `ORDER_CANCELLED`).
5. The customer gets an update with the reason and next steps. Owner and Sales get a staff update.

`order_decisions` and `reservation_releases` are append-only (a trigger refuses updates and deletes).

## Closed branch (Mwanza)

V14 relaxes the closed-branch trigger in two narrow ways so old Mwanza orders can be closed out: an order may change
only its status to `REJECTED` or `CANCELLED`, and a balance may only reduce `reserved` with `on_hand` unchanged.
Every other change at a closed branch is still refused.

## In the app

- Staff order page: "Reject this request…" for pending requests, "Cancel this order…" for confirmed ones, with a
  required reason and a check that the customer will see it.
- Fulfilment: each job not yet dispatched links to its order page for cancelling.
- Staff updates show "Request rejected" and "Order cancelled".
- Customer order page: the heading, the shop's reason and next steps.

## Not decided yet

- Reservation expiry (OD-04): no automatic expiry. Nothing is released except by an explicit cancellation.
- Payments are not recorded in the system yet, so the cancellation message tells the customer to contact the shop
  about any refund. When payments arrive, cancelling a paid order should create a refund task.
- Inventory staff have no updates feed; they see the released stock in availability. Add one if the shop wants it.
- Customers cannot cancel their own requests; the requirements give this to Owner and Sales only.
