package dev.etmc.client.screen;

import dev.etmc.client.ClientNetworkController;
import dev.etmc.config.NetworkProfile;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Join-network screen (plan §9.3): existing name/secret + shared nodes,
 * starts EasyTier as CLIENT role with DHCP.
 */
public class JoinNetworkScreen extends NetworkFormScreen {
    public JoinNetworkScreen(Screen parent) {
        super(parent, Component.literal("Join EasyTier Network"));
    }

    @Override
    protected void init() {
        int x = width / 2 - 120;
        nameBox = addLabeledBox(x, 56, 220, "", 64, "network name from host");
        secretBox = addLabeledBox(x, 92, 220, "", 128, "network secret from host");
        peersBox = addLabeledBox(x, 128, 220, "", 512, "tcp://host:11010, ...");

        addRenderableWidget(Button.builder(Component.literal("Join"), b -> onJoin())
                .bounds(x, 154, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(x + 104, 154, 100, 20).build());
    }

    private void onJoin() {
        String name = nameBox.getValue().trim();
        String secret = secretBox.getValue();
        if (name.isEmpty() || secret.isEmpty()) {
            message = "network name and secret must not be empty";
            return;
        }
        NetworkProfile p = ClientNetworkController.get().profileFromConfig();
        p.role(NetworkProfile.Role.CLIENT)
                .networkName(name)
                .networkSecret(secret)
                .peers(parsePeers(peersBox.getValue()))
                .dhcp(true)
                .clientIp("");
        message = "starting EasyTier...";
        boolean ok = ClientNetworkController.get().connect(p);
        message = ok ? "EasyTier started, connecting..." : "failed - see logs / config errors";
    }
}
