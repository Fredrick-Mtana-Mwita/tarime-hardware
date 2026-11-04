# Foundation HTTP contract

Named staff routes are implemented with explicit role grants; see [staff access and review API](staff-access.md). Customer authority remains separate. Unlisted staff/administrative operations remain denied.

Customer commerce routes are implemented; see [cart, order and in-app update contract](customer-orders.md). The original foundation description below is retained for context; commerce endpoints are additional explicit grants under the same deny-by-default policy.

Public catalogue routes have since been added; see [catalogue contract](catalogue.md). The denial rules below apply to all routes other than explicitly permitted foundation/catalogue GET endpoints.

Customer authentication routes have also been added: see [customer account contract](customer-accounts.md). Customer sessions grant their own profile, order/notification reads, protected sign-out, order submission and notice-read mutations; they do not grant staff or catalogue-write permissions.

Backend base address: `http://127.0.0.1:8080`. Browser development calls use same-origin `/api` through Vite on port 5173.

| Method and path | Access | Response |
| --- | --- | --- |
| `GET /api/system/info` | Public | Application name, `foundation` stage, zero-budget policy and disabled external-messaging flag |
| `GET /api/security/csrf` | Public session | `headerName` and token; initializes a CSRF-protected session |
| `GET /actuator/health` | Public, no details | Aggregate health |
| `GET /actuator/health/liveness` | Public, no details | Process liveness |
| `GET /actuator/health/readiness` | Public, no details | Readiness including database connectivity |

All routes other than the explicitly granted catalogue/foundation, customer/staff authentication, cart quote, customer commerce and role-scoped staff routes are denied by default. Protected anonymous reads return 401; writes without CSRF return 403. Named staff get only their implemented role capabilities. Further features must enforce record ownership/assignment in backend use cases.

Never treat a readable order reference as authorization. Customer order submission enforces ownership, server pricing, idempotency and one transaction for snapshots/audit/notification intent. Staff confirmation, stock/payment writes and their transition rules remain later work.

For production, serve frontend/backend through the same HTTPS origin or design explicit cookie/CORS/CSRF behavior. Vite's development proxy is not a production reverse proxy.
