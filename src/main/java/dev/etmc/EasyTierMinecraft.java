package dev.etmc;

import dev.etmc.config.ModConfig;
import dev.etmc.server.DedicatedServerManager;
import dev.etmc.server.ServerCommands;
import java.nio.file.Path;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod entry point. P1 scope: config bootstrap only; the EasyTier process is
 * NOT auto-started (auto_start=false default) and lifecycle wiring lands in P2.
 */
@Mod(EasyTierMinecraft.MODID)
public class EasyTierMinecraft {
    public static final String MODID = "etmc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public EasyTierMinecraft(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.register(DedicatedServerManager.get());
        NeoForge.EVENT_BUS.register(new ServerCommands());
        LOGGER.info("[etmc] EasyTier for Minecraft 0.1.0 (P1) constructing");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Path configDir = FMLPaths.CONFIGDIR.get();
            try {
                ModConfig.Loaded loaded = ModConfig.load(configDir);
                for (String created : loaded.created()) {
                    LOGGER.info("[etmc] created default config file: {}", created);
                }
                if (!loaded.errors().isEmpty()) {
                    LOGGER.error("[etmc] config errors (network disabled until fixed):");
                    for (String err : loaded.errors()) {
                        LOGGER.error("[etmc]   - {}", err);
                    }
                } else {
                    LOGGER.info("[etmc] config ok: role={} name={} listen={} rpc={} hostIp={} forwards={}",
                            loaded.profile().role(),
                            loaded.profile().networkName(),
                            loaded.profile().listenPort(),
                            loaded.profile().rpcPort(),
                            loaded.profile().hostIp(),
                            loaded.profile().forwards().size());
                }
            } catch (Exception e) {
                // Config problems must never crash the game (plan §6.3).
                LOGGER.error("[etmc] failed to load config", e);
            }
        });
    }

    private void onServerStopping(ServerStoppingEvent event) {
        DedicatedServerManager.get().stopAll();
    }
}
