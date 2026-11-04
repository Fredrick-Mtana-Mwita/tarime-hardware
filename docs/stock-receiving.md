# Branch stock receiving

Owner and Inventory staff can open Inventory and choose Tarime or Mwanza. Search by product, option or SKU, then choose Receive stock on the required option. Enter a positive quantity, supplier/delivery reference and receipt reason. Whole-unit products reject fractions; fractional units allow up to three decimal places.

Receiving adds to on-hand stock at the chosen branch. Reserved quantities and the other branch are unchanged. A branch without a balance starts at zero. Ordinary receiving does not edit prices, confirm orders, dispatch goods or perform count corrections/returns.

Receipt history shows the product/option/SKU/unit snapshot, quantity, source/reference, reason, staff identity, timestamp and balances immediately after receiving. Those balances are historical snapshots, not necessarily current availability. History is append-only. Corrections require a future linked adjustment workflow; receipts cannot be deleted or edited.

## Request and access contract

- `GET /api/staff/inventory?branch=TARIME&q=&page=0&size=12`: branch balances, available quantity, low-stock indicator and bounded matching counts. Unarchived unpublished options are visible to authorized inventory staff so stock can be received before public listing. A missing balance is zero; low stock means available is at or below the stored threshold (zero if absent).
- `GET /api/staff/inventory/receipts?branch=MWANZA&page=0&size=10`: newest-first receipt history for one branch.
- `POST /api/staff/inventory/receipts`: `{id, variantId, branch, quantity, sourceReference, reason}`. `id` is a client-generated UUID retained for retries. Server derives the actor from the authenticated session. CSRF is required.
- Owner and Inventory only; guests, customers, Sales, Delivery, temporary-password accounts and revoked sessions cannot receive or inspect inventory history through these endpoints.

One transaction serializes the receipt identity, validates the product unit, adds stock atomically, inserts the immutable receipt and appends an audit event. Identical retries by the same actor return the original receipt; a changed payload or actor with the same identity returns 409. A network failure keeps the form's original request for a safe retry. Before closing an uncertain request and creating another receipt, check history. Different request IDs are treated as different physical receipts; references are not unique because a delivery may contain multiple product options.

## Running locally

Restart IntelliJ's **Full Stack - local** configuration to compile the backend and apply Flyway V8 to `finishers_dev`. Sign in through `/#/staff/login`; Overview opens after sign-in. Inventory and receipt history links appear only for Owner and Inventory. Tarime and Mwanza stock remain separate. The preview video is a design reference; operational controls remain stationary and keyboard accessible.

V8 introduces `app.stock_receipts`, its branch/time index, and an append-only trigger. It does not seed staff, change existing stock, or reset reservations. Automated database/browser checks use the isolated `finishers_test` database and ports 18080/15173, never development inventory.
