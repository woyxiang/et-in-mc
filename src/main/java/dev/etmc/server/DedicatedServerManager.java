package dev.etmc.server;

import dev.etmc.config.ModConfig;
import dev.etmc.config.NetworkProfile;
import dev.etmc.core.EasyTierProcessManager;
import dev.etmc.core.EasyTierCommandBuilder;
import dev.etmc.core.ProcessState;
import dev.etmc.network.NetworkManager;
import dev.etmc.network.NetworkPhase;
import dev.etmc.network.PeerInfo;

import dev.etmc.EasyTierMinecraft;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.nio.file.Path;
import java.util.List;

/**
 * Dedicated-server lifecycle (plan §8.3): bring the overlay up while the
 * server starts, expose policy once the server is ready, tear down on stop.
 * No client/GUI classes are touched.
 */
public final class DedicatedServerManager {
    private static final DedicatedServerManager INSTANCE = new DedicatedServerManager();

    public static DedicatedServerManager get() {
        return INSTANCE;
    }

    private EasyTierProcessManager processManager;
    private NetworkManager networkManager;
    private NetworkProfile profile;
    private Path configDir;
    private volatile String lastError = "";

    /** Event-bus hook; harmless on client (profile loading is cheap). */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        startFor(event.getServer());
    }

    /** Activate exposure + kick polling once the server is actually ready. */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (networkManager != null && networkManager.phase() == NetworkPhase.IDLE) {
            networkManager.startPolling(profile);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        stopAll();
    }

    private synchronized void startFor(MinecraftServer server) {
        // FMLPaths resolves <gameDir>/config in both dev and production;
        // server.getServerDirectory() is relative and points at run/ in dev.
        this.configDir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
        ModConfig.Loaded loaded = ModConfig.load(configDir);
        this.profile = loaded.profile();
        if (!loaded.errors().isEmpty()) {
            this.lastError = "config errors: " + loaded.errors();
            EasyTierMinecraft.LOGGER.error(lastError);
            return;
        }
        if (profile.role() != NetworkProfile.Role.HOST) {
            this.lastError = "dedicated server requires role=host, got " + profile.role();
            EasyTierMinecraft.LOGGER.error(lastError);
            return;
        }
        try {
            processManager = new EasyTierProcessManager(
                    profile, new EasyTierCommandBuilder(), configDir.resolve("etmc-runtime"));
            processManager.start();
        } catch (Exception e) {
            this.lastError = "failed to start EasyTier: " + e.getMessage();
            EasyTierMinecraft.LOGGER.error(lastError, e);
            processManager = null;
            return;
        }
        networkManager = new NetworkManager(profile.binaryPath());
        EasyTierMinecraft.LOGGER.info("EasyTier overlay starting for dedicated server");
    }

    public synchronized void stopAll() {
        if (networkManager != null) {
            networkManager.stopPolling();
            networkManager = null;
        }
        if (processManager != null) {
            processManager.close();
            processManager = null;
        }
        EasyTierMinecraft.LOGGER.info("EasyTier overlay stopped");
    }

    public synchronized boolean restart() {
        boolean hadProfile = profile != null;
        stopAll();
        if (hadProfile && configDir != null) {
            ModConfig.Loaded loaded = ModConfig.load(configDir);
            this.profile = loaded.profile();
            if (!loaded.errors().isEmpty()) {
                this.lastError = "config errors: " + loaded.errors();
                return false;
            }
            try {
                processManager = new EasyTierProcessManager(
                        profile, new EasyTierCommandBuilder(),
                        configDir.resolve("etmc-runtime"));
                processManager.start();
                networkManager = new NetworkManager(profile.binaryPath());
                networkManager.startPolling(profile);
                return true;
            } catch (Exception e) {
                this.lastError = "restart failed: " + e.getMessage();
                processManager = null;
            }
        }
        return false;
    }

    public synchronized String statusLine() {
        if (processManager == null) {
            return lastError.isEmpty() ? "stopped" : "stopped (" + lastError + ")";
        }
        ProcessState ps = processManager.state();
        NetworkPhase np = networkManager == null ? NetworkPhase.IDLE : networkManager.phase();
        return "process=" + ps + " network=" + np;
    }

    public synchronized List<PeerInfo> peers() {
        return networkManager == null ? List.of() : networkManager.peers();
    }
}
