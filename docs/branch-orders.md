# Branch stock and order confirmation

Approved 04 November 2026: customers select Tarime (main branch) or Mwanza for both delivery and pickup. Stock is independent per branch. All pre-existing stock belongs to Tarime; Mwanza starts at zero. A missing balance row means zero, not stock inherited from Tarime.

V7 adds the branch directory, branch stock keys, nullable historical order branch, immutable confirmation records and per-order reservations. New API submissions require branch. Historical orders remain unassigned, with confirmation blocked pending a new customer request; no customer branch choice is invented.

Browsing and the cart show aggregate availability across branches. Checkout requires an explicit branch and refreshes the quote against that branch. Selection is saved locally for checkout continuity. The server validates it again on submission and includes it in the idempotency fingerprint. Submission still does not reserve stock.

Owner and Sales retain their existing access across both branches. Inventory and Delivery cannot confirm orders. Branch-restricted staff access has not been requested or implemented.

`POST /api/staff/orders/{id}/confirm` accepts `deliveryFee` and `note` (10–500 characters documenting who agreed, when and how, branch, materials, fulfilment and final total). CSRF and current staff account checks apply. Pickup fee must be zero. The first confirmation is an immutable revision preserving the original fee/total and recording the new fee/total, named staff member and time. Customer agreement is recorded by staff after contacting the customer; no external message is sent by the application.

Confirmation locks the pending order, rechecks published products and current prices, and reserves each line at the order's branch using conditional balance updates in a stable order. Insufficient stock, changed prices or unavailable products reject the whole transaction. Exact confirmation retries return the existing order; changed retry payloads return conflict. On-hand stock is unchanged. Reservations and the confirmation audit preserve quantities, branch, actor and time. A customer in-app notice is stored in the same transaction.

Existing prices, quantities and materials cannot be edited through this step. If prices change, the customer must review and submit a new request. Arbitrary revisions, cancellation/rejection, release, dispatch/collection, reservation expiry, stock receiving, interbranch transfers and finance remain separate workflows. Do not dispatch goods by editing balances manually.

Restart Full Stack - local to apply V7. No production server or user-owned running process was stopped by this change.
