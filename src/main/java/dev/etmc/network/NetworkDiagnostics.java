package dev.etmc.network;

import dev.etmc.core.EasyTierProcessManager;
import dev.etmc.core.ProcessState;
import java.util.ArrayList;
import java.util.List;

/**
 * Network diagnostics (plan §9.5/§12): distinguish 组网失败 (overlay down),
 * 代理失败 (forward not bound) and Minecraft 服务失败 (target port closed)
 * — the three failure classes the test plan requires us to separate.
 */
public final class NetworkDiagnostics {
    public record Report(List<String> findings) {
        public boolean healthy() {
            return findings.isEmpty();
        }
    }

    private final EasyTierProcessManager processManager;
    private final EasyTierCliRunner cli;
    private final int rpcPort;

    public NetworkDiagnostics(EasyTierProcessManager processManager,
            String cliBinaryPath, int rpcPort) {
        this.processManager = processManager;
        this.cli = new EasyTierCliRunner(cliBinaryPath);
        this.rpcPort = rpcPort;
    }

    public Report diagnose() {
        List<String> out = new ArrayList<>();
        ProcessState st = processManager.state();
        if (st == ProcessState.STOPPED || st == ProcessState.ERROR
                || st == ProcessState.STOPPING) {
            out.add("overlay: process is " + st + " — EasyTier not running");
            for (String line : processManager.recentLog()) {
                out.add("core log: " + line);
            }
            return new Report(out);
        }
        var node = cli.run(rpcPort, "node", 5000);
        if (node.exitCode() != 0 || node.stdout() == null
                || !node.stdout().contains("Virtual IP")) {
            out.add("overlay: rpc not answering (port " + rpcPort + ") — "
                    + "core may still be starting or crashed");
            for (String line : processManager.recentLog()) {
                out.add("core log: " + line);
            }
            return new Report(out);
        }
        var peers = cli.run(rpcPort, "peer", 5000);
        int remote = 0;
        if (peers.exitCode() == 0 && peers.stdout() != null) {
            for (String line : peers.stdout().split("\n")) {
                if (line.contains("p2p") || line.contains("relay")) {
                    remote++;
                }
            }
        }
        if (remote == 0) {
            out.add("overlay: running but no remote peers (bootstrap/nat issue)");
        }
        return new Report(out);
    }
}
