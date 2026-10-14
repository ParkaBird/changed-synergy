package net.parkabird.changedsynergy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.parkabird.changedsynergy.origin.NativeOriginService;

public final class NativeOriginCommand {
    private NativeOriginCommand() {}
    public static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("origin").executes(context -> {
            var player = context.getSource().getPlayerOrException();
            var identity = NativeOriginService.identity(player);
            context.getSource().sendSuccess(() -> Component.translatable("origin.changed_synergy.status",
                    Component.translatable("origin.changed_synergy." + (identity == null ? "human" : identity.state)),
                    identity == null || identity.faction.isEmpty() ? Component.literal("—")
                            : Component.translatable(identity.faction.equals("light")
                                    ? "faction.changed_synergy.light." + identity.region
                                    : "faction.changed_synergy." + identity.faction)), false);
            return 1;
        }).then(Commands.literal("choose").requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                    boolean opened = NativeOriginService.reopen(EntityArgument.getPlayer(context, "player"));
                    context.getSource().sendSuccess(() -> Component.translatable("origin.changed_synergy."
                            + (opened ? "reopened" : "cannot_open")), false);
                    return opened ? 1 : 0;
                })));
    }
}
