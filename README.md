# Tarime Hardware

Client review: [GitHub Pages preview setup](docs/github-pages.md). This is a static, read-only demonstration with synthetic catalogue data; the full application still needs Java and PostgreSQL hosting. The **light inventory dashboard video is the only design reference**; the dark admin reference is retired.

React/TypeScript frontend and Java/Spring Boot backend in one IntelliJ-friendly repository. The backend is a modular monolith backed by PostgreSQL and Flyway. No paid service is needed for this local foundation.

## Current implementation

Named staff sign-in, Owner authenticator/offline codes, staff account management, the redesigned staff workspace and Owner/Sales order confirmation are implemented. Owner and Inventory can receive stock separately at Tarime or Mwanza and review immutable receipt history. See [stock receiving](docs/stock-receiving.md) and [private first Owner setup](docs/staff-access.md); there is no default staff password.

Guest carts, branch-selected delivery/pickup checkout, pending order requests, private My orders and customer in-app updates are available. See [customer order behavior, API and remaining work](docs/customer-orders.md). Restart **Full Stack - local** in IntelliJ after backend changes to apply V8. Submission checks prices/availability; shop confirmation records customer agreement and atomically reserves stock at the selected branch.

Customer account registration/sign-in/sign-out and a private account page are now available. See [account setup, session/security behavior and remaining launch work](docs/customer-accounts.md). Registration creates customers only; staff authority is not enabled through the public forms.

The first catalogue slice is implemented: homepage, search/category/price/availability filters, pagination/sorting, and product details with variants, gallery and specifications. See [catalogue API, seed policy and checks](docs/catalogue.md). Restart **Full Stack - local** after pulling backend changes; its `dev` profile loads the clearly marked sample catalogue.

- A branded development shell calling a real backend through Vite's same-origin proxy.
- Public system information, liveness/readiness, and session CSRF-token endpoints.
- Deny-by-default security; no demo login or generated administrator password.
- First Flyway migration: append-only audit history.
- Isolated local development/test databases, separate application users, and cross-database access checks.
- Frontend unit/build/lint checks, browser smoke tests, backend architecture and PostgreSQL integration tests.

Customer verification/recovery, staff recovery/renewal beyond offline codes, catalogue editing, order confirmation/revisions, stock operations, financial workflows, and all five 3D experiences are subsequent feature work. Declaring a module boundary does not implement its business workflows.

## Open in IntelliJ

Open **this folder** (`D:\Finishers\finishers-app`), trust only this known project, and import the root `pom.xml` as Maven. Set Project SDK and Maven Runner JRE to your installed JDK 24; source/bytecode target is Java 21. Use the Maven wrapper. This project does not change your machine's global Java settings.

After Maven import, the shared **Backend - local** run configuration runs `africa.finishers.FinishersApplication` with `backend` as its working directory. You can also run the main class directly. JavaScript/npm IDE integrations depend on your installed IntelliJ edition/plugins; the terminal commands below work independently of those integrations.

See [IntelliJ setup](docs/intellij.md) and [architecture](docs/architecture.md).

For one-click startup, select **Full Stack - local** in IntelliJ and click Run. It starts/reuses the local database, waits for backend readiness, and starts the frontend. Use Stop to shut down its application processes; the database remains available.

## First local setup

From the project root in PowerShell:

```powershell
python .\scripts\setup-local-db.py
cd frontend
npm ci
cd ..
```

The setup script uses existing PostgreSQL binaries, initializes **only** `.local/postgres-data`, and listens on `127.0.0.1:55432`. Your existing server on port 5432 is not touched. Override `--pg-bin` and `--port` for another machine. Generated credentials live in ignored `backend/.env`, `backend/.env.test`, and `.local/`; do not share them or the data directory.

Run the backend in IntelliJ or a terminal:

```powershell
.\scripts\maven.ps1 -pl backend spring-boot:run
```

Run the frontend in another terminal:

```powershell
cd frontend
npm run dev
```

Frontend: `http://127.0.0.1:5173`. Backend: `http://127.0.0.1:8080`. The development server proxies `/api` and `/actuator` to the backend. No broad CORS exception is needed.

## Checks

```powershell
.\scripts\maven.ps1 verify
cd frontend
npm run lint
npm test
npm run build
```

For end-to-end tests, keep the backend running, then run `npx playwright install chromium` once and `npm run test:e2e` from `frontend`. The test configuration starts Vite automatically. Browser binaries and packages are free downloads, not paid services.

Integration tests fail closed without the private test settings and refuse non-loopback databases, non-test database names, or the development role. They use real PostgreSQL; Docker is not required. `mvn test` runs architecture tests; `mvn verify` also runs `*IT` integration tests.

The included GitHub workflow is manual-only. It has not run remotely; verify free-minute eligibility before enabling/running it. Its fixed CI passwords belong only to disposable CI containers, never production or local private data.

To stop the project database: `python .\scripts\stop-local-db.py`. To restart it: `python .\scripts\setup-local-db.py`. Do not delete its data directory to fix an ordinary connection problem.

## Requirements and policy

Current baseline: `D:/Finishers/output/pdf/Finishers_Point_Africa_System_Requirements_v0.3.pdf`. The sibling `finishers-prototype` is a visual reference, not production backend logic.

No paid purchase, card-backed trial, paid API, or billable service without explicit approval. In-app notifications are required for launch; external channels remain disabled pending a verified free option. The five 3D feature families remain in scope, using a small approved free-asset set. Local development success does not establish production hosting, recovery, or launch readiness.

## Confirmed client identity and catalogue

See [client decisions](docs/client-decisions.md) and requirements v0.4 for Tarime (main), Mwanza, and the 17 confirmed categories. Restart Full Stack - local to apply V6.

## Branch checkout and confirmation

Customers choose Tarime or Mwanza at checkout; each branch holds independent stock. Existing balances migrate to Tarime. Owner/Sales can record customer agreement and confirm with atomic branch reservations. See [branch orders](docs/branch-orders.md) and [requirements v0.5](../output/pdf/Tarime_Hardware_System_Requirements_v0.5.pdf). Restart Full Stack - local to apply V7.

