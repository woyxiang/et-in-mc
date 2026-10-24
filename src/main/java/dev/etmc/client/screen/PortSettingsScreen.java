package dev.etmc.client.screen;

import dev.etmc.client.ClientNetworkController;
import dev.etmc.config.PortRule;
import dev.etmc.network.PortAllocator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Port management screen (plan §9.4): shows auto-detected Minecraft port,
 * custom forward rules with protocol/ports/enabled state and the concrete
 * error reason (e.g. local port already taken) for each rule.
 */
public class PortSettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private final Screen parent;
    private final List<String> lines = new ArrayList<>();
    private final List<Runnable> toggleActions = new ArrayList<>();

    public PortSettingsScreen(Screen parent) {
        super(Component.literal("EasyTier MC - Ports"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int x = cx - PANEL_WIDTH / 2;
        int y = 60;
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(x, y + 150, PANEL_WIDTH, 20).build());
        rebuild();
        // one toggle row per rule (plan §9.4: each rule shows state + reason)
        int rowY = y + 12;
        for (Runnable action : toggleActions) {
            addRenderableWidget(Button.builder(Component.literal("toggle"), b -> action.run())
                    .bounds(x + PANEL_WIDTH - 50, rowY, 50, 12).build());
            rowY += 12;
            if (rowY > y + 140) {
                break;
            }
        }
    }

    private void rebuild() {
        lines.clear();
        toggleActions.clear();
        var c = ClientNetworkController.get();
        if (c.current() == null) {
            lines.add("Not connected to a network.");
            return;
        }
        List<PortRule> forwards = c.current().forwards();
        lines.add("Rules: " + forwards.size()
                + " | MC lan port: " + (dev.etmc.minecraft.LanWorldManager.lanPort()));
        int idx = 0;
        for (PortRule r : forwards) {
            boolean bound = r.isUdp()
                    ? !PortAllocator.isUdpFree(r.localPort())
                    : !PortAllocator.isTcpFree(r.localPort());
            String state = !r.enabled() ? "disabled"
                    : bound ? "forwarding" : "not bound (check easytier log)";
            lines.add((idx + 1) + ". [" + r.protocol() + "] " + r.name()
                    + " " + r.localPort() + "->" + r.remoteHost() + ":"
                    + r.remotePort() + " : " + state);
            PortRule rule = r;
            int at = idx;
            toggleActions.add(() -> {
                List<PortRule> fw = new ArrayList<>(c.current().forwards());
                fw.set(at, new PortRule(rule.name(), rule.protocol(), rule.localPort(),
                        rule.remoteHost(), rule.remotePort(), !rule.enabled()));
                c.current().forwards(fw);
                rebuild();
                init(minecraft, width, height);
            });
            idx++;
        }
        if (forwards.isEmpty()) {
            lines.add("No custom rules. Edit easytier-mc-networks.toml [v].");
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 25, 0xFFFFFF);
        int y = 58;
        for (String line : lines) {
            g.drawString(font, line, width / 2 - PANEL_WIDTH / 2, y, 0xC0C0C0);
            y += 11;
            if (y > height - 40) {
                break;
            }
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
