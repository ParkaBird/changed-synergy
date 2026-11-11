package net.parkabird.changedsynergy.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.compat.CompanionSeats;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.CompanionSeatStatePacket;

/** Commands own only navigation and the seat they successfully acquired. */
@Mod.EventBusSubscriber(modid = "changed_synergy")
public final class CompanionSeatingService {
    private static final String SEATED = "ChangedSynergyCommandSeat";
    private static final Map<ChangedEntity, Order> ORDERS = new WeakHashMap<>();
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    private static int sequence;
    private record Selection(int token, UUID creature, ServerLevel level, long expires, long nextInput) {}
    private static final class Group {
        final UUID owner;
        int remaining, boarded, failed;
        Group(UUID owner, int count) { this.owner = owner; remaining = count; }
    }
    private static final class Order {
        final UUID owner, vehicle;
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        final boolean wasFollowing;
        final BlockPos block;
        final int seat, sequence;
        final long expires;
        final Group group;
        Vec3 progress;
        long lastProgress;
        Order(ServerPlayer owner, ChangedEntity mob, @Nullable Entity vehicle, @Nullable BlockPos block, int seat, @Nullable Group group) {
            this.owner = owner.getUUID(); this.vehicle = vehicle == null ? null : vehicle.getUUID();
            this.block = block; this.seat = seat; this.group = group; sequence = ++CompanionSeatingService.sequence;
            dimension = mob.level().dimension(); wasFollowing = following(mob, owner);
            expires = mob.level().getGameTime() + 400; progress = mob.position(); lastProgress = mob.level().getGameTime();
        }
    }
    private CompanionSeatingService() {}

    public static boolean canCommand(ChangedEntity mob, ServerPlayer owner) {
        Order active = ORDERS.get(mob);
        if (active != null && !active.owner.equals(owner.getUUID())) return false;
        var savedSeat = mob.getPersistentData().getCompound(SEATED);
        if (mob.isPassenger() && savedSeat.hasUUID("Owner") && !savedSeat.getUUID("Owner").equals(owner.getUUID())) return false;
        if (PlayerOutpostService.hasAssignment(mob) && PlayerOutpostData.get(owner.server).byMember(mob.getUUID())
                .map(home -> !home.owner.equals(owner.getUUID())).orElse(true)) return false;
        if (!mob.isAlive() || mob.isNoAi()
                || mob.getUnderlyingPlayer() != null || TrueTransfurCompat.isInstinctBody(mob)
                || !CreatureSocialProfile.allowsPersonalRelationship(mob)
                || mob.level() != owner.level() || !owner.isAlive() || owner.isSpectator()
                || LatexSocialMemory.isProvoked(mob, owner)
                || LatexSocialMemory.petOwnerUuid(mob).filter(id -> !id.equals(owner.getUUID())).isPresent()
                || PlayerOutpostService.assignedOwner(mob).filter(id -> !id.equals(owner.getUUID())).isPresent()) return false;
        boolean bonded = LatexSocialMemory.isPetOwner(mob, owner);
        return ChangedSynergyGameRules.enabled(mob.level(), bonded ? ChangedSynergyGameRules.BOND_SYSTEM : ChangedSynergyGameRules.FRIENDSHIP_SYSTEM)
                && PlayerOutpostService.authorized(mob, owner);
    }

    private static boolean free(ChangedEntity mob) {
        return mob.getTarget() == null && !mob.isLeashed() && !mob.isVehicle() && !mob.isSleeping()
                && !mob.isUsingItem() && !CreatureGrabService.isGrabberBusy(mob)
                && GrabEntityAbility.getGrabberSafe(mob).isEmpty() && !TakeoverService.carrying(mob)
                && HypnosisQteService.getActiveVictim(mob) == null && !CreaturePeerInteractionService.active(mob)
                && !PlayerOutpostService.isWorking(mob) && !ProvisionerTradeService.isTrading(mob)
                && mob.getHealth() > mob.getMaxHealth() * 0.25F;
    }

