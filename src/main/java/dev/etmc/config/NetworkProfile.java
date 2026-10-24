package dev.etmc.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything needed to launch one EasyTier Core instance (plan §4.1, §6.2).
 * Pure data - no Minecraft or process API here so it can be unit tested.
 */
public final class NetworkProfile {
    public enum Role {
        HOST,
        CLIENT
    }

    private Role role = Role.CLIENT;
    private String binaryPath = "";
    private String networkName = "";
    private String networkSecret = "";
    private List<String> peers = new ArrayList<>();
    /** Local EasyTier listener port (own instance). */
    private int listenPort = 0;
    /** RPC portal bound to 127.0.0.1. */
    private int rpcPort = 0;
    /** Virtual IP of the host node, e.g. 10.126.126.1. */
    private String hostIp = "10.126.126.1";
    /** Own virtual IP for CLIENT role; empty or dhcp=true means DHCP. */
    private String clientIp = "";
    private boolean dhcp = true;
    /** Host only: local CIDR exposed through the virtual network. */
    private String exposeLocalCidr = "127.0.0.1/32";
    private List<PortRule> forwards = new ArrayList<>();
    private List<String> tcpWhitelist = new ArrayList<>();
    private List<String> udpWhitelist = new ArrayList<>();
    /** Inbound exposure mode: "allowlist" (default) or "trusted_all". */
    private String exposureMode = "allowlist";
    /** Explicit user confirmation required for trusted_all (plan §5.1). */
    private boolean trustedAllConfirmed = false;

    public Role role() {
        return role;
    }

    public NetworkProfile role(Role role) {
        this.role = role;
        return this;
    }

    public String binaryPath() {
        return binaryPath;
    }

    public NetworkProfile binaryPath(String binaryPath) {
        this.binaryPath = binaryPath;
        return this;
    }

    public String networkName() {
        return networkName;
    }

    public NetworkProfile networkName(String networkName) {
        this.networkName = networkName;
        return this;
    }

    public String networkSecret() {
        return networkSecret;
    }

    public NetworkProfile networkSecret(String networkSecret) {
        this.networkSecret = networkSecret;
        return this;
    }

    public List<String> peers() {
        return peers;
    }

    public NetworkProfile peers(List<String> peers) {
        this.peers = peers;
        return this;
    }

    public int listenPort() {
        return listenPort;
    }

    public NetworkProfile listenPort(int listenPort) {
        this.listenPort = listenPort;
        return this;
    }

    public int rpcPort() {
        return rpcPort;
    }

    public NetworkProfile rpcPort(int rpcPort) {
        this.rpcPort = rpcPort;
        return this;
    }

    public String hostIp() {
        return hostIp;
    }

    public NetworkProfile hostIp(String hostIp) {
        this.hostIp = hostIp;
        return this;
    }

    public String clientIp() {
        return clientIp;
    }

    public NetworkProfile clientIp(String clientIp) {
        this.clientIp = clientIp;
        return this;
    }

    public boolean dhcp() {
        return dhcp;
    }

    public NetworkProfile dhcp(boolean dhcp) {
        this.dhcp = dhcp;
        return this;
    }

    public String exposeLocalCidr() {
        return exposeLocalCidr;
    }

    public NetworkProfile exposeLocalCidr(String exposeLocalCidr) {
        this.exposeLocalCidr = exposeLocalCidr;
        return this;
    }

    public List<PortRule> forwards() {
        return forwards;
    }

    public NetworkProfile forwards(List<PortRule> forwards) {
        this.forwards = forwards;
        return this;
    }

    public List<String> tcpWhitelist() {
        return tcpWhitelist;
    }

    public NetworkProfile tcpWhitelist(List<String> tcpWhitelist) {
        this.tcpWhitelist = tcpWhitelist;
        return this;
    }

    public List<String> udpWhitelist() {
        return udpWhitelist;
    }

    public NetworkProfile udpWhitelist(List<String> udpWhitelist) {
        this.udpWhitelist = udpWhitelist;
        return this;
    }

    /** Virtual IP this node assigns itself, or empty for DHCP. */
    public String ownVirtualIp() {
        return role == Role.HOST ? hostIp : clientIp;
    }

    /** CIDR used by the host proxy-networks mapping, or null when not host. */
    public String hostProxyNetworkMapping() {
        if (role != Role.HOST) {
            return null;
        }
        return exposeLocalCidr + "->" + hostIp + "/" + prefixLengthOf(exposeLocalCidr);
    }

    public static int prefixLengthOf(String cidr) {
        int slash = cidr.lastIndexOf('/');
        if (slash < 0) {
            return 32;
        }
        return Integer.parseInt(cidr.substring(slash + 1));
    }

    public static String networkPartOf(String cidr) {
        int slash = cidr.lastIndexOf('/');
        return slash < 0 ? cidr : cidr.substring(0, slash);
    }


    public String exposureMode() {
        return exposureMode;
    }

    public NetworkProfile exposureMode(String exposureMode) {
        this.exposureMode = exposureMode;
        return this;
    }

    public boolean trustedAllConfirmed() {
        return trustedAllConfirmed;
    }

    public NetworkProfile trustedAllConfirmed(boolean trustedAllConfirmed) {
        this.trustedAllConfirmed = trustedAllConfirmed;
        return this;
    }
}
