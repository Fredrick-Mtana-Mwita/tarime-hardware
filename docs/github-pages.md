# Client preview on GitHub Pages

The public site is a design preview, not the production shop. GitHub Pages cannot run Spring Boot or PostgreSQL. The standard development build continues to use the real backend; only `vite build --mode pages` enables synthetic read-only responses.

Run `npm run build:pages` in `frontend`. The preview uses the repository name in `GITHUB_REPOSITORY`, defaulting to `tarime-hardware`, as its base path. Its hash routes work on direct refresh. All local images are resolved under that path.

The preview generator reads only the versioned sample SQL in `backend/src/main/resources/db/dev/R__sample_catalogue.sql`. It never connects to a database. It includes 12 synthetic products and 17 confirmed categories. Prices, stock and photos remain illustrative. A visible banner identifies the site as a client preview. Storefront browsing, search, product details, a local sample cart and branch inventory inspection work. Real sign-in, registration, payments, order submission, password changes and stock mutations are unavailable. The inventory screen is a public demo, not an authenticated Owner session.

The Pages workflow builds and deploys only `frontend/dist`; backend code, environment files, logs, test databases and local fixtures are not part of the deployed website. The source repository excludes `.local`, environment secrets and generated build/test output. Do not upload the reference videos, real customer/staff data or private credentials.

## Deployment

Create the public repository `tarime-hardware` under the authorized GitHub account, publish the reviewed source on `main`, and configure Pages with build type `workflow`. `.github/workflows/pages.yml` deploys on pushes to `main` and manual runs. Standard public-repository GitHub-hosted Actions and Pages require no paid service. No custom domain or subscription is required.

The client review URL will be `https://<account>.github.io/tarime-hardware/`. Verify both storefront and `#/staff/inventory`, including refreshed deep links and loaded images, after the workflow succeeds. The full application needs separate Java and PostgreSQL hosting before real transactions can be enabled.

Design direction: the light inventory video is the only reference. The dark admin video is retired. Light is the default, and the admin video's decorative background spheres are removed. Further faithful inventory-layout work follows publication, as requested by the user.
