package dev.etmc.client.screen;

import dev.etmc.client.ClientNetworkController;
import dev.etmc.core.EasyTierProcessManager;
import dev.etmc.core.ProcessState;
import dev.etmc.minecraft.LanWorldManager;
import dev.etmc.minecraft.ServerConnectionManager;
import dev.etmc.network.NetworkManager;
import dev.etmc.network.NetworkPhase;
import dev.etmc.network.PeerInfo;
import dev.etmc.network.PortForwardManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.etmc.config.NetworkProfile;

import java.util.List;

/**
 * Main network overview screen (plan §9.1).
 */
public class NetworkHomeScreen extends Screen {
    private static final int PANEL_WIDTH = 240;

    private final Screen parent;
    private String statusLine = "";
    private String detailLine = "";

    public NetworkHomeScreen(Screen parent) {
        super(Component.literal("EasyTier MC - Network"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int x = cx - PANEL_WIDTH / 2;
        int y = 60;

        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal("Create Network"), b -> minecraft.setScreen(new CreateNetworkScreen(this)))
                .bounds(x, y, PANEL_WIDTH, 20).build());
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal("Join Network"), b -> minecraft.setScreen(new JoinNetworkScreen(this)))
                .bounds(x, y + 24, PANEL_WIDTH, 20).build());
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal("Disconnect"), b -> ClientNetworkController.get().disconnect())
                .bounds(x, y + 48, PANEL_WIDTH, 20).build());
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal("Refresh"), b -> refreshStatus())
                .bounds(x, y + 72, PANEL_WIDTH / 2 - 2, 20).build());
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal("Join World"), b -> joinWorld())
                .bounds(x + PANEL_WIDTH / 2 + 2, y + 72, PANEL_WIDTH / 2 - 2, 20).build());
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal("Copy Network Info"), b -> copyNetworkInfo())
                .bounds(x, y + 96, PANEL_WIDTH / 2 - 2, 20).build());
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal("Host World"), b -> hostWorldViaEasyTier())
                .bounds(x + PANEL_WIDTH / 2 + 2, y + 96, PANEL_WIDTH / 2 - 2, 20).build());
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.literal("Back"), b -> onClose())
                .bounds(x, y + 120, PANEL_WIDTH, 20).build());
        refreshStatus();
    }

    private void refreshStatus() {
        ClientNetworkController c = ClientNetworkController.get();
        ProcessState ps = c.processState();
        NetworkPhase np = c.networkPhase();
        List<PeerInfo> peers = c.peers();
        statusLine = "process: " + ps + " | network: " + np;
        detailLine = "peers: " + peers.size();
        if (c.current() != null) {
            detailLine += " | net: " + c.current().networkName();
        }
        List<String> errs = c.configErrors();
        if (!errs.isEmpty()) {
            detailLine += " | errors: " + errs.get(0);
        }
    }

    /** Plan §4.4: explicit copy action exposes the secret (hidden otherwise). */
    private void copyNetworkInfo() {
        ClientNetworkController c = ClientNetworkController.get();
        NetworkProfile p = c.current() != null ? c.current() : c.profileFromConfig();
        if (p == null) {
            detailLine = "no network profile";
            return;
        }
        String info = "network_name=" + p.networkName() + "\n"
                + "network_secret=" + p.networkSecret() + "\n"
                + "peers=" + String.join(" ", p.peers()) + "\n"
                + "host_ip=" + p.hostIp() + "\n"
                + "minecraft_port=" + dev.etmc.minecraft.LanWorldManager.PREFERRED_LAN_PORT;
        minecraft.keyboardHandler.setClipboard(info);
        detailLine = "network info copied to clipboard";
    }

    /**
     * Plan §8.1: from inside a singleplayer world, open LAN on a fixed port
     * and bring the overlay up as HOST in one click.
     */
    private void hostWorldViaEasyTier() {
        if (!dev.etmc.minecraft.LanWorldManager.inSinglePlayerWorld()) {
            detailLine = "enter a singleplayer world first";
            return;
        }
        int lanPort = dev.etmc.minecraft.LanWorldManager.openLan(
                net.minecraft.world.level.GameType.SURVIVAL, false, true);
        if (lanPort <= 0) {
            detailLine = "failed to open LAN";
            return;
        }
        ClientNetworkController c = ClientNetworkController.get();
        NetworkProfile p = c.profileFromConfig().role(NetworkProfile.Role.HOST)
                .networkName(c.current() != null ? c.current().networkName() : null);
        if (p.networkName() == null || p.networkName().isBlank()) {
            p.networkName(NetworkFormScreen.randomName());
            p.networkSecret(NetworkFormScreen.randomSecret());
        }
        detailLine = "lan=" + lanPort + " starting overlay...";
        new Thread(() -> {
            boolean ok = c.connect(p);
            minecraft.execute(() -> detailLine = ok
                    ? "hosting: lan=" + lanPort + " net=" + p.networkName()
                    : "overlay start failed");
        }, "etmc-host").start();
    }

    /**
     * Plan §8.2: ensure network -> host online -> forward -> probe ->
     * vanilla connect to the mapped local address. Runs off the render
     * thread; the actual screen switch happens on the client thread.
     */
    private void joinWorld() {
        ClientNetworkController c = ClientNetworkController.get();
        EasyTierProcessManager pm = c.processManager();
        NetworkManager nm = c.networkManager();
        NetworkProfile profile = c.current();
        if (pm == null || nm == null || profile == null) {
            detailLine = "not connected to a network";
            return;
        }
        detailLine = "preparing join...";
        Thread t = new Thread(() -> {
            ServerConnectionManager scm = new ServerConnectionManager(
                    pm, nm, new PortForwardManager(pm),
                    new ServerConnectionManager.Target(profile.hostIp(),
                            LanWorldManager.PREFERRED_LAN_PORT));
            var result = scm.prepareJoin(profile, 15000, "127.0.0.1",
                    LanWorldManager.PREFERRED_LAN_PORT);
            minecraft.execute(() -> {
                if (result.ok()) {
                    LanWorldManager.joinHost(result.joinHost(), result.joinPort());
                } else {
                    detailLine = "[" + result.failureStage() + "] "
                            + result.failureDetail();
                }
            });
        }, "etmc-join");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 25, 0xFFFFFF);
        g.drawCenteredString(font, statusLine, width / 2, 42, 0xA0A0A0);
        g.drawCenteredString(font, detailLine, width / 2, 160, 0xA0A0A0);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
