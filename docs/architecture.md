# Architecture and folder structure

## Decision

Current feature state: catalogue, customer accounts, pending customer orders, named staff access and role-scoped request review. Flyway V1–V5 cover audit, catalogue/stock balances, customer identity, order snapshots/customer notices, and named staff/reviewer notices. See [customer orders](customer-orders.md) and [staff access](staff-access.md).

Use a monorepo containing a feature-based React frontend and a package-by-business-capability Spring Boot modular monolith. One transactional PostgreSQL database per environment allows order confirmation and stock reservation to commit together. Independent development/test users and databases prevent test setup from touching development records.

Separate deployments of frontend/backend are possible later. Microservices, a shared cross-language model package, message brokers, and a generic repository layer are unnecessary at this stage.

## Structure

```text
finishers-app/
  pom.xml                         Maven parent and backend aggregator
  mvnw / mvnw.cmd / .mvn/          Pinned Maven wrapper
  .run/                           Shared IntelliJ run configurations
  .github/workflows/              Automated build/test pipeline
  backend/
    pom.xml
    .env.example                  Credential names only
    .env.test.example
    src/main/java/africa/finishers/
      FinishersApplication.java
      platform/api/               Safe foundation metadata
      identity/
        api/                      CSRF transport endpoint
        domain/                   StaffRole; future identity rules
        infrastructure/           Spring Security configuration
      catalogue/                  Product/category/variant boundary
      orders/                     Orders and approved revisions
      inventory/                  Reservations and stock movements
      finance/                    Sales, receipts, reversals, refunds
      fulfilment/                 Preparation, delivery, collection
      notifications/              In-app notifications and outbox
      assets/                     Model/material provenance/publication
      imports/                    Validated import batches
      reporting/                  Authorized read projections
      audit/                      Audit-writing boundary
    src/main/resources/
      application.yml
      application-prod.yml
      db/migration/               Ordered, immutable Flyway migrations
    src/test/java/africa/finishers/
      ArchitectureTest.java       Module cycles and domain boundaries
      FoundationIT.java           Real PostgreSQL + HTTP integration
  frontend/
    package.json / package-lock.json
    vite.config.ts / tsconfig.json / eslint.config.js
    src/
      app/                        Application composition and global styles
      features/system/            Foundation API contract and tests
      shared/api/                 Small same-origin HTTP boundary
      main.tsx
    e2e/                          Browser journeys against the actual backend
    playwright.config.ts
  scripts/                        Local setup, start/stop, and Maven helpers
  docs/                           Setup, boundaries, decisions, API contract
  .local/                         Ignored PostgreSQL instance and private files
```

The package-info files declare intended ownership for modules whose feature code is not yet written. Create `api`, `application`, `domain`, and `infrastructure` subpackages within each module **when the feature needs them**; do not fill the tree with empty service/repository classes.

## Backend rules

| Layer | Responsibility | Dependency direction |
| --- | --- | --- |
| `api` | HTTP DTOs, validation, status/error mapping | Calls application use cases |
| `application` | Authorization, transaction boundaries, coordination | Depends on domain and declared ports |
| `domain` | Business state and invariants | Java types; no HTTP, Spring, or ORM dependencies |
| `infrastructure` | JPA adapters, provider clients, framework configuration | Implements application/domain ports |

Expose cross-module use cases through explicit application interfaces or value types. Do not import another module's controllers, JPA entities, or repository implementations. Avoid a catch-all `shared` business model; each capability owns its vocabulary and state.

Orders coordinates inventory through a transaction-aware application API. Both participate in one database transaction; do not defer stock reservation to an asynchronous event. Persist notification intent/audit records in the business transaction. Send external notifications after commit via an outbox worker, once approved. Reporting can use explicit authorized read projections; it cannot mutate operational tables.

Implemented migrations cover audit (V1), published catalogue/stock balances (V2), customer accounts (V3), and pending order snapshots/customer notices (V4). The development profile supplies clearly labelled catalogue fixtures; production does not load them. Reservations, stock movements and financial transactions require later migrations with their implemented workflows. No sample sales are inserted automatically.

## Frontend growth

Add feature directories for `catalogue`, `identity`, `cart`, `orders`, `staff`, and the other implemented business flows. Co-locate their components, API functions, state, and tests. Keep reusable presentation primitives in `shared/ui` only when actually shared. Route composition belongs in `app`, not in individual API modules.

Add lazy-loaded 3D experiences (`product-viewer`, `room-visualizer`, `configurator`, `showroom`, `effects`) under `src/experiences` when implemented. Share rendering/runtime utilities under `shared/three` only where necessary. Keep canonical variant IDs and prices in commerce state/API contracts, not inferred from meshes. Heavy scenes must not enter the initial storefront bundle. Every scene needs HTML commerce controls and image/error fallbacks.

## Environments and security

- PostgreSQL loopback port 55432 is project-local; the existing system server is untouched.
- `finishers_dev` owns/accesses only the development database. `finishers_test` owns/accesses only the test database. Neither role can create databases/roles or act as superuser.
- The application uses JPA schema validation; Flyway owns schema changes and clean is disabled.
- HTTP permits public catalogue/foundation reads and CSRF-protected guest quotes. Customer registration/login and named staff login create separate authorities. Customer commerce enforces ownership; staff review/account management checks current named role and session revision. Unlisted capabilities, including confirmation/fulfilment/finance, remain denied.
- Production uses secure session cookies, environment-supplied credentials, HTTPS termination, explicit hosting and backup decisions. Production runtime permissions/migration ownership need a separate deployment role strategy.
- The audit trigger blocks ordinary row updates/deletes. Database owners/superusers can change schema/triggers; this is an application guard, not protection against a privileged database administrator.

## Verification and versions

Frontend: React 19.3.1, Vite 8.3.5, Tailwind 4.3.4, TypeScript 6.0.2; exact direct dependencies and lockfile. TypeScript 6 is deliberately selected because the installed ESLint integration's published peer range excludes TypeScript 7. Backend: Spring Boot 4.1.2, Java 21 release target, Maven wrapper 3.9.6. The installed JDK 24 can compile/run the Java 21 target. This avoids a machine-wide Java change.

Architecture checks prevent module cycles and framework dependencies in domain code. PostgreSQL integration checks verify migrations, audit immutability, safe metadata, health, CSRF issuance, and denial of protected anonymous requests. Browser tests verify real frontend/backend proxy connectivity and failure/retry states. These are foundation checks, not proof that future checkout/stock/refund features work.

Official references: [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html), [Vite](https://vite.dev/guide/), [Tailwind/Vite](https://tailwindcss.com/docs/installation/using-vite).
