package dev.etmc.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Shared form scaffolding for create/join screens: name, secret, peers fields.
 */
public abstract class NetworkFormScreen extends Screen {
    protected static final SecureRandom RANDOM = new SecureRandom();

    protected final Screen parent;
    protected EditBox nameBox;
    protected EditBox secretBox;
    protected EditBox peersBox;
    protected String message = "";

    protected NetworkFormScreen(Screen parent, Component title) {
        super(title);
        this.parent = parent;
    }

    protected EditBox addLabeledBox(int x, int y, int w, String initial, int maxLength, String placeholder) {
        EditBox box = new EditBox(font, x, y, w, 18, Component.literal(placeholder));
        box.setMaxLength(maxLength);
        box.setValue(initial);
        box.setHint(Component.literal(placeholder));
        addRenderableWidget(box);
        return box;
    }

    /** "tcp://1.2.3.4:11010, tcp://5.6.7.8:11010" -> [same] */
    protected static java.util.List<String> parsePeers(String raw) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String s : raw.split("[,;\\s]+")) {
            if (!s.isBlank()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    /** Unpredictable network name: mc- + 8 hex chars. */
    protected static String randomName() {
        return "mc-" + randomHex(4);
    }

    protected static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        StringBuilder sb = new StringBuilder();
        for (byte b : buf) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** High-entropy secret: 16 bytes (128 bit) URL-safe base64. */
    protected static String randomSecret() {
        byte[] buf = new byte[16];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        g.drawString(font, "Network Name", width / 2 - 120, 46, 0xA0A0A0);
        g.drawString(font, "Network Secret", width / 2 - 120, 82, 0xA0A0A0);
        g.drawString(font, "Shared Nodes", width / 2 - 120, 118, 0xA0A0A0);
        if (!message.isEmpty()) {
            g.drawCenteredString(font, message, width / 2, 176, 0xFFFF55);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
