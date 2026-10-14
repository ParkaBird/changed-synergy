package net.parkabird.changedsynergy.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
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
import net.minecraft.world.entity.projectile.ProjectileUtil;
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
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Work;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.CrewJob;
import net.parkabird.changedsynergy.ai.PlayerOutpostService;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostMenu;

/** In-game management for a player-built campfire, real chest, and resident crew. */
public final class PlayerOutpostCommand {
    private PlayerOutpostCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> create() {
        var root = Commands.literal("outpost")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> open(context.getSource()))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("create").executes(context -> claim(context.getSource())))
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
        var facilities = Commands.literal("link").executes(context -> linkFacility(context.getSource(), null));
        for (String facility : new String[]{"storage", "bed", "rest", "campfire", "composter"}) {
            facilities.then(Commands.literal(facility).executes(context -> linkFacility(context.getSource(), facility)));
        }
        root.then(facilities);
        for (String area : new String[]{"farm", "hunt", "gather", "haul"}) {
            root.then(Commands.literal(area)
                    .then(Commands.literal("corner1").executes(context -> area(context.getSource(), area, 1)))
                    .then(Commands.literal("corner2").executes(context -> area(context.getSource(), area, 2)))
                    .then(Commands.literal("clear").executes(context -> area(context.getSource(), area, 0))));
        }
        var jobs = Commands.literal("work");
        var selected = Commands.argument("member", UuidArgument.uuid()).suggests((context, builder) -> {
            ServerPlayer owner = context.getSource().getPlayerOrException();
            return SharedSuggestionProvider.suggest(PlayerOutpostData.get(owner.server).byOwner(owner.getUUID())
                    .stream().flatMap(home -> home.members.keySet().stream()).map(UUID::toString), builder);
        });
        for (Work task : Work.values()) {
            if (task == Work.COMPOST) continue; // Retained only for saved-data migration.
            String name = task.name().toLowerCase(java.util.Locale.ROOT);
            jobs.then(Commands.literal(name).executes(context -> work(context.getSource(), null, task, false)));
            selected.then(Commands.literal(name).executes(context -> work(context.getSource(),
                    UuidArgument.getUuid(context, "member"), task, false)));
        }
        var backupMember = Commands.argument("member", UuidArgument.uuid());
        backupMember.then(Commands.literal("none").executes(context -> work(context.getSource(),
                UuidArgument.getUuid(context, "member"), null, true)));
        for (Work task : Work.values()) {
            if (task == Work.COMPOST) continue;
            backupMember.then(Commands.literal(task.name().toLowerCase(java.util.Locale.ROOT))
                    .executes(context -> work(context.getSource(), UuidArgument.getUuid(context, "member"), task, true)));
        }
        root.then(jobs.then(selected)).then(Commands.literal("backup").then(backupMember));
        var crewJobs = Commands.literal("crew");
        var crewMember = Commands.argument("member", UuidArgument.uuid()).suggests((context, builder) -> {
            ServerPlayer owner = context.getSource().getPlayerOrException();
            return SharedSuggestionProvider.suggest(PlayerOutpostData.get(owner.server).byOwner(owner.getUUID())
                    .stream().flatMap(home -> home.members.keySet().stream()).map(UUID::toString), builder);
        });
        for (CrewJob job : CrewJob.values()) {
            String name = job.name().toLowerCase(java.util.Locale.ROOT);
            crewJobs.then(Commands.literal(name).executes(context -> crew(context.getSource(), null, job)));
            crewMember.then(Commands.literal(name).executes(context -> crew(context.getSource(), UuidArgument.getUuid(context, "member"), job)));
        }
        root.then(crewJobs.then(crewMember));
        var policies = Commands.literal("policy");
        for (String key : new String[]{"meals"}) policies.then(Commands.literal(key)
                .then(Commands.argument("enabled", BoolArgumentType.bool()).executes(context -> policy(context.getSource(),
                        key, BoolArgumentType.getBool(context, "enabled") ? 1 : 0))));
        for (String key : new String[]{"raw_reserve", "seed_reserve"}) policies.then(Commands.literal(key)
                .then(Commands.argument("count", IntegerArgumentType.integer(key.equals("seed_reserve") ? 1 : 0, 64))
                        .executes(context -> policy(context.getSource(), key, IntegerArgumentType.getInteger(context, "count")))));
        root.then(policies).then(Commands.literal("meal").executes(context -> meal(context.getSource())));
        return root;
    }

    private static int linkFacility(CommandSourceStack source, String facility)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        Outpost outpost = owned(source, player);
        if (outpost == null) return 0;
        ServerLevel level = player.serverLevel();
        if (!outpost.active(level)) return error(source, "inactive");
        BlockPos pos = lookedAt(player);
        if (pos == null || !outpost.contains(pos)) return error(source, "range");
        var block = level.getBlockState(pos).getBlock();
        if (facility == null) {
            facility = facilityAt(level, pos);
            if (facility == null) return error(source, "facility");
        }
        PlayerOutpostData data = PlayerOutpostData.get(player.server);
        switch (facility) {
            case "storage" -> {
                if (PlayerOutpostData.container(level, pos) == null
                        || CreatureCommunityData.snapshotAtCache(level, pos).isPresent()) return error(source, "container");
                data.setStorage(outpost, pos);
            }
            case "bed", "rest" -> {
                if (facility.equals("bed") ? !(block instanceof BedBlock)
                        : !net.parkabird.changedsynergy.ai.PlayerOutpostRestGoal.isRestPoint(level.getBlockState(pos)))
                    return error(source, facility);
                pos = net.parkabird.changedsynergy.ai.PlayerOutpostRestGoal.restingPlace(level, pos);
                if (pos == null || !outpost.contains(pos)) return error(source, facility);
                data.refreshRestPlaces(level, outpost);
                if (facility.equals("bed")) data.setBed(outpost, pos);
                else data.addRestPoint(outpost, pos);
            }
            case "campfire" -> {
                if (!(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.CampfireBlockEntity)) return error(source, "cooking");
                data.setFacility(outpost, facility, pos);
            }
            case "composter" -> {
                if (!(block instanceof net.minecraft.world.level.block.ComposterBlock)) return error(source, "composter");
                data.setFacility(outpost, facility, pos);
            }
            default -> { return error(source, "facility"); }
        }
        success(source, "linked", Component.translatable("command.changed_synergy.outpost.facility." + facility), pos.toShortString());
        return 1;
    }

    private static int area(CommandSourceStack source, String area, int corner)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        Outpost outpost = owned(source, player);
        if (outpost == null) return 0;
        if (!outpost.active(player.serverLevel())) return error(source, "inactive");
        PlayerOutpostData data = PlayerOutpostData.get(player.server);
        if (corner == 0) {
            data.setFacility(outpost, area + "_start", null);
            data.setFacility(outpost, area + "_end", null);
            success(source, "area_cleared", Component.translatable("command.changed_synergy.outpost.area." + area));
            return 1;
        }
        BlockPos pos = lookedAt(player);
        if (pos == null || !outpost.workContains(pos)) return error(source, "area_range");
        BlockPos other = switch (area) {
            case "farm" -> corner == 1 ? outpost.farmEnd : outpost.farmStart;
            case "hunt" -> corner == 1 ? outpost.huntEnd : outpost.huntStart;
            case "gather" -> corner == 1 ? outpost.gatherEnd : outpost.gatherStart;
            case "haul" -> corner == 1 ? outpost.haulEnd : outpost.haulStart;
            default -> throw new IllegalArgumentException("Unknown camp area: " + area);
        };
        if (other != null && (Math.abs(other.getX() - pos.getX()) > 31
                || Math.abs(other.getZ() - pos.getZ()) > 31 || Math.abs(other.getY() - pos.getY()) > 4)) return error(source, "area_size");
        data.setFacility(outpost, area + (corner == 1 ? "_start" : "_end"), pos);
        success(source, "area_corner", Component.translatable("command.changed_synergy.outpost.area." + area), corner, pos.toShortString());
        return 1;
    }

    private static int work(CommandSourceStack source, UUID id, Work work, boolean backup)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        Outpost outpost = owned(source, player);
        if (outpost == null) return 0;
        if (id == null) {
            ChangedEntity mob = lookedAtCreature(player);
            if (mob == null) return error(source, "member_look");
            id = mob.getUUID();
        }
        Member member = outpost.members.get(id);
        if (member == null) return error(source, "member");
        if (backup && member.role != Role.SUPPLY) return error(source, "backup_supply");
        PlayerOutpostData.get(player.server).setWork(outpost, id,
                backup ? member.work : work, backup ? work : member.backupWork);
        Entity entity = loaded(player, id);
        if (entity instanceof ChangedEntity mob) PlayerOutpostService.applyRole(mob, player, Role.SUPPLY);
        Work effective = backup ? member.backupWork : member.work;
        success(source, backup ? "backup_set" : "work_set", member.name,
                Component.translatable("menu.changed_synergy.player_outpost.work."
                        + (effective == null ? "no_backup" : effective.name().toLowerCase(java.util.Locale.ROOT))));
        return 1;
    }

    private static int crew(CommandSourceStack source, UUID id, CrewJob job)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        Outpost outpost = owned(source, player);
        if (outpost == null) return 0;
        if (id == null) {
            ChangedEntity target = lookedAtCreature(player);
            if (target == null) return error(source, "member_look");
            id = target.getUUID();
        }
        Member member = outpost.members.get(id);
        if (member == null) return error(source, "member");
        if (!PlayerOutpostData.get(player.server).crewJob(outpost, id, job)) return error(source, "crew_full");
        Entity entity = loaded(player, id);
        if (entity instanceof ChangedEntity mob) PlayerOutpostService.applyRole(mob, player, Role.CREW);
        success(source, "crew_set", member.name, Component.translatable("menu.changed_synergy.player_outpost.crew."
                + job.name().toLowerCase(java.util.Locale.ROOT)));
        return 1;
    }

    private static int policy(CommandSourceStack source, String key, int value)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        Outpost outpost = owned(source, player);
        if (outpost == null) return 0;
        switch (key) {
            case "meals" -> outpost.meals = value != 0;
            case "raw_reserve" -> outpost.rawReserve = value;
            case "seed_reserve" -> outpost.seedReserve = value;
            default -> throw new IllegalArgumentException("Unknown camp policy: " + key);
        }
        PlayerOutpostData.get(player.server).setDirty();
        Component display = key.equals("meals")
                ? Component.translatable(value == 0 ? "options.off" : "options.on") : Component.literal(Integer.toString(value));
        success(source, "policy_set", Component.translatable("command.changed_synergy.outpost.policy." + key), display);
        return 1;
    }

    private static int meal(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        Outpost outpost = owned(source, player);
        if (outpost == null) return 0;
        if (!ChangedSynergyConfig.COMMON.playerOutpostMeals.get() || !outpost.meals) return error(source, "meals_disabled");
        if (!outpost.active(player.serverLevel())) return error(source, "inactive");
        if (outpost.cooking == null || !player.serverLevel().hasChunkAt(outpost.cooking)
                || !(player.serverLevel().getBlockEntity(outpost.cooking)
                    instanceof net.minecraft.world.level.block.entity.CampfireBlockEntity)) return error(source, "cooking");
        var fireState = player.serverLevel().getBlockState(outpost.cooking);
        if (!fireState.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)
                || fireState.getValue(net.minecraft.world.level.block.CampfireBlock.WATERLOGGED)) return error(source, "fire_unlit");
        if (outpost.mealRequestedAt >= 0 && player.serverLevel().getGameTime() - outpost.mealRequestedAt < 600) return error(source, "meal_pending");
        net.parkabird.changedsynergy.ai.PlayerOutpostMealGoal.request(player, outpost);
        success(source, "meal_called");
        return 1;
    }

    private static ServerPlayer player(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return source.getPlayerOrException();
    }

    private static Outpost owned(CommandSourceStack source, ServerPlayer player) {
        if (!net.parkabird.changedsynergy.ai.PlayerOutpostService.enabled(player.level())) {
            error(source, "disabled");
            return null;
        }
        Outpost outpost = PlayerOutpostData.get(player.server).byOwner(player.getUUID()).orElse(null);
        if (outpost == null) error(source, "missing");
        return outpost;
    }

    public static BlockPos lookedAt(ServerPlayer player) {
        HitResult hit = player.pick(6.0D, 1.0F, false);
        return hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult block ? block.getBlockPos() : null;
    }

    private static String facilityAt(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof BedBlock) return "bed";
        if (net.parkabird.changedsynergy.ai.PlayerOutpostRestGoal.isRestPoint(state)) return "rest";
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.CampfireBlockEntity) return "campfire";
        if (state.getBlock() instanceof net.minecraft.world.level.block.ComposterBlock) return "composter";
        return PlayerOutpostData.container(level, pos) != null ? "storage" : null;
    }

    /** The wheel is a shortcut to the same server-validated commands, not another claim system. */
    public static String wheelAction(ServerPlayer player) {
        if (!net.parkabird.changedsynergy.ai.PlayerOutpostService.enabled(player.level())) return "outpost";
        ServerLevel level = player.serverLevel();
        PlayerOutpostData data = PlayerOutpostData.get(player.server);
        Outpost home = data.byOwner(player.getUUID()).orElse(null);
        if (wheelInvitee(player) != null) return "invite_outpost";
        BlockPos pos = lookedAt(player);
        if (pos == null || !level.hasChunkAt(pos)) return "outpost";
        if (home == null) {
            boolean used = data.all().stream().anyMatch(camp -> camp.dimension.equals(level.dimension().location().toString())
                    && (pos.equals(camp.marker) || pos.equals(camp.cooking)));
            return !used && level.getBlockState(pos).is(net.minecraft.tags.BlockTags.CAMPFIRES)
                    ? "create_outpost" : "outpost";
        }
        if (!home.active(level) || !home.contains(pos)) return "outpost";
        String facility = facilityAt(level, pos);
        if (facility == null || pos.equals(home.marker)) return "outpost";
        if (facility.equals("storage") && CreatureCommunityData.snapshotAtCache(level, pos).isPresent()) return "outpost";
        BlockPos normalized = facility.equals("bed") || facility.equals("rest")
                ? net.parkabird.changedsynergy.ai.PlayerOutpostRestGoal.restingPlace(level, pos) : pos;
        boolean linked = normalized == null || home.beds.contains(normalized) || home.restPoints.contains(normalized)
                || pos.equals(home.storage) || pos.equals(home.cooking) || pos.equals(home.composter);
        return linked ? "outpost" : "link_outpost";
    }

    public static ChangedEntity wheelInvitee(ServerPlayer player) {
        if (!PlayerOutpostService.enabled(player.level())) return null;
        PlayerOutpostData data = PlayerOutpostData.get(player.server);
        Outpost home = data.byOwner(player.getUUID()).orElse(null);
        if (home == null || !home.active(player.serverLevel()) || !home.contains(player.blockPosition())
                || home.members.size() >= ChangedSynergyConfig.COMMON.playerOutpostResidents.get()) return null;
        ChangedEntity mob = lookedAtCreature(player);
        return mob != null && home.contains(mob.blockPosition()) && data.byMember(mob.getUUID()).isEmpty()
                && PlayerOutpostService.canInvite(mob, player) ? mob : null;
    }

    /** Recheck the server snapshot so a moved crosshair cannot invite a different creature. */
    public static boolean wheelInvite(ServerPlayer player, UUID expected) {
        ChangedEntity mob = wheelInvitee(player);
        if (mob == null || !mob.getUUID().equals(expected)) {
            player.displayClientMessage(Component.translatable("command.changed_synergy.outpost.error.target_changed"), true);
            return false;
        }
        try { return invite(player.createCommandSourceStack(), mob) > 0; }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) { return false; }
    }

    public static boolean wheelAction(ServerPlayer player, String expected, BlockPos target) {
        if (!expected.equals(wheelAction(player)) || !java.util.Objects.equals(target, lookedAt(player))) {
            player.displayClientMessage(Component.translatable("command.changed_synergy.outpost.error.target_changed"), true);
            return false;
        }
        String command = switch (expected) {
            case "create_outpost" -> "changedsynergy outpost create";
            case "link_outpost" -> "changedsynergy outpost link";
            default -> "changedsynergy outpost";
        };
        return player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command) > 0;
    }

    private static ChangedEntity lookedAtCreature(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = player.pick(6.0D, 1.0F, false).getLocation();
        var hit = ProjectileUtil.getEntityHitResult(player, eye, end,
                player.getBoundingBox().expandTowards(player.getViewVector(1.0F).scale(6.0D)).inflate(1.0D),
                entity -> entity.isPickable() && !entity.isSpectator() && entity.isAlive(), eye.distanceToSqr(end));
        return hit != null && hit.getEntity() instanceof ChangedEntity mob ? mob : null;
    }

    private static int claim(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        BlockPos pos = lookedAt(player);
        if (pos == null) return error(source, "look");
        String result = PlayerOutpostData.get(player.server).claim(player.serverLevel(), player.getUUID(), pos);
        if (!"claimed".equals(result)) return error(source, result);
        net.parkabird.changedsynergy.advancement.SynergyAdvancements.grant(player,
                net.parkabird.changedsynergy.advancement.SynergyAdvancements.PLAYER_CAMP);
        success(source, "claimed", pos.getX(), pos.getY(), pos.getZ());
        return 1;
    }

    private static int invite(CommandSourceStack source, Entity entity) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = player(source);
        Outpost outpost = owned(source, player);
        if (outpost == null) return 0;
        if (!outpost.active(player.serverLevel())) return error(source, "inactive");
        if (!(entity instanceof ChangedEntity mob) || entity.level() != player.level()
                || !outpost.contains(mob.blockPosition()) || !outpost.contains(player.blockPosition())
                || !PlayerOutpostService.canInvite(mob, player)) return error(source, "ineligible");
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
        source.sendSuccess(() -> Component.translatable("command.changed_synergy.outpost.help.header")
                .withStyle(ChatFormatting.GOLD), false);
        String[][] lines = {
                {"open", ""}, {"create", "create"}, {"link", "link"}, {"storage", "link storage"}, {"bed", "link bed"}, {"rest", "link rest"},
                {"campfire", "link campfire"}, {"composter", "link composter"},
                {"invite", "invite"}, {"status", "status"}, {"role", "role "},
                {"farm", "farm corner1"}, {"hunt", "hunt corner1"}, {"gather", "gather corner1"}, {"haul", "haul corner1"},
                {"work", "work farm"}, {"backup", "backup "}, {"crew", "crew mine"}, {"policy", "policy "},
                {"meal", "meal"}, {"team", "team "}, {"remove", "remove "}, {"disband", "disband"}
        };
        for (String[] line : lines) {
            Component text = Component.translatable("command.changed_synergy.outpost.help." + line[0])
                    .withStyle(style -> style.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND, "/changedsynergy outpost " + line[1])));
            source.sendSuccess(() -> text, false);
        }
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
        PlayerOutpostData.get(player.server).refreshRestPlaces(home, outpost);
        success(source, "facilities", storage, outpost.beds.size(), outpost.restPoints.size());
        success(source, "production_facilities", outpost.cooking == null ? "—" : outpost.cooking.toShortString(),
                outpost.composter == null ? "—" : outpost.composter.toShortString());
        success(source, "areas", outpost.farmStart == null || outpost.farmEnd == null ? "—"
                : outpost.farmStart.toShortString() + " → " + outpost.farmEnd.toShortString(),
                outpost.huntStart == null || outpost.huntEnd == null ? "—"
                        : outpost.huntStart.toShortString() + " → " + outpost.huntEnd.toShortString());
        success(source, "gather_area", !outpost.hasGatherArea() ? "—"
                : outpost.gatherStart.toShortString() + " → " + outpost.gatherEnd.toShortString());
        success(source, "haul_area", !outpost.hasHaulArea() ? "— (32)"
                : outpost.haulStart.toShortString() + " → " + outpost.haulEnd.toShortString());
        for (Member member : outpost.members.values()) {
            boolean loaded = loaded(player, member.id) instanceof ChangedEntity;
            Component line = Component.literal(member.name + " · " + member.dimension + " "
                            + member.lastPosition.toShortString() + " · ")
                    .append(Component.translatable("command.changed_synergy.outpost.role." + member.role.name().toLowerCase(java.util.Locale.ROOT)))
                    .append(member.role == Role.SUPPLY ? Component.literal(" · ").append(Component.translatable(
                            "menu.changed_synergy.player_outpost.work." + member.work.name().toLowerCase(java.util.Locale.ROOT)))
                            .append(Component.literal(" · ")).append(Component.translatable(
                                    "menu.changed_synergy.player_outpost.status." + member.workStatus)) : Component.empty())
                    .append(member.role == Role.CREW ? Component.literal(" · ").append(Component.translatable(
                            "menu.changed_synergy.player_outpost.crew." + member.crewJob.name().toLowerCase(java.util.Locale.ROOT)))
                            : Component.empty())
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
