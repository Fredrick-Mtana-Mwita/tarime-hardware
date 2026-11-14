# Interior and exterior design page

Added 12 November 2026. Route `#/design-services`, linked first in the header category bar, from the home page banner and from the footer. Frontend only: no backend route, migration or dependency was added.

## What the page shows

- **Hero**: two panels (Interior, Exterior) standing in CSS 3D perspective. They float, lean towards the mouse and scroll to their section when pressed.
- **Interior**: nine pictures on a 3D ring that turns every few seconds. Previous, next and pause buttons; hovering or focusing pauses it.
- **Exterior**: three concept cards that tilt towards the mouse.
- Service summary, a call to visit the Tarime shop, and a note that the pictures are concept visuals, not completed projects.

All motion is CSS transforms. With the operating system's reduced-motion setting the panels stay still, the ring does not turn by itself and clips are not loaded. Touch devices get no pointer tilt.

## Media

| File | Source |
| --- | --- |
| `public/images/design-1.jpg` to `design-9.jpg` | Inspiration pictures supplied by the client. 1 to 4 were already used by the home gallery. |
| `public/images/design/exterior-1.jpg` to `exterior-3.jpg` | Generated with Higgsfield (Nano Banana 2) on 12 November 2026. |
| `public/images/design/interior.mp4`, `exterior.mp4` | Optional animation clips. Not created yet. |

The exterior pictures are not in the repository yet. Run `.\scripts\fetch-design-media.ps1` once from the project root to download them and save them as JPEG files. Until then the page loads the originals (about 3 MB each) from Higgsfield's file server through the `remote` addresses in `src/features/design/media.ts`, which is slow on mobile data. After they are saved and committed, those `remote` entries can be removed.

When `interior.mp4` and `exterior.mp4` are placed in `public/images/design`, the hero plays them automatically over the still pictures. Keep each under about 3 MB, 4:3, silent, 4 to 6 seconds, so they loop cleanly.

## Higgsfield status

The account was on the free plan with 10 credits. Higgsfield refused every video model and the free-trial allowance with "Requires basic plan or higher" / "Unlimited generations aren't available on this account". Four pictures were generated for 4.62 credits; 5.38 remain. No trial, card or subscription was started, in line with the project's no-paid-service rule.

The supplied interior pictures look like inspiration images collected online. Replace them with photographs of Tarime Hardware's own finished work, or pictures the business has the right to use, before the public launch.

## Checks

On a copy of the frontend: `tsc --noEmit` and ESLint passed, the 12 unit tests passed, and the Pages build succeeded. The built page was opened in Chrome at 1440 and 390 pixels wide: no script errors, no horizontal overflow, the header link present and the ring advancing with its buttons. That copy could not reach Higgsfield's file server, so the exterior pictures were checked separately by opening their addresses in a browser. The download script has not been run. Browser journeys for this page have not been added to the Playwright suites.
