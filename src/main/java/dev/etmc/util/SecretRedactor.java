package dev.etmc.util;

/**
 * Removes network secrets from log lines (plan §7.1: secrets never reach logs).
 */
public final class SecretRedactor {
    private final String secret;

    public SecretRedactor(String secret) {
        this.secret = (secret == null || secret.isBlank()) ? null : secret;
    }

    public String redact(String line) {
        if (secret == null) {
            return line;
        }
        return line.replace(secret, "***");
    }

    /** Masks "--network-secret VALUE" pairs even without knowing the secret. */
    public static String maskSecretArg(String line) {
        return line.replaceAll("(--network-secret[= ])(\\S+)", "$1***");
    }
}
