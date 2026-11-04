# Tarime Hardware: confirmed client decisions

Current requirements: [v0.5](../../output/pdf/Tarime_Hardware_System_Requirements_v0.5.pdf), dated 04 November 2026. Read its revision pages before the retained v0.3 baseline.

Main branch: **Tarime**. Second branch: **Mwanza**. Addresses, hours, branch-specific stock and access, order allocation and pickup selection remain to be specified. Current stock is shared, not assigned to a branch.

## Confirmed categories

1. Building Materials — Cement, nails and building essentials.
2. Electricals — Electrical supplies and lighting.
3. Plumbing & Sanitary — Includes Pumps & Water Systems.
4. Roofing Materials
5. Paints, Coatings & Painting Accessories
6. Hand Tools
7. Power Tools & Machinery
8. Fasteners & Fixings
9. Steel & Metal Products
10. Doors & Windows Accessories
11. Flooring Materials — Tiles and terrazzo.
12. Ceilings & Partitions — Gypsum board.
13. Welding & Fabrication
14. Security & Safety Equipment — Padlocks.
15. Adhesives & Sealants — Silicone sealants.
16. Gardening & Outdoor — Watering pipes.
17. Cleaning & Maintenance — Brooms.

Blank input row omitted. Pumps & Water Systems is included in Plumbing & Sanitary. Lighting is included in Electricals. Existing category IDs and seven legacy URL slugs remain stable; lighting products move to Electricals. New categories have no invented product photos or stock. V6 preserves product/order IDs and stock. Development sample catalogue follows the same taxonomy.

Internal package names, database names, sample SKUs and environment variable prefixes remain unchanged to preserve existing installations. Email-based customer authentication is a separate proposed change, not implemented by this update.

## Verification

8 backend unit tests, 33 isolated PostgreSQL/API tests and 12 frontend tests passed. Frontend lint passed. Browser visual fixtures checked branding, 17 category cards, registration and no page overflow at 1440/390/320px; these were not live API browser journeys. New PDF pages rendered and visually inspected. Backend repackage was blocked by a Windows jar rename/file lock; integration tests passed separately without stopping user processes. Restart the local full-stack run to apply V6 to development data.


## Branch decision — 04 November 2026

Customers select Tarime or Mwanza for delivery or pickup. Each branch has separate stock. Existing balances are assigned to Tarime; Mwanza starts at zero. See [branch orders](branch-orders.md) for implementation and remaining workflows.

