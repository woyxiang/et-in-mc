package dev.etmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.etmc.config.NetworkProfile;
import dev.etmc.config.PortRule;
import dev.etmc.core.EasyTierCommandBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandBuilderTest {
    private final EasyTierCommandBuilder builder = new EasyTierCommandBuilder();

    private NetworkProfile host() {
        return new NetworkProfile()
                .role(NetworkProfile.Role.HOST)
                .binaryPath("/opt/et/easytier-core")
                .networkName("mcnet")
                .networkSecret("s3cret")
                .listenPort(21010)
                .rpcPort(25888)
                .hostIp("10.126.126.1")
                .tcpWhitelist(List.of("25565", "30000-30005"))
                .udpWhitelist(List.of("24454"));
    }

    private NetworkProfile client() {
        return new NetworkProfile()
                .role(NetworkProfile.Role.CLIENT)
                .binaryPath("/opt/et/easytier-core")
                .networkName("mcnet")
                .networkSecret("s3cret")
                .peers(List.of("tcp://172.25.87.100:21010"))
                .listenPort(21015)
                .rpcPort(25893)
                .dhcp(true)
                .forwards(List.of(
                        new PortRule("Minecraft", "tcp", 25565, "10.126.126.1", 25565, true),
                        new PortRule("Voice", "udp", 34454, "10.126.126.1", 24454, false)));
    }

    @Test
    void hostArgsMatchP0Recipe() {
        List<String> argv = builder.build(host());
        int i = 0;
        assertEquals("/opt/et/easytier-core", argv.get(i++));
        assertEquals("--network-name", argv.get(i++));
        assertEquals("mcnet", argv.get(i++));
        assertEquals("--network-secret", argv.get(i++));
        assertEquals("s3cret", argv.get(i++));
        assertEquals("--no-tun", argv.get(i++));
        assertEquals("--ipv4", argv.get(i++));
        assertEquals("10.126.126.1", argv.get(i++));
        assertEquals("--use-smoltcp", argv.get(i++));
        assertEquals("true", argv.get(i++));
        assertEquals("--proxy-networks", argv.get(i++));
        assertEquals("127.0.0.1/32->10.126.126.1/32", argv.get(i++));
        assertEquals("--listeners", argv.get(i++));
        assertEquals("tcp://0.0.0.0:21010", argv.get(i++));
        // each whitelist entry is its own argv element (easytier rejects a
        // space-joined string with "Invalid port number")
        assertEquals("--tcp-whitelist", argv.get(i++));
        assertEquals("25565", argv.get(i++));
        assertEquals("30000-30005", argv.get(i++));
        assertEquals("--udp-whitelist", argv.get(i++));
        assertEquals("24454", argv.get(i++));
        assertEquals("--rpc-portal", argv.get(i++));
        assertEquals("127.0.0.1:25888", argv.get(i++));
        assertEquals(i, argv.size());
    }

    @Test
    void clientArgsIncludeOnlyEnabledForwards() {
        List<String> argv = builder.build(client());
        assertTrue(argv.contains("--dhcp"));
        assertTrue(argv.contains("tcp://172.25.87.100:21010"));
        assertTrue(argv.contains("tcp://0.0.0.0:25565/10.126.126.1:25565"));
        // disabled rule must not appear
        assertTrue(argv.stream().noneMatch(a -> a.contains("34454")));
        assertTrue(argv.contains("tcp://127.0.0.1:21015"));
    }

    @Test
    void clientStaticIp() {
        NetworkProfile p = client().dhcp(false).clientIp("10.126.126.4");
        List<String> argv = builder.build(p);
        int i = argv.indexOf("--ipv4");
        assertEquals("10.126.126.4", argv.get(i + 1));
    }

    @Test
    void rejectsMismatchedPrefixMapping() {
        NetworkProfile p = host().exposeLocalCidr("127.0.0.1/8");
        // mapping would be 127.0.0.1/8 -> 10.126.126.1/32 -> must fail validation
        var ex = assertThrows(IllegalArgumentException.class, () -> builder.build(p));
        assertTrue(ex.getMessage().contains("prefix"));
    }

    @Test
    void rejectsForwardPortCollidingWithListener() {
        NetworkProfile p = client().forwards(List.of(
                new PortRule("bad", "tcp", 21015, "10.126.126.1", 25565, true)));
        var ex = assertThrows(IllegalArgumentException.class, () -> builder.build(p));
        assertTrue(ex.getMessage().contains("collides"));
    }
}
