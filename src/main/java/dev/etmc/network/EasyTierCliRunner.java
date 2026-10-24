package dev.etmc.network;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around the `easytier-cli` binary (ships with easytier-core).
 * Uses the local RPC portal only; never touches other instances.
 */
public final class EasyTierCliRunner {
    private final Path cliBinary;

    public EasyTierCliRunner(String coreBinaryPath) {
        Path core = Path.of(coreBinaryPath);
        this.cliBinary = core.getParent().resolve("easytier-cli");
    }

    public boolean available() {
        return Files.isRegularFile(cliBinary) && Files.isExecutable(cliBinary);
    }

    public Result run(int rpcPort, String subcommand, long timeoutMs) {
        if (!available()) {
            return new Result(-1, "", "easytier-cli not found next to core binary");
        }
        try {
            Process p = new ProcessBuilder(
                    cliBinary.toString(), "-p", "127.0.0.1:" + rpcPort, subcommand)
                    .redirectErrorStream(true)
                    .start();
            boolean done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!done) {
                p.destroyForcibly();
                return new Result(-1, "", "easytier-cli timeout after " + timeoutMs + "ms");
            }
            String out = new String(p.getInputStream().readAllBytes());
            return new Result(p.exitValue(), out, "");
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new Result(-1, "", e.toString());
        }
    }

    public record Result(int exitCode, String stdout, String error) {
        public boolean ok() {
            return exitCode == 0;
        }
    }

    /** Markdown-ish table parser shared by peer/route output. */
    public static List<List<String>> parseTable(String output) {
        List<List<String>> rows = new ArrayList<>();
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("|")) {
                continue;
            }
            String[] cells = trimmed.split("\\|");
            boolean isSeparator = true;
            List<String> row = new ArrayList<>();
            for (String c : cells) {
                String cell = c.trim();
                if (!cell.isEmpty() && !cell.matches("-+")) {
                    isSeparator = false;
                }
                row.add(cell);
            }
            if (row.size() < 2 || isSeparator) {
                continue;
            }
            rows.add(row);
        }
        return rows;
    }
}
