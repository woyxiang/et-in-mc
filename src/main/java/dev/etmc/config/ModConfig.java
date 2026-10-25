package dev.etmc.config;

import com.electronwill.nightconfig.core.file.FileConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the three etmc TOML files (plan §6.1):
 *  etmc-common.toml   - runtime options
 *  etmc-networks.toml - network profile + port rules (local, never synced)
 *  etmc-private.toml  - the network secret
 *
 * Loaded by the mod itself (not NeoForge's config system) so that the private
 * file can never be synced to clients. Missing files are created from defaults.
 */
public final class ModConfig {
    public record Loaded(NetworkProfile profile, List<String> errors, List<String> created) {
    }

    private ModConfig() {
    }

    public static Loaded load(Path configDir) {
        List<String> errors = new ArrayList<>();
        List<String> created = new ArrayList<>();

        Path commonFile = configDir.resolve("etmc-common.toml");
        Path networkFile = configDir.resolve("etmc-networks.toml");
        Path privateFile = configDir.resolve("etmc-private.toml");

        ensureDefaults(commonFile, DEFAULT_COMMON, created);
        ensureDefaults(networkFile, DEFAULT_NETWORKS, created);
        ensureDefaults(privateFile, DEFAULT_PRIVATE, created);

        NetworkProfile profile = new NetworkProfile();

        try (FileConfig common = FileConfig.builder(commonFile).build()) {
            common.load();
            profile.binaryPath(common.getOrElse("core.binary_path", ""));
        } catch (Exception e) {
            errors.add("etmc-common.toml: " + e.getMessage());
        }

        try (FileConfig net = FileConfig.builder(networkFile).build()) {
            net.load();
            String role = net.getOrElse("network.role", "client");
            profile.role("host".equalsIgnoreCase(role)
                    ? NetworkProfile.Role.HOST : NetworkProfile.Role.CLIENT);
            profile.networkName(net.getOrElse("network.name", ""));
            profile.listenPort(net.getOrElse("network.listen_port", 0));
            profile.rpcPort(net.getOrElse("network.rpc_port", 0));
            profile.hostIp(net.getOrElse("network.host_ip", "10.126.126.1"));
            profile.clientIp(net.getOrElse("network.client_ip", ""));
            profile.dhcp(net.getOrElse("network.dhcp", true));
            profile.exposeLocalCidr(net.getOrElse("network.expose_local_cidr", "127.0.0.1/32"));
            profile.peers(new ArrayList<>(net.getOrElse("network.peers", List.of())));
            profile.tcpWhitelist(new ArrayList<>(net.getOrElse("exposure.tcp_whitelist", List.of())));
            profile.udpWhitelist(new ArrayList<>(net.getOrElse("exposure.udp_whitelist", List.of())));
            profile.exposureMode(net.getOrElse("exposure.mode", "allowlist"));
            profile.trustedAllConfirmed(Boolean.parseBoolean(String.valueOf(
                    net.getOrElse("security.trusted_all_confirmed", false))));
            List<? extends PortRule> rules = readRules(net);
            profile.forwards(new ArrayList<>(rules));
        } catch (Exception e) {
            errors.add("etmc-networks.toml: " + e.getMessage());
        }

        try (FileConfig priv = FileConfig.builder(privateFile).build()) {
            priv.load();
            profile.networkSecret(priv.getOrElse("network.secret", ""));
        } catch (Exception e) {
            errors.add("etmc-private.toml: " + e.getMessage());
        }

        errors.addAll(ConfigValidator.validate(profile));
        return new Loaded(profile, errors, created);
    }

    /**
     * Persists a profile back to etmc-networks.toml + etmc-private.toml
     * (plan §4.2 step 4). The secret never touches the networks file.
     * Unknown keys are preserved; comments are not.
     */
    public static void save(NetworkProfile profile, Path configDir) {
        Path networkFile = configDir.resolve("etmc-networks.toml");
        Path privateFile = configDir.resolve("etmc-private.toml");
        try (FileConfig net = FileConfig.builder(networkFile).build()) {
            net.load();
            net.set("network.role", profile.role() == NetworkProfile.Role.HOST ? "host" : "client");
            net.set("network.name", profile.networkName());
            net.set("network.listen_port", profile.listenPort());
            net.set("network.rpc_port", profile.rpcPort());
            net.set("network.host_ip", profile.hostIp());
            net.set("network.client_ip", profile.clientIp());
            net.set("network.dhcp", profile.dhcp());
            net.set("network.expose_local_cidr", profile.exposeLocalCidr());
            net.set("network.peers", profile.peers());
            net.set("exposure.mode", profile.exposureMode());
            net.set("exposure.tcp_whitelist", profile.tcpWhitelist());
            net.set("exposure.udp_whitelist", profile.udpWhitelist());
            net.set("security.trusted_all_confirmed", profile.trustedAllConfirmed());
            List<com.electronwill.nightconfig.core.Config> rules = new ArrayList<>();
            for (PortRule r : profile.forwards()) {
                var c = com.electronwill.nightconfig.core.Config.inMemory();
                c.set("name", r.name());
                c.set("protocol", r.protocol());
                c.set("local_port", r.localPort());
                c.set("remote_host", r.remoteHost());
                c.set("remote_port", r.remotePort());
                c.set("enabled", r.enabled());
                rules.add(c);
            }
            net.set("client_proxy.rules", rules);
            net.save();
        } catch (Exception e) {
            throw new RuntimeException("failed to save etmc-networks.toml: " + e.getMessage(), e);
        }
        try (FileConfig priv = FileConfig.builder(privateFile).build()) {
            priv.load();
            priv.set("network.secret", profile.networkSecret() == null ? "" : profile.networkSecret());
            priv.save();
        } catch (Exception e) {
            throw new RuntimeException("failed to save etmc-private.toml: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<PortRule> readRules(FileConfig net) {
        List<PortRule> rules = new ArrayList<>();
        var raw = net.get("client_proxy.rules");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof com.electronwill.nightconfig.core.Config c) {
                    rules.add(new PortRule(
                            c.getOrElse("name", "rule"),
                            c.getOrElse("protocol", "tcp"),
                            c.getOrElse("local_port", 0),
                            c.getOrElse("remote_host", ""),
                            c.getOrElse("remote_port", 0),
                            c.getOrElse("enabled", false)));
                }
            }
        }
        return rules;
    }

    private static void ensureDefaults(Path file, String content, List<String> created) {
        if (!Files.exists(file)) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, content);
                created.add(file.getFileName().toString());
            } catch (Exception e) {
                created.add(file.getFileName().toString() + " FAILED: " + e.getMessage());
            }
        }
    }

    private static final String DEFAULT_COMMON = """
            config_version = 1

            [core]
            # managed = mod ships and manages the binary; custom = user provided path
            mode = "managed"
            # Absolute path to easytier-core binary (empty = bundled path lookup)
            binary_path = ""
            auto_start = false
            auto_restart = true
            no_tun = true

            [security]
            redact_secrets_in_logs = true
            """;

    private static final String DEFAULT_NETWORKS = """
            config_version = 1

            [network]
            role = "client"
            name = ""
            peers = []
            listen_port = 0
            rpc_port = 0
            host_ip = "10.126.126.1"
            client_ip = ""
            dhcp = true
            expose_local_cidr = "127.0.0.1/32"

            [minecraft]
            auto_detect_port = true
            preferred_port = 25565
            local_join_port = 25565

            [exposure]
            mode = "allowlist"
            auto_allow_minecraft = true
            tcp_whitelist = []
            udp_whitelist = []

            [[client_proxy.rules]]
            name = "Minecraft"
            protocol = "tcp"
            local_port = 25565
            remote_host = "10.126.126.1"
            remote_port = 25565
            enabled = false
            """;

    private static final String DEFAULT_PRIVATE = """
            config_version = 1

            # This file stays on disk and is never synced to clients.
            [network]
            secret = ""
            """;
}
