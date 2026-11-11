package net.parkabird.changedsynergy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.parkabird.changedsynergy.ai.TelepathyService;

public final class TelepathyCommand {
    private TelepathyCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("telepathy")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                            var target = EntityArgument.getPlayer(context, "player");
                            boolean unlocked = TelepathyService.isUnlocked(target);
                            int count = TelepathyService.getTransfurCount(target);
                            boolean global = TelepathyService.enabled();
                            boolean transfurred = ProcessTransfur.isPlayerTransfurred(target);
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    "command.changed_synergy.telepathy.inspect",
                                    target.getDisplayName(),
                                    unlocked,
                                    count,
                                    global,
                                    transfurred), false);
                            return 1;
                        })))
                .then(Commands.literal("grant")
                        .then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                            var target = EntityArgument.getPlayer(context, "player");
                            TelepathyService.grant(target);
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    "command.changed_synergy.telepathy.granted", target.getDisplayName()), true);
                            return 1;
                        })))
                .then(Commands.literal("revoke")
                        .then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                            var target = EntityArgument.getPlayer(context, "player");
                            TelepathyService.revoke(target);
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    "command.changed_synergy.telepathy.revoked", target.getDisplayName()), true);
                            return 1;
                        })));
    }
}