    private static boolean following(ChangedEntity mob, ServerPlayer owner) {
        return LatexSocialMemory.isPetOwner(mob, owner) ? LatexSocialMemory.isFollowingOwner(mob)
                : CreaturePersonality.isSocialFollowing(mob, owner);
    }

    public static String action(ChangedEntity mob, ServerPlayer owner) {
        if (!canCommand(mob, owner)) return "";
        if (ORDERS.containsKey(mob)) return "social_cancel_seat";
        if (canLeaveSeat(mob, owner)) return "social_disembark";
        return ChangedSynergyConfig.COMMON.companionSeating.get() && !mob.isPassenger() && free(mob) ? "social_seat" : "";
    }

    public static void command(ServerPlayer owner, ChangedEntity mob, String command) {
        if (!canCommand(mob, owner) || mob.distanceToSqr(TrueTransfurCompat.interactionBody(owner)) > 16 * 16) return;
        if (command.equals("social_cancel_seat")) { finish(mob, false, "cancelled"); return; }
        if (command.equals("social_disembark")) { disembark(mob, owner); return; }
        if (!command.equals("social_seat") || !action(mob, owner).equals(command)) return;
        clearSelection(owner);
        int token = ++sequence;
        SELECTIONS.put(owner.getUUID(), new Selection(token, mob.getUUID(), owner.serverLevel(), owner.server.getTickCount() + 300L, 0));
        SocialAudienceGoal.end(mob, owner);
        owner.closeContainer();
        send(owner, new CompanionSeatStatePacket(token, mob.getId(), mob.getDisplayName()));
    }

    public static boolean rallyAvailable(ServerPlayer owner) {
        Entity vehicle = TrueTransfurCompat.interactionBody(owner).getVehicle();
        return ChangedSynergyConfig.COMMON.companionSeating.get() && ChangedSynergyConfig.COMMON.companionRallyBoarding.get()
                && vehicle != null && CompanionSeats.supported(vehicle, true) && CompanionSeats.usable(vehicle)
                && CompanionSeats.capacity(vehicle) != 1;
    }

    public static void rally(ServerPlayer owner) {
        if (!rallyAvailable(owner)) return;
        Entity vehicle = TrueTransfurCompat.interactionBody(owner).getVehicle();
        List<ChangedEntity> candidates = owner.serverLevel().getEntitiesOfClass(ChangedEntity.class,
                        TrueTransfurCompat.interactionBody(owner).getBoundingBox().inflate(16),
                        mob -> canCommand(mob, owner) && following(mob, owner) && free(mob) && !mob.isPassenger())
                .stream().sorted(Comparator.comparingDouble((ChangedEntity mob) -> distanceToHull(mob, vehicle))
                        .thenComparing(Entity::getUUID)).limit(32).toList();
        if (candidates.isEmpty()) { message(owner, "no_followers"); return; }
        var group = new Group(owner.getUUID(), candidates.size());
        for (var mob : candidates) {
            finish(mob, false, null);
            if (!reserve(owner, mob, vehicle, null, group)) complete(owner, group, false);
        }
        owner.closeContainer();
    }

