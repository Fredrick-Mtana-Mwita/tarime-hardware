package africa.finishers.devtools;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Local development entry point for a plain IntelliJ Java Application configuration. */
public final class FullStackLauncher {
    private static final List<Process> CHILDREN = new CopyOnWriteArrayList<>();
    private static final int BACKEND_PORT = Integer.getInteger("finishers.backend.port", 8080);
    private static final int FRONTEND_PORT = Integer.getInteger("finishers.frontend.port", 5173);

    private FullStackLauncher() {}

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve("scripts/setup-local-db.py"))) {
            throw new IllegalArgumentException("Set the working directory/argument to the finishers-app folder.");
        }
        if (!System.getProperty("os.name").startsWith("Windows")) {
            throw new IllegalStateException("This launcher targets the project's Windows/IntelliJ environment.");
        }
        if (!Files.isRegularFile(root.resolve("frontend/node_modules/vite/bin/vite.js"))) {
            throw new IllegalStateException("Install the free frontend dependencies once: cd frontend; npm ci");
        }
        assertPortFree(BACKEND_PORT);
        assertPortFree(FRONTEND_PORT);
        Runtime.getRuntime().addShutdownHook(new Thread(FullStackLauncher::stopChildren, "finishers-stop"));
        try {
            Process database = start(root, "python", root.resolve("scripts/setup-local-db.py").toString());
            if (database.waitFor() != 0) throw new IllegalStateException("Local database setup failed. See output above.");
            Process backend = start(root, "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                    root.resolve("scripts/maven.ps1").toString(), "-pl", "backend", "spring-boot:run");
            awaitReady(backend, "http://127.0.0.1:" + BACKEND_PORT + "/actuator/health/readiness", Duration.ofMinutes(3));
            Process frontend = start(root.resolve("frontend"), "node", "node_modules/vite/bin/vite.js", "--port", Integer.toString(FRONTEND_PORT));
            awaitReady(frontend, "http://127.0.0.1:" + FRONTEND_PORT, Duration.ofSeconds(45));
            System.out.println("\nFinishers full stack is ready: http://127.0.0.1:" + FRONTEND_PORT);
            System.out.println("Stop this run configuration to stop frontend and backend. The local database stays available.");
            if (args.length > 1 && args[1].equals("--smoke-test")) return;
            while (backend.isAlive() && frontend.isAlive()) Thread.sleep(500);
            throw new IllegalStateException("An application process stopped; shutting down the other process.");
        } finally {
            stopChildren();
        }
    }

    private static Process start(Path directory, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile()).inheritIO();
        // Use IntelliJ's selected project JDK for Maven and the backend, without global changes.
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        builder.environment().put("FPA_SERVER_PORT", Integer.toString(BACKEND_PORT));
        builder.environment().put("FPA_BACKEND_PORT", Integer.toString(BACKEND_PORT));
        builder.environment().put("SPRING_PROFILES_ACTIVE", "dev");
        Process process = builder.start();
        CHILDREN.add(process);
        return process;
    }

    private static void assertPortFree(int port) throws Exception {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", port));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Port " + port + " is already in use. Stop the existing app first; no process was killed.", exception);
        }
    }

    private static void awaitReady(Process process, String url, Duration timeout) throws Exception {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2)).build();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) throw new IllegalStateException("Startup process exited before readiness: " + url);
            try {
                var response = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).GET().build(),
                        HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200 && process.isAlive()) return;
            } catch (java.io.IOException ignored) { /* Bounded startup polling. */ }
            Thread.sleep(500);
        }
        throw new IllegalStateException("Timed out waiting for " + url);
    }

    private static synchronized void stopChildren() {
        List<Process> owned = new ArrayList<>(CHILDREN);
        CHILDREN.clear();
        for (int i = owned.size() - 1; i >= 0; i--) {
            Process process = owned.get(i);
            if (!process.isAlive()) continue;
            if (System.getProperty("os.name").startsWith("Windows")) {
                try {
                    new ProcessBuilder("taskkill.exe", "/PID", Long.toString(process.pid()), "/T", "/F")
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .redirectError(ProcessBuilder.Redirect.DISCARD).start().waitFor();
                } catch (Exception exception) {
                    System.err.println("Process-tree cleanup fallback: " + exception.getClass().getSimpleName());
                }
            }
            // Capture descendants before terminating their parent, so no Maven/Java child is orphaned.
            List<ProcessHandle> descendants = process.descendants().toList();
            for (int j = descendants.size() - 1; j >= 0; j--) descendants.get(j).destroy();
            process.destroy();
            try { process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            for (ProcessHandle child : descendants) if (child.isAlive()) child.destroyForcibly();
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
