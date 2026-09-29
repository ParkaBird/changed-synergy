package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.ltxprogrammer.changed.block.Pillow;
import net.ltxprogrammer.changed.block.entity.PillowBlockEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Member;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Outpost;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Role;

/** A resident can use the linked bed; latex bees can also use a hive's yellow pillow. */
public final class PlayerOutpostRestGoal extends Goal {
    private static final String RESTING = "ChangedSynergyPlayerOutpostResting";
    private static final Set<ChangedEntity> ACTIVE_RESTS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private final ChangedEntity mob;
    private Outpost outpost;
    private BlockPos bed;
    private int remaining;
    private int repath;
    private boolean seated;

    public PlayerOutpostRestGoal(ChangedEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (mob.tickCount % 40 != 0 || !(mob.level() instanceof ServerLevel level)
                || !ChangedSynergyConfig.COMMON.playerOutposts.get()
                || !ChangedSynergyConfig.COMMON.playerOutpostRest.get()
                || level.isDay() || mob.isSleeping() || mob.isPassenger()
                || mob.isNoAi() || mob.isLeashed() || mob.getTarget() != null
                || CreatureSettlementService.hasCargo(mob)
                || PlayerOutpostGoal.hasCargo(mob)) return false;
        outpost = PlayerOutpostData.get(level.getServer()).byMember(mob.getUUID()).orElse(null);
        if (outpost == null || !outpost.active(level)
                || !outpost.contains(mob.blockPosition())) return false;
        Member member = outpost.members.get(mob.getUUID());
        if (member == null || member.role != Role.RESIDENT && member.role != Role.GUARD
                && member.role != Role.SUPPLY) return false;
        bed = beePillow(level);
        if (bed == null || !free(level, bed)) bed = outpost.bed;
        if (bed == null || !free(level, bed)) return false;
        if (mob.distanceToSqr(Vec3.atCenterOf(bed)) > 8.0D) {
            var path = mob.getNavigation().createPath(bed, 1);
            if (path == null || !path.canReach()) return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!(mob.level() instanceof ServerLevel level) || outpost == null
                || !ChangedSynergyConfig.COMMON.playerOutpostRest.get()
                || !outpost.active(level) || level.isDay() || !mob.isAlive()
                || mob.getTarget() != null || remaining <= 0) return false;
        Member member = outpost.members.get(mob.getUUID());
        if (member == null || member.role != Role.RESIDENT && member.role != Role.GUARD
                && member.role != Role.SUPPLY) return false;
        if (mob.isSleeping()) return bed != null
                && level.getBlockState(bed).getBlock() instanceof BedBlock;
        if (seated) return bed != null
                && level.getBlockState(bed).getBlock() instanceof Pillow
                && mob.getVehicle() instanceof SeatEntity;
        return bed != null && free(level, bed);
    }

    @Override
    public void start() {
        remaining = 500;
        repath = 0;
        seated = false;
    }

    @Override
    public void tick() {
        remaining--;
        if (mob.isSleeping() || seated || bed == null) return;
        if (mob.distanceToSqr(Vec3.atCenterOf(bed)) > 6.0D) {
            if (--repath <= 0 || mob.getNavigation().isDone()) {
                repath = 20;
                mob.getNavigation().moveTo(bed.getX() + 0.5D, bed.getY(),
                        bed.getZ() + 0.5D, 0.27D);
            }
            return;
        }
        mob.getNavigation().stop();
        BlockState state = mob.level().getBlockState(bed);
        if (state.getBlock() instanceof BedBlock && free((ServerLevel)mob.level(), bed)) {
            mob.startSleeping(bed);
            remaining = 12000;
        } else if (state.getBlock() instanceof Pillow
                && mob.level().getBlockEntity(bed) instanceof PillowBlockEntity pillow
                && free((ServerLevel)mob.level(), bed) && pillow.sitEntity(mob)) {
            seated = true;
            remaining = 240 + mob.getRandom().nextInt(181);
            mob.getPersistentData().putBoolean(RESTING, true);
            ACTIVE_RESTS.add(mob);
        } else remaining = 0;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        if (mob.isSleeping()) mob.stopSleeping();
        cleanup(mob);
        remaining = 0;
        seated = false;
        bed = null;
        outpost = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    private BlockPos beePillow(ServerLevel level) {
        if (PlayerOutpostSupplyType.of(mob) != PlayerOutpostSupplyType.NECTAR) return null;
        BlockPos origin = outpost.marker;
        for (BlockPos cursor : BlockPos.betweenClosed(origin.offset(-8, -3, -8),
                origin.offset(8, 3, 8))) {
            if (outpost.contains(cursor) && level.hasChunkAt(cursor)
                    && isYellowPillow(level.getBlockState(cursor))
                    && free(level, cursor)) return cursor.immutable();
        }
        return null;
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BedBlock) {
            return level.dimensionType().bedWorks() && !state.getValue(BedBlock.OCCUPIED);
        }
        return state.getBlock() instanceof Pillow && !state.getValue(Pillow.OCCUPIED);
    }

    private static boolean isYellowPillow(BlockState state) {
        var id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && id.getNamespace().equals("changed")
                && id.getPath().equals("yellow_pillow");
    }

    public static void cleanup(ChangedEntity mob) {
        ACTIVE_RESTS.remove(mob);
        if (!mob.getPersistentData().getBoolean(RESTING)) return;
        mob.getPersistentData().remove(RESTING);
        if (mob.getVehicle() instanceof SeatEntity seat) {
            mob.stopRiding();
            seat.discard();
        }
    }

    public static void cleanupStale(ChangedEntity mob) {
        if (mob.getPersistentData().getBoolean(RESTING)
                && !ACTIVE_RESTS.contains(mob)) cleanup(mob);
    }

    public static void cleanupAllForServer(MinecraftServer server) {
        for (ChangedEntity mob : List.copyOf(ACTIVE_RESTS)) {
            if (mob.level().getServer() == server) cleanup(mob);
        }
    }
}
