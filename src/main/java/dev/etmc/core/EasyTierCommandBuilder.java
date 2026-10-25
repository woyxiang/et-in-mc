package dev.etmc.core;

import dev.etmc.config.ConfigValidator;
import dev.etmc.config.NetworkProfile;
import dev.etmc.config.PortRule;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds EasyTier Core command lines from a {@link NetworkProfile}.
 * The recipes mirror docs/P0-REPORT.md §1 exactly (verified working).
 */
public final class EasyTierCommandBuilder implements EasyTierProcessManager.CommandFactory {

    @Override
    public List<String> build(NetworkProfile p) {
        var errors = ConfigValidator.validate(p);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("invalid network profile: " + String.join("; ", errors));
        }
        return p.role() == NetworkProfile.Role.HOST ? buildHost(p) : buildClient(p);
    }

    /** Verified host recipe: no-tun + smoltcp subnet proxy exposing local services. */
    private List<String> buildHost(NetworkProfile p) {
        List<String> argv = baseArgs(p);
        argv.add("--ipv4");
        argv.add(p.hostIp());
        argv.add("--use-smoltcp");
        argv.add("true");
        argv.add("--proxy-networks");
        argv.add(p.hostProxyNetworkMapping());
        // Host listener must accept remote peers: bind all interfaces.
        argv.add("--listeners");
        argv.add("tcp://0.0.0.0:" + p.listenPort());
        addWhitelists(argv, p);
        argv.add("--rpc-portal");
        argv.add("127.0.0.1:" + p.rpcPort());
        return argv;
    }

    /** Verified client recipe: own listener on loopback + --port-forward to host services. */
    private List<String> buildClient(NetworkProfile p) {
        List<String> argv = baseArgs(p);
        if (!p.dhcp() && !p.clientIp().isBlank()) {
            argv.add("--ipv4");
            argv.add(p.clientIp());
        } else {
            argv.add("--dhcp");
        }
        argv.add("--listeners");
        argv.add("tcp://127.0.0.1:" + p.listenPort());
        // repeat the flag per peer; easytier treats each as a separate peer URL
        for (String peer : p.peers()) {
            argv.add("--peers");
            argv.add(peer);
        }
        for (PortRule rule : p.forwards()) {
            if (rule.enabled()) {
                argv.add("--port-forward");
                argv.add(rule.toPortForwardArg());
            }
        }
        argv.add("--rpc-portal");
        argv.add("127.0.0.1:" + p.rpcPort());
        return argv;
    }

    private List<String> baseArgs(NetworkProfile p) {
        List<String> argv = new ArrayList<>();
        argv.add(p.binaryPath());
        argv.add("--network-name");
        argv.add(p.networkName());
        argv.add("--network-secret");
        argv.add(p.networkSecret());
        argv.add("--no-tun");
        return argv;
    }

    private void addWhitelists(List<String> argv, NetworkProfile p) {
        if (!p.tcpWhitelist().isEmpty()) {
            // each entry must be its own argv element; easytier rejects
            // "22387 25565" as one string ("Invalid port number")
            argv.add("--tcp-whitelist");
            argv.addAll(p.tcpWhitelist());
        }
        if (!p.udpWhitelist().isEmpty()) {
            argv.add("--udp-whitelist");
            argv.addAll(p.udpWhitelist());
        }
    }
}
