# Customer account slice

Customer registration, phone/password sign-in, sign-out, session restoration and a private account page are implemented. Restart **Full Stack - local** once to apply `V3__customer_accounts.sql` and load the new security routes. Use **Sign in** in the header, then **Create an account** for a new customer. No preset customer or administrator password is installed.

## API and session contract

| Operation | Access | Result |
| --- | --- | --- |
| `GET /api/security/csrf` | Public session | Fresh masked CSRF token and expected header name |
| `POST /api/auth/register` | Public, CSRF-protected and rate-limited | Creates a customer and signs it in; 201 with safe profile |
| `POST /api/auth/login` | Public, CSRF-protected and rate-limited | Authenticates phone/password; 200 with safe profile |
| `GET /api/auth/me` | Customer session | Current principal's profile only; 401 when anonymous |
| `POST /api/auth/logout` | Customer session, CSRF-protected | Invalidates the session; 204 |

Registration accepts name, phone, optional email and password. Name length is 2-100 characters; password length is 12-128 characters. Passwords are not trimmed. Tanzania local formats are normalized to E.164; international numbers must include the country code. Phone uniqueness is enforced in PostgreSQL, including simultaneous/equivalent-format registrations. Email is normalized when supplied and is not currently a login identifier.

Public payload fields such as `role`, `active` or `contactVerified` do not grant authority. The customer table has no staff-role column. Every authenticated customer receives only `ROLE_CUSTOMER`; staff endpoints and catalogue writes remain forbidden. Owner access and its required second factor are not implemented by this slice.

Passwords use Spring Security's salted PBKDF2 encoder behind a delegating format identifier. Safe profile responses contain ID, name, phone, optional email and contact-verification status, never hashes or raw passwords. Credential/request objects redact their string representations. Registration writes an audit event without contact/password details in the same database transaction.

Sessions use an HttpOnly, SameSite=Lax cookie, cookie-only tracking and a 30-minute inactivity timeout. Production configuration also requires Secure cookies. Login/registration rotates the session ID and clears the old CSRF token. The frontend fetches a fresh token for each explicit mutation and keeps credentials/tokens out of localStorage. Login/logout requests are never silently retried. Disabling a customer account revokes its existing session on its next request, not just its next sign-in.

CSRF protection remains enabled for all state-changing requests. Missing/stale tokens return 403. Wrong/disabled/nonexistent credentials return a generic 401 response. Conflicting registration returns a generic 409. Validation does not echo rejected password values. Public catalogue GET requests remain available without signing in.

## Abuse controls and deployment limits

Authentication attempts are counted atomically in PostgreSQL by operation and hashed direct client address, in fixed 15-minute windows. Defaults are 30 login and 10 registration attempts, configurable through `fpa.auth.login-limit` and `fpa.auth.registration-limit`. Responses use 429 and Retry-After. Buckets older than one hour are cleaned during authentication operations. The implementation does not trust arbitrary X-Forwarded-For headers; production reverse-proxy address handling and appropriate limits must be configured before launch.

Session storage is currently process-local. Restarting the backend signs customers out. Shared sessions/recovery behavior need a deployment decision if multiple instances are introduced. This preview does not establish public production readiness.

## Explicitly unfinished

Customer contact verification remains false for new accounts; no external verification is simulated. Channel selection and customer password recovery remain later work. Named staff/Owner second-factor access is now separate and implemented: see [staff setup](staff-access.md). Registered, active, signed-in customers can submit pending requests through [checkout and My Orders](customer-orders.md), without implying verified contact or shop approval.

## Evidence

Local verification on 03 November 2026 passed 34 tests: 5 backend unit/architecture tests, 13 PostgreSQL/HTTP integration tests, 7 frontend unit tests, and 9 Chromium journeys. ESLint and the TypeScript/Vite production build passed. Desktop account and 390px phone registration screenshots were visually inspected.

Backend tests exercise CSRF requirements, safe registration with attempted role escalation, salted hashes, normalized duplicates, session rotation, logout/login persistence, immediate deactivation, two separate customers, forbidden catalogue/staff access, and atomic rate-limit behavior. Phone-format unit tests cover local/international inputs and malformed values. Authentication tests reset throttle buckets only in the guarded test database for repeatability.

Frontend tests cover safe-profile parsing, cookie/CSRF requests, anonymous versus unavailable sessions, registration/sign-in/out/reload, bad credentials, phone layout/password visibility, and failed registration/retry. Browser tests use synthetic data. The local validation run points the temporary backend at the isolated test database; your IntelliJ server and its development accounts are left untouched.

Implementation follows [Spring Security session persistence](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html) and [password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).
