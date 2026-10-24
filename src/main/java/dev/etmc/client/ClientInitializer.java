package dev.etmc.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.etmc.EasyTierMinecraft;
import dev.etmc.client.screen.NetworkHomeScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Client entry: registers the network home keybind (K) and opens the GUI.
 */
public final class ClientInitializer {
    private static KeyMapping openNetworkHome;

    @EventBusSubscriber(modid = EasyTierMinecraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            openNetworkHome = new KeyMapping(
                    "key.etmc.network_home",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_K,
                    "key.categories.etmc");
            event.register(openNetworkHome);
        }
    }

    @EventBusSubscriber(modid = EasyTierMinecraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
    public static final class GameBus {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            while (openNetworkHome != null && openNetworkHome.consumeClick()) {
                mc.setScreen(new NetworkHomeScreen(null));
            }
        }
    }

    private ClientInitializer() { }
}
