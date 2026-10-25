package dev.etmc.network;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Local port availability probing and collision-safe allocation.
 * TCP uses ServerSocket bind; UDP uses DatagramSocket bind.
 */
public final class PortAllocator {
    private PortAllocator() { }

    public static boolean isTcpFree(int port) {
        if (port <= 0 || port > 65535) {
            return false;
        }
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isUdpFree(int port) {
        if (port <= 0 || port > 65535) {
            return false;
        }
        try (DatagramSocket ds = new DatagramSocket(null)) {
            ds.bind(new java.net.InetSocketAddress(InetAddress.getByName("0.0.0.0"), port));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns preferred port when free; otherwise probes up to 32 consecutive
     * alternatives, then falls back to a random ephemeral-style port.
     */
    public static int pickFree(int preferred, boolean udp, int max) {
        if (preferred <= 0 || preferred > 65535) {
            return -1;
        }
        if (udp ? isUdpFree(preferred) : isTcpFree(preferred)) {
            return preferred;
        }
        for (int i = 1; i <= max; i++) {
            int candidate = preferred + i;
            if (candidate > 65535) {
                break;
            }
            if (udp ? isUdpFree(candidate) : isTcpFree(candidate)) {
                return candidate;
            }
        }
        for (int tries = 0; tries < 16; tries++) {
            int candidate = ThreadLocalRandom.current().nextInt(30000, 60000);
            if (udp ? isUdpFree(candidate) : isTcpFree(candidate)) {
                return candidate;
            }
        }
        return -1;
    }
}
