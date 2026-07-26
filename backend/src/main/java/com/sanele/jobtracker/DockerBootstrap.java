package com.sanele.jobtracker;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Makes the app self-sufficient on a fresh clone: on startup it ensures Docker is
 * present (installing + launching Docker Desktop automatically if it isn't), then
 * starts the Postgres container — and stops that container when the backend stops,
 * so the database only runs while the backend runs.
 *
 * <ul>
 *   <li>Postgres already reachable → does nothing (and won't stop a DB you own).</li>
 *   <li>Docker missing → runs {@code install-docker.ps1} (installs, launches, waits).</li>
 *   <li>It started Postgres → registers a shutdown hook to stop it.</li>
 *   <li>{@code JOBTRACKER_AUTO_DB=false} skips all of this (prod / CI / self-managed).</li>
 * </ul>
 */
final class DockerBootstrap {

    private static final String CONTAINER = "jobtracker-postgres";
    private static final int DB_PORT = 5433;

    /** Resolved docker executable — "docker" on PATH, or a known absolute path. */
    private static String docker = "docker";

    private DockerBootstrap() {}

    /** Ensure Docker + Postgres are up before Spring connects. Never throws. */
    static void ensureDatabaseUp() {
        try {
            if ("false".equalsIgnoreCase(env("JOBTRACKER_AUTO_DB", "true"))) return;

            if (isPortOpen(DB_PORT)) {
                log("Postgres already running on :" + DB_PORT + " — leaving it as is.");
                return;
            }

            if (!resolveDocker()) {
                log("Docker not found — installing and launching it automatically…");
                autoInstallDocker();
                if (!resolveDocker()) {
                    err("Docker still isn't available. Run ./install-docker.ps1, then start the backend again.");
                    return;
                }
            }
            waitForDockerEngine(Duration.ofMinutes(3));   // ensure the engine (not just the CLI) is up

            log("Starting the Postgres container for this session…");
            boolean started = exec(docker, "start", CONTAINER)
                    || composeUp()
                    || dockerRun();
            if (!started) { err("Could not start Postgres via Docker — please start it manually."); return; }

            if (waitForPort(DB_PORT, Duration.ofSeconds(60))) {
                log("Postgres is ready on :" + DB_PORT);
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    log("Backend shutting down — stopping the Postgres container.");
                    exec(docker, "stop", CONTAINER);
                }, "postgres-stopper"));
            } else {
                err("Postgres did not become ready in time on :" + DB_PORT + ".");
            }
        } catch (Exception e) {
            err("Docker bootstrap skipped: " + e.getMessage());
        }
    }

    // ---- docker resolution / install -----------------------------------------

    /** True if a working docker CLI is found (on PATH or the standard install path). */
    private static boolean resolveDocker() {
        if (exec("docker", "--version")) { docker = "docker"; return true; }
        String pf = env("ProgramFiles", "C:\\Program Files");
        File f = new File(pf, "Docker\\Docker\\resources\\bin\\docker.exe");
        if (f.isFile() && exec(f.getPath(), "--version")) { docker = f.getPath(); return true; }
        return false;
    }

    /** Install + launch Docker Desktop via the bundled PowerShell script (best effort). */
    private static void autoInstallDocker() {
        File script = findFile("install-docker.ps1");
        if (script != null) {
            execInherit(Duration.ofMinutes(20), "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-File", script.getPath());
        } else {
            // Fallback: install with winget and launch Docker Desktop directly.
            execInherit(Duration.ofMinutes(20), "powershell", "-NoProfile", "-Command",
                    "winget install -e --id Docker.DockerDesktop --accept-source-agreements --accept-package-agreements");
            launchDockerDesktop();
        }
    }

    private static void launchDockerDesktop() {
        String pf = env("ProgramFiles", "C:\\Program Files");
        File dd = new File(pf, "Docker\\Docker\\Docker Desktop.exe");
        if (dd.isFile()) exec("cmd", "/c", "start", "", dd.getPath());
    }

    /** Wait until `docker info` succeeds (engine actually running, not just installed). */
    private static boolean waitForDockerEngine(Duration timeout) {
        long end = System.nanoTime() + timeout.toNanos();
        boolean launched = false;
        while (System.nanoTime() < end) {
            if (exec(docker, "info")) return true;
            if (!launched) { launchDockerDesktop(); launched = true; }   // nudge it once
            sleep(2000);
        }
        return false;
    }

    // ---- postgres start helpers ----------------------------------------------

    private static boolean composeUp() {
        File f = findFile("docker-compose.yml", "compose.yaml", "compose.yml");
        if (f == null) return false;
        return exec(docker, "compose", "-f", f.getPath(), "up", "-d", "postgres")
                || exec("docker-compose", "-f", f.getPath(), "up", "-d", "postgres");
    }

    private static boolean dockerRun() {
        return exec(docker, "run", "-d", "--name", CONTAINER,
                "-e", "POSTGRES_DB=jobtracker", "-e", "POSTGRES_USER=postgres",
                "-e", "POSTGRES_PASSWORD=postgres", "-p", DB_PORT + ":5432",
                "postgres:16-alpine");
    }

    // ---- generic helpers ------------------------------------------------------

    /** Look for a file in the working dir or the parent (backend runs from ./backend). */
    private static File findFile(String... names) {
        for (String n : names) {
            for (String base : new String[]{n, ".." + File.separator + n}) {
                File f = new File(base);
                if (f.isFile()) return f.getAbsoluteFile();
            }
        }
        return null;
    }

    private static boolean isPortOpen(int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("localhost", port), 800);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean waitForPort(int port, Duration timeout) {
        long end = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < end) {
            if (isPortOpen(port)) return true;
            sleep(1000);
        }
        return false;
    }

    /** Run a command, discard output; true iff exit 0 within 90s. */
    private static boolean exec(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(90, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Run a long command with inherited I/O (user sees progress); true iff exit 0. */
    private static boolean execInherit(Duration timeout, String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).inheritIO().start();
            if (!p.waitFor(timeout.toMinutes(), TimeUnit.MINUTES)) { p.destroyForcibly(); return false; }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        if (v == null) v = System.getProperty(key);
        return v == null ? def : v;
    }

    private static void log(String m) { System.out.println("[db] " + m); }
    private static void err(String m) { System.err.println("[db] " + m); }
}
