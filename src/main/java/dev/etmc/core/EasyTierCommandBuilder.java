package dev.etmc.core;

import dev.etmc.config.ConfigValidator;
import dev.etmc.config.NetworkProfile;
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
        argv.add("--peers");
        argv.add(String.join(" ", p.peers()));
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
            argv.add("--tcp-whitelist");
            argv.add(String.join(" ", p.tcpWhitelist()));
        }
        if (!p.udpWhitelist().isEmpty()) {
            argv.add("--udp-whitelist");
            argv.add(String.join(" ", p.udpWhitelist()));
        }
    }
}
