package dev.etmc.network;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeerTableParserTest {

    private static final String PEER_OUTPUT = """
            | ipv4            | hostname | cost  | lat(ms) | loss | rx       | tx       | tunnel | NAT            | version        |
            |-----------------|----------|-------|---------|------|----------|----------|--------|----------------|----------------|
            | 10.126.126.4/24 | slug     | Local | -       | -    | -        | -        | -      | PortRestricted | 2.6.4-8428a89d |
            | 10.126.126.1/24 | slug     | p2p   | 0.16    | 0.0% | 53.70 kB | 59.70 kB | tcp    | PortRestricted | 2.6.4-8428a89d |
            """;

    @Test
    void parsesPeerTableRows() {
        List<PeerInfo> peers = PeerTableParser.parse(PEER_OUTPUT);
        assertEquals(2, peers.size());

        PeerInfo self = peers.get(0);
        assertEquals("10.126.126.4", self.virtualIp());
        assertEquals("Local", self.cost());
        assertEquals("PortRestricted", self.natType());

        PeerInfo host = peers.get(1);
        assertEquals("10.126.126.1", host.virtualIp());
        assertEquals("p2p", host.cost());
        assertEquals("0.16", host.latencyMs());
        assertEquals("tcp", host.tunnelProto());
        assertTrue(host.isHost("10.126.126.1"));
    }

    @Test
    void emptyAndGarbageInputYieldEmptyList() {
        assertTrue(PeerTableParser.parse("").isEmpty());
        assertTrue(PeerTableParser.parse("not a table").isEmpty());
        assertTrue(PeerTableParser.parse("| ipv4 | hostname | cost |\n|-|-|-|").isEmpty());
    }
}
