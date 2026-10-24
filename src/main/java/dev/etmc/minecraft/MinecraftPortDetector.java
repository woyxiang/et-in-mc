package dev.etmc.minecraft;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Reachability probes against a Minecraft TCP endpoint.
 * Used to distinguish "network OK but game service down" (plan §12.10).
 */
public final class MinecraftPortDetector {
    /** Handshake timeout for probes, ms. */
    public static final int PROBE_TIMEOUT_MS = 3000;

    private MinecraftPortDetector() { }

    public static boolean isTcpReachable(String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Sends a modern status-request-sized handshake and reads the first
     * bytes; a vanilla server replies with a VarInt-prefixed JSON blob.
     * Returns true when anything readable arrives (protocol details are
     * delegated to the real client connection flow).
     */
    public static boolean respondsLikeMinecraft(String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MS);
            s.setSoTimeout(PROBE_TIMEOUT_MS);
            // minimal C1S handshake for 1.21.1: length + 0x00 + proto 0x2B + host + port + state 1
            var out = s.getOutputStream();
            var hostBytes = host.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            var buf = new java.io.ByteArrayOutputStream();
            writeVarInt(buf, 0x2B);
            writeVarInt(buf, hostBytes.length);
            buf.write(hostBytes);
            buf.write((port >> 8) & 0xFF);
            buf.write(port & 0xFF);
            writeVarInt(buf, 1);
            byte[] packet = buf.toByteArray();
            var body = new java.io.ByteArrayOutputStream();
            writeVarInt(body, 0x00);
            body.write(packet);
            byte[] payload = body.toByteArray();
            writeVarInt(out, payload.length);
            out.write(payload);
            out.flush();
            var in = s.getInputStream();
            return in.read() != -1;
        } catch (IOException e) {
            return false;
        }
    }

    private static void writeVarInt(java.io.OutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static void writeVarInt(java.io.ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }
}
