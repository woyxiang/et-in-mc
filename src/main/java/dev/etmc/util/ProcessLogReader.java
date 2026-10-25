package dev.etmc.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.slf4j.Logger;

/**
 * Consumes a child process stdout/stderr stream line by line,
 * redacts secrets, logs them and keeps a bounded ring buffer
 * for crash diagnostics (plan §7.1, §7.3).
 */
public final class ProcessLogReader {
    private static final int BUFFER_LINES = 200;

    private final Deque<String> ring = new ArrayDeque<>();
    private final SecretRedactor redactor;
    private final Logger logger;
    private final String prefix;
    private volatile Thread thread;

    public ProcessLogReader(SecretRedactor redactor, Logger logger, String prefix) {
        this.redactor = redactor;
        this.logger = logger;
        this.prefix = prefix;
    }

    private volatile java.util.function.Consumer<String> onLine;

    /** Optional per-line hook used by the process state machine. */
    public ProcessLogReader onLine(java.util.function.Consumer<String> consumer) {
        this.onLine = consumer;
        return this;
    }

    public void start(InputStream in) {
        thread = Thread.ofPlatform().name("etmc-process-log").daemon(true).unstarted(() -> {
            try (var reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String safe = SecretRedactor.maskSecretArg(redactor.redact(line));
                    record(safe);
                    logger.info("{} {}", prefix, safe);
                    var hook = onLine;
                    if (hook != null) {
                        try {
                            hook.accept(safe);
                        } catch (RuntimeException e) {
                            logger.debug("{} state hook failed: {}", prefix, e.toString());
                        }
                    }
                }
            } catch (IOException e) {
                // Stream closed by normal shutdown or abrupt process death - both fine here.
                if (!"Stream closed".equals(e.getMessage())) {
                    logger.debug("{} log stream ended: {}", prefix, e.toString());
                }
            }
        });
        thread.start();
    }

    private synchronized void record(String line) {
        if (ring.size() >= BUFFER_LINES) {
            ring.pollFirst();
        }
        ring.addLast(line);
    }

    public synchronized List<String> snapshot() {
        return new ArrayList<>(ring);
    }

    public void awaitTermination(long millis) throws InterruptedException {
        Thread t = thread;
        if (t != null) {
            t.join(millis);
        }
    }
}
