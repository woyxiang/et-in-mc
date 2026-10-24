package dev.etmc.minecraft;

import dev.etmc.core.ProcessState;
import dev.etmc.core.EasyTierProcessManager;
import dev.etmc.network.NetworkManager;
import dev.etmc.network.NetworkPhase;
import dev.etmc.network.PortForwardManager;
import dev.etmc.network.PortAllocator;
import dev.etmc.config.NetworkProfile;
import dev.etmc.config.PortRule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrates the client join path (plan §8.2):
 * EasyTier up -> host node online -> local port-forward listening ->
 * target TCP reachable -> hand the mapped address to vanilla "Direct Join".
 * Pure orchestration over injected collaborators; no Minecraft classes.
 */
public final class ServerConnectionManager {

    /** Result of a join attempt. */
    public record JoinResult(boolean ok, String joinHost, int joinPort, String failureStage,
            String failureDetail) {
        public static JoinResult success(String host, int port) {
            return new JoinResult(true, host, port, null, null);
        }

        public static JoinResult fail(String stage, String detail) {
            return new JoinResult(false, null, 0, stage, detail);
        }
    }

    /** Where the game server is expected, from the network's perspective. */
    public record Target(String virtualIp, int remotePort) { }

    private final EasyTierProcessManager processManager;
    private final NetworkManager networkManager;
    private final PortForwardManager portForwardManager;
    private final Target target;

    public ServerConnectionManager(EasyTierProcessManager processManager,
            NetworkManager networkManager,
            PortForwardManager portForwardManager,
            Target target) {
        this.processManager = processManager;
        this.networkManager = networkManager;
        this.portForwardManager = portForwardManager;
        this.target = target;
    }

    /**
     * Ensures the network is up and returns the local address the vanilla
     * client should connect to. Never throws; failures are returned with a
     * stage identifier so the UI can show which leg broke (plan §12.10).
     *
     * @param profile      active network profile (client role)
     * @param waitMs       max time to wait for EasyTier/host readiness
     * @param localBind    preferred local bind address for forwards
     * @param preferredPort preferred local port for the game forward
     */
    public JoinResult prepareJoin(NetworkProfile profile, long waitMs,
            String localBind, int preferredPort) {
        // 1. process running?
        ProcessState st = processManager.state();
        if (st != ProcessState.CONNECTED && st != ProcessState.READY
                && st != ProcessState.CONNECTING) {
            return JoinResult.fail("PROCESS", "EasyTier is " + st
                    + "; start the network first");
        }

        // 2. wait for host node online
        long deadline = System.currentTimeMillis() + waitMs;
        AtomicReference<NetworkPhase> phase = new AtomicReference<>(networkManager.phase());
        while (System.currentTimeMillis() < deadline) {
            NetworkPhase p = networkManager.phase();
            phase.set(p);
            if (p == NetworkPhase.HOST_ONLINE) {
                break;
            }
            if (p == NetworkPhase.RPC_LOST) {
                return JoinResult.fail("RPC",
                        "lost contact with local easytier-core rpc");
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return JoinResult.fail("INTERRUPTED", "wait cancelled");
            }
        }
        NetworkPhase finalPhase = phase.get();
        if (finalPhase != NetworkPhase.HOST_ONLINE) {
            return JoinResult.fail("NETWORK", "host node not online (phase="
                    + finalPhase + ")");
        }

        // 3. ensure the game port-forward exists
        int localPort = PortAllocator.isTcpFree(preferredPort) ? preferredPort
                : PortAllocator.pickFree(preferredPort, false, 20);
        if (localPort <= 0) {
            return JoinResult.fail("PORTS", "no free local TCP port near "
                    + preferredPort);
        }
        List<PortRule> needed = new ArrayList<>();
        needed.add(new PortRule("Minecraft", PortRule.TCP, localPort,
                target.virtualIp(), target.remotePort(), true));
        try {
            if (!profile.forwards().equals(needed)) {
                profile.forwards(needed);
                portForwardManager.applyByRestart(profile);
            }
        } catch (RuntimeException e) {
            return JoinResult.fail("FORWARD",
                    "failed to (re)configure port forwards: " + e.getMessage());
        }

        // 4. wait for the local forward to bind, then probe remote target
        String bind = localBind == null ? "127.0.0.1" : localBind;
        long bindDeadline = System.currentTimeMillis() + 8000;
        while (PortAllocator.isTcpFree(localPort)
                && System.currentTimeMillis() < bindDeadline) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return JoinResult.fail("INTERRUPTED", "wait cancelled");
            }
        }
        if (PortAllocator.isTcpFree(localPort)) {
            return JoinResult.fail("LISTEN", "local forward did not start on "
                    + bind + ":" + localPort);
        }
        if (!MinecraftPortDetector.isTcpReachable(target.virtualIp(), target.remotePort())) {
            return JoinResult.fail("TARGET", "game service not reachable at "
                    + target.virtualIp() + ":" + target.remotePort()
                    + " (host may not have opened LAN/server)");
        }
        return JoinResult.success(bind, localPort);
    }
}
