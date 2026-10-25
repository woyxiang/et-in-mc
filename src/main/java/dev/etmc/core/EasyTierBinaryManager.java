package dev.etmc.core;

import dev.etmc.util.PlatformDetector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Locates and validates the EasyTier Core binary (plan §7.1).
 * Modes: managed (project-provided pinned build) and custom (user path).
 * Never touches an already-running system instance.
 */
public final class EasyTierBinaryManager {
    /** The only core version this mod is validated against (plan §14 risk 7). */
    public static final String PINNED_VERSION = "2.6.4";

    public record CoreInfo(Path binary, String version, List<String> problems) {
        public boolean ok() {
            return problems.isEmpty();
        }
    }

    private EasyTierBinaryManager() { }

    /** Candidate paths, first existing wins: explicit, managed dir, PATH name. */
    public static Optional<Path> locate(String configuredPath) {
        List<Path> candidates = new ArrayList<>();
        if (configuredPath != null && !configuredPath.isBlank()) {
            candidates.add(Path.of(configuredPath));
        }
        String dir = PlatformDetector.detect().binaryDirName();
        Path managed = Path.of("easytier", dir, PlatformDetector.detect().coreBinaryName());
        candidates.add(managed);
        candidates.add(Path.of(PlatformDetector.detect().coreBinaryName()));
        for (Path p : candidates) {
            if (Files.isRegularFile(p) && Files.isExecutable(p)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /**
     * Full pre-flight: locate, check executable, and verify the reported
     * version starts with the pinned one (2.6.x patch drift tolerated,
     * minor drift reported as a problem but not fatal for custom cores).
     */
    public static CoreInfo validate(String configuredPath) {
        List<String> problems = new ArrayList<>();
        Optional<Path> found = locate(configuredPath);
        if (found.isEmpty()) {
            problems.add("easytier-core binary not found (configured='"
                    + configuredPath + "', managed dir easytier/"
                    + PlatformDetector.detect().binaryDirName() + ", or PATH)");
            return new CoreInfo(null, null, problems);
        }
        Path binary = found.get();
        String version = queryVersion(binary);
        if (version == null) {
            problems.add("could not execute " + binary + " or parse its version");
            return new CoreInfo(binary, null, problems);
        }
        if (!version.startsWith(PINNED_VERSION)) {
            problems.add("easytier version " + version + " differs from pinned "
                    + PINNED_VERSION + "; behaviour is not guaranteed (plan §14-7)");
        }
        return new CoreInfo(binary, version, problems);
    }

    /** Runs `<binary> --version`; returns e.g. "2.6.4" or null on failure. */
    public static String queryVersion(Path binary) {
        try {
            Process p = new ProcessBuilder(binary.toString(), "--version")
                    .redirectErrorStream(true)
                    .start();
            String out;
            try (var is = p.getInputStream()) {
                out = new String(is.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8).trim();
            }
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            // typical: "easytier-core 2.6.4" or "2.6.4-8428a89d"
            for (String token : out.split("\\s+")) {
                if (token.matches("\\d+\\.\\d+\\.\\d+.*")) {
                    return token;
                }
            }
            return null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
