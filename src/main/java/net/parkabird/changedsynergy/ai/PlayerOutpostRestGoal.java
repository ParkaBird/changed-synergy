package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.block.CardboardBoxTall;
import net.ltxprogrammer.changed.block.OfficeChair;
import net.ltxprogrammer.changed.block.Pillow;
import net.ltxprogrammer.changed.block.entity.CardboardBoxTallBlockEntity;
import net.ltxprogrammer.changed.block.entity.ChairBlockEntity;
import net.ltxprogrammer.changed.block.entity.PillowBlockEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Member;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Outpost;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Role;

/** Night beds and interruptible daytime rests use the actual Changed seats. */
public final class PlayerOutpostRestGoal extends Goal {
    private static final String RESTING = "ChangedSynergyPlayerOutpostResting";
    private static final String SEAT = "ChangedSynergyPlayerOutpostRestSeat";
    // Reserve while approaching as well as while resting; no chunk tickets.
    private static final Map<ChangedEntity, BlockPos> ACTIVE_RESTS = new WeakHashMap<>();
    private final ChangedEntity mob;
    private Outpost outpost;
    private BlockPos bed;
    private int remaining;
    private int repath;
    private boolean seated;
    private boolean night;
    private long nextAttempt;
    private int restCursor;

    public PlayerOutpostRestGoal(ChangedEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel level)
                || level.getGameTime() < nextAttempt || !PlayerOutpostService.operational(mob)
                || mob.isSleeping() || mob.isPassenger() || !available(mob) || !PlayerOutpostGoal.canNavigate(mob)
                || CreatureSettlementService.hasCargo(mob)
                || PlayerOutpostGoal.hasCargo(mob) || PlayerOutpostWorkGoal.hasCargo(mob)
                || PlayerOutpostMealGoal.wantsMeal(mob)) return false;
        outpost = PlayerOutpostData.get(level.getServer()).byMember(mob.getUUID()).orElse(null);
        if (outpost == null || !outpost.active(level) || !outpost.contains(mob.blockPosition())
                || !eligible(outpost.members.get(mob.getUUID()))) return false;
        PlayerOutpostData.get(level.getServer()).refreshRestPlaces(level, outpost);
        night = !level.isDay();
        if (night) {
            if (!ChangedSynergyConfig.COMMON.playerOutpostRest.get()) return false;
            bed = select(level, outpost.beds, false);
            if (bed == null && PlayerOutpostSupplyType.of(mob) == PlayerOutpostSupplyType.NECTAR)
                bed = select(level, outpost.restPoints.stream()
                        .filter(pos -> level.hasChunkAt(pos) && isYellowPillow(level.getBlockState(pos))).toList(), false);
        } else {
            if (!ChangedSynergyConfig.COMMON.playerOutpostIdleRest.get()) return false;
            nextAttempt = level.getGameTime() + 100L + mob.getRandom().nextInt(101);
            if (mob.getRandom().nextInt(3) != 0) return false;
            bed = select(level, outpost.restPoints, true);
        }
        if (bed == null) nextAttempt = level.getGameTime() + 100L;
        return bed != null;
    }

    private BlockPos select(ServerLevel level, java.util.Collection<BlockPos> places, boolean nearWork) {
        Member member = outpost.members.get(mob.getUUID());
        List<BlockPos> candidates = places.stream().filter(p -> free(level, p))
                .sorted(Comparator.comparingDouble(p -> mob.distanceToSqr(Vec3.atCenterOf(p)))).toList();
        int start = candidates.isEmpty() ? 0 : Math.floorMod(restCursor, candidates.size());
        int probes = 0;
        for (int offset = 0; offset < candidates.size(); offset++) {
            int index = (start + offset) % candidates.size();
            BlockPos pos = candidates.get(index);
            if (nearWork && member.role == Role.SUPPLY
                    && !PlayerOutpostGoal.workArea(outpost, member).inflate(2).contains(Vec3.atCenterOf(pos))) continue;
            if (mob.distanceToSqr(Vec3.atCenterOf(pos)) <= 6.0D) { restCursor = 0; return pos; }
            // Large sets of blocked beds must not rebuild every route during the same server tick.
            if (probes++ >= 8) { restCursor = index; break; }
            var path = mob.getNavigation().createPath(pos, 1);
            if (path != null && path.canReach()) { restCursor = 0; return pos; }
            restCursor = index + 1;
        }
        return null;
    }

    @Override public boolean canContinueToUse() {
        if (!(mob.level() instanceof ServerLevel level) || outpost == null || bed == null
                || !PlayerOutpostService.operational(mob)
                || !(night ? ChangedSynergyConfig.COMMON.playerOutpostRest.get()
                        : ChangedSynergyConfig.COMMON.playerOutpostIdleRest.get())
                || level.isDay() == night || !outpost.active(level) || !available(mob) || remaining <= 0
                || PlayerOutpostData.get(level.getServer()).byMember(mob.getUUID()).orElse(null) != outpost
                || !eligible(outpost.members.get(mob.getUUID())) || !outpost.contains(bed)
                || !bed.equals(ACTIVE_RESTS.get(mob)) || !level.hasChunkAt(bed)
                || !(outpost.beds.contains(bed) || outpost.restPoints.contains(bed))) return false;
        if (mob.isSleeping()) return level.getBlockState(bed).getBlock() instanceof BedBlock
                && bed.equals(restingPlace(level, bed)) && mob.getSleepingPos().filter(bed::equals).isPresent();
        if (seated) return isRestPoint(level.getBlockState(bed)) && isResting(mob)
                && bed.equals(restingPlace(level, ((SeatEntity)mob.getVehicle()).getAttachedBlockPos()));
        return free(level, bed);
    }

    @Override
    public void start() {
        remaining = 500;
        repath = 0;
        seated = false;
        ACTIVE_RESTS.put(mob, bed);
        mob.getNavigation().stop();
    }

    @Override public void tick() {
        // Orders can clear the reservation between the selector's alternating validity checks.
        // Do not lie down again during that last queued tick.
        if (!canContinueToUse()) { remaining = 0; return; }
        if (!seated || night) remaining--;
        if (mob.isSleeping() || seated) {
            Member member = outpost.members.get(mob.getUUID());
            if (member != null) member.workStatus = "resting";
            return;
        }
        if (bed == null) return;
        if (mob.distanceToSqr(Vec3.atCenterOf(bed)) > 6.0D) {
            if (--repath <= 0) {
                repath = 20;
                if (!PlayerOutpostGoal.walkTo(mob, bed, bed.getY(), 0.27D)) remaining = 0;
            }
            return;
        }
        mob.getNavigation().stop();
        ServerLevel level = (ServerLevel)mob.level();
        if (!free(level, bed)) { remaining = 0; return; }
        if (level.getBlockState(bed).getBlock() instanceof BedBlock) {
            mob.startSleeping(bed);
            remaining = 12000;
        } else {
            boolean sat = sit(mob, level, bed);
            if (!sat || !(mob.getVehicle() instanceof SeatEntity seat)) { remaining = 0; return; }
            seated = true;
            remaining = 12000;
            mob.getPersistentData().putBoolean(RESTING, true);
            mob.getPersistentData().putUUID(SEAT, seat.getUUID());
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        cleanup(mob);
        nextAttempt = mob.level().getGameTime() + 100L;
        Member member = outpost == null ? null : outpost.members.get(mob.getUUID());
        if (member != null && member.workStatus.equals("resting")) member.workStatus = "idle";
        remaining = 0;
        seated = false;
        bed = null;
        outpost = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    private static boolean eligible(Member member) {
        return member != null && (member.role == Role.RESIDENT || member.role == Role.GUARD || member.role == Role.SUPPLY);
    }

    /** Work may interrupt our seat, never another interaction's vehicle. */
    public static boolean isResting(ChangedEntity mob) {
        return mob.getPersistentData().getBoolean(RESTING) && ACTIVE_RESTS.containsKey(mob)
                && mob.getVehicle() instanceof SeatEntity seat && mob.getPersistentData().hasUUID(SEAT)
                && seat.getUUID().equals(mob.getPersistentData().getUUID(SEAT));
    }

    private static boolean available(ChangedEntity mob) {
        var suit = BondedSuitService.ability(mob);
        return mob.isAlive() && !mob.isNoAi() && !mob.isLeashed() && mob.getTarget() == null
                && !PlayerOutpostGoal.onCampfire(mob)
                && !mob.isVehicle() && (!mob.isPassenger() || isResting(mob))
                && !TakeoverService.carrying(mob) && !ChangedAddonCompat.isGrabberBusy(mob)
                && GrabEntityAbility.getGrabber(mob) == null && (suit == null || suit.grabbedEntity == null)
                && !SocialAudienceGoal.isActive(mob) && HypnosisQteService.getActiveVictim(mob) == null
                && !mob.getPersistentData().hasUUID("ChangedSynergyRestOwner");
    }

    private boolean free(ServerLevel level, BlockPos pos) {
        return free(mob, level, pos);
    }

    public static boolean free(ChangedEntity mob, ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !pos.equals(restingPlace(level, pos))) return false;
        for (var entry : ACTIVE_RESTS.entrySet()) {
            ChangedEntity other = entry.getKey();
            if (other != mob && other.level() == level && other.isAlive() && !other.isRemoved()
                    && pos.equals(entry.getValue())) return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BedBlock)
            return level.dimensionType().bedWorks() && !state.getValue(BedBlock.OCCUPIED);
        if (state.getBlock() instanceof Pillow)
            return !state.getValue(Pillow.OCCUPIED) && level.getBlockEntity(pos) instanceof PillowBlockEntity pillow
                    && pillow.getSeatedEntity() == null;
        if (state.getBlock() instanceof OfficeChair)
            return level.getBlockEntity(pos) instanceof ChairBlockEntity chair && chair.getSeatedEntity() == null;
        return state.getBlock() instanceof CardboardBoxTall
                && level.getBlockEntity(pos.above()) instanceof CardboardBoxTallBlockEntity box
                && box.getSeatedEntity() == null;
    }

    public static boolean isRestPoint(BlockState state) {
        return state.getBlock() instanceof Pillow || state.getBlock() instanceof OfficeChair
                || state.getBlock() instanceof CardboardBoxTall;
    }

    public static boolean sit(ChangedEntity mob, ServerLevel level, BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        return entity instanceof PillowBlockEntity pillow && pillow.sitEntity(mob)
                || entity instanceof ChairBlockEntity chair && chair.sitEntity(mob)
                || level.getBlockState(pos).getBlock() instanceof CardboardBoxTall
                    && level.getBlockEntity(pos.above()) instanceof CardboardBoxTallBlockEntity box && box.hideEntity(mob);
    }

    /** An unloaded other half must not erase a registration. */
    public static boolean partsLoaded(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BedBlock)
            return level.hasChunkAt(pos.relative(state.getValue(BedBlock.PART) == BedPart.HEAD
                    ? state.getValue(BedBlock.FACING).getOpposite() : state.getValue(BedBlock.FACING)));
        if (state.getBlock() instanceof OfficeChair || state.getBlock() instanceof CardboardBoxTall)
            return level.hasChunkAt(state.getValue(OfficeChair.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below());
        return true;
    }

    /** LivingEntity.startSleeping uses its argument as-is; vanilla bed interaction first selects the head. */
    public static BlockPos restingPlace(Level level, BlockPos pos) {
        if (pos == null || !partsLoaded(level, pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof OfficeChair || state.getBlock() instanceof CardboardBoxTall) {
            BlockPos lower = state.getValue(OfficeChair.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            BlockState bottom = level.getBlockState(lower), top = level.getBlockState(lower.above());
            return bottom.getBlock() == state.getBlock() && top.getBlock() == state.getBlock()
                    && bottom.getValue(OfficeChair.HALF) == DoubleBlockHalf.LOWER
                    && top.getValue(OfficeChair.HALF) == DoubleBlockHalf.UPPER ? lower.immutable() : null;
        }
        if (!(state.getBlock() instanceof BedBlock)) return pos.immutable();
        var facing = state.getValue(BedBlock.FACING);
        BlockPos head = state.getValue(BedBlock.PART) == BedPart.HEAD ? pos : pos.relative(facing);
        BlockPos foot = head.relative(facing.getOpposite());
        if (!level.hasChunkAt(head) || !level.hasChunkAt(foot)) return null;
        BlockState headState = level.getBlockState(head), footState = level.getBlockState(foot);
        return headState.getBlock() == state.getBlock() && footState.getBlock() == state.getBlock()
                && headState.getValue(BedBlock.PART) == BedPart.HEAD
                && footState.getValue(BedBlock.PART) == BedPart.FOOT
                && headState.getValue(BedBlock.FACING) == facing && footState.getValue(BedBlock.FACING) == facing
                ? head.immutable() : null;
    }

    private static boolean isYellowPillow(BlockState state) {
        var id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && id.getNamespace().equals("changed")
                && id.getPath().equals("yellow_pillow");
    }

    public static void cleanup(ChangedEntity mob) {
        BlockPos reserved = ACTIVE_RESTS.remove(mob);
        if (reserved != null && mob.getSleepingPos().filter(reserved::equals).isPresent()) mob.stopSleeping();
        if (!mob.getPersistentData().getBoolean(RESTING)) return;
        boolean ownSeat = !mob.getPersistentData().hasUUID(SEAT)
                || mob.getVehicle() instanceof SeatEntity seat && seat.getUUID().equals(mob.getPersistentData().getUUID(SEAT));
        mob.getPersistentData().remove(RESTING);
        mob.getPersistentData().remove(SEAT);
        if (ownSeat) CreatureComfortGoal.endSeatedActivity(mob);
    }

    public static void cleanupStale(ChangedEntity mob) {
        if (mob.getPersistentData().getBoolean(RESTING)
                && !ACTIVE_RESTS.containsKey(mob)) cleanup(mob);
    }

    public static void cleanupAllForServer(MinecraftServer server) {
        for (ChangedEntity mob : List.copyOf(ACTIVE_RESTS.keySet())) {
            if (mob.level().getServer() == server) cleanup(mob);
        }
    }
}
