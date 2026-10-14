package net.parkabird.changedsynergy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.BondReleaseService;
import net.parkabird.changedsynergy.ai.BondTokenData;
import net.parkabird.changedsynergy.ai.BondTokenService;
import net.parkabird.changedsynergy.ai.CreaturePersonality;
import net.parkabird.changedsynergy.ai.CreatureSocialProfile;
import net.parkabird.changedsynergy.ai.FactionReputation;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/** Operator tools for the same NPC/player relationship state used by social interactions. */
final class RelationshipCommand {
    private static final SimpleCommandExceptionType LOOK_REQUIRED = error("look_required");
    private static final SimpleCommandExceptionType INVALID_CREATURE = error("personal_creature_required");
    private static final SimpleCommandExceptionType PLAYER_UNAVAILABLE = error("player_unavailable");

    private RelationshipCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("relationship")
                .executes(context -> BondCommand.openRelationshipManager(context.getSource()))
                .then(Commands.literal("help").executes(context -> help(context.getSource())))
                .then(targets(Commands.literal("inspect"), RelationshipCommand::inspect))
                .then(scalar("set", "value", -40, 60, RelationshipCommand::set))
                .then(scalar("add", "amount", -100, 100, RelationshipCommand::add))
                .then(operator("establish", RelationshipCommand::establish))
                .then(operator("forget", RelationshipCommand::forget))
                .then(operator("reconcile", RelationshipCommand::reconcile))
                .then(operator("bond", RelationshipCommand::bond))
                .then(operator("release", RelationshipCommand::release))
                .then(Commands.literal("cooldown").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("care").then(targets(
                                Commands.argument("seconds", IntegerArgumentType.integer(0, 7200)),
                                context -> cooldown(context, true))))
                        .then(Commands.literal("gift").then(targets(
                                Commands.argument("seconds", IntegerArgumentType.integer(0, 7200)),
                                context -> cooldown(context, false))))
                        .then(targets(Commands.literal("reset"), RelationshipCommand::resetCooldowns)))
                .then(Commands.literal("allowance").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("set").then(targets(
                                Commands.argument("value", IntegerArgumentType.integer(0, 60)),
                                RelationshipCommand::allowance)))
                        .then(targets(Commands.literal("refill"), RelationshipCommand::allowance)))
                .then(Commands.literal("token").requires(source -> source.hasPermission(2))
                        .then(targets(Commands.literal("offer"), RelationshipCommand::offerToken))
                        .then(targets(Commands.literal("withdraw"), RelationshipCommand::withdrawToken))
                        .then(targets(Commands.literal("cancel"), RelationshipCommand::cancelToken)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> operator(
            String name, Command<CommandSourceStack> action) {
        return targets(Commands.literal(name).requires(source -> source.hasPermission(2)), action);
    }

    /** Value-first shortcuts coexist with the previous <creature> <player> <value> syntax. */
    private static LiteralArgumentBuilder<CommandSourceStack> scalar(
            String name, String argument, int minimum, int maximum,
            Command<CommandSourceStack> action) {
        return Commands.literal(name).requires(source -> source.hasPermission(2))
                .then(targets(Commands.argument(argument, IntegerArgumentType.integer(minimum, maximum)), action))
                .then(Commands.argument("creature", EntityArgument.entity())
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument(argument, IntegerArgumentType.integer(minimum, maximum))
                                        .executes(action))));
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T targets(
            T node, Command<CommandSourceStack> action) {
        return node.executes(action)
                .then(Commands.argument("creature", EntityArgument.entity()).executes(action)
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2)).executes(action)));
    }

    private static boolean hasArgument(CommandContext<CommandSourceStack> context, String name) {
        return context.getNodes().stream().anyMatch(node -> node.getNode().getName().equals(name));
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = hasArgument(context, "player")
                ? EntityArgument.getPlayer(context, "player") : context.getSource().getPlayerOrException();
        var body = TrueTransfurCompat.interactionBody(player);
        if (TrueTransfurCompat.isInstinctShell(player)
                && (body == player || !body.isAlive() || body.isRemoved())) throw PLAYER_UNAVAILABLE.create();
        return player;
    }

    private static ChangedEntity creature(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity target;
        if (hasArgument(context, "creature")) {
            target = EntityArgument.getEntity(context, "creature");
        } else {
            var observer = context.getSource().getPlayerOrException();
            var body = TrueTransfurCompat.interactionBody(observer);
            if (TrueTransfurCompat.isInstinctShell(observer) && body == observer) throw PLAYER_UNAVAILABLE.create();
            Vec3 eye = body.getEyePosition();
            Vec3 end = body.pick(8.0D, 1.0F, false).getLocation();
            var hit = ProjectileUtil.getEntityHitResult(body, eye, end,
                    body.getBoundingBox().expandTowards(body.getViewVector(1.0F).scale(8.0D)).inflate(1.0D),
                    entity -> entity.isPickable() && !entity.isSpectator() && entity.isAlive(),
                    eye.distanceToSqr(end));
            if (hit == null) throw LOOK_REQUIRED.create();
            target = hit.getEntity();
        }
        // A player model or an Instinct proxy must never fall through into ordinary NPC memory.
        if (!(target instanceof ChangedEntity npc) || !npc.isAlive() || npc.isRemoved()
                || !LatexSocialMemory.isSocialLatex(npc)
                || !CreatureSocialProfile.allowsPersonalRelationship(npc)) throw INVALID_CREATURE.create();
        return npc;
    }

    private static boolean growthEnabled(CommandSourceStack source, ChangedEntity npc) {
        if (ChangedSynergyGameRules.enabled(npc.level(), ChangedSynergyGameRules.FRIENDSHIP_SYSTEM)) return true;
        source.sendFailure(Component.translatable("command.changed_synergy.relationship.disabled",
                "changedSynergyFriendshipSystem"));
        return false;
    }

    private static int inspect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var npc = creature(context);
        var player = player(context);
        var source = context.getSource();
        var growth = CreaturePersonality.growthSnapshot(npc, player);
        int affection = growth.affection();
        source.sendSuccess(() -> Component.translatable("command.changed_synergy.relationship.detail",
                npc.getDisplayName(), player.getDisplayName(), npc.getStringUUID(),
                Component.translatable(CreaturePersonality.relationshipTier(npc, player).translationKey()),
                affection).withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("command.changed_synergy.relationship.flags",
                yes(growth.established()),
                yes(LatexSocialMemory.bondedPlayerUuids(npc).contains(player.getUUID())),
                yes(CreaturePersonality.recognizesBond(npc, player))), false);
        source.sendSuccess(() -> Component.translatable("command.changed_synergy.relationship.growth",
                yes(CreaturePersonality.balanced()),
                growth.careAllowance(), ChangedSynergyConfig.COMMON.careAffectionAllowance.get(),
                seconds(growth.careCooldownTicks()), seconds(growth.giftCooldownTicks())), false);
        var token = BondTokenService.required(npc);
        String tokenState = BondTokenService.reservedFor(npc, player) ? "reserved_self"
                : BondTokenData.get(player.server).hasOffer(npc.getUUID()) ? "reserved_other" : "none";
        source.sendSuccess(() -> Component.translatable("command.changed_synergy.relationship.token_status",
                yes(ChangedSynergyConfig.COMMON.bondTokensRequired.get()), token.getCount(), token.getHoverName(),
                Component.translatable("command.changed_synergy.relationship.token." + tokenState),
                yes(BondTokenService.ready(npc, player))), false);
        source.sendSuccess(() -> Component.translatable("command.changed_synergy.relationship.rules",
                yes(ChangedSynergyGameRules.enabled(npc.level(), ChangedSynergyGameRules.FRIENDSHIP_SYSTEM)),
                yes(ChangedSynergyGameRules.enabled(npc.level(), ChangedSynergyGameRules.BOND_SYSTEM)),
                yes(LatexSocialMemory.isProvoked(npc, player) || LatexSocialMemory.hasBetrayedPatTruce(npc, player)),
                Component.translatable(FactionReputation.Standing.forScore(
                        FactionReputation.score(npc, player)).translationKey())), false);
        return affection;
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var npc = creature(context); var player = player(context);
        if (!growthEnabled(context.getSource(), npc)) return 0;
        report(context.getSource(), npc, player, CreaturePersonality.setFamiliarity(
                npc, player, IntegerArgumentType.getInteger(context, "value")));
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var npc = creature(context); var player = player(context);
        if (!growthEnabled(context.getSource(), npc)) return 0;
        report(context.getSource(), npc, player, CreaturePersonality.adjustFamiliarity(
                npc, player, IntegerArgumentType.getInteger(context, "amount")));
        return 1;
    }

    private static int establish(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var npc = creature(context); var player = player(context);
        if (!growthEnabled(context.getSource(), npc)) return 0;
        CreaturePersonality.establishRelationship(npc, player);
        report(context.getSource(), npc, player, CreaturePersonality.setFamiliarity(npc, player, 60));
        return 1;
    }

    private static int forget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var npc = creature(context); var player = player(context);
        if (LatexSocialMemory.bondedPlayerUuids(npc).contains(player.getUUID())
                || LatexSocialMemory.isPetOwner(npc, player)) return failure(context, "cannot_forget_bond");
        if (!CreaturePersonality.forgetRelationship(npc, player)) return failure(context, "no_relationship");
        if (BondTokenService.reservedFor(npc, player)) BondTokenService.withdraw(player, npc.getUUID());
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.changed_synergy.relationship.forgotten", npc.getDisplayName(), player.getDisplayName()), true);
        return 1;
    }

    private static int reconcile(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var npc = creature(context); var player = player(context);
        if (!growthEnabled(context.getSource(), npc)) return 0;
        CreaturePersonality.establishRelationship(npc, player);
        LatexSocialMemory.reconcileRelationship(npc, player);
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.changed_synergy.relationship.reconciled", npc.getDisplayName(), player.getDisplayName()), true);
        return 1;
    }

    /** Debug shortcut bypasses affection/token progression, never ownership or server bond rules. */
    private static int bond(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var npc = creature(context); var player = player(context);
        if (!growthEnabled(context.getSource(), npc)) return 0;
        if (LatexSocialMemory.bondedPlayerUuids(npc).contains(player.getUUID())) return failure(context, "already_bonded");
        if (BondTokenService.reservedElsewhere(npc, player)) return failure(context, "token_reserved_other");
        if (!BondTokenService.canCreateBond(npc, player)) return failure(context, "bond_unavailable");
        CreaturePersonality.establishRelationship(npc, player);
        CreaturePersonality.setFamiliarity(npc, player, 60);
        LatexSocialMemory.addBond(npc, player);
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.changed_synergy.relationship.bonded", npc.getDisplayName(), player.getDisplayName()), true);
        return 1;
    }

    private static int release(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var npc = creature(context); var player = player(context);
        if (BondReleaseService.release(player, npc.getUUID()) == BondReleaseService.Result.NOT_BONDED)
            return failure(context, "no_bond");
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.changed_synergy.relationship.released", npc.getDisplayName(), player.getDisplayName()), true);
        return 1;
    }

    private static int cooldown(CommandContext<CommandSourceStack> context, boolean care) throws CommandSyntaxException {
        var npc = creature(context); var player = player(context);
        if (!growthEnabled(context.getSource(), npc)) return 0;
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        if (care) CreaturePersonality.setCareCooldown(npc, player, seconds);
        else CreaturePersonality.setGiftCooldown(npc, player, seconds);
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.changed_synergy.relationship.cooldown_set", npc.getDisplayName(), player.getDisplayName(),
                Component.translatable("command.changed_synergy.relationship.cooldown." + (care ? "care" : "gift")), seconds), true);
        return 1;
    }

    private static int resetCooldowns(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var npc = creature(context); var player = player(context);
        if (!growthEnabled(context.getSource(), npc)) return 0;
        CreaturePersonality.setCareCooldown(npc, player, 0);
        CreaturePersonality.setGiftCooldown(npc, player, 0);
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.changed_synergy.relationship.cooldowns_reset", npc.getDisplayName(), player.getDisplayName()), true);
        return 1;
    }

    private static int allowance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var npc = creature(context); var player = player(context);
        if (!growthEnabled(context.getSource(), npc)) return 0;
        int requested = hasArgument(context, "value") ? IntegerArgumentType.getInteger(context, "value")
                : ChangedSynergyConfig.COMMON.careAffectionAllowance.get();
        int actual = CreaturePersonality.setCareAllowance(npc, player, requested);
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.changed_synergy.relationship.allowance_set", npc.getDisplayName(), player.getDisplayName(), actual), true);
        return 1;
    }

    private static int offerToken(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var npc = creature(context); var player = player(context);
        if (!BondTokenService.offer(npc, player)) return failure(context, "token_offer_failed");
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.changed_synergy.relationship.token_offered", npc.getDisplayName(), player.getDisplayName()), true);
        return 1;
    }

    private static int withdrawToken(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var npc = creature(context); var player = player(context);
        if (!BondTokenService.withdraw(player, npc.getUUID())) return failure(context, "no_token");
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.changed_synergy.relationship.token_withdrawn", npc.getDisplayName(), player.getDisplayName()), true);
        return 1;
    }

    private static int cancelToken(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var npc = creature(context);
        var data = BondTokenData.get(context.getSource().getServer());
        if (!data.hasOffer(npc.getUUID())) return failure(context, "no_token");
        data.refund(npc.getUUID());
        context.getSource().sendSuccess(() -> Component.translatable(
                "message.changed_synergy.relationship.token_cancelled"), true);
        return 1;
    }

    private static void report(CommandSourceStack source, ChangedEntity npc, ServerPlayer player, int affection) {
        source.sendSuccess(() -> Component.translatable("command.changed_synergy.relationship.changed",
                npc.getDisplayName(), player.getDisplayName(),
                Component.translatable(CreaturePersonality.relationshipTier(npc, player).translationKey()), affection), true);
        if (!CreaturePersonality.hasEstablishedRelationship(npc, player)) source.sendSuccess(() -> Component.translatable(
                "command.changed_synergy.relationship.still_stranger").withStyle(ChatFormatting.GRAY), false);
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("command.changed_synergy.relationship.help.header")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("command.changed_synergy.relationship.help.targets"), false);
        BondCommand.sendHelpLine(source, "command.changed_synergy.relationship.help.inspect", "/changedsynergy relationship inspect");
        if (source.hasPermission(2)) {
            String[] commands = {"establish", "set 8", "add 2", "cooldown reset", "cooldown care 0",
                    "cooldown gift 0", "allowance refill", "allowance set 0", "token offer", "token withdraw",
                    "token cancel", "bond", "release", "reconcile", "forget"};
            String[] keys = {"establish", "set", "add", "cooldown_reset", "cooldown_care", "cooldown_gift",
                    "allowance_refill", "allowance_set", "token_offer", "token_withdraw", "token_cancel",
                    "bond", "release", "reconcile", "forget"};
            for (int i = 0; i < commands.length; i++) BondCommand.sendHelpLine(source,
                    "command.changed_synergy.relationship.help." + keys[i], "/changedsynergy relationship " + commands[i]);
        }
        return 1;
    }

    private static Component yes(boolean value) { return Component.translatable(value ? "gui.yes" : "gui.no"); }
    private static long seconds(long ticks) { return (ticks + 19L) / 20L; }
    private static SimpleCommandExceptionType error(String key) {
        return new SimpleCommandExceptionType(Component.translatable("command.changed_synergy.relationship." + key));
    }
    private static int failure(CommandContext<CommandSourceStack> context, String key) {
        context.getSource().sendFailure(Component.translatable("command.changed_synergy.relationship." + key));
        return 0;
    }
}
