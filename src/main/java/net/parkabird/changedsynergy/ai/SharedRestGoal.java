package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.SeatEntity;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket.GrabType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.FriendlySocialHugSyncPacket;

/** A short ground rest, or a companion leading its owner to a usable bed. */
public final class SharedRestGoal extends Goal {
    private static final double WALK_SPEED = 0.35D;
    private static final String OWNER = "ChangedSynergyRestOwner";
    private static final String UNTIL = "ChangedSynergyRestUntil";
    private static final String BED = "ChangedSynergyRestBed";
    private static final String SLEEPING = "ChangedSynergyRestSleeping";
    private final ChangedEntity creature;
    private SeatEntity groundSeat;
    private int repath;

    public SharedRestGoal(ChangedEntity creature) {
        this.creature = creature;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public static void begin(ChangedEntity creature, ServerPlayer owner) {
        if (CreatureSocialProfile.isJuvenile(creature)) return;
        CompoundTag data = creature.getPersistentData();
        BlockPos bed = nearestBed(creature, owner);
        data.putUUID(OWNER, owner.getUUID());
        data.putLong(UNTIL, creature.level().getGameTime() + (bed == null ? 180 : 1200));
        data.remove(SLEEPING);
        if (bed == null) data.remove(BED);
        else data.putLong(BED, bed.asLong());
    }

    public static boolean isCarryTarget(ChangedEntity creature, LivingEntity target) {
        CompoundTag data = creature.getPersistentData();
        return data.hasUUID(OWNER) && data.contains(BED)
                && target.getUUID().equals(data.getUUID(OWNER))
                && data.getLong(UNTIL) > creature.level().getGameTime();
    }

    @Override public boolean canUse() { return valid(); }
    @Override public boolean canContinueToUse() { return valid(); }

    private boolean valid() {
        CompoundTag data = creature.getPersistentData();
        if (!(creature.level() instanceof ServerLevel level)
                || !data.hasUUID(OWNER)
                || !data.getBoolean(SLEEPING)
                        && data.getLong(UNTIL) <= level.getGameTime()
                || !creature.isAlive() || creature.getTarget() != null
                || CreatureSocialProfile.isJuvenile(creature)) return false;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(data.getUUID(OWNER));
        return owner != null && owner.isAlive() && owner.level() == level
                && (LatexSocialMemory.isBonded(creature, owner)
                        || LatexSocialMemory.isPetOwner(creature, owner));
    }

    @Override public void start() {
        repath = 0;
        if (creature.getPersistentData().contains(BED)) {
            ServerLevel level = (ServerLevel)creature.level();
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(
                    creature.getPersistentData().getUUID(OWNER));
            if (owner != null && startCarry(owner)) return;
            creature.getPersistentData().remove(BED);
            creature.getPersistentData().putLong(UNTIL, level.getGameTime() + 180);
        }
        makeGroundSeat();
    }

    private void makeGroundSeat() {
        if (!creature.isPassenger()) {
            BlockPos floor = creature.blockPosition().below();
            BlockState floorState = creature.level().getBlockState(floor);
            if (!floorState.isSolidRender(creature.level(), floor)) return;
            groundSeat = SeatEntity.createFor(creature.level(),
                    floorState, floor, false, false, true);
            if (groundSeat != null) {
                groundSeat.setPos(creature.getX(), creature.getY(), creature.getZ());
                if (!creature.startRiding(groundSeat, true)) {
                    groundSeat.discard();
                    groundSeat = null;
                }
            }
        }
    }

    private boolean startCarry(ServerPlayer owner) {
        GrabEntityAbilityInstance ability = BondedSuitService.ability(creature);
        if (ability == null || ability.grabbedEntity != null) return false;
        ChangedAddonCompat.configureFriendlyGrab(ability, true);
        ability.suited = false;
        ability.grabbedHasControl = false;
        ability.grabStrength = 1.0F;
        ability.attackDown = ability.useDown = false;
        if (!ability.grabEntity(owner)) {
            ChangedAddonCompat.configureFriendlyGrab(ability, false);
            return false;
        }
        Changed.PACKET_HANDLER.send(PacketDistributor.TRACKING_ENTITY.with(() -> creature),
                new GrabEntityPacket(creature, owner, GrabType.ARMS));
        ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> owner),
                new FriendlySocialHugSyncPacket(creature.getId(), owner.getId(), true, -72000));
        return true;
    }

    private void finishCarry(ServerPlayer owner) {
        GrabEntityAbilityInstance ability = BondedSuitService.ability(creature);
        if (ability != null && ability.grabbedEntity == owner && !ability.suited) {
            ability.attackDown = ability.useDown = false;
            ability.releaseEntity(false);
            Changed.PACKET_HANDLER.send(PacketDistributor.TRACKING_ENTITY.with(() -> creature),
                    new GrabEntityPacket(creature, owner, GrabType.RELEASE));
        }
        ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> owner),
                new FriendlySocialHugSyncPacket(creature.getId(), owner.getId(), false, 0));
        if (ability != null) ChangedAddonCompat.configureFriendlyGrab(ability, false);
    }

    @Override public void tick() {
        CompoundTag data = creature.getPersistentData();
        ServerLevel level = (ServerLevel)creature.level();
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(data.getUUID(OWNER));
        if (owner == null) return;
        if (data.getBoolean(SLEEPING)) {
            if (!owner.isSleeping()) {
                finishCarry(owner);
                creature.stopSleeping();
                data.remove(OWNER);
                data.remove(BED);
                data.remove(SLEEPING);
                data.remove(UNTIL);
            }
            return;
        }
        if (!data.contains(BED)) {
            creature.getNavigation().stop();
            return;
        }
        BlockPos bed = BlockPos.of(data.getLong(BED));
        if (!isFreeBed(level, bed)) {
            finishCarry(owner);
            data.remove(BED);
            data.putLong(UNTIL, level.getGameTime() + 180);
            makeGroundSeat();
            return;
        }
        GrabEntityAbilityInstance ability = BondedSuitService.ability(creature);
        if (ability == null || ability.grabbedEntity != owner) {
            data.remove(BED);
            data.putLong(UNTIL, level.getGameTime() + 180);
            makeGroundSeat();
            return;
        }
        if (creature.distanceToSqr(bed.getX() + 0.5D, bed.getY(), bed.getZ() + 0.5D)
                > 4.0D) {
            if (--repath <= 0 || creature.getNavigation().isDone()) {
                repath = 20;
                creature.getNavigation().moveTo(bed.getX() + 0.5D,
                        bed.getY(), bed.getZ() + 0.5D, WALK_SPEED);
            }
            return;
        }
        creature.getNavigation().stop();
        if (owner.startSleepInBed(bed).right().isPresent()) {
            creature.startSleeping(bed);
            data.putBoolean(SLEEPING, true);
            data.putLong(UNTIL, Long.MAX_VALUE);
        } else {
            finishCarry(owner);
            data.remove(BED);
            data.putLong(UNTIL, level.getGameTime() + 180);
            makeGroundSeat();
        }
    }

    @Override public void stop() {
        creature.getNavigation().stop();
        if (creature.getPersistentData().contains(BED)
                && creature.getPersistentData().hasUUID(OWNER)
                && creature.level() instanceof ServerLevel level) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(
                    creature.getPersistentData().getUUID(OWNER));
            if (owner != null) finishCarry(owner);
            else {
                GrabEntityAbilityInstance ability = BondedSuitService.ability(creature);
                if (ability != null && ability.grabbedEntity != null && !ability.suited) {
                    LivingEntity held = ability.grabbedEntity;
                    ability.releaseEntity(false);
                    Changed.PACKET_HANDLER.send(
                            PacketDistributor.TRACKING_ENTITY.with(() -> creature),
                            new GrabEntityPacket(creature, held, GrabType.RELEASE));
                    ChangedAddonCompat.configureFriendlyGrab(ability, false);
                }
            }
        }
        if (creature.getPersistentData().getBoolean(SLEEPING)) creature.stopSleeping();
        if (groundSeat != null) {
            if (creature.getVehicle() == groundSeat) creature.stopRiding();
            groundSeat.discard();
            groundSeat = null;
        }
        CompoundTag data = creature.getPersistentData();
        data.remove(OWNER);
        data.remove(UNTIL);
        data.remove(BED);
        data.remove(SLEEPING);
    }

    private static BlockPos nearestBed(ChangedEntity creature, ServerPlayer owner) {
        if (!(owner.level() instanceof ServerLevel level)
                || !BondedSuitService.canStartSuit(creature, owner)
                || BondedSuitService.ability(creature) == null || level.isDay()
                || !level.dimensionType().bedWorks()) return null;
        BlockPos origin = owner.blockPosition();
        BlockPos best = null;
        double distance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-5, -2, -5),
                origin.offset(5, 2, 5))) {
            if (!isFreeBed(level, pos)) continue;
            double candidate = creature.distanceToSqr(pos.getX() + 0.5D,
                    pos.getY(), pos.getZ() + 0.5D);
            if (candidate < distance) {
                best = pos.immutable();
                distance = candidate;
            }
        }
        return best;
    }

    private static boolean isFreeBed(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof BedBlock && !state.getValue(BedBlock.OCCUPIED);
    }
}
