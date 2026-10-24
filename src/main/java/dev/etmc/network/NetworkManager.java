package dev.etmc.network;

import dev.etmc.config.NetworkProfile;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Polls the local easytier-cli RPC portal and derives the {@link NetworkPhase}.
 * Never touches process lifecycle; pair with {@code EasyTierProcessManager}.
 */
public final class NetworkManager {
    /** Interval between RPC polls while active, ms. */
    public static final long POLL_INTERVAL_MS = 2000;
    private static final long CLI_TIMEOUT_MS = 3000;

    private final EasyTierCliRunner cli;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "etmc-netmon");
                t.setDaemon(true);
                return t;
            });
    private final AtomicReference<NetworkPhase> phase =
            new AtomicReference<>(NetworkPhase.IDLE);
    private final AtomicReference<NetworkProfile> profile = new AtomicReference<>();
    private final List<Consumer<NetworkPhase>> listeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<List<PeerInfo>>> peerListeners = new CopyOnWriteArrayList<>();

    private volatile ScheduledFuture<?> task;
    private volatile int rpcPort;
    private volatile NodeInfo lastNode = NodeInfo.EMPTY;
    private volatile List<PeerInfo> lastPeers = List.of();

    public NetworkManager(String coreBinaryPath) {
        this.cli = new EasyTierCliRunner(coreBinaryPath);
    }

    public void addPhaseListener(Consumer<NetworkPhase> l) {
        listeners.add(l);
    }

    public void addPeerListener(Consumer<List<PeerInfo>> l) {
        peerListeners.add(l);
    }

    public NetworkPhase phase() {
        return phase.get();
    }

    public NodeInfo node() {
        return lastNode;
    }

    public List<PeerInfo> peers() {
        return lastPeers;
    }

    /** Begin polling for the given profile (does not start any process). */
    public void startPolling(NetworkProfile p) {
        this.profile.set(p);
        this.rpcPort = p.rpcPort();
        setPhase(NetworkPhase.RPC_WAIT);
        if (task == null) {
            task = scheduler.scheduleWithFixedDelay(
                    this::pollOnce, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
    }

    public void stopPolling() {
        ScheduledFuture<?> t = task;
        task = null;
        if (t != null) {
            t.cancel(false);
        }
        profile.set(null);
        lastPeers = List.of();
        lastNode = NodeInfo.EMPTY;
        setPhase(NetworkPhase.IDLE);
    }

    private void pollOnce() {
        NetworkProfile p = profile.get();
        if (p == null || task == null) {
            return;
        }
        EasyTierCliRunner.Result nodeRes = cli.run(rpcPort, "node", CLI_TIMEOUT_MS);
        if (!nodeRes.ok()) {
            setPhase(NetworkPhase.RPC_LOST);
            return;
        }
        lastNode = NodeParser.parse(nodeRes.stdout());

        EasyTierCliRunner.Result peerRes = cli.run(rpcPort, "peer", CLI_TIMEOUT_MS);
        if (peerRes.ok()) {
            lastPeers = PeerTableParser.parse(peerRes.stdout());
        } else {
            lastPeers = List.of();
        }

        boolean remotePeer = lastPeers.stream().anyMatch(pi -> !"Local".equals(pi.cost()));
        boolean hostOnline = hostRoutePresent(p);

        NetworkPhase current = phase.get();
        NetworkPhase next;
        if (p.role() == NetworkProfile.Role.HOST) {
            // The host is itself the target; "online" means clients can see us.
            next = remotePeer ? NetworkPhase.HOST_ONLINE : NetworkPhase.NO_PEERS;
        } else if (hostOnline) {
            next = NetworkPhase.HOST_ONLINE;
        } else if (current == NetworkPhase.HOST_ONLINE) {
            next = NetworkPhase.HOST_LOST;
        } else if (remotePeer) {
            next = NetworkPhase.PEERS_SEEN;
        } else {
            next = NetworkPhase.NO_PEERS;
        }
        setPhase(next);
        for (Consumer<List<PeerInfo>> l : peerListeners) {
            l.accept(lastPeers);
        }
    }

    private boolean hostRoutePresent(NetworkProfile p) {
        String hostIp = p.hostIp();
        if (hostIp == null || hostIp.isBlank()) {
            return false;
        }
        EasyTierCliRunner.Result routeRes = cli.run(rpcPort, "route", CLI_TIMEOUT_MS);
        if (!routeRes.ok()) {
            return false;
        }
        for (List<String> row : EasyTierCliRunner.parseTable(routeRes.stdout())) {
            if (!row.isEmpty() && row.get(0).startsWith(hostIp + "/")) {
                return true;
            }
        }
        return false;
    }

    private void setPhase(NetworkPhase next) {
        NetworkPhase prev = phase.getAndSet(next);
        if (prev != next) {
            for (Consumer<NetworkPhase> l : listeners) {
                l.accept(next);
            }
        }
    }
}