    public static void select(ServerPlayer owner, int token, int entityId, @Nullable BlockPos block, boolean cancel) {
        Selection selection = SELECTIONS.get(owner.getUUID());
        if (selection == null || selection.token != token) return;
        if (cancel || !validSelection(owner, selection)) { clearSelection(owner); return; }
        if (owner.server.getTickCount() < selection.nextInput) return;
        SELECTIONS.put(owner.getUUID(), new Selection(selection.token, selection.creature, selection.level,
                selection.expires, owner.server.getTickCount() + 4L));
        var mob = selection.level.getEntity(selection.creature);
        if (!(mob instanceof ChangedEntity creature) || !canCommand(creature, owner) || !free(creature) || creature.isPassenger()) {
            clearSelection(owner); return;
        }
        Entity target = entityId < 0 ? null : selection.level.getEntity(entityId);
        BlockPos seatBlock = block == null ? null : CompanionSeats.blockSeat(selection.level, block);
        if (entityId >= 0) seatBlock = null;
        if (entityId >= 0 && target == null) { message(owner, "invalid"); return; }
        var body = TrueTransfurCompat.interactionBody(owner);
        // The visible body's head may be animated independently of the player's camera.
        Vec3 eye = body.getEyePosition(), end = eye.add(owner.getLookAngle().scale(8));
        HitResult wall = selection.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, body));
        boolean visible = false;
        if (target != null && CompanionSeats.supported(target, false)) {
            var intersection = target.getBoundingBox().inflate(0.25).clip(eye, end);
            visible = intersection.isPresent() && (wall.getType() == HitResult.Type.MISS
                    || eye.distanceToSqr(intersection.get()) <= eye.distanceToSqr(wall.getLocation()) + 0.1);
        } else if (target == null && seatBlock != null && wall instanceof net.minecraft.world.phys.BlockHitResult hit) {
            visible = wall.getType() == HitResult.Type.BLOCK && seatBlock.equals(CompanionSeats.blockSeat(selection.level, hit.getBlockPos()));
        }
        if (!visible || target != null && distanceToHull(creature, target) > 24 * 24
                || seatBlock != null && creature.distanceToSqr(Vec3.atCenterOf(seatBlock)) > 24 * 24) {
            message(owner, "invalid"); return;
        }
        if (reserve(owner, creature, target, seatBlock, null)) clearSelection(owner);
        else message(owner, "unavailable");
    }

    private static boolean validSelection(ServerPlayer owner, Selection selection) {
        return owner.isAlive() && !owner.isSpectator() && owner.level() == selection.level
                && owner.server.getTickCount() <= selection.expires && ChangedSynergyConfig.COMMON.companionSeating.get()
                && selection.level.getEntity(selection.creature) instanceof ChangedEntity mob
                && canCommand(mob, owner) && free(mob) && !mob.isPassenger() && !pending(mob);
    }

    private static boolean reserve(ServerPlayer owner, ChangedEntity mob, @Nullable Entity vehicle, @Nullable BlockPos block, @Nullable Group group) {
        if (vehicle == null && (block == null || !CompanionSeats.blockAvailable(mob.level(), block))) return false;
        int seat = 0;
        if (vehicle != null) {
            var reserved = new java.util.HashSet<Integer>();
            for (var order : ORDERS.values()) if (vehicle.getUUID().equals(order.vehicle)) reserved.add(order.seat);
            seat = CompanionSeats.firstFree(vehicle, reserved);
            if (seat < CompanionSeats.UNKNOWN) return false;
        } else if (ORDERS.values().stream().anyMatch(order -> order.dimension.equals(mob.level().dimension())
                && order.vehicle == null && block.equals(order.block))) return false;
        CreatureComfortGoal.endSeatedActivity(mob);
        SocialAudienceGoal.end(mob, owner);
        CreatureTaskNavigation.clearFailure(mob);
        ORDERS.put(mob, new Order(owner, mob, vehicle, block, seat, group));
        ensureGoal(mob);
        if (group == null) message(owner, "approaching", mob.getDisplayName());
        return true;
    }

    public static boolean pending(ChangedEntity mob) { return ORDERS.containsKey(mob); }

    public static boolean commandedSeat(ChangedEntity mob) { return mob.isPassenger() && mob.getPersistentData().contains(SEATED); }

    public static boolean canLeaveSeat(ChangedEntity mob, ServerPlayer owner) {
        return mob.isPassenger() && canCommand(mob, owner) && CompanionSeats.supported(mob.getVehicle(), true);
    }

    public static void newCommand(ChangedEntity mob, ServerPlayer owner) {
        Order order = ORDERS.get(mob);
        if (order != null && order.owner.equals(owner.getUUID())) finish(mob, false, "cancelled");
        if (canLeaveSeat(mob, owner)) disembark(mob, owner);
    }

    static void walk(ChangedEntity mob) {
        Order order = ORDERS.get(mob);
        if (order == null || !(mob.level() instanceof ServerLevel level)) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(order.owner);
        Entity vehicle = order.vehicle == null ? null : level.getEntity(order.vehicle);
        if (owner == null || !ChangedSynergyConfig.COMMON.companionSeating.get() || !canCommand(mob, owner) || !free(mob) || mob.isPassenger()
                || following(mob, owner) != order.wasFollowing
                || level.getGameTime() > order.expires || order.group != null && (!following(mob, owner)
                    || !ChangedSynergyConfig.COMMON.companionRallyBoarding.get()
                    || TrueTransfurCompat.interactionBody(owner).getVehicle() != vehicle)
                || order.vehicle != null && (vehicle == null || !vehicle.isAlive() || !CompanionSeats.usable(vehicle))
                || order.vehicle == null && !CompanionSeats.blockAvailable(level, order.block)) {
            finish(mob, false, "interrupted"); return;
        }
        if (vehicle != null && (vehicle.getDeltaMovement().horizontalDistanceSqr() > 0.04
                || Math.abs(vehicle.getDeltaMovement().y) > 0.15)) { finish(mob, false, "moving"); return; }
        if (vehicle != null && distanceToHull(mob, vehicle) > 24 * 24) { finish(mob, false, "blocked"); return; }
        if (order.seat == CompanionSeats.UNKNOWN && ORDERS.values().stream().anyMatch(other -> other != order
                && order.vehicle.equals(other.vehicle) && other.sequence < order.sequence)) {
            order.lastProgress = level.getGameTime(); return;
        }
        long now = level.getGameTime();
        if (mob.position().distanceToSqr(order.progress) > 0.5) { order.progress = mob.position(); order.lastProgress = now; }
        if (now - order.lastProgress > 160) { finish(mob, false, "blocked"); return; }
        double distance = vehicle == null ? mob.distanceToSqr(Vec3.atBottomCenterOf(order.block)) : distanceToHull(mob, vehicle);
        if (distance <= 2.5 * 2.5 && (vehicle == null || vehicle.getDeltaMovement().horizontalDistanceSqr() <= 0.04 && Math.abs(vehicle.getDeltaMovement().y) < 0.15)) {
            Vec3 point = vehicle == null ? Vec3.atCenterOf(order.block) : closest(mob.getEyePosition(), vehicle.getBoundingBox());
            var wall = level.clip(new ClipContext(mob.getEyePosition(), point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
            if (wall.getType() == HitResult.Type.MISS || vehicle == null && ((net.minecraft.world.phys.BlockHitResult)wall).getBlockPos().equals(order.block)) {
                boolean success = vehicle == null ? CompanionSeats.boardBlock(mob, order.block) : CompanionSeats.board(mob, vehicle, order.seat);
                if (success) {
                    var saved = new CompoundTag(); saved.putUUID("Owner", order.owner);
                    saved.putUUID("Vehicle", mob.getVehicle().getUUID()); saved.putBoolean("Rally", order.group != null);
                    saved.putBoolean("Following", order.wasFollowing);
                    mob.getPersistentData().put(SEATED, saved);
                    net.parkabird.changedsynergy.advancement.SynergyAdvancements.grant(owner,
                            net.parkabird.changedsynergy.advancement.SynergyAdvancements.COMPANION_BOARDING);
                }
                finish(mob, success, success ? "boarded" : "unavailable"); return;
            }
        }
        if (mob.tickCount % 10 == 0) {
            Vec3 goal = vehicle == null ? Vec3.atBottomCenterOf(order.block) : closest(mob.position(), vehicle.getBoundingBox().inflate(0.8));
            boolean water = level.getFluidState(BlockPos.containing(goal)).is(net.minecraft.tags.FluidTags.WATER);
            if (mob instanceof net.ltxprogrammer.changed.entity.beast.AbstractAquaticEntity && mob.isInWater() && !water) {
                finish(mob, false, "blocked"); return;
            }
            if (!mob.isInWater() && HunterFaction.of(mob) != HunterFaction.AQUATIC
                    && water) {
                finish(mob, false, "blocked"); return;
            }
            CreatureTaskNavigation.moveTo(mob, BlockPos.containing(goal), 1, 0.35);
            mob.getLookControl().setLookAt(goal.x, goal.y, goal.z);
        }
    }

    private static Vec3 closest(Vec3 point, AABB box) {
        return new Vec3(net.minecraft.util.Mth.clamp(point.x, box.minX, box.maxX),
                net.minecraft.util.Mth.clamp(point.y, box.minY, box.maxY), net.minecraft.util.Mth.clamp(point.z, box.minZ, box.maxZ));
    }
    private static double distanceToHull(Entity mob, Entity vehicle) { return mob.position().distanceToSqr(closest(mob.position(), vehicle.getBoundingBox())); }

    static void finish(ChangedEntity mob, boolean success, @Nullable String reason) {
        Order order = ORDERS.remove(mob);
        if (order == null) return;
        mob.getNavigation().stop(); CreatureTaskNavigation.clearFailure(mob);
        var owner = ((ServerLevel)mob.level()).getServer().getPlayerList().getPlayer(order.owner);
        if (order.group != null) complete(owner, order.group, success);
        else if (owner != null && reason != null) message(owner, reason, mob.getDisplayName());
    }
    private static void complete(@Nullable ServerPlayer owner, Group group, boolean success) {
        if (success) group.boarded++; else group.failed++;
        if (--group.remaining == 0 && owner != null) message(owner, "rally_result", group.boarded, group.failed);
    }

    public static void disembark(ChangedEntity mob, ServerPlayer owner) {
        if (!canLeaveSeat(mob, owner)) return;
        var vehicle = mob.getVehicle();
        var saved = mob.getPersistentData().getCompound(SEATED);
        saved.putUUID("Owner", owner.getUUID()); saved.putUUID("Vehicle", vehicle.getUUID());
        saved.putBoolean("Following", following(mob, owner)); saved.putBoolean("ExitRequested", true);
        mob.getPersistentData().put(SEATED, saved);
        Vec3 safe = safeExit(mob, vehicle);
        if (mob.getRootVehicle().getDeltaMovement().lengthSqr() > 0.04 || safe == null) {
            message(owner, "unsafe_exit", mob.getDisplayName()); return;
        }
        if (vehicle instanceof SeatEntity) CreatureComfortGoal.endSeatedActivity(mob);
        else mob.stopRiding();
        if (mob.isPassenger()) return;
        mob.setPos(safe); mob.getPersistentData().remove(SEATED);
        message(owner, "disembarked", mob.getDisplayName());
    }

    @Nullable private static Vec3 safeExit(ChangedEntity mob, Entity vehicle) {
        Vec3 suggested = vehicle.getDismountLocationForPassenger(mob);
        if (safeFloor(mob, suggested)) return suggested;
        BlockPos center = vehicle.blockPosition();
        for (int radius = 1; radius <= 3; radius++) for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) if (Math.abs(dx) == radius || Math.abs(dz) == radius)
                for (int dy = 1; dy >= -1; dy--) {
                    Vec3 position = Vec3.atBottomCenterOf(center.offset(dx, dy, dz));
                    if (safeFloor(mob, position)) return position;
                }
        return null;
    }

    private static boolean safeFloor(ChangedEntity mob, Vec3 position) {
        BlockPos feet = BlockPos.containing(position), ground = feet.below();
        var level = mob.level();
        if (!level.hasChunkAt(ground) || !level.getWorldBorder().isWithinBounds(feet)) return false;
        boolean water = HunterFaction.isAquatic(mob) && level.getFluidState(feet).is(net.minecraft.tags.FluidTags.WATER);
        var box = mob.getBoundingBox().move(position.subtract(mob.position()));
        if (!water && (!level.getFluidState(feet).isEmpty()
                || !level.getBlockState(ground).isFaceSturdy(level, ground, net.minecraft.core.Direction.UP))
                || !level.noCollision(mob, box)) return false;
        for (BlockPos occupied : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX - 0.001, box.maxY - 0.001, box.maxZ - 0.001))) {
            var fluid = level.getFluidState(occupied);
            if (BondedTeleportSafety.isDangerous(level.getBlockState(occupied))
                    || !fluid.isEmpty() && (!water || !fluid.is(net.minecraft.tags.FluidTags.WATER))) return false;
        }
        for (BlockPos pos : List.of(feet, ground)) {
            if (BondedTeleportSafety.isDangerous(level.getBlockState(pos))) return false;
        }
        return true;
    }

    private static void ensureGoal(ChangedEntity mob) {
        if (mob.goalSelector.getAvailableGoals().stream().noneMatch(goal -> goal.getGoal() instanceof CompanionSeatGoal))
            mob.goalSelector.addGoal(-2, new CompanionSeatGoal(mob));
    }
    @SubscribeEvent public static void creatureTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity mob) || !(mob.level() instanceof ServerLevel level)) return;
        if (pending(mob)) {
            ensureGoal(mob);
            Order order = ORDERS.get(mob);
            var owner = level.getServer().getPlayerList().getPlayer(order.owner);
            if (!free(mob) || !ChangedSynergyConfig.COMMON.companionSeating.get()
                    || owner == null || !canCommand(mob, owner) || level.getGameTime() > order.expires)
                finish(mob, false, "interrupted");
        }
        if (mob.tickCount % 20 != 0 || !mob.getPersistentData().contains(SEATED)) return;
        var saved = mob.getPersistentData().getCompound(SEATED);
        if (!saved.hasUUID("Vehicle") || mob.getVehicle() == null || !mob.getVehicle().getUUID().equals(saved.getUUID("Vehicle"))) {
            mob.getPersistentData().remove(SEATED); return;
        }
        ServerPlayer owner = saved.hasUUID("Owner") ? level.getServer().getPlayerList().getPlayer(saved.getUUID("Owner")) : null;
        if (owner != null && level.getGameTime() >= saved.getLong("ExitRetry")
                && (saved.getBoolean("ExitRequested") || saved.getBoolean("Following") != following(mob, owner)
                    || saved.getBoolean("Rally") && TrueTransfurCompat.interactionBody(owner).getVehicle() != mob.getVehicle())) {
            saved.putLong("ExitRetry", level.getGameTime() + 100);
            disembark(mob, owner);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (var entry : new ArrayList<>(SELECTIONS.entrySet())) {
            var owner = entry.getValue().level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (owner == null) SELECTIONS.remove(entry.getKey());
            else if (!validSelection(owner, entry.getValue())) clearSelection(owner);
        }
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ChangedEntity mob) {
            finish(mob, false, "interrupted");
            for (var entry : new ArrayList<>(SELECTIONS.entrySet())) if (entry.getValue().creature.equals(mob.getUUID())) {
                var owner = entry.getValue().level.getServer().getPlayerList().getPlayer(entry.getKey());
                if (owner != null) clearSelection(owner); else SELECTIONS.remove(entry.getKey());
            }
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer owner) {
            SELECTIONS.remove(owner.getUUID());
            for (var entry : new ArrayList<>(ORDERS.entrySet())) if (entry.getValue().owner.equals(owner.getUUID())) finish(entry.getKey(), false, null);
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { ORDERS.clear(); SELECTIONS.clear(); }
    private static void clearSelection(ServerPlayer owner) {
        if (SELECTIONS.remove(owner.getUUID()) != null) send(owner, new CompanionSeatStatePacket(0, -1, Component.empty()));
    }
    private static void send(ServerPlayer owner, CompanionSeatStatePacket packet) { ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> owner), packet); }
    private static void message(ServerPlayer owner, String key, Object... args) { owner.displayClientMessage(Component.translatable("message.changed_synergy.seating." + key, args), true); }
}
