# Staff access and pending-order review

Implemented against requirements v0.3 Appendix A, D2 and Appendix G. On 03 November 2026 the user approved a free authenticator (TOTP) plus single-use offline recovery codes. No paid identity or messaging service is used.

## First Owner setup

In IntelliJ's Terminal, from the project root:

```powershell
.\scripts\setup-owner.ps1
```

The script packages the current backend, starts/reuses the project-owned PostgreSQL instance, and opens private enrollment. It applies the development migrations and accepts only loopback `finishers_dev` with its dedicated user. It refuses enrollment once any staff account exists. No public bootstrap endpoint, real Owner account or default password is installed by development/tests.

Enter your own name, username and private password. Add the displayed manual key to a compatible free authenticator: time-based, SHA1, six digits, 30 seconds. Verify a current code, securely save the ten displayed recovery codes offline and acknowledge that they are saved. Cancellation or failed verification does not create an Owner.

Restart **Full Stack - local**, then open [staff sign-in](http://127.0.0.1:5173/#/staff/login), also linked in the storefront footer. Wait for a fresh code: the enrollment code has been consumed. Normal subsequent startup remains the existing IntelliJ Run configuration.

Enrollment generates a 32-byte MFA encryption key in ignored `backend/.env` if no effective key exists (`FPA_STAFF_MFA_KEY`), and enables the approved code policy (`FPA_STAFF_RECOVERY_ENABLED=true`). Environment settings override file settings; keep them consistent. Back up the key securely with the database. Codes recover the authenticator factor, not a forgotten password or lost server encryption key. Never share enrollment secrets through chat, logs, screenshots, Git or Run configurations. The agent has not enrolled a real user; testing uses synthetic accounts in `finishers_test`.

## Named accounts and permissions

The Owner creates Sales, Inventory and Delivery accounts and privately gives each staff member their unique username and temporary password. At first sign-in, a restricted session permits only profile/password-change/logout. A private password is required before opening staff tools.

The Owner can edit ordinary staff names, roles and active flags. Updates require the current revision. Role changes/disabling invalidate existing sessions on their next request. Password changes rotate session/CSRF state and revoke other sessions. Owner enrollment, promotion/deactivation and factor recovery are protected separately; ordinary role editing cannot create or weaken an Owner.

| Capability | Owner | Sales | Inventory | Delivery |
| --- | --- | --- | --- | --- |
| Own sign-in/password/sign-out | Yes; second factor | Yes | Yes | Yes |
| Pending orders and fulfilment contacts | Yes | Yes | Denied | Denied |
| Staff order notices/own read status | Yes | Yes | Denied | Denied |
| Ordinary staff account management | Yes | Denied | Denied | Denied |
| Customer-owned order/profile APIs | Denied in staff session | Denied | Denied | Denied |
| Confirm/adjust/reserve/dispatch/pay/refund | Not exposed yet | Not exposed yet | Not exposed yet | Not exposed yet |

Inventory/Delivery landing pages explain that preparation/assigned-job tools are forthcoming. They neither fetch pending-customer information nor claim there are zero jobs. Tests deny pending-order access for two distinct drivers; assignment-scoped workflow tests remain future work.

Owner/Sales search pending requests by reference/name, filter pickup/delivery and paginate oldest first. List projections omit phone/address/instructions; authorized detail views expose fulfilment contact and immutable requested material snapshots. Opening a request changes no status, price, stock, agreement or financial record.

Order submission saves a staff operational notice in the same transaction as the customer notice, items, order and audit. V5 backfills existing pending requests. Owner/Sales share the event stream; each named staff account has independent, idempotent read state. Latest 50 notices are available, displayed five at a time. External sending remains disabled.

## Security behavior

- Staff and customer principals/roles are distinct. The browser cookie represents one identity at a time; signing in as staff replaces a customer session and vice versa. Guest cart data is preserved.
- HttpOnly/SameSite=Lax cookies, 30-minute inactivity expiry, CSRF checks and production Secure cookies remain. Passwords use the existing salted PBKDF2 encoder.
- Backend filters check active flag, role, password-change state and session revision on requests; application services independently check permissions. Frontend identity refreshes on focus and staff-route changes. Staff secrets and identity are never saved in browser storage.
- TOTP follows [RFC 6238](https://www.rfc-editor.org/rfc/rfc6238): HMAC-SHA1, six digits, 30 seconds, one adjacent step of tolerance. Successful counters are persisted under a row lock to reject replay. AES-256-GCM encrypts secrets using a fresh nonce and the account UUID as authenticated data; missing/incorrect keys fail closed.
- Ten recovery codes each contain 160 random bits, are stored as SHA-256 hashes, expire after 365 days and are consumed atomically with password authentication. Reused/expired codes fail; usage is audited without values. Authenticator replacement, code renewal and lost-password recovery remain later protected workflows; there is no bypass if all factors are lost.
- Database limits allow 30 staff login attempts/IP and 10/normalized username per 15-minute window, and 10 password-change attempts/account/window. Wrong, missing, disabled and incorrect-code logins return generic 401. Session handling follows [Spring Security's explicit persistence/rotation guidance](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html).

## API

All writes require a fresh CSRF token from `/api/security/csrf`. Unlisted routes remain denied.

| Method/path | Access |
| --- | --- |
| `POST /api/staff/auth/login` | Public with CSRF: username/password/Owner code |
| `GET /api/staff/me` | Staff or password-change-only session; safe profile |
| `POST /api/staff/auth/password` | Staff/partial session plus current password |
| `POST /api/staff/auth/logout` | Staff/partial session |
| `GET/POST /api/staff/accounts` | Owner only |
| `PUT /api/staff/accounts/{id}` | Owner; revision/name/ordinary role/active flag |
| `GET /api/staff/orders?q=&fulfilment=ALL&page=0&size=10` | Owner/Sales; pending requests, page size 1–50 |
| `GET /api/staff/orders/{id}` | Owner/Sales; private request detail |
| `GET /api/staff/notifications` | Owner/Sales; latest 50 operational notices |
| `POST /api/staff/notifications/{id}/read` | Owner/Sales; mark own read state |

## Verification and remaining work

`StaffMfaTest` covers RFC vectors/replay/expiry windows, encrypted secret binding/tampering and recovery code hashes. `StaffAccessIT` checks every role, two drivers, public escalation attempts, session/CSRF rotation, temporary-password restrictions, role/deactivation revocation, Owner management, concurrent/expired recovery, throttles, private projections and independent read state against PostgreSQL.

Optional browser fixtures are generated only in guarded `finishers_test`:

```powershell
.\scripts\maven.ps1 verify '-Dfpa.test.browser-fixtures=true'
```

This writes ignored `.local/staff-browser-fixtures.json` after checking both current database/user. Use those synthetic MFA settings for isolated backend 18080 and set `FPA_STAFF_TEST_FIXTURES`, `FPA_BACKEND_PORT=18080` and `FPA_FRONTEND_PORT=15173` for Playwright. Staff browser cases skip without this explicit setup; they never use real Owner credentials. Export fresh fixtures before repeating tests that consume temporary passwords/MFA counters. See [verification](verification.md) for observed results.

This slice provides staff access and read-only review. Confirmation/revisions/customer agreement, atomic reservations, preparation/assigned delivery, finance, production Owner enrollment, recovery renewal and release operations remain separate work. No production readiness is claimed from local enrollment tooling.

## Confirmation update — 04 November 2026

Order review now includes branch-aware confirmation for Owner/Sales after recording customer agreement. The earlier read-only limitation is superseded by [branch orders](branch-orders.md). Inventory/Delivery remain excluded from confirmation.

