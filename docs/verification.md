# Local verification

## Live dashboard motion correction — 04 November 2026

- User clarified that the reference motion belongs in the working dashboard as well as the preview video. Added dark glass surfaces, amber/neutral depth decorations, pointer-responsive perspective cards, staggered entrances, animated totals, page/dialog transitions, moving sidebar selection and a circular light/dark reveal. Theme preference persists locally. No animation dependency, paid service or backend change was added.
- Two focused Chromium checks passed: live transforms, reset on pointer exit, theme reveal/persistence, modal focus and Escape, mobile/tablet widths, no page errors; and OS reduced-motion behavior with no active animations. These visual tests use clearly isolated stubbed API data; business behavior is checked separately against the test backend.
- Production build, ESLint and all 12 frontend unit tests passed. Visual review caught metric spans inheriting the label font size; corrected. Long-page perspective could briefly expand horizontal scroll bounds; the workspace now clips horizontal animation overflow.
- Circular reveal uses the native [View Transition API](https://developer.mozilla.org/en-US/docs/Web/API/Document/startViewTransition) with an immediate theme update when unsupported or reduced motion is requested. Screenshots retained in `.local/motion-qa`.
- The real PostgreSQL receiving browser journey also passed with live motion enabled: keyboard opening/closing, mobile dialog, deliberately lost committed response, idempotent retry, receipt history, branch isolation and widths 320/768/1024/1440. Temporary test servers were stopped after verification.

## Staff workspace and branch receiving — 04 November 2026

- Passed 8 backend unit/architecture tests, 35 distinct relevant PostgreSQL/API integration tests (18 customer-order, 11 staff-access, 6 new inventory), 12 frontend unit tests, production build and ESLint.
- Inventory tests cover per-branch isolation, preserved reservations, fractional/whole units, invalid quantities, role restrictions, real HTTP CSRF/session enforcement, concurrent identical/different receipt requests, rollback, append-only history and audit events. The initial inventory run found a test fixture referencing `version` instead of `session_version`; corrected and all six passed on rerun.
- Passed five distinct Chromium journeys: stock receiving with a deliberately lost committed response and safe retry; Owner account creation/revocation; Sales password setup and mobile order review; Inventory/Delivery data boundaries; failed sign-in without a fake session. Browser tests now wait for password-change responses, use the branch combobox's accessible role, and allow the longer Owner workflow 90 seconds. Staff password setup now routes to Overview consistently.
- Checked inventory/receipts at 320, 768, 1024 and 1440 pixels and Owner Overview at 320/1440; no horizontal overflow. Inspected rendered desktop/mobile screenshots. Receipt retry added stock once, preserved reserved stock, and left Tarime unchanged when receiving at Mwanza.
- Keyboard verification found and fixed initial quantity focus and focus restoration after dialog unmount. The final receiving journey passes Tab order, Escape, return focus, Enter-to-reopen and the 320px dialog-width check.
- Screenshots retained in `.local/workspace-qa`; all displayed accounts, orders and stock in verification came from guarded `finishers_test`, not development data. Temporary browser servers used ports 18080/15173. The user's IntelliJ servers were not stopped.
- Restart **Full Stack - local** to load the new backend and apply V8 to development. No production deployment or executable-JAR repackaging is claimed. Receipt corrections, stock adjustments, preparation, dispatch and fulfilment remain future workflows.

## Branch checkout and confirmation — 04 November 2026

- Passed 8 backend unit/architecture tests and 42 distinct PostgreSQL/API integration tests. The full initial run passed 39 integration tests; the final affected-class run passed 29 including the three added concurrency, legacy-order and HTTP checks. These runs overlap; counts above are unique.
- Passed 12 frontend unit tests, production build and ESLint.
- Passed 10 affected Chromium journeys: 5 catalogue checks, 4 checkout/order checks and 1 complete branch-selection → Sales confirmation → customer-notification journey. The latter verified Mwanza has no stock for the sample product, Tarime selection, separate staff/customer sessions, mandatory temporary-password change, agreement entry, confirmation and customer status.
- Database checks cover per-branch isolation, mandatory branch, branch-sensitive retry identity, unchanged physical stock, atomic reservation, competing final-stock orders, concurrent identical confirmation retries, rollback of all lines/notification failures, price changes, legacy unassigned requests, roles and CSRF.
- Test-only servers used ports 18080/15173 and the guarded finishers_test database. No real Owner credentials or development stock were used. Requirements v0.5 revision page rendered and visually inspected.
- Browser checks initially exposed a test assumption that checkout fields appeared before branch selection, plus registration waits that ended before the HTTP response. Tests now select a branch and await actual responses; the rerun passed.
- Backend compilation and a temporary Spring Boot run passed. This turn did not repackage the user-held executable JAR or claim public deployment readiness. Restart Full Stack - local to apply V7 to development data.


## Staff access and pending review — 03 November 2026

| Check | Observed result |
| --- | --- |
| Final Maven reactor `verify` | Passed: 8 unit/architecture tests and 33 real PostgreSQL/HTTP integration tests (41 total), executable JAR packaged |
| Staff MFA unit tests | 3 passed: RFC 6238 SHA1 vectors/window/replay; AES-GCM nonce/account binding/tamper rejection; random recovery-code format and hashes |
| Staff access integration tests | 10 passed: Owner MFA/CSRF/session rotation, all four roles/two drivers, customer privilege isolation, restricted temporary-password sessions, Owner account management, role/deactivation revocation, concurrent and expired recovery, shared abuse limits, safe filtered review projections, per-staff notification read state |
| Order notification integrity | Customer and staff notice persistence checked; notification storage failure leaves no partial order/items/audit/staff notice |
| Frontend unit tests | 12 passed |
| ESLint and production TypeScript/Vite build | Passed |
| Chromium journeys with explicit isolated fixtures | 17 passed, no skips: existing 13 customer/catalogue/cart flows plus 4 staff journeys |
| Staff browser coverage | Owner review/create/change/disable with active-session revocation; Sales first password change and mobile review; Inventory/Delivery denial; recoverable login network failure |
| Responsive/visual QA | Real test-account Owner desktop and Sales 390px request screens inspected. Corrected white-on-white sign-out text, then independently checked 1440/390/320px header contrast, no page errors and a 100-character staff name using an explicit visual-only API fixture |
| Private data exclusions | `backend/.env` and synthetic `.local/staff-browser-fixtures.json` are ignored by Git |

Total: **70 automated tests passed** (41 backend + 12 frontend unit + 17 browser). Browser tests use only synthetic `finishers_test` identities and MFA settings on temporary backend 18080/frontend 15173. A real first Owner was not created. Private enrollment requires the user's chosen password, authenticator verification and offline-code acknowledgement; see [staff setup](staff-access.md).

The initial Owner browser checks exposed ambiguous selectors and a real stale-session display after role revocation. Selectors now use the relevant role/status, and staff identity refreshes on staff-route changes as well as focus. The complete browser suite passed after the session fix. Later styling checks verified readable sign-out text and long-name wrapping without substituting mock data for real authorization tests.

V5 was verified as an upgrade in the guarded local test database. Local checks do not establish production enrollment/recovery operations, full staff fulfilment permissions, confirmation/reservation/dispatch races, financial workflows, external messaging, backup restoration or 3D readiness. The dashboard is read-only pending-order review; no confirmation/stock mutation is exposed.

## Customer commerce — 03 November 2026

| Check | Observed result |
| --- | --- |
| Maven reactor `verify` | Passed: 5 unit/architecture and 23 real PostgreSQL/HTTP integration tests (28 total) |
| `CustomerOrdersIT` | 10 passed: server pricing and snapshots, fractional rounding, quantity/visibility/currency rules, stock/price conflicts, simultaneous idempotent requests, retry conflicts/recovery, pending requests leave stock unchanged, notification-intent failure rollback, successful-submission limiting, private HTTP ownership/CSRF and notice read state |
| Frontend unit tests | 9 passed, including malformed cart storage and quantity boundaries |
| ESLint and TypeScript/Vite production build | Passed |
| Chromium browser journeys | 13 passed: existing 9 catalogue/account journeys and 4 commerce journeys |
| Commerce browser coverage | Guest cart edit/reload → registration → delivery request → private order/update; pickup and retry after a committed but lost response; invalid quantities/remove/failed quote recovery; new-tab authentication with cart continuity |
| Visual checks | Desktop checkout and order details; 390px order details; 320px cart; no horizontal overflow. Screenshots normalize scroll position before capture so the sticky header remains at the top |
| Flyway migration | V3 → V4 applied successfully to guarded `finishers_test`; existing fixtures remain usable |

Tests use only the isolated `finishers_test` database, not development customer/order records. The browser suite is run against temporary backend 18080/frontend 15173; restart IntelliJ **Full Stack - local** to load the new backend routes and V4 in development. One cold-start registration check was made response-aware rather than assuming password hashing completes within a five-second UI assertion. Production hashing and rate limits were not weakened.

Pending request tests do **not** prove staff confirmation/reservation/dispatch concurrency, payment/refund workflows, external messaging, clean production rollout, backup restoration or 3D readiness. Those remain later feature/release gates. Order submission never reserves stock or recognizes a completed sale. See [customer order rules and limits](customer-orders.md).

## Original foundation verification

Verified locally on 03 November 2026 against the project-owned PostgreSQL instance, using JDK 24 with Java 21 release target and Node.js 24.19.0.

| Check | Observed result |
| --- | --- |
| Maven reactor `verify` | Passed; packaged the executable Spring Boot JAR |
| Architecture tests | 2 passed: module cycles and domain/framework dependency boundary |
| PostgreSQL/HTTP integration tests | 4 passed: migrations and safe metadata/readiness; anonymous access and CSRF-less write rejection; CSRF/session issuance; append-only audit rollback |
| Database setup | Separate non-superuser development/test roles and databases created; cross-database connections denied |
| Setup rerun | Reused the project instance without overwriting credentials or records |
| Frontend ESLint | Passed |
| Frontend unit tests | 3 passed: valid budget contract, malformed response rejection, HTTP failure handling |
| TypeScript and Vite production build | Passed |
| Chromium end-to-end | 2 passed: real same-origin backend connection and failure/retry recovery; phone width checked for horizontal overflow |
| Desktop/mobile screenshot inspection | Completed at 1280x720 and 390x844; no clipping or horizontal overflow observed |
| npm dependency audit | Zero vulnerabilities reported after the minimatch 11.0.1 override |
| POM/run-configuration XML | Parsed successfully |
| Git exclusions | Private environment files, database instance files, dependencies and build outputs ignored |

The minimatch override addresses the glob-matching denial-of-service advisory reported by npm for transitive lint-tool dependencies. Lint, unit, build, and browser checks passed after the fix. Review the override during dependency upgrades rather than applying automatic breaking upgrades.

## Environment fixes

- Maven repository downloads use TLS 1.2/IPv4 JVM options; certificate checking remains enabled.
- Windows' long temporary directory exceeded Java's Unix-domain socket path limit. Maven integration tests and the shared IntelliJ backend configuration use a short local socket directory. See the [Java networking documentation](https://docs.oracle.com/en/java/javase/24/core/java-networking.html) for the property and path limit.

## Limits

The GitHub workflow is present but has not been run remotely; it is manual-only to avoid unapproved usage. No public deployment, paid service, production database, customer registration, staff authentication, catalogue/order/inventory/finance workflow, or 3D scene has been implemented by this foundation step. Their future tests are not counted as passed here. Backup recovery and non-Chromium/physical-device testing remain later release work.

The append-only trigger guards ordinary row mutation; the local database owners retain administrative schema authority. Production migration/application role separation must be configured at deployment.
