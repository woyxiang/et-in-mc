package dev.etmc.client;

import dev.etmc.config.ModConfig;
import dev.etmc.config.NetworkProfile;
import dev.etmc.core.EasyTierCommandBuilder;
import dev.etmc.core.EasyTierProcessManager;
import dev.etmc.core.ProcessState;
import dev.etmc.network.NetworkManager;
import dev.etmc.network.NetworkPhase;
import dev.etmc.network.PeerInfo;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.List;

/**
 * Client-side orchestrator binding the process manager and the network monitor.
 * GUI screens call this; they never touch processes directly (plan §10).
 */
public final class ClientNetworkController {
    private static ClientNetworkController instance;

    private final Path configDir;
    private EasyTierProcessManager processManager;
    private NetworkManager networkManager;
    private NetworkProfile current;
    private List<String> configErrors = List.of();

    private ClientNetworkController(Path configDir) {
        this.configDir = configDir;
    }

    public static synchronized ClientNetworkController get() {
        if (instance == null) {
            instance = new ClientNetworkController(FMLPaths.CONFIGDIR.get());
        }
        return instance;
    }

    /** Profile as loaded from config files; errors captured for display. */
    public synchronized NetworkProfile profileFromConfig() {
        ModConfig.Loaded loaded = ModConfig.load(configDir);
        this.configErrors = loaded.errors();
        return loaded.profile();
    }

    public synchronized List<String> configErrors() {
        return configErrors;
    }

    /**
     * Start EasyTier for the given profile and begin monitoring.
     * Returns false (and records error) when a pre-flight check fails.
     */
    public synchronized boolean connect(NetworkProfile profile) {
        disconnect();
        try {
            ModConfig.save(profile, configDir);
        } catch (Exception e) {
            configErrors = List.of("failed to save network config: " + e.getMessage());
            return false;
        }
        this.current = profile;
        try {
            processManager = new EasyTierProcessManager(
                    profile, new EasyTierCommandBuilder(), configDir.resolve("etmc-runtime"));
            processManager.start();
        } catch (Exception e) {
            configErrors = List.of("failed to start EasyTier: " + e);
            processManager = null;
            return false;
        }
        networkManager = new NetworkManager(profile.binaryPath());
        networkManager.startPolling(profile);
        return true;
    }

    public synchronized void disconnect() {
        if (networkManager != null) {
            networkManager.stopPolling();
            networkManager = null;
        }
        if (processManager != null) {
            processManager.stop();
            processManager.close();
            processManager = null;
        }
        current = null;
    }

    public synchronized boolean running() {
        return processManager != null && processManager.isRunning();
    }

    public synchronized ProcessState processState() {
        return processManager == null ? ProcessState.STOPPED : processManager.state();
    }

    public synchronized NetworkPhase networkPhase() {
        return networkManager == null ? NetworkPhase.IDLE : networkManager.phase();
    }

    public synchronized List<PeerInfo> peers() {
        return networkManager == null ? List.of() : networkManager.peers();
    }

    public synchronized NetworkProfile current() {
        return current;
    }

    public static boolean isClientSide() {
        return FMLEnvironment.dist.isClient();
    }


    /** Exposed for the join-flow orchestrator; may be null when offline. */
    public synchronized EasyTierProcessManager processManager() {
        return processManager;
    }

    /** Exposed for the join-flow orchestrator; may be null when offline. */
    public synchronized NetworkManager networkManager() {
        return networkManager;
    }


    /** Persists a profile to the config files (UI "Save" path). */
    public synchronized void persistProfile(NetworkProfile profile) {
        try {
            ModConfig.save(profile, configDir);
        } catch (Exception e) {
            this.configErrors = List.of("failed to save config: " + e.getMessage());
        }
    }


    /** Current node info from the polling loop; null when offline. */
    public synchronized dev.etmc.network.NodeInfo node() {
        return networkManager == null ? null : networkManager.node();
    }
}
