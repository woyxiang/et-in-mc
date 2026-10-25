package dev.etmc.core;

import dev.etmc.config.NetworkProfile;
import dev.etmc.util.ProcessLogReader;
import dev.etmc.util.SecretRedactor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns exactly one EasyTier Core child process (plan §7).
 *
 * Key behaviours:
 *  - refuses to start twice; never touches processes it did not spawn;
 *  - a process that dies within the grace window is an ERROR (no auto restart) -
 *    EasyTier exits immediately on any bind failure (P0 finding #3);
 *  - later abnormal exits restart with exponential backoff up to maxRestarts;
 *  - stop() is idempotent and always ends in STOPPED.
 */
public final class EasyTierProcessManager implements AutoCloseable {
    public interface CommandFactory {
        List<String> build(NetworkProfile profile);
    }

    private static final Logger LOG = LoggerFactory.getLogger("etmc");

    private NetworkProfile profile;
    private final CommandFactory commandFactory;
    private final Path workDir;
    private final boolean autoRestart;
    private final int maxRestarts;
    private final long graceMs;

    private final AtomicReference<ProcessState> state = new AtomicReference<>(ProcessState.STOPPED);
    private final List<Consumer<ProcessState>> listeners = new CopyOnWriteArrayList<>();
    private final ProcessLogReader logReader;
    private volatile Process process;
    private volatile long startedAt;
    private volatile int restartCount;
    private volatile boolean stopping;

    public EasyTierProcessManager(NetworkProfile profile, CommandFactory commandFactory, Path workDir) {
        this(profile, commandFactory, workDir, true, 5, 5000);
    }

    public EasyTierProcessManager(NetworkProfile profile, CommandFactory commandFactory, Path workDir,
                                  boolean autoRestart, int maxRestarts, long graceMs) {
        this.profile = profile;
        this.commandFactory = commandFactory;
        this.workDir = workDir;
        this.autoRestart = autoRestart;
        this.maxRestarts = maxRestarts;
        this.graceMs = graceMs;
        this.logReader = new ProcessLogReader(
                new SecretRedactor(profile.networkSecret()), LOG, "[easytier]");
    }

    /** Rebinds the profile (e.g. changed forwards) and (re)starts the core. */
    public synchronized void start(NetworkProfile profile) {
        this.profile = profile;
        start();
    }

    /** True while the core process exists and is not shutting down. */
    public synchronized boolean isRunning() {
        ProcessState st = state();
        return st != ProcessState.STOPPED && st != ProcessState.ERROR
                && st != ProcessState.STOPPING;
    }

    public synchronized void start() {
        if (state.get() != ProcessState.STOPPED && state.get() != ProcessState.ERROR) {
            throw new IllegalStateException("cannot start from state " + state.get());
        }
        List<String> argv = commandFactory.build(profile);
        Path binary = Path.of(argv.get(0));
        if (!Files.isRegularFile(binary) || !Files.isExecutable(binary)) {
            setState(ProcessState.ERROR);
            throw new IllegalStateException("easytier binary missing or not executable: " + binary);
        }
        try {
            Files.createDirectories(workDir);
        } catch (IOException e) {
            setState(ProcessState.ERROR);
            throw new IllegalStateException("cannot create work dir " + workDir, e);
        }
        try {
            process = new ProcessBuilder(argv)
                    .directory(workDir.toFile())
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            setState(ProcessState.ERROR);
            throw new IllegalStateException("failed to spawn easytier process", e);
        }
        stopping = false;
        startedAt = System.currentTimeMillis();
        setState(ProcessState.STARTING);
        logReader.onLine(this::handleCoreLogLine);
        logReader.start(process.getInputStream());
        long pid = process.pid();
        LOG.info("[etmc] easytier started pid={} role={} listen={} rpc=127.0.0.1:{}",
                pid, profile.role(), profile.listenPort(), profile.rpcPort());
        Thread.ofPlatform().name("etmc-process-watch").daemon(true).start(this::watchLoop);
    }

    /**
     * Log-driven state transitions (plan §7.2): network connectivity must be
     * judged separately from process liveness. Lines verified against
     * easytier 2.6.4 output (docs/P0-REPORT.md).
     */
    private void handleCoreLogLine(String line) {
        ProcessState cur = state();
        if (cur == ProcessState.STOPPED || cur == ProcessState.ERROR
                || cur == ProcessState.STOPPING) {
            return;
        }
        if (line.contains("Starting easytier from cli")) {
            if (cur == ProcessState.STARTING) {
                setState(ProcessState.CONNECTING);
            }
        } else if (line.contains("new peer connection added")) {
            if (cur == ProcessState.STARTING || cur == ProcessState.CONNECTING
                    || cur == ProcessState.RECONNECTING) {
                setState(ProcessState.CONNECTED);
                restartCount = 0;
                LOG.info("[etmc] easytier connected to the network");
            }
        } else if (line.contains("handshake failed")
                || line.contains("peer connection lost")
                || line.contains("connection closed during")) {
            if (cur == ProcessState.CONNECTED || cur == ProcessState.READY) {
                setState(ProcessState.RECONNECTING);
                LOG.warn("[etmc] easytier lost network connectivity; reconnecting");
            }
        }
    }

    private void watchLoop() {
        int code;
        try {
            code = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        synchronized (this) {
            if (stopping) {
                setState(ProcessState.STOPPED);
                LOG.info("[etmc] easytier stopped cleanly (exit={})", code);
                return;
            }
            long uptime = System.currentTimeMillis() - startedAt;
            LOG.warn("[etmc] easytier exited unexpectedly code={} uptimeMs={} restarts={}/{}",
                    code, uptime, restartCount, maxRestarts);
            if (uptime < graceMs) {
                // P0 finding #3: bind failures die instantly - restarting cannot help.
                LOG.error("[etmc] easytier died within grace window ({}ms); giving up. Last log:\n{}",
                        graceMs, String.join("\n", logReader.snapshot()));
                setState(ProcessState.ERROR);
                return;
            }
            if (autoRestart && restartCount < maxRestarts) {
                restartCount++;
                setState(ProcessState.RECONNECTING);
                long backoff = Math.min(1000L << Math.min(restartCount, 5), 30_000L);
                Thread.ofPlatform().name("etmc-process-restart").daemon(true).start(() -> {
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    synchronized (EasyTierProcessManager.this) {
                        if (!stopping) {
                            start();
                        }
                    }
                });
            } else {
                setState(ProcessState.ERROR);
            }
        }
    }

    public synchronized void stop() {
        Process p = process;
        if (p == null || !p.isAlive()) {
            setState(ProcessState.STOPPED);
            return;
        }
        stopping = true;
        setState(ProcessState.STOPPING);
        p.destroy();
        try {
            if (!p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                LOG.warn("[etmc] easytier did not exit in 10s, killing forcibly");
                p.destroyForcibly();
                p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
        }
        try {
            logReader.awaitTermination(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        process = null;
        setState(ProcessState.STOPPED);
    }

    public ProcessState state() {
        return state.get();
    }

    public Optional<Long> pid() {
        Process p = process;
        return p != null && p.isAlive() ? Optional.of(p.pid()) : Optional.empty();
    }

    public List<String> recentLog() {
        return logReader.snapshot();
    }

    public void onStateChange(Consumer<ProcessState> listener) {
        listeners.add(listener);
    }

    private void setState(ProcessState next) {
        state.set(next);
        for (var l : listeners) {
            try {
                l.accept(next);
            } catch (RuntimeException e) {
                LOG.warn("[etmc] state listener failed", e);
            }
        }
    }

    @Override
    public void close() {
        stop();
    }
}
