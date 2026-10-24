package dev.etmc.network;

import dev.etmc.config.NetworkProfile;
import dev.etmc.config.PortRule;
import dev.etmc.core.EasyTierProcessManager;

import java.util.List;
import java.util.Objects;

/**
 * Applies {@link NetworkProfile} port-forward rules.
 * v0.1 strategy (plan §5.3): local proxy rule changes trigger a controlled
 * restart of the managed EasyTier process instead of live RPC patching.
 */
public final class PortForwardManager {
    private final EasyTierProcessManager processManager;

    public PortForwardManager(EasyTierProcessManager processManager) {
        this.processManager = processManager;
    }

    /** Pre-flight: make sure every enabled forward's local port is bindable. */
    public List<String> precheck(NetworkProfile profile) {
        List<String> problems = new java.util.ArrayList<>();
        for (PortRule f : profile.forwards()) {
            if (!f.enabled()) {
                continue;
            }
            boolean udp = f.isUdp();
            boolean free = udp
                    ? PortAllocator.isUdpFree(f.localPort())
                    : PortAllocator.isTcpFree(f.localPort());
            if (!free) {
                problems.add("local " + f.protocol() + " port " + f.localPort()
                        + " (" + f.name() + ") is already in use");
            }
        }
        return problems;
    }

    /** True when the effective forward list differs from the running one. */
    public boolean needsRestart(NetworkProfile running, NetworkProfile desired) {
        if (running == null) {
            return false;
        }
        return !Objects.equals(running.forwards(), desired.forwards());
    }

    /**
     * Restart the managed process with the new forward set.
     * Stops, waits for stop, then starts again with the desired profile.
     */
    public void applyByRestart(NetworkProfile desired) {
        if (processManager.isRunning()) {
            processManager.stop();
        }
        processManager.start(desired);
    }
}
