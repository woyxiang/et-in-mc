package dev.etmc.client.screen;

import dev.etmc.client.ClientNetworkController;
import dev.etmc.config.NetworkProfile;
import dev.etmc.network.PortPolicyManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Advanced settings (plan §9.5): core path, proxy bind, and the
 * trusted-all-ports mode with its explicit, persisted confirmation.
 * The warning stays visible while the mode is armed.
 */
public class AdvancedSettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private final Screen parent;
    private EditBox corePath;
    private EditBox bindAddress;
    private Button trustedAllButton;
    private String note = "";

    public AdvancedSettingsScreen(Screen parent) {
        super(Component.literal("EasyTier MC - Advanced"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int x = cx - PANEL_WIDTH / 2;
        int y = 50;
        NetworkProfile p = ClientNetworkController.get().profileFromConfig();

        corePath = new EditBox(font, x, y, PANEL_WIDTH, 18, Component.literal("core"));
        corePath.setValue(p.binaryPath());
        corePath.setHint(Component.literal("path to easytier-core binary"));
        addRenderableWidget(corePath);

        bindAddress = new EditBox(font, x, y + 26, PANEL_WIDTH, 18, Component.literal("bind"));
        bindAddress.setValue("127.0.0.1");
        bindAddress.setHint(Component.literal("client proxy bind address"));
        addRenderableWidget(bindAddress);

        trustedAllButton = Button.builder(trustedAllLabel(p), b -> toggleTrustedAll(p))
                .bounds(x, y + 56, PANEL_WIDTH, 20).build();
        addRenderableWidget(trustedAllButton);

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(x, y + 88, PANEL_WIDTH / 2 - 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(x + PANEL_WIDTH / 2 + 2, y + 88, PANEL_WIDTH / 2 - 2, 20).build());
    }

    private Component trustedAllLabel(NetworkProfile p) {
        boolean armed = "trusted_all".equals(p.exposureMode()) && p.trustedAllConfirmed();
        return armed
                ? Component.literal("TRUSTED ALL PORTS: ON (click to disable)")
                : Component.literal("Trusted all ports: OFF (click to arm)");
    }

    private void toggleTrustedAll(NetworkProfile p) {
        boolean armed = "trusted_all".equals(p.exposureMode()) && p.trustedAllConfirmed();
        if (armed) {
            p.exposureMode(PortPolicyManager.MODE_ALLOWLIST).trustedAllConfirmed(false);
            note = "trusted_all disabled";
        } else {
            // §5.1/§9.5: arming requires an explicit second click on the
            // already-ON-looking button; we model that as two-step confirm.
            if ("trusted_all".equals(p.exposureMode()) && !p.trustedAllConfirmed()) {
                p.trustedAllConfirmed(true);
                note = "TRUSTED ALL ARMED - full local service exposure";
            } else {
                p.exposureMode(PortPolicyManager.MODE_TRUSTED_ALL);
                note = "click again to confirm trusted_all";
            }
        }
        ClientNetworkController.get().persistProfile(p);
        trustedAllButton.setMessage(trustedAllLabel(p));
    }

    private void save() {
        ClientNetworkController c = ClientNetworkController.get();
        NetworkProfile p = c.profileFromConfig().binaryPath(corePath.getValue().trim());
        c.persistProfile(p);
        note = "saved (takes effect on next network start)";
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 25, 0xFFFFFF);
        int x = width / 2 - PANEL_WIDTH / 2;
        g.drawString(font, "EasyTier core path", x, 42, 0xA0A0A0);
        g.drawString(font, "Proxy bind address", x, 68, 0xA0A0A0);
        if ("trusted_all".equals(ClientNetworkController.get().profileFromConfig().exposureMode())) {
            g.drawCenteredString(font, "WARNING: full local service exposure via overlay",
                    width / 2, 122, 0xFF5555);
        }
        if (!note.isEmpty()) {
            g.drawCenteredString(font, note, width / 2, 136, 0x80FF80);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
