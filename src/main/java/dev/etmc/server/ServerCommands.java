package dev.etmc.server;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Admin console commands (plan §8.3): /etmc status|peers|reload|reconnect.
 * Permission level 2 (ops) — network control must not be wide open.
 */
public final class ServerCommands {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("etmc")
                .requires(src -> src.hasPermission(2));
        root.then(Commands.literal("status").executes(ctx -> {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[etmc] " + DedicatedServerManager.get().statusLine()), false);
            return 1;
        }));
        root.then(Commands.literal("peers").executes(ctx -> {
            var peers = DedicatedServerManager.get().peers();
            if (peers.isEmpty()) {
                ctx.getSource().sendSuccess(
                        () -> Component.literal("[etmc] no peers"), false);
            }
            for (var p : peers) {
                ctx.getSource().sendSuccess(() -> Component.literal("[etmc] "
                        + p.virtualIp() + " " + p.hostname() + " " + p.cost()
                        + (p.latencyMs() >= 0 ? " " + p.latencyMs() + "ms" : "")),
                        false);
            }
            return peers.size();
        }));
        root.then(Commands.literal("reload").executes(ctx -> {
            boolean ok = DedicatedServerManager.get().restart();
            ctx.getSource().sendSuccess(() -> Component.literal("[etmc] reload "
                    + (ok ? "ok: " + DedicatedServerManager.get().statusLine()
                            : "failed (see log)")), ok);
            return ok ? 1 : 0;
        }));
        root.then(Commands.literal("reconnect").executes(ctx -> {
            boolean ok = DedicatedServerManager.get().restart();
            ctx.getSource().sendSuccess(() -> Component.literal("[etmc] reconnect "
                    + (ok ? "ok" : "failed (see log)")), ok);
            return ok ? 1 : 0;
        }));
        event.getDispatcher().register(root);
    }
}
