package net.parkabird.changedsynergy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Comparator;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.CreatureCommunityData;
import net.parkabird.changedsynergy.ai.CreatureSettlementService;
import net.parkabird.changedsynergy.ai.PlayerOutpostData;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Member;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Outpost;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Role;
import net.parkabird.changedsynergy.ai.PlayerOutpostService;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostMenu;

/** In-game management for a player-built bell, real chest, and resident crew. */
final class PlayerOutpostCommand {
    private PlayerOutpostCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> create() {
        var root = Commands.literal("outpost")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> open(context.getSource()))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("claim").executes(context -> claim(context.getSource())))
                .then(Commands.literal("storage").executes(context -> link(context.getSource(), true)))
                .then(Commands.literal("bed").executes(context -> link(context.getSource(), false)))
                .then(Commands.literal("invite")
                        .executes(context -> invite(context.getSource(), lookedAtCreature(player(context.getSource()))))
                        .then(Commands.argument("creature", EntityArgument.entity())
                                .executes(context -> invite(context.getSource(), EntityArgument.getEntity(context, "creature")))))
                .then(Commands.literal("team")
                        .then(Commands.literal("follow").executes(context -> team(context.getSource(), Role.CREW)))
                        .then(Commands.literal("hold").executes(context -> team(context.getSource(), Role.HOLD)))
                        .then(Commands.literal("return").executes(context -> team(context.getSource(), Role.RETURN)))
                        .then(Commands.literal("retreat").executes(context -> team(context.getSource(), Role.RETREAT))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("member", UuidArgument.uuid())
                                .executes(context -> remove(context.getSource(), UuidArgument.getUuid(context, "member")))))
                .then(Commands.literal("role")
                        .then(Commands.argument("member", UuidArgument.uuid())
                                .then(Commands.literal("resident").executes(context -> role(context.getSource(), UuidArgument.getUuid(context, "member"), Role.RESIDENT)))
                                .then(Commands.literal("guard").executes(context -> role(context.getSource(), UuidArgument.getUuid(context, "member"), Role.GUARD)))
                                .then(Commands.literal("supply").executes(context -> role(context.getSource(), UuidArgument.getUuid(context, "member"), Role.SUPPLY)))
                                .then(Commands.literal("crew").executes(context -> role(context.getSource(), UuidArgument.getUuid(context, "member"), Role.CREW)))
                                .then(Commands.literal("hold").executes(context -> role(context.getSource(), UuidArgument.getUuid(context, "member"), Role.HOLD)))
                                .then(Commands.literal("return").executes(context -> role(context.getSource(), UuidArgument.getUuid(context, "member"), Role.RETURN)))
                                .then(Commands.literal("retreat").executes(context -> role(context.getSource(), UuidArgument.getUuid(context, "member"), Role.RETREAT)))))
                .then(Commands.literal("help").executes(context -> help(context.getSource())))
                .then(Commands.literal("disband").executes(context -> disband(context.getSource())));
        return root;
    }

    private static ServerPlayer player(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return source.getPlayerOrException();
    }

    private static Outpost owned(CommandSourceStack source, ServerPlayer player) {
        if (!ChangedSynergyConfig.COMMON.playerOutposts.get()) {
            error(source, "disabled");
            return null;
        }
        Outpost outpost = PlayerOutpostData.get(player.server).byOwner(player.getUUID()).orElse(null);
        if (outpost == null) error(source, "missing");
        return outpost;
    }

    private static BlockPos lookedAt(ServerPlayer player) {
        HitResult hit = player.pick(6.0D, 0.0F, false);
        return hit instanceof BlockHitResult block ? block.getBlockPos() : null;
    }

    private static ChangedEntity lookedAtCreature(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        return player.serverLevel().getEntitiesOfClass(ChangedEntity.class,
                        player.getBoundingBox().inflate(6.0D),
                        mob -> mob.isAlive() && player.hasLineOfSight(mob)
                                && eye.vectorTo(mob.getBoundingBox().getCenter()).normalize().dot(look) > 0.96D)
                .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
    }

    private static int claim(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        BlockPos pos = lookedAt(player);
        if (pos == null) return error(source, "look");
        String result = PlayerOutpostData.get(player.server).claim(player.serverLevel(), player.getUUID(), pos);
        if (!"claimed".equals(result)) return error(source, result);
        success(source, "claimed", pos.getX(), pos.getY(), pos.getZ());
        return 1;
    }

    private static int link(CommandSourceStack source, boolean storage) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        Outpost outpost = owned(source, player);
        if (outpost == null) return 0;
        if (!outpost.active(player.serverLevel())) return error(source, "inactive");
        BlockPos pos = lookedAt(player);
        if (pos == null || !outpost.contains(pos)) return error(source, "range");
        if (storage) {
            if (PlayerOutpostData.container(player.serverLevel(), pos) == null
                    || CreatureCommunityData.snapshotAtCache(player.serverLevel(), pos).isPresent()) return error(source, "container");
            PlayerOutpostData.get(player.server).setStorage(outpost, pos);
            success(source, "storage", pos.getX(), pos.getY(), pos.getZ());
        } else {
            if (!(player.serverLevel().getBlockState(pos).getBlock() instanceof BedBlock)
                    && !(player.serverLevel().getBlockState(pos).getBlock()
                            instanceof net.ltxprogrammer.changed.block.Pillow)) return error(source, "bed");
            PlayerOutpostData.get(player.server).setBed(outpost, pos);
            success(source, "bed", pos.getX(), pos.getY(), pos.getZ());
        }
        return 1;
    }

    private static int invite(CommandSourceStack source, Entity entity) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        Outpost outpost = owned(source, player);
        if (outpost == null) return 0;
        if (!outpost.active(player.serverLevel())) return error(source, "inactive");
        if (!(entity instanceof ChangedEntity mob) || entity.level() != player.level()
                || !outpost.contains(mob.blockPosition()) || !outpost.contains(player.blockPosition())
                || !PlayerOutpostService.authorized(mob, player)) return error(source, "ineligible");
        PlayerOutpostData data = PlayerOutpostData.get(player.server);
        if (!data.add(outpost, mob.getUUID(), mob.getDisplayName().getString(),
                player.level().dimension().location().toString(), mob.blockPosition(),
                net.parkabird.changedsynergy.ai.PlayerOutpostSupplyType.of(mob))) return error(source, "full");
        CreatureSettlementService.dropCargo(mob);
        CreatureCommunityData.detach(mob);
        mob.setPersistenceRequired();
        PlayerOutpostService.applyRole(mob, player, Role.RESIDENT);
        success(source, "invited", mob.getDisplayName());
        return 1;
    }

    private static int team(CommandSourceStack source, Role role) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        Outpost outpost = owned(source, player);
        if (outpost == null) return 0;
        PlayerOutpostData data = PlayerOutpostData.get(player.server);
        int changed = 0;
        for (Member member : outpost.members.values()) {
            Entity entity = loaded(player, member.id);
            BlockPos position = entity instanceof ChangedEntity mob ? mob.blockPosition() : member.lastPosition;
            Role oldRole = member.role;
            if (data.role(outpost, member.id, role, position)) {
                if (entity instanceof ChangedEntity mob) {
                    if (oldRole == Role.SUPPLY && role != Role.SUPPLY) PlayerOutpostService.releaseCargo(mob);
                    PlayerOutpostService.applyRole(mob, player, role);
                }
                changed++;
            }
        }
        success(source, "team", changed);
        return changed;
    }

    private static int help(CommandSourceStack source) {
        success(source, "help");
        return 1;
    }

    private static int role(CommandSourceStack source, UUID id, Role role) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        Outpost outpost = owned(source, player);
        if (outpost == null) return 0;
        Member member = outpost.members.get(id);
        if (member == null) return error(source, "member");
        Entity entity = loaded(player, id);
        if (role == Role.HOLD && !(entity instanceof ChangedEntity)) return error(source, "hold_loaded");
        BlockPos hold = entity == null ? member.lastPosition : entity.blockPosition();
        Role oldRole = member.role;
        if (!PlayerOutpostData.get(player.server).role(outpost, id, role, hold)) return error(source, "crew_full");
        if (entity instanceof ChangedEntity mob) {
            if (oldRole == Role.SUPPLY && role != Role.SUPPLY) PlayerOutpostService.releaseCargo(mob);
            PlayerOutpostService.applyRole(mob, player, role);
        }
        success(source, "role", member.name, Component.translatable("command.changed_synergy.outpost.role." + role.name().toLowerCase(java.util.Locale.ROOT)));
        return 1;
    }

    private static int remove(CommandSourceStack source, UUID id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        Outpost outpost = owned(source, player);
        if (outpost == null) return 0;
        Member member = outpost.members.get(id);
        if (member == null) return error(source, "member");
        Entity entity = loaded(player, id);
        if (entity instanceof ChangedEntity mob) {
            PlayerOutpostService.clearAssignment(mob);
        }
        PlayerOutpostData.get(player.server).remove(outpost, id);
        success(source, "removed", member.name);
        return 1;
    }

    private static int disband(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        Outpost outpost = owned(source, player);
        if (outpost == null) return 0;
        for (Member member : outpost.members.values()) {
            Entity entity = loaded(player, member.id);
            if (entity instanceof ChangedEntity mob) {
                PlayerOutpostService.clearAssignment(mob);
            }
        }
        PlayerOutpostData.get(player.server).disband(player.getUUID());
        success(source, "disbanded");
        return 1;
    }

    private static int status(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        Outpost outpost = owned(source, player);
        if (outpost == null) return 0;
        ResourceLocation key = ResourceLocation.tryParse(outpost.dimension);
        ServerLevel home = key == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, key));
        success(source, home != null && outpost.active(home) ? "status" : "inactive_status",
                outpost.marker.getX(), outpost.marker.getY(), outpost.marker.getZ(), outpost.members.size(),
                ChangedSynergyConfig.COMMON.playerOutpostResidents.get());
        String storage = outpost.storage == null ? "—" : outpost.storage.toShortString();
        Container container = PlayerOutpostData.container(home, outpost.storage);
        if (container != null) {
            int items = 0;
            for (int slot = 0; slot < container.getContainerSize(); slot++) items += container.getItem(slot).getCount();
            storage += " (" + items + ")";
        }
        success(source, "facilities", storage,
                outpost.bed == null ? "—" : outpost.bed.toShortString());
        for (Member member : outpost.members.values()) {
            boolean loaded = loaded(player, member.id) instanceof ChangedEntity;
            Component line = Component.literal(member.name + " · " + member.dimension + " "
                            + member.lastPosition.toShortString() + " · ")
                    .append(Component.translatable("command.changed_synergy.outpost.role." + member.role.name().toLowerCase(java.util.Locale.ROOT)))
                    .append(Component.literal(loaded ? " ●" : " ○"))
                    .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                    "/changedsynergy outpost role " + member.id + " ")));
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int open(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        if (owned(source, player) == null) return 0;
        PlayerOutpostMenu.open(player, 0);
        return 1;
    }

    private static Entity loaded(ServerPlayer player, UUID id) {
        for (ServerLevel level : player.server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    private static int error(CommandSourceStack source, String key) {
        source.sendFailure(Component.translatable("command.changed_synergy.outpost.error." + key));
        return 0;
    }

    private static void success(CommandSourceStack source, String key, Object... args) {
        source.sendSuccess(() -> Component.translatable("command.changed_synergy.outpost." + key, args), false);
    }
}
