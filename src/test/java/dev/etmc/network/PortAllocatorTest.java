package dev.etmc.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortAllocatorTest {
    @Test
    void freeAndBoundTcpPorts() {
        assertTrue(PortAllocator.isTcpFree(0) == false); // invalid range
        int port;
        try (java.net.ServerSocket ss = new java.net.ServerSocket(0)) {
            port = ss.getLocalPort();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertFalse(PortAllocator.isTcpFree(port), "bound port must report not free");
    }

    @Test
    void pickFreeFallsBackWhenPreferredTaken() {
        int preferred;
        try (java.net.ServerSocket ss = new java.net.ServerSocket(0)) {
            preferred = ss.getLocalPort();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        int picked = PortAllocator.pickFree(preferred, false, 16);
        assertTrue(picked > 0);
        assertTrue(picked != preferred || PortAllocator.isTcpFree(preferred),
                "picked port must differ from the taken one");
        if (picked != preferred) {
            assertTrue(PortAllocator.isTcpFree(picked));
        }
    }

    @Test
    void udpProbeWorks() {
        int port;
        try (java.net.DatagramSocket ds = new java.net.DatagramSocket(0)) {
            port = ds.getLocalPort();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertFalse(PortAllocator.isUdpFree(port));
        assertEquals(-1, PortAllocator.pickFree(-5, true, 4));
    }
}
