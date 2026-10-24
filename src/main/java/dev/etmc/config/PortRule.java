package dev.etmc.config;

/**
 * One client-side port-forward rule (EasyTier --port-forward).
 *
 * @param name       human readable rule name
 * @param protocol   "tcp" or "udp"
 * @param localPort  local listen port on the Minecraft machine
 * @param remoteHost target IP inside the EasyTier virtual network (usually the host IP)
 * @param remotePort target port at the remote node
 * @param enabled    rule participates in command building only when true
 */
public record PortRule(
        String name,
        String protocol,
        int localPort,
        String remoteHost,
        int remotePort,
        boolean enabled) {

    public static final String TCP = "tcp";
    public static final String UDP = "udp";

    public PortRule {
        if (protocol != null) {
            protocol = protocol.toLowerCase();
        }
    }

    public boolean isTcp() {
        return TCP.equals(protocol);
    }

    public boolean isUdp() {
        return UDP.equals(protocol);
    }

    /** EasyTier CLI representation: tcp://0.0.0.0:LOCAL/REMOTE_HOST:REMOTE_PORT */
    public String toPortForwardArg() {
        return protocol + "://0.0.0.0:" + localPort + "/" + remoteHost + ":" + remotePort;
    }
}
