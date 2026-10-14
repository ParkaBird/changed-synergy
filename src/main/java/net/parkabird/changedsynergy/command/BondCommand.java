package net.parkabird.changedsynergy.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.BondReleaseService;
import net.parkabird.changedsynergy.ai.BondReleaseService.Result;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory;
import net.parkabird.changedsynergy.ai.CreatureCommunityData;
import net.parkabird.changedsynergy.ai.CreatureSettlementService;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory.GroupRole;
import net.parkabird.changedsynergy.ai.FactionReputation;
import net.parkabird.changedsynergy.ai.HunterFaction;
import net.parkabird.changedsynergy.ai.LightFactionGroup;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.world.inventory.PlayerRelationshipMenu;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Player status, relationship and administration commands for Changed: Synergy. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BondCommand {
    private BondCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("changedsynergy")
                .executes(context -> showHelp(context.getSource()))
                .then(Commands.literal("help")
                        .executes(context -> showHelp(context.getSource())))
                .then(bondCommand())
                .then(RelationshipCommand.create())
                .then(reputationCommand())
                .then(routineCommand())
                .then(PlayerOutpostCommand.create())
                .then(ServerConfigCommand.create())
                .then(PersonalitySpawnCommand.create())
                .then(NativeOriginCommand.create());
        root.then(net.parkabird.changedsynergy.ai.PlayerSocialService.command());
        event.getDispatcher().register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> bondCommand() {
        return Commands.literal("bond")
                .then(Commands.literal("token")
                        .then(Commands.literal("withdraw").requires(source -> source.getEntity() instanceof ServerPlayer)
                                .then(Commands.argument("creature", UuidArgument.uuid())
                                        .executes(context -> net.parkabird.changedsynergy.ai.BondTokenService.withdraw(context.getSource().getPlayerOrException(), UuidArgument.getUuid(context, "creature")) ? 1 : 0)))
                        .then(Commands.literal("cancel").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("creature", UuidArgument.uuid()).executes(context -> {
                                    UUID id = net.parkabird.changedsynergy.ai.CreatureMorphAliasData.get(context.getSource().getServer()).resolveAlias(UuidArgument.getUuid(context, "creature"));
                                    var data = net.parkabird.changedsynergy.ai.BondTokenData.get(context.getSource().getServer());
                                    boolean refunded = data.hasOffer(id);
                                    data.refund(id);
                                    context.getSource().sendSuccess(() -> Component.translatable("message.changed_synergy.relationship.token_cancelled"), false);
                                    return refunded ? 1 : 0;
                                }))))
                .then(Commands.literal("release").requires(source -> source.getEntity() instanceof ServerPlayer)
                        .executes(context -> releaseOnly(context.getSource()))
                        .then(Commands.literal("all")
                                .executes(context -> releaseAll(context.getSource())))
                        .then(Commands.argument("creature", UuidArgument.uuid())
                                .executes(context -> release(
                                        context.getSource(),
                                        UuidArgument.getUuid(context, "creature")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> reputationCommand() {
        return Commands.literal("reputation")
                .executes(context -> showReputation(
                        context.getSource(),
                        context.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> showReputation(
                                context.getSource(),
                                EntityArgument.getPlayer(context, "player"))))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("creature", EntityArgument.entity())
                                .executes(context -> inspectReputation(
                                        context.getSource(),
                                        EntityArgument.getEntity(context, "creature"),
                                        context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(source -> source.hasPermission(2))
                                        .executes(context -> inspectReputation(
                                                context.getSource(),
                                                EntityArgument.getEntity(context, "creature"),
                                                EntityArgument.getPlayer(context, "player"))))))
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("creature", EntityArgument.entity())
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument(
                                                        "value",
                                                        IntegerArgumentType.integer(-100, 100))
                                                .executes(context -> setReputation(
                                                        context.getSource(),
                                                        EntityArgument.getEntity(
                                                                context, "creature"),
                                                        EntityArgument.getPlayer(
                                                                context, "player"),
                                                        IntegerArgumentType.getInteger(
                                                                context, "value")))))))
                .then(Commands.literal("add")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("creature", EntityArgument.entity())
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument(
                                                        "amount",
                                                        IntegerArgumentType.integer(-200, 200))
                                                .executes(context -> addReputation(
                                                        context.getSource(),
                                                        EntityArgument.getEntity(
                                                                context, "creature"),
                                                        EntityArgument.getPlayer(
                                                                context, "player"),
                                                        IntegerArgumentType.getInteger(
                                                                context, "amount")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> routineCommand() {
        return Commands.literal("routine")
                .then(Commands.literal("inspect")
                        .then(Commands.argument("creature", EntityArgument.entity())
                                .executes(context -> inspectRoutine(
                                        context.getSource(),
                                        EntityArgument.getEntity(context, "creature")))))
                .then(Commands.literal("setrole")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("creature", EntityArgument.entity())
                                .then(Commands.argument(
                                                "role", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (GroupRole role : GroupRole.values()) {
                                                builder.suggest(role.id());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> setRoutineRole(
                                                context.getSource(),
                                                EntityArgument.getEntity(
                                                        context, "creature"),
                                                StringArgumentType.getString(
                                                        context, "role"))))))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("creature", EntityArgument.entity())
                                .executes(context -> resetRoutine(
                                        context.getSource(), EntityArgument.getEntity(
                                                context, "creature")))));
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable(
                "command.changed_synergy.help.header")
                .withStyle(ChatFormatting.GOLD), false);
        sendHelpLine(source, "command.changed_synergy.help.reputation",
                "/changedsynergy reputation");
        sendHelpLine(source, "command.changed_synergy.help.relationship",
                "/changedsynergy relationship");
        sendHelpLine(source, "command.changed_synergy.help.outpost",
                "/changedsynergy outpost");
        sendHelpLine(source, "command.changed_synergy.help.inspect",
                "/changedsynergy relationship inspect");
        if (source.getEntity() instanceof ServerPlayer) {
            sendHelpLine(source, "command.changed_synergy.help.bond",
                    "/changedsynergy bond release");
            sendHelpLine(source, "command.changed_synergy.help.token_withdraw",
                    "/changedsynergy bond token withdraw ");
        }
        sendHelpLine(source, "command.changed_synergy.help.routine",
                "/changedsynergy routine inspect ");
        if (source.hasPermission(2)) {
            sendHelpLine(source, "command.changed_synergy.help.relationship_debug",
                    "/changedsynergy relationship help");
            sendHelpLine(source, "command.changed_synergy.help.token_cancel",
                    "/changedsynergy bond token cancel ");
            sendHelpLine(source, "command.changed_synergy.help.config",
                    "/changedsynergy config list");
            sendHelpLine(source, "command.changed_synergy.help.spawn",
                    "/changedsynergy spawn ");
            source.sendSuccess(() -> Component.translatable(
                    "command.changed_synergy.help.admin")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    static void sendHelpLine(
            CommandSourceStack source,
            String translationKey,
            String command) {
        source.sendSuccess(() -> Component.translatable(
                translationKey, commandLink(command)), false);
    }

    private static Component commandLink(String command) {
        return Component.literal(command)
                .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.SUGGEST_COMMAND, command))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.translatable(
                                        "command.changed_synergy.help.click"))));
    }

    static int openRelationshipManager(CommandSourceStack source)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!player.isAlive() || player.isSpectator()) {
            source.sendFailure(Component.translatable(
                    "command.changed_synergy.relationship.unavailable"));
            return 0;
        }
        PlayerRelationshipMenu.open(player);
        return 1;
    }

    private static int showReputation(
            CommandSourceStack source,
            ServerPlayer player) {
        if (!ChangedSynergyGameRules.enabled(
                player.level(), ChangedSynergyGameRules.FACTION_REPUTATION)) {
            source.sendFailure(Component.translatable(
                    "command.changed_synergy.reputation.disabled",
                    player.getDisplayName()));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable(
                "command.changed_synergy.reputation.overview",
                player.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
        for (HunterFaction faction : HunterFaction.values()) {
            int score = FactionReputation.scoreAt(
                    faction,
                    player,
                    player.level(),
                    player.blockPosition());
            FactionReputation.Standing standing =
                    FactionReputation.Standing.forScore(score);
            Component factionName = Component.translatable(
                    faction == HunterFaction.LIGHT
                            ? LightFactionGroup.translationKey(
                                    LightFactionGroup.at(
                                            player.level(), player.blockPosition()))
                            : faction.translationKey());
            Component standingName = Component.translatable(
                    standing.translationKey())
                    .withStyle(standingColor(standing));
            source.sendSuccess(() -> Component.translatable(
                    "command.changed_synergy.reputation.entry",
                    factionName, standingName, score), false);
        }
        return HunterFaction.values().length;
    }

    private static ChatFormatting standingColor(
            FactionReputation.Standing standing) {
        return switch (standing) {
            case HOSTILE -> ChatFormatting.RED;
            case DISTRUSTED -> ChatFormatting.GOLD;
            case NEUTRAL -> ChatFormatting.GRAY;
            case RECOGNIZED -> ChatFormatting.AQUA;
            case RESPECTED -> ChatFormatting.GREEN;
            case ALLIED -> ChatFormatting.LIGHT_PURPLE;
        };
    }

    private static int inspectRoutine(
            CommandSourceStack source,
            Entity entity) {
        ChangedEntity creature = socialCreature(source, entity);
        if (creature == null) return 0;
        CreatureLifeMemory.Snapshot life = CreatureLifeMemory.snapshot(creature);
        CreatureLifeMemory.RoleStats stats = CreatureLifeMemory.roleStats(creature);
        source.sendSuccess(() -> Component.translatable(
                "command.changed_synergy.routine.status",
                creature.getDisplayName(),
                Component.translatable("routine.changed_synergy.role."
                        + life.role().id()),
                Component.translatable("routine.changed_synergy.state."
                        + life.routine().id()),
                stats.first(), stats.second(), stats.third()), false);
        CreatureCommunityData.snapshot(creature).ifPresent(community ->
                source.sendSuccess(() -> Component.translatable(
                        "command.changed_synergy.community.status",
                        CreatureSettlementService.storedItemCount(creature),
                        community.foodDelivered(),
                        community.materialsDelivered(),
                        community.cache().isPresent()), false));
        return 1;
    }

    private static int setRoutineRole(
            CommandSourceStack source,
            Entity entity,
            String roleId) {
        ChangedEntity creature = socialCreature(source, entity);
        if (creature == null) return 0;
        GroupRole role = GroupRole.fromId(roleId);
        if (role == null) {
            source.sendFailure(Component.translatable(
                    "command.changed_synergy.routine.invalid_role", roleId));
            return 0;
        }
        CreatureLifeMemory.setRole(creature, role);
        CreatureLifeMemory.scheduleNextDecision(
                creature, creature.level().getGameTime() + 1L);
        source.sendSuccess(() -> Component.translatable(
                "command.changed_synergy.routine.role_set",
                creature.getDisplayName(),
                Component.translatable("routine.changed_synergy.role." + role.id())),
                true);
        return 1;
    }

    private static int resetRoutine(
            CommandSourceStack source,
            Entity entity) {
        ChangedEntity creature = socialCreature(source, entity);
        if (creature == null) return 0;
        CreatureCommunityData.detach(creature);
        CreatureLifeMemory.reset(creature);
        source.sendSuccess(() -> Component.translatable(
                "command.changed_synergy.routine.reset",
                creature.getDisplayName()), true);
        return 1;
    }

    private static int inspectReputation(
            CommandSourceStack source,
            Entity entity,
            ServerPlayer player) {
        ChangedEntity creature = socialCreature(source, entity);
        if (creature == null) return 0;
        int score = FactionReputation.score(creature, player);
        source.sendSuccess(() -> reputationStatus(creature, player, score), false);
        return score;
    }

    private static int setReputation(
            CommandSourceStack source,
            Entity entity,
            ServerPlayer player,
            int value) {
        ChangedEntity creature = socialCreature(source, entity);
        if (creature == null) return 0;
        int score = FactionReputation.set(creature, player, value);
        source.sendSuccess(() -> reputationStatus(creature, player, score), true);
        return score;
    }

    private static int addReputation(
            CommandSourceStack source,
            Entity entity,
            ServerPlayer player,
            int amount) {
        ChangedEntity creature = socialCreature(source, entity);
        if (creature == null) return 0;
        int score = FactionReputation.adjust(creature, player, amount);
        source.sendSuccess(() -> reputationStatus(creature, player, score), true);
        return score;
    }

    private static ChangedEntity socialCreature(
            CommandSourceStack source,
            Entity entity) {
        if (entity instanceof ChangedEntity creature
                && LatexSocialMemory.isSocialLatex(creature)) {
            return creature;
        }
        source.sendFailure(Component.translatable(
                "command.changed_synergy.relationship.invalid_creature"));
        return null;
    }

    private static Component reputationStatus(
            ChangedEntity creature,
            ServerPlayer player,
            int score) {
        return Component.translatable(
                "command.changed_synergy.reputation.status",
                player.getDisplayName(),
                Component.translatable(
                        FactionReputation.displayTranslationKey(creature)),
                Component.translatable(FactionReputation.Standing
                        .forScore(score).translationKey()),
                score);
    }

    private static int releaseOnly(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Set<UUID> bonds = LatexSocialMemory.bondedCreatureUuids(player);
        if (bonds.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                    "command.changed_synergy.bond.none"));
            return 0;
        }
        if (bonds.size() > 1) {
            player.sendSystemMessage(Component.translatable(
                    "command.changed_synergy.bond.choose", bonds.size()));
            return 0;
        }
        return release(source, bonds.iterator().next());
    }

    private static int release(CommandSourceStack source, UUID creatureUuid)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Result result = BondReleaseService.release(player, creatureUuid);
        switch (result) {
            case RELEASED_LOADED -> player.sendSystemMessage(Component.translatable(
                    "command.changed_synergy.bond.released", creatureUuid.toString()));
            case QUEUED_UNLOADED -> player.sendSystemMessage(Component.translatable(
                    "command.changed_synergy.bond.queued", creatureUuid.toString()));
            case NOT_BONDED -> player.sendSystemMessage(Component.translatable(
                    "command.changed_synergy.bond.not_bonded", creatureUuid.toString()));
        }
        return result == Result.NOT_BONDED ? 0 : 1;
    }

    private static int releaseAll(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<UUID> bonds = new ArrayList<>(LatexSocialMemory.bondedCreatureUuids(player));
        if (bonds.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                    "command.changed_synergy.bond.none"));
            return 0;
        }

        int released = 0;
        int queued = 0;
        for (UUID uuid : bonds) {
            Result result = BondReleaseService.release(player, uuid);
            if (result == Result.RELEASED_LOADED) {
                released++;
            } else if (result == Result.QUEUED_UNLOADED) {
                queued++;
            }
        }
        player.sendSystemMessage(Component.translatable(
                "command.changed_synergy.bond.released_all", released, queued));
        return released + queued;
    }
}
