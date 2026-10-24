package dev.etmc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.etmc.config.ConfigValidator;
import dev.etmc.config.NetworkProfile;
import dev.etmc.config.PortRule;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigValidatorTest {

    private NetworkProfile validHost() {
        return new NetworkProfile()
                .role(NetworkProfile.Role.HOST)
                .binaryPath("/opt/et/easytier-core")
                .networkName("n")
                .networkSecret("s")
                .listenPort(21010)
                .rpcPort(25888)
                .hostIp("10.126.126.1");
    }

    @Test
    void validHostHasNoErrors() {
        assertTrue(ConfigValidator.validate(validHost()).isEmpty());
    }

    @Test
    void emptyNameAndSecretRejected() {
        var errors = ConfigValidator.validate(validHost().networkName("").networkSecret(" "));
        assertTrue(errors.stream().anyMatch(e -> e.contains("name")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("secret")));
    }

    @Test
    void clientWithoutPeersRejected() {
        NetworkProfile p = validHost()
                .role(NetworkProfile.Role.CLIENT)
                .listenPort(21015)
                .peers(List.of());
        assertTrue(ConfigValidator.validate(p).stream().anyMatch(e -> e.contains("peer")));
    }

    @Test
    void badIpRejected() {
        assertTrue(ConfigValidator.validate(validHost().hostIp("999.1.1.1"))
                .stream().anyMatch(e -> e.contains("hostIp")));
        assertTrue(ConfigValidator.validate(validHost().hostIp("not-an-ip"))
                .stream().anyMatch(e -> e.contains("hostIp")));
    }

    @Test
    void portRangeOrdering() {
        var errors = ConfigValidator.validate(
                validHost().tcpWhitelist(List.of("30000-20000")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("range start greater than end")));
    }

    @Test
    void duplicateForwardLocalPortsRejected() {
        NetworkProfile p = validHost().forwards(List.of(
                new PortRule("a", "tcp", 30000, "10.126.126.2", 80, true),
                new PortRule("b", "tcp", 30000, "10.126.126.2", 81, true)));
        assertTrue(ConfigValidator.validate(p).stream()
                .anyMatch(e -> e.contains("duplicate local tcp")));
    }

    @Test
    void samePortOnDifferentProtocolsAllowed() {
        NetworkProfile p = validHost().forwards(List.of(
                new PortRule("a", "tcp", 30000, "10.126.126.2", 80, true),
                new PortRule("b", "udp", 30000, "10.126.126.2", 81, true)));
        assertTrue(ConfigValidator.validate(p).isEmpty());
    }

    @Test
    void badProtocolRejected() {
        NetworkProfile p = validHost().forwards(List.of(
                new PortRule("x", "icmp", 30000, "10.126.126.2", 80, true)));
        assertFalse(ConfigValidator.validate(p).isEmpty());
    }
}
