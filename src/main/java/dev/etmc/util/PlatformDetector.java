package dev.etmc.util;

/** Detects OS/Arch to pick the right EasyTier binary name (plan §7.1). */
public final class PlatformDetector {
    public record Platform(String os, String arch) {
        /** easytier-release naming, e.g. easytier-linux-x86_64. */
        public String binaryDirName() {
            String osPart = switch (os) {
                case "linux" -> "linux";
                case "windows" -> "windows";
                case "macos" -> "macos";
                default -> os;
            };
            String archPart = switch (arch) {
                case "x86_64", "amd64" -> "x86_64";
                case "aarch64", "arm64" -> "arm64";
                default -> arch;
            };
            return "easytier-" + osPart + "-" + archPart;
        }

        public String coreBinaryName() {
            return "windows".equals(os) ? "easytier-core.exe" : "easytier-core";
        }
    }

    private PlatformDetector() {
    }

    public static Platform detect() {
        String osProp = System.getProperty("os.name", "").toLowerCase();
        String os = osProp.contains("win") ? "windows"
                : osProp.contains("mac") || osProp.contains("darwin") ? "macos"
                : osProp.contains("linux") ? "linux"
                : osProp;
        String archProp = System.getProperty("os.arch", "").toLowerCase();
        return new Platform(os, archProp);
    }

    public static boolean isLinux() {
        return "linux".equals(detect().os());
    }
}
