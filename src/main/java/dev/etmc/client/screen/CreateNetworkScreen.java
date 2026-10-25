package dev.etmc.client.screen;

import dev.etmc.client.ClientNetworkController;
import dev.etmc.config.NetworkProfile;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Create-network screen (plan §9.2): random or manual name/secret,
 * shared node list, starts EasyTier as HOST role.
 */
public class CreateNetworkScreen extends NetworkFormScreen {
    public CreateNetworkScreen(Screen parent) {
        super(parent, Component.literal("Create EasyTier Network"));
    }

    @Override
    protected void init() {
        int x = width / 2 - 120;
        nameBox = addLabeledBox(x, 56, 200, randomName(), 64, "mc-xxxxxxxx");
        addRenderableWidget(Button.builder(Component.literal("R"), b -> nameBox.setValue(randomName()))
                .bounds(x + 204, 55, 16, 18).build());

        secretBox = addLabeledBox(x, 92, 200, randomSecret(), 128, "strong secret");
        addRenderableWidget(Button.builder(Component.literal("R"), b -> secretBox.setValue(randomSecret()))
                .bounds(x + 204, 91, 16, 18).build());

        peersBox = addLabeledBox(x, 128, 220, "", 512, "tcp://host:11010, ...");

        addRenderableWidget(Button.builder(Component.literal("Create & Connect"), b -> onCreate())
                .bounds(x, 154, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(x + 154, 154, 66, 20).build());
    }

    private void onCreate() {
        String name = nameBox.getValue().trim();
        String secret = secretBox.getValue();
        if (name.isEmpty() || secret.isEmpty()) {
            message = "network name and secret must not be empty";
            return;
        }
        NetworkProfile p = ClientNetworkController.get().profileFromConfig();
        p.role(NetworkProfile.Role.HOST)
                .networkName(name)
                .networkSecret(secret)
                .peers(parsePeers(peersBox.getValue()))
                .dhcp(false)
                .clientIp("")
                .hostIp("10.126.126.1");
        message = "starting EasyTier...";
        boolean ok = ClientNetworkController.get().connect(p);
        message = ok ? "EasyTier started, connecting..." : "failed - see logs / config errors";
    }
}
