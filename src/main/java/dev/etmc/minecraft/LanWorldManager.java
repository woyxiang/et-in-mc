package dev.etmc.minecraft;

import dev.etmc.network.PortAllocator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * Single-player LAN exposure + native join flow (plan §8.1/§8.2).
 * Client-only class; never loaded on the dedicated server.
 */
public final class LanWorldManager {
    /** Preferred fixed LAN port so peers can be told a stable target (plan §5.1). */
    public static final int PREFERRED_LAN_PORT = 25565;

    private LanWorldManager() { }

    public static boolean inSinglePlayerWorld() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server != null;
    }

    /** True when the integrated server is already published to LAN. */
    public static boolean isLanOpen() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server != null && server.isPublished();
    }

    /** Actual listening port of the integrated server (0 if not running). */
    public static int lanPort() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server == null ? 0 : server.getPort();
    }

    /**
     * Opens the world to LAN on a fixed port. Tries the preferred port first,
     * then falls back to the next free ports so the call always succeeds
     * unless publishing itself fails. Returns the real listening port, or -1.
     */
    public static int openLan(GameType gameType, boolean cheats, boolean allowPortFallback) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return -1;
        }
        if (server.isPublished()) {
            return server.getPort();
        }
        int port = PREFERRED_LAN_PORT;
        for (int attempt = 0; attempt < (allowPortFallback ? 32 : 1); attempt++) {
            if (PortAllocator.isTcpFree(port) && server.publishServer(gameType, cheats, port)) {
                return server.getPort();
            }
            port = PortAllocator.pickFree(port + 1, false, 32);
            if (port <= 0) {
                break;
            }
        }
        return -1;
    }

    /** Starts the vanilla "Direct Join" flow against a host:port. */
    public static void joinHost(String host, int port) {
        ServerAddress address = ServerAddress.parseString(host + ":" + port);
        ServerData data = new ServerData("etmc-local", host + ":" + port,
                ServerData.Type.OTHER);
        ConnectScreen.startConnecting(new TitleScreen(),
                Minecraft.getInstance(), address, data, false, null);
    }
}
