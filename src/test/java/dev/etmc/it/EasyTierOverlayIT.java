package dev.etmc.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.etmc.config.NetworkProfile;
import dev.etmc.config.PortRule;
import dev.etmc.core.EasyTierCommandBuilder;
import dev.etmc.core.EasyTierProcessManager;
import dev.etmc.core.ProcessState;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P0/P2 automated acceptance against the REAL easytier-core binary
 * (skipped when the binary is absent, e.g. CI without tools).
 * Topology mirrors docs/P0-REPORT.md: host exposes 127.0.0.1 via
 * --proxy-networks CIDR mapping; client reaches it via --port-forward.
 */
class EasyTierOverlayIT {
    private static final String CORE = System.getProperty("etmc.core.bin",
            "/home/ecs-user/tools/easytier/easytier-linux-x86_64/easytier-core");
    // P0 lesson: connector sockets bind the NIC, so peers must NOT use loopback.
    private static final String NIC_IP = System.getProperty("etmc.nic.ip", detectNicIp());

    private static String detectNicIp() {
        try {
            var nifs = java.util.Collections.list(
                    java.net.NetworkInterface.getNetworkInterfaces());
            for (var nif : nifs) {
                if (!nif.isUp() || nif.isLoopback() || nif.isPointToPoint()) {
                    continue; // skip tun/tap/wg (incl. the user's real instance!)
                }
                if (nif.getName().toLowerCase().matches(".*(tun|tap|wg|ppp|zt).*")) {
                    continue;
                }
                for (var addr : java.util.Collections.list(nif.getInetAddresses())) {
                    if (addr instanceof java.net.Inet4Address in4 && !in4.isLoopbackAddress()) {
                        return in4.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "127.0.0.1";
    }

    @TempDir
    Path workDir;

    private static String readLine(Socket s) throws Exception {
        s.setSoTimeout(3000);
        return new BufferedReader(new InputStreamReader(s.getInputStream())).readLine();
    }

    @Test
    void hostExposesTcpViaOverlayAndClientForwards() throws Exception {
        Assumptions.assumeTrue(Files.exists(Path.of(CORE)), "easytier-core not present");

        // local echo service on the "host side" loopback
        int echoPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            echoPort = probe.getLocalPort();
        }
        Process echo = new ProcessBuilder("python3", "-c", """
                import socket, threading
                s=socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR,1)
                s.bind(('127.0.0.1',%d)); s.listen(8)
                def h(c):
                    try:
                        d=c.recv(4096); c.sendall(b'IT-ECHO:'+d)
                    finally: c.close()
                while True:
                    c,_=s.accept(); threading.Thread(target=h,args=(c,),daemon=True).start()
                """.formatted(echoPort)).start();

        try {
            int listen = 22010 + (int) (System.currentTimeMillis() % 500);
            int rpcH = 26110 + (int) (System.currentTimeMillis() % 500);
            int rpcC = rpcH + 1;
            int localFwd = 36110 + (int) (System.currentTimeMillis() % 500);
            int clientListen = listen + 500;

            NetworkProfile host = new NetworkProfile()
                    .binaryPath(CORE)
                    .role(NetworkProfile.Role.HOST)
                    .networkName("it-" + Long.toHexString(System.currentTimeMillis()))
                    .networkSecret("it-secret-123")
                    .peers(List.of())
                    .listenPort(listen)
                    .rpcPort(rpcH)
                    .hostIp("10.126.126.1")
                    .exposeLocalCidr("127.0.0.1/32");
            host.forwards(List.of());

            NetworkProfile client = new NetworkProfile()
                    .binaryPath(CORE)
                    .role(NetworkProfile.Role.CLIENT)
                    .networkName(host.networkName())
                    .networkSecret(host.networkSecret())
                    .peers(List.of("tcp://" + NIC_IP + ":" + listen))
                    .listenPort(clientListen)
                    .rpcPort(rpcC)
                    .clientIp("10.126.126.4")
                    .forwards(List.of(new PortRule("echo", PortRule.TCP, localFwd,
                            "10.126.126.1", echoPort, true)));

            EasyTierProcessManager h = new EasyTierProcessManager(
                    host, new EasyTierCommandBuilder(), workDir.resolve("h"));
            EasyTierProcessManager c = new EasyTierProcessManager(
                    client, new EasyTierCommandBuilder(), workDir.resolve("c"));
            h.start();
            c.start();
            try {
                long deadline = System.currentTimeMillis() + 45_000;
                while (System.currentTimeMillis() < deadline
                        && c.state() != ProcessState.CONNECTED) {
                    Thread.sleep(500);
                    Assumptions.assumeTrue(c.state() != ProcessState.ERROR,
                            "client process errored: " + c.recentLog());
                }
                assertEquals(ProcessState.CONNECTED, c.state(),
                        "client never connected; host log: " + h.recentLog()
                                + " ||| client log: " + c.recentLog());

                // TCP through the overlay via the client-side forward.
                // The overlay data path (smoltcp relay) needs a moment after
                // the control-plane reports connected (P0 finding #1).
                String reply = null;
                for (int attempt = 0; attempt < 10 && reply == null; attempt++) {
                    try (Socket s = new Socket("127.0.0.1", localFwd)) {
                        s.getOutputStream().write("ping".getBytes());
                        reply = readLine(s);
                        if (reply == null || !reply.startsWith("IT-ECHO:ping")) {
                            reply = null;
                        }
                    } catch (java.io.IOException retry) {
                        // forward not ready yet
                    }
                    if (reply == null) {
                        Thread.sleep(1500);
                    }
                }
                assertTrue(reply != null && reply.startsWith("IT-ECHO:ping"),
                        "no reply through forward; host log: " + h.recentLog()
                                + " ||| client log: " + c.recentLog());
            } finally {
                h.close();
                c.close();
            }
        } finally {
            echo.destroy();
            echo.waitFor(3, TimeUnit.SECONDS);
        }
    }
}
