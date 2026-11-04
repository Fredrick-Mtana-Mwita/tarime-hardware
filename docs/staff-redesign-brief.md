# Tarime Hardware staff redesign brief

## Current decision — sole reference

04 November 2026: The user selected **Video by _code_and_chill_.mp4**, the light inventory dashboard, as the main and only design reference. **E-Commerce store Admin Dashboard.mp4 is permanently retired as a reference.** This overrides the earlier combined direction and dark-admin motion interpretation below, which remain historical notes only. Keep Tarime Hardware's amber/charcoal/white palette. Publish a client preview to GitHub Pages first; then continue the faithful light-inventory redesign. Light is now the default, and the dark-admin decorative spheres are removed.

Approved direction: combine the first supplied video's labelled staff sidebar and order workspace with the second video's clean inventory catalogue layout. Use amber #F2A900, charcoal #16191C and white. Preserve the working React/Spring Boot/PostgreSQL application and existing role, branch and reservation rules.

## Shared workspace

Implemented 04 November 2026: Overview after sign-in/password setup, role-filtered charcoal navigation, live pending-request summary for Owner/Sales, branch inventory cards and append-only receipt history for Owner/Inventory. See `stock-receiving.md` for API, retry behavior and restart instructions. Delivery preparation and fulfilment remain separate future work.

Persistent charcoal navigation with an amber active state; white content panels; compact top bar showing staff identity and role; accessible mobile navigation; clear loading, empty and error states. Show only navigation supported by the signed-in role. Use real operational totals rather than fabricated revenue charts.

## Inventory receiving

Owner and Inventory staff select Tarime or Mwanza, search products/variants, and inspect on-hand, reserved and available stock. Receive a positive quantity in the variant's permitted unit, record a source/reference and reason, and show the resulting branch balance. Preserve reserved quantities. Store the receipt, named actor and timestamp atomically, with retry protection and a receipt history. Sales and Delivery cannot receive stock.

## Approved preview video

Follow-up correction from the user: the same motion language is also required in the **working dashboard**, not just the separate preview. Implemented live perspective hover, staggered panel entrances, page transitions, a moving sidebar highlight, count-up totals, dialog entrance and circular light/dark switching. Both themes retain amber, charcoal and white; no blue or green reference accent colors are adopted. Reduced-motion settings disable these effects and touch users do not receive cursor tilt. This supersedes the earlier stationary-panel interpretation.

The user clarified that the requested animation is a separate 3D animated preview video. Higgsfield rejected the approved 16-credit generation because the selected model requires a paid plan; no job was created and the balance remained 43.3 credits. The user then approved a free local render. The preview uses an eight-second, 1080p sequence with perspective camera motion, floating overview panels and a transition to branch inventory. All displayed figures are labelled illustrative. This artifact does not implement or change the working application interface. No trial or subscription was started.

Keep essential controls stationary and readable. Motion must respect reduced-motion preferences and remain usable by keyboard and on touch devices. Reference videos demonstrate desktop layouts; mobile behavior must be designed and verified separately.
