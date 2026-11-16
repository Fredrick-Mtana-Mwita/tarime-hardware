# 3D experiences (removed)

On 16 Nov 2026 the client decided to remove the 3D experience: the 3D hub, virtual showroom, room
visualizer, product configurator, product 3D views, the floating 3D banners, the footer "3D views and
effects" switch and the staff "3D models" page.

Reasons agreed with the client: the 3D code and models added about 1 MB of code and 8 MB of models to a
site most customers open on phones over mobile data, the models were not the shop's exact brands, and
3D views did not help customers decide what to buy.

What was removed:

- Frontend: `src/experiences`, `src/shared/three`, `src/features/staff/Models.tsx`, the `three` and
  `@react-three/fiber` packages, and the bundled models, textures and lighting files.
- Backend: the `assets` module (model upload, review, publication and the public model manifest), its
  security rules, `ModelAssetsIT`, and the `model_assets` table, order-line model snapshot columns and
  trigger, dropped by migration `V17__remove_model_assets.sql`. `V12__model_assets.sql` stays because
  Flyway migrations are never edited after they run. The development seed `R__sample_model_assets.sql`
  is kept as an empty file for the same reason.

The original 3D work is still in earlier Git commits if it is ever needed again. The realistic-model
version made on 15 Nov 2026 was never committed, so it is gone.
