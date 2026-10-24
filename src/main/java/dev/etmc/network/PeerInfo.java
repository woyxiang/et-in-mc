package dev.etmc.network;

/**
 * Snapshot of one peer as reported by `easytier-cli peer`.
 * All fields are raw strings; parsing tolerates cosmetic changes.
 */
public record PeerInfo(
        String virtualIp,
        String hostname,
        String cost,
        String latencyMs,
        String lossRate,
        String rxPackets,
        String txPackets,
        String tunnelProto,
        String natType,
        String version) {

    public boolean isHost(String hostIp) {
        return virtualIp != null && virtualIp.equals(hostIp);
    }
}
