# Customer cart and order requests

Implemented against requirements v0.3: FR cart/order requirements, BR-01–07, Appendix C1/C3, D1, E3/E4 and revised in-app notification policy in Appendix G. The user selected **delivery and shop pickup** for checkout. No paid service or external notification provider is used.

## Customer journey

1. Open a product, select its published option and quantity, and add it to the cart. Whole/fractional units follow the database unit policy. Quote-only and out-of-stock options cannot be ordered online.
2. The guest cart stores public product/variant IDs, display names and quantities in `finishers.cart.v1` in browser localStorage. It contains no contact data, credentials or trusted prices. Browser storage errors leave the current tab usable with a warning. Other tabs receive cart updates.
3. Cart and checkout ask the backend for current prices and availability. A failed check prevents checkout; items remain removable. Prices are never taken from localStorage.
4. Checkout requires a customer session. Registration and login retain the cart and use an allowlisted `next=checkout` return route. The header Sign in link still opens a standalone tab; the account refreshes when a store tab regains focus.
5. Choose delivery or shop pickup, provide contact information and acknowledge that this is a request for review. Delivery needs an address. Pickup arrangements require shop confirmation before travel.
6. Submission creates one `PENDING` order with immutable contact/product/variant/SKU/unit/price/quantity snapshots, an audit entry, and a customer in-app notice in one transaction. The browser clears purchased quantities only after a successful response. A network failure retains the cart.
7. My account links to My orders and updates. Customers can view their own requests and mark their own notices as read. Order detail pages remain available if catalogue names/prices/publication later change.

## Price and stock semantics

- The backend loads only visible, fixed-price catalogue options and validates stock, quantity precision and unit rules. Maximum 50 distinct options, quantity greater than zero and at most 1,000,000, precision at most three decimal places; whole units reject fractions. Combining multiple currencies is rejected.
- Monetary values use PostgreSQL NUMERIC and Java BigDecimal. Each quantity × unit-price line is rounded HALF_UP to two decimal places, then summed. This follows the existing two-decimal catalogue storage convention; final tax/invoice/discount policy remains a launch decision. No invented tax or discount is applied.
- The request echoes **reviewed** unit prices and currency only to detect changes. Every amount saved is calculated from the database. A lower/higher tampered price, changed price, unavailable item or insufficient available stock is rejected. The customer must refresh and review their cart.
- A delivery request stores `delivery_fee = NULL` and `total = NULL` until a fee is agreed. The interface says “To be agreed”/“Awaiting delivery fee”. It does not silently treat delivery as free. Pickup records a zero pickup fee and the requested materials total; this remains a pending request, not a sale or final confirmation.
- **Submission never reserves or deducts stock.** Concurrent pending requests can request the same remaining stock. Staff confirmation must later revalidate prices/stock and reserve atomically; dispatch/collection must deduct and release reservations atomically. No staff confirmation, reservation expiry, fulfilment or payment operation is exposed by this slice.
- Payments and final balance are not fabricated on pending requests. The screen explains that payment arrangements and final agreement follow shop review. Manual receipt, balance and financial status projections will be added with the finance module.

## API

All mutations require a current session CSRF token from `GET /api/security/csrf`, including guest quote requests. Orders and notifications are private; successful responses carry Spring Security's cache-control headers. Anonymous callers receive 401, missing CSRF receives 403, and an unknown or another customer's order/notice receives 404.

| Method and path | Access and contract |
| --- | --- |
| `POST /api/cart/quote` | Guest/customer with CSRF; `{items:[{variantId,quantity}]}`; returns authoritative item snapshots, available quantities, currency and subtotal |
| `POST /api/orders` | Customer with CSRF and UUID `Idempotency-Key`; `{items:[{variantId,quantity,expectedPrice,currency}],fulfilment,name,phone,address,instructions}`; returns persisted order and summary, 201 |
| `GET /api/orders?page=0&size=10` | Current customer's summaries only; size 1–50, page 0–10000 |
| `GET /api/orders/{uuid}` | Current customer's immutable submission snapshot |
| `GET /api/notifications` | Latest 50 notices belonging to the current customer |
| `POST /api/notifications/{uuid}/read` | Mark current customer's notice read, idempotent; 204 |

400 indicates malformed input/quantity/currency; 409 requires reviewing prices/availability or indicates conflicting reuse of a retry key; 429 indicates the successful-submission limit (20/hour/customer). Unlisted staff and financial routes remain denied.

## Transactions and duplicate protection

The order service derives the customer from the authenticated principal. It locks the active customer row, checks `(customer_id, idempotency_key)` and the hash of normalized input, then obtains consistently ordered shared row locks on catalogue/unit/stock records during validation and persistence. All order/items/audit/notification-intent writes commit together. The transaction is bounded to 20 seconds; a lock conflict gives a retryable error without partial writes. This uses [Spring transaction boundaries](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html) and [PostgreSQL row locking](https://www.postgresql.org/docs/16/explicit-locking.html).

Identical simultaneous requests return the same order. Reusing a key with changed details returns 409. Matching retries are checked before stock/price validation and submission limits, so they can retrieve the original result even if the catalogue changed. The browser retains the same key for the same submitted details, including after a lost response. SessionStorage keeps only a payload hash and UUID key, never contact data. A browser storage restriction limits retry recovery across page reloads; in-memory recovery still covers the open form. Check My orders before starting a different request after an uncertain result.

The adapter writes in-app intent only; it makes no external calls. Intent storage failure rolls back the order. External provider failures must later be handled after commit. Named Owner/Sales staff now receive role-scoped operational notices with independent read state; see [staff access](staff-access.md). Further order/payment event routing remains part of those upcoming workflows.

## Run and verify

Restart **Full Stack - local** in IntelliJ to load the backend migration and routes. Existing development data is preserved. Vite reflects frontend source changes automatically. The isolated browser environment uses backend 18080 and frontend 15173 against `finishers_test`; it does not create customers/orders in `finishers_dev`.

Relevant checks: `CustomerOrdersIT` (real PostgreSQL), `store.test.ts` (corrupt cart/quantity boundaries), and `e2e/orders.spec.ts` (guest→registration→delivery, pickup after lost response, quantity/removal/retry/mobile). See [verification](verification.md) for observed results. Staff confirmation/reservation concurrency is a later acceptance gate and is not claimed by the pending-request tests.
