package dev.etmc.network;

import java.util.List;

/**
 * Parsed snapshot of `easytier-cli node` (key-value table, v2.6.4).
 */
public record NodeInfo(
        String virtualIp,
        String hostname,
        String peerId,
        String publicIpv4,
        String stunType) {

    public static final NodeInfo EMPTY =
            new NodeInfo("", "", "", "", "");

    public static NodeInfo parse(String output) {
        String virtualIp = "", hostname = "", peerId = "", publicIpv4 = "", stun = "";
        for (String line : output.split("\n")) {
            String t = line.trim();
            if (!t.startsWith("|")) {
                continue;
            }
            String[] cells = t.split("\\|");
            if (cells.length < 3) {
                continue;
            }
            String key = cells[1].trim();
            String val = cells.length > 2 ? cells[2].trim() : "";
            switch (key) {
                case "Virtual IP" -> virtualIp = val;
                case "Hostname" -> hostname = val;
                case "Peer ID" -> peerId = val;
                case "Public IPv4" -> publicIpv4 = val;
                case "UDP Stun Type" -> stun = val;
                default -> { }
            }
        }
        return new NodeInfo(stripPrefixLen(virtualIp), hostname, peerId, publicIpv4, stun);
    }

    /** "10.126.126.4/24" -> "10.126.126.4"; passthrough when no prefix len. */
    public static String stripPrefixLen(String ip) {
        if (ip == null) {
            return "";
        }
        int slash = ip.indexOf('/');
        return slash > 0 ? ip.substring(0, slash) : ip;
    }
}
