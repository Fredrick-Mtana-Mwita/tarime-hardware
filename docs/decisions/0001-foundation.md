# ADR 0001: Modular monolith and isolated local databases

Status: adopted for the application foundation, 03 November 2026.

## Context

The approved baseline selects React/Vite/Tailwind, Spring Boot/Security, PostgreSQL and Flyway. Inventory confirmation, reservation and order status must commit atomically. The user works in IntelliJ, has existing PostgreSQL/Java installations, and prohibits paid services without explicit approval.

## Decision

- Use a single Maven backend and a sibling frontend, grouped by business capability rather than global controller/service/repository folders.
- Compile to Java 21 with the existing JDK 24; pin the Spring Boot parent and Maven wrapper. Do not change machine-wide Java or install another JDK unnecessarily.
- Use a new local PostgreSQL cluster on loopback port 55432, stored under ignored `.local`. Separate dev/test databases and non-superuser roles, with PUBLIC access revoked. Leave the pre-existing server untouched.
- Keep CSRF protection and same-origin development proxying. Allow safe foundation endpoints and deny everything else until feature-specific authorization is implemented. No demo administrator or production credentials.
- Add only an audit-history migration; defer business migrations until the corresponding feature and unresolved policies are ready.
- Keep the GitHub workflow manual-only until free-tier eligibility is verified. No deployment, paid account, or recurring workload is activated.

## Consequences

Local integration tests need no Docker engine. PostgreSQL data survives restarts and setup scripts refuse unrecorded directories/credential overwrites. A native Windows PostgreSQL instance does not substitute for validating production migrations/backups on the chosen host. Production application/migration role separation, customer authentication and business workflows remain feature/release work.

The frontend is a development shell, not the finished storefront. The existing prototype remains the visual reference. 3D dependencies/assets will be added when implementing the five experiences so initial foundations do not download or execute heavy scenes unnecessarily.
