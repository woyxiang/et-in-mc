package dev.etmc.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.etmc.config.NetworkProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortPolicyManagerTest {
    private final PortPolicyManager pm = new PortPolicyManager();

    @Test
    void allowlistMergesMinecraftPort() {
        NetworkProfile p = new NetworkProfile()
                .tcpWhitelist(List.of("25565", "30000-30005"))
                .udpWhitelist(List.of("24454"));
        var policy = pm.resolve(p, 25565, true);
        assertEquals("allowlist", policy.effectiveMode());
        assertEquals(List.of("25565", "30000-30005"), policy.tcpWhitelist());
        assertEquals(List.of("24454"), policy.udpWhitelist());
        assertTrue(policy.warnings().isEmpty());
    }

    @Test
    void trustedAllRequiresConfirmation() {
        NetworkProfile p = new NetworkProfile().exposureMode("trusted_all");
        var refused = pm.resolve(p, 25565, true);
        assertEquals("allowlist", refused.effectiveMode());
        assertEquals(List.of("25565"), refused.tcpWhitelist());
        assertTrue(!refused.warnings().isEmpty());

        var granted = pm.resolve(p.trustedAllConfirmed(true), 25565, true);
        assertEquals("trusted_all", granted.effectiveMode());
        assertEquals(List.of(), granted.tcpWhitelist());
        assertEquals(List.of(), granted.udpWhitelist());
    }

    @Test
    void nullModeDefaultsToAllowlist() {
        var policy = pm.resolve(new NetworkProfile(), 0, false);
        assertEquals("allowlist", policy.effectiveMode());
    }
}
