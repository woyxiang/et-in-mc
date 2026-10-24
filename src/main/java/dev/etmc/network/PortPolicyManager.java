package dev.etmc.network;

import dev.etmc.config.NetworkProfile;
import dev.etmc.config.PortRule;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides the inbound exposure policy (plan §5.1) and produces the
 * whitelist arguments handed to EasyTier via the command builder.
 *
 * Modes:
 * - allowlist: only listed tcp/udp ports are reachable through the overlay
 *              (auto-allowed Minecraft port appended when configured).
 * - trusted_all: no whitelist restriction; requires the profile's
 *              trustedAllConfirmed flag, otherwise refuses and stays allowlist.
 */
public final class PortPolicyManager {
    public static final String MODE_ALLOWLIST = "allowlist";
    public static final String MODE_TRUSTED_ALL = "trusted_all";

    /** Resolved policy for a profile. */
    public record Policy(String effectiveMode, List<String> tcpWhitelist,
            List<String> udpWhitelist, List<String> warnings) { }

    /**
     * @param profile            the network profile (host side decides inbound)
     * @param minecraftPort      actual Minecraft server port, or 0 when unknown
     * @param autoAllowMinecraft whether the MC port is auto-whitelisted
     */
    public Policy resolve(NetworkProfile profile, int minecraftPort,
            boolean autoAllowMinecraft) {
        String mode = profile.exposureMode() == null ? MODE_ALLOWLIST
                : profile.exposureMode();
        List<String> warnings = new ArrayList<>();
        if (MODE_TRUSTED_ALL.equals(mode)) {
            if (!profile.trustedAllConfirmed()) {
                warnings.add("trusted_all requested but not confirmed; "
                        + "falling back to allowlist (plan §5.1/§6.3)");
                mode = MODE_ALLOWLIST;
            } else {
                warnings.add("TRUSTED ALL PORTS active: every local service "
                        + "reachable via the overlay virtual IP is exposed");
                return new Policy(mode, List.of(), List.of(), warnings);
            }
        }
        Set<String> tcp = new LinkedHashSet<>(profile.tcpWhitelist());
        Set<String> udp = new LinkedHashSet<>(profile.udpWhitelist());
        if (autoAllowMinecraft && minecraftPort > 0) {
            tcp.add(String.valueOf(minecraftPort));
        }
        return new Policy(mode, List.copyOf(tcp), List.copyOf(udp), warnings);
    }

    /** Convenience for host-side LAN exposure: single rule -> whitelist arg. */
    public static List<PortRule> asRules(Policy policy) {
        List<PortRule> rules = new ArrayList<>();
        for (String entry : policy.tcpWhitelist()) {
            rules.add(new PortRule("allow-tcp-" + entry, PortRule.TCP,
                    parseFirst(entry), "0.0.0.0", parseFirst(entry), true));
        }
        for (String entry : policy.udpWhitelist()) {
            rules.add(new PortRule("allow-udp-" + entry, PortRule.UDP,
                    parseFirst(entry), "0.0.0.0", parseFirst(entry), true));
        }
        return rules;
    }

    private static int parseFirst(String entry) {
        int dash = entry.indexOf('-');
        return Integer.parseInt(dash > 0 ? entry.substring(0, dash) : entry);
    }
}
