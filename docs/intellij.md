# IntelliJ workflow

For the first named Owner, run `./scripts/setup-owner.ps1` in IntelliJ's Terminal from the project root, complete private password/authenticator/offline-code enrollment, then restart **Full Stack - local**. No shared/default staff credential is installed. See [staff setup and permissions](staff-access.md). Subsequent startup remains the same Run triangle.

## Run the full stack with one triangle button

After opening the project and reloading Maven, select **Full Stack - local** from IntelliJ's run-configuration dropdown, then click the green Run triangle. This is a standard Java Application configuration; it does not require npm/Spring-specific IDE integrations.

The launcher checks that ports 8080/5173 are free, starts/reuses the project database, starts the backend with your selected JDK, waits for database-backed readiness, then starts Vite. Open `http://127.0.0.1:5173` when the console prints **Finishers full stack is ready**.

The ordinary Stop button runs cleanup for the launcher-owned frontend/backend processes. The PostgreSQL instance remains running to preserve other local database sessions. Avoid force-killing IntelliJ/the launcher: a hard process kill can bypass Java shutdown hooks. No existing process is killed to free an occupied port. Initial dependency installation (`npm ci`) is required once; Maven may download free dependencies on the first run.

If the configuration is not listed yet, reload/reopen the project. Alternatively, open `FullStackLauncher.java` and run its `main` method, setting its working directory to the project root. The full-stack launcher coordinates startup; backend breakpoint debugging still uses **Backend - local** in Debug mode.

1. Choose **File > Open**, then select `D:\Finishers\finishers-app` (the folder containing the root `pom.xml`). Import as Maven if prompted.
2. Under **Project Structure**, set the SDK to the existing `D:\Program Files\JetBrains\jdk-24` and language level to Java 21. Under Maven settings select the wrapper and the same JDK for the importer/runner. Settings labels can vary by IntelliJ version.
3. In the built-in terminal run `python .\scripts\setup-local-db.py`. On the initial setup this creates private credentials and the isolated databases. Later runs restart only that project-owned instance.
4. Reload Maven. Select **Backend - local** from the shared run configurations, or run `FinishersApplication.main`. The working directory must be `backend`, or the repository root; both locations load the private development settings correctly.
5. In a second terminal run `cd frontend`, `npm ci` (initial installation), then `npm run dev`. Open `http://127.0.0.1:5173`.
6. Run `ArchitectureTest` as a JUnit test, or run `.\scripts\maven.ps1 verify` from the root for architecture plus real PostgreSQL integration checks. `FoundationIT` can also run directly after its private test configuration exists.

The root Java project contains only backend sources; the frontend remains in the same IntelliJ window. npm scripts may be available from `frontend/package.json` depending on edition/plugins. No paid IDE feature is required to run commands in the terminal.

If IntelliJ has not imported the module name in `.run/Backend.run.xml`, choose the imported `finishers-backend` module in the run configuration once. Do not save passwords into run configurations. `.idea` and private environment files are excluded from version control.

The existing prototype at `D:\Finishers\finishers-prototype` is a separate repository and remains unchanged. This new application has no remote or published deployment configured.

The shared backend run configuration and Maven integration tests use a short temporary socket directory. This avoids the JDK's Windows socket-path limit when the host's default temporary path is very long. If running a main/test class through a new IntelliJ configuration, add `-Djdk.net.unixdomain.tmpdir=$PROJECT_DIR$/.local/tmp` to its VM options. The setup script creates that directory; no global Java setting is changed.
