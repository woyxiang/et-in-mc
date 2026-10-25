package dev.etmc.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Static validation of network profiles (plan §6.3) plus the constraints
 * discovered during P0 (docs/P0-REPORT.md §3):
 *  - proxy-networks mapping must have equal prefix lengths on both sides;
 *  - local forward ports must not collide with own listeners or each other;
 *  - quick-exit risk: invalid ports would make Core exit immediately.
 */
public final class ConfigValidator {
    private static final Pattern IPV4 = Pattern.compile(
            "^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private ConfigValidator() {
    }

    /** @return list of human readable errors; empty when the profile is valid. */
    public static List<String> validate(NetworkProfile p) {
        List<String> errors = new ArrayList<>();
        if (p.networkName() == null || p.networkName().isBlank()) {
            errors.add("network name must not be empty");
        }
        if (p.networkSecret() == null || p.networkSecret().isBlank()) {
            errors.add("network secret must not be empty");
        }
        if (p.binaryPath() == null || p.binaryPath().isBlank()) {
            errors.add("easytier binary path must not be empty");
        }
        if (p.role() == NetworkProfile.Role.CLIENT && p.peers().isEmpty()) {
            errors.add("client needs at least one peer URL");
        }
        checkPort(errors, "listenPort", p.listenPort());
        checkPort(errors, "rpcPort", p.rpcPort());
        if (p.listenPort() == p.rpcPort()) {
            errors.add("listenPort and rpcPort must differ");
        }

        // Own virtual IP
        String own = p.ownVirtualIp();
        if (p.role() == NetworkProfile.Role.HOST) {
            if (!isIpv4(own)) {
                errors.add("host virtual IP is not a valid IPv4: " + own);
            }
        } else if (!p.dhcp()) {
            if (!isIpv4(own)) {
                errors.add("client virtual IP is not a valid IPv4: " + own);
            }
        }
        if (!isIpv4(p.hostIp())) {
            errors.add("hostIp is not a valid IPv4: " + p.hostIp());
        }

        for (String peer : p.peers()) {
            if (!isValidPeerUrl(peer)) {
                errors.add("peer URL must be tcp|udp|wg://host:port, got: " + peer);
            }
        }

        if ("trusted_all".equals(p.exposureMode()) && !p.trustedAllConfirmed()) {
            errors.add("trusted_all requires trusted_all_confirmed=true "
                    + "(plan §5.1: explicit user confirmation mandatory)");
        }
        if (p.role() == dev.etmc.config.NetworkProfile.Role.HOST
                && p.exposeLocalCidr() != null && p.exposeLocalCidr().contains("/")) {
            int prefix = NetworkProfile.prefixLengthOf(p.exposeLocalCidr());
            if (prefix < 24) {
                errors.add("proxy-networks mapping prefix too broad: /" + prefix
                        + " (minimum /24, equal prefix lengths are mandatory)");
            }
        }
        // Host proxy mapping: equal prefix length on both sides (P0 finding #2).
        if (p.role() == NetworkProfile.Role.HOST) {
            String mapping = p.hostProxyNetworkMapping();
            String[] parts = mapping.split("->");
            int leftLen = NetworkProfile.prefixLengthOf(parts[0]);
            int rightLen = NetworkProfile.prefixLengthOf(parts[1]);
            if (leftLen != rightLen) {
                errors.add("proxy-networks mapping must use equal prefix lengths: " + mapping);
            }
            if (!isIpv4(NetworkProfile.networkPartOf(parts[0]))) {
                errors.add("proxy-networks local CIDR is not valid IPv4: " + parts[0]);
            }
        }

        // Forward rules
        Set<Integer> localTcp = new HashSet<>();
        Set<Integer> localUdp = new HashSet<>();
        for (PortRule rule : p.forwards()) {
            if (!rule.isTcp() && !rule.isUdp()) {
                errors.add("rule '" + rule.name() + "' protocol must be tcp or udp");
                continue;
            }
            checkPort(errors, "rule '" + rule.name() + "' localPort", rule.localPort());
            checkPort(errors, "rule '" + rule.name() + "' remotePort", rule.remotePort());
            if (!isIpv4(rule.remoteHost())) {
                errors.add("rule '" + rule.name() + "' remoteHost is not IPv4: " + rule.remoteHost());
            }
            if (rule.isTcp() ? !localTcp.add(rule.localPort()) : !localUdp.add(rule.localPort())) {
                errors.add("duplicate local " + rule.protocol() + " forward port: " + rule.localPort());
            }
            if (rule.localPort() == p.listenPort() || rule.localPort() == p.rpcPort()) {
                errors.add("rule '" + rule.name() + "' localPort collides with own listener/rpc port");
            }
        }

        // Whitelists: entries are single ports or ranges "a-b" with a<=b.
        checkWhitelist(errors, "tcpWhitelist", p.tcpWhitelist());
        checkWhitelist(errors, "udpWhitelist", p.udpWhitelist());
        return errors;
    }

    private static void checkWhitelist(List<String> errors, String what, List<String> entries) {
        for (String entry : entries) {
            String e = entry.trim();
            int dash = e.indexOf('-');
            if (dash < 0) {
                checkPort(errors, what + " entry", parsePort(e));
            } else {
                int a = parsePort(e.substring(0, dash));
                int b = parsePort(e.substring(dash + 1));
                checkPort(errors, what + " range start", a);
                checkPort(errors, what + " range end", b);
                if (a > b) {
                    errors.add(what + " range start greater than end: " + entry);
                }
            }
        }
    }

    private static int parsePort(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void checkPort(List<String> errors, String what, int port) {
        if (port < 1 || port > 65535) {
            errors.add(what + " must be in [1,65535], got: " + port);
        }
    }

    private static boolean isValidPeerUrl(String url) {
        int sep = url.indexOf("://");
        if (sep <= 0) {
            return false;
        }
        String scheme = url.substring(0, sep);
        if (!scheme.equals("tcp") && !scheme.equals("udp") && !scheme.equals("wg")) {
            return false;
        }
        String rest = url.substring(sep + 3);
        int colon = rest.lastIndexOf(':');
        if (colon < 0) {
            return false;
        }
        return parsePort(rest.substring(colon + 1)) > 0;
    }

    public static boolean isIpv4(String s) {
        if (s == null) {
            return false;
        }
        var m = IPV4.matcher(s);
        if (!m.matches()) {
            return false;
        }
        for (int i = 1; i <= 4; i++) {
            if (Integer.parseInt(m.group(i)) > 255) {
                return false;
            }
        }
        return true;
    }
}
