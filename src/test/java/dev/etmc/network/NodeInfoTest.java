package dev.etmc.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodeInfoTest {
    private static final String NODE_OUTPUT = """
            | Virtual IP     | 10.126.126.4/24                        |
            |----------------|----------------------------------------|
            | Hostname       | slug                                   |
            | Proxy CIDRs    |                                        |
            | Peer ID        | 114554523                              |
            | Public IPv6    | 2408:4006:12a2:c200:e072:1f9:1a93:ca5b |
            | Public IPv4    | 8.137.122.37                           |
            | UDP Stun Type  | PortRestricted                         |
            | Interface IPv4 | 172.25.87.100                          |
            """;

    @Test
    void parsesNodeTable() {
        NodeInfo n = NodeInfo.parse(NODE_OUTPUT);
        assertEquals("10.126.126.4", n.virtualIp());
        assertEquals("slug", n.hostname());
        assertEquals("114554523", n.peerId());
        assertEquals("8.137.122.37", n.publicIpv4());
        assertEquals("PortRestricted", n.stunType());
    }

    @Test
    void stripsPrefixLen() {
        assertEquals("10.0.0.1", NodeInfo.stripPrefixLen("10.0.0.1/24"));
        assertEquals("10.0.0.1", NodeInfo.stripPrefixLen("10.0.0.1"));
        assertEquals("", NodeInfo.stripPrefixLen(null));
    }
}
