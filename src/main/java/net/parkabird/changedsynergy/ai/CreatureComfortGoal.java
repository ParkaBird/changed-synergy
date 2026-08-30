package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.block.DroppedOrange;
import net.ltxprogrammer.changed.block.CardboardBoxTall;
import net.ltxprogrammer.changed.block.OfficeChair;
import net.ltxprogrammer.changed.block.Pillow;
import net.ltxprogrammer.changed.block.entity.CardboardBoxTallBlockEntity;
import net.ltxprogrammer.changed.block.entity.ChairBlockEntity;
import net.ltxprogrammer.changed.block.entity.PillowBlockEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.Emote;
import net.ltxprogrammer.changed.entity.SeatEntity;
import net.ltxprogrammer.changed.entity.TamableLatexEntity;
import net.ltxprogrammer.changed.init.ChangedBlocks;
import net.ltxprogrammer.changed.init.ChangedItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory.GroupRole;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;

/**
 * Infrequent ambient interactions with Changed's physical comfort props.
 * Combat, following, social interaction and community work all outrank it.
 */
public final class CreatureComfortGoal extends Goal {
    public static final int PRIORITY = 5;
    private static final String NEXT_ACTION =
            "ChangedSynergyNextComfortAction";
    private static final int SEARCH_RADIUS = 12;
    private static final int VERTICAL_RADIUS = 4;
    private static final double MOVE_SPEED = 0.25D;
    private static final double ARRIVAL_SQR = 2.4D * 2.4D;
    private static final int FULL_HEALTH_ORANGE_CHANCE = 12;
    private static final int RESERVED_ORANGES = 1;

    private final ChangedEntity mob;
    private Mode mode = Mode.NONE;
    @Nullable private BlockPos target;
    @Nullable private Path path;
    private int actionTicks;
    private int repathTicks;
    private int restTicks;
    private boolean seatedByGoal;

    public CreatureComfortGoal(ChangedEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel level)
                || !CreatureLifeMemory.enabled(mob)
                || !idleAvailable(false)) {
            return false;
        }
        long now = level.getGameTime();
        long next = mob.getPersistentData().getLong(NEXT_ACTION);
        if (next == 0L) {
            scheduleNext(now, 180, 241);
            return false;
        }
        if (next > now) {
            return false;
        }

        resetSelection();
        boolean acceptsOrange = RelationshipFavorService.acceptsOrange(mob);
        boolean needsHealing = mob.getHealth() <= mob.getMaxHealth() - 1.0F;
        if (needsHealing && acceptsOrange
                && selectReachable(
                        Mode.EAT_ORANGE,
                        CreatureComfortGoal::hasConsumableOrange)) {
            return true;
        }
        if (CreatureLifeMemory.role(mob) == GroupRole.PROVISIONER
                && CreatureSettlementService.recentProvisionSource(mob)
                        == CreatureSettlementService.ProvisionSource.ORANGE
                && mob.getRandom().nextInt(3) == 0
                && CreatureSettlementService.hasOrangeSupply(mob)
                && selectOrangeRestockSite()) {
            return true;
        }
        if (!needsHealing && acceptsOrange
                && mob.getRandom().nextInt(FULL_HEALTH_ORANGE_CHANCE) == 0
                && selectReachable(
                        Mode.EAT_ORANGE,
                        CreatureComfortGoal::hasConsumableOrange)) {
            return true;
        }
        // Tall Changed cardboard boxes are real seats with their own open/close
        // animation.  Feline bodies seek them out much more often, while other
        // social creatures still occasionally investigate one.
        int boxChance = HunterArchetype.of(mob) == HunterArchetype.FELINE
                ? 2 : 7;
        if (mob.getRandom().nextInt(boxChance) == 0
                && selectReachable(
                        Mode.HIDE_BOX,
                        state -> state.getBlock() instanceof CardboardBoxTall
                                && state.hasProperty(CardboardBoxTall.HALF)
                                && state.getValue(CardboardBoxTall.HALF)
                                        == DoubleBlockHalf.LOWER)) {
            return true;
        }
        if (mob.getRandom().nextInt(3) == 0
                && selectReachable(
                        Mode.REST,
                        CreatureComfortGoal::isAvailableRestSeatState)) {
            return true;
        }

        scheduleNext(now, 220, 281);
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (mode == Mode.NONE || actionTicks <= 0 || !idleAvailable(seatedByGoal)) {
            return false;
        }
        if (seatedByGoal) {
            return (mode == Mode.REST || mode == Mode.HIDE_BOX)
                    && restTicks > 0
                    && mob.getVehicle() instanceof SeatEntity;
        }
        return target != null && targetStillValid();
    }

    @Override
    public void start() {
        actionTicks = 360;
        repathTicks = 20;
        if (path != null) {
            mob.getNavigation().moveTo(path, MOVE_SPEED);
        }
    }

    @Override
    public void tick() {
        actionTicks--;
        if (seatedByGoal) {
            restTicks--;
            return;
        }
        if (target == null || !targetStillValid()) {
            actionTicks = 0;
            return;
        }

        mob.getLookControl().setLookAt(
                target.getX() + 0.5D,
                target.getY() + 0.45D,
                target.getZ() + 0.5D);
        if (mob.distanceToSqr(
                target.getX() + 0.5D,
                target.getY() + 0.5D,
                target.getZ() + 0.5D) > ARRIVAL_SQR) {
            if (--repathTicks <= 0 || mob.getNavigation().isDone()) {
                repathTicks = 20;
                path = mob.getNavigation().createPath(target, 1);
                if (path == null || !path.canReach()) {
                    actionTicks = 0;
                } else {
                    mob.getNavigation().moveTo(path, MOVE_SPEED);
                }
            }
            return;
        }

        mob.getNavigation().stop();
        switch (mode) {
            case REST -> beginRest();
            case HIDE_BOX -> beginBoxRest();
            case EAT_ORANGE -> eatOrange();
            case RESTOCK_ORANGE -> restockOrange();
            default -> actionTicks = 0;
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        if (seatedByGoal && mob.getVehicle() instanceof SeatEntity) {
            mob.stopRiding();
        }
        if (mob.level() instanceof ServerLevel level) {
            scheduleNext(level.getGameTime(), 600, 801);
        }
        resetSelection();
        actionTicks = 0;
        restTicks = 0;
        seatedByGoal = false;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean selectReachable(
            Mode selectedMode,
            Predicate<BlockState> predicate) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos origin = mob.blockPosition();
        double nearest = Double.MAX_VALUE;
        BlockPos chosen = null;
        Path chosenPath = null;
        for (BlockPos cursor : BlockPos.betweenClosed(
                origin.offset(-SEARCH_RADIUS, -VERTICAL_RADIUS, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, VERTICAL_RADIUS, SEARCH_RADIUS))) {
            BlockState state = level.getBlockState(cursor);
            if (!predicate.test(state)) {
                continue;
            }
            double distance = cursor.distSqr(origin);
            if (distance >= nearest) {
                continue;
            }
            Path candidate = mob.getNavigation().createPath(cursor, 1);
            if (candidate == null || !candidate.canReach()) {
                continue;
            }
            nearest = distance;
            chosen = cursor.immutable();
            chosenPath = candidate;
        }
        if (chosen == null) {
            return false;
        }
        mode = selectedMode;
        target = chosen;
        path = chosenPath;
        return true;
    }

    private boolean selectOrangeRestockSite() {
        BlockPos site = CreatureSettlementService.nearestOrangePileSite(
                        mob, SEARCH_RADIUS)
                .orElse(null);
        if (site == null) {
            return false;
        }
        Path candidate = mob.getNavigation().createPath(site, 1);
        if (candidate == null || !candidate.canReach()) {
            return false;
        }
        mode = Mode.RESTOCK_ORANGE;
        target = site.immutable();
        path = candidate;
        return true;
    }

    private void beginRest() {
        BlockState state = mob.level().getBlockState(target);
        boolean seated = false;
        if (mob.level().getBlockEntity(target) instanceof PillowBlockEntity pillow) {
            seated = state.getBlock() instanceof Pillow
                    && state.hasProperty(Pillow.OCCUPIED)
                    && !state.getValue(Pillow.OCCUPIED)
                    && pillow.sitEntity(mob);
        } else if (mob.level().getBlockEntity(target) instanceof ChairBlockEntity chair) {
            seated = state.getBlock() instanceof OfficeChair
                    && state.hasProperty(OfficeChair.HALF)
                    && state.getValue(OfficeChair.HALF) == DoubleBlockHalf.LOWER
                    && chair.getSeatedEntity() == null
                    && chair.sitEntity(mob);
        }
        if (!seated) {
            actionTicks = 0;
            return;
        }
        seatedByGoal = true;
        restTicks = 140 + mob.getRandom().nextInt(181);
        actionTicks = Math.max(actionTicks, restTicks + 1);
        NpcDialogue.emoteOnly(mob, Emote.SLEEPY);
    }

    private void beginBoxRest() {
        if (!(mob.level().getBlockEntity(target.above())
                        instanceof CardboardBoxTallBlockEntity box)
                || box.getSeatedEntity() != null
                || !box.hideEntity(mob)) {
            actionTicks = 0;
            return;
        }
        seatedByGoal = true;
        restTicks = 120 + mob.getRandom().nextInt(181);
        actionTicks = Math.max(actionTicks, restTicks + 1);
        NpcDialogue.emoteOnly(
                mob,
                HunterArchetype.of(mob) == HunterArchetype.FELINE
                        ? Emote.HEART : Emote.CASUAL);
    }

    private void eatOrange() {
        if (!(mob.level() instanceof ServerLevel level)
                || !decrementOrangePile(level, target)) {
            actionTicks = 0;
            return;
        }
        mob.swing(InteractionHand.MAIN_HAND);
        if (mob.getHealth() < mob.getMaxHealth()) {
            mob.heal(Math.min(4.0F, mob.getMaxHealth() - mob.getHealth()));
        }
        level.playSound(null, target, SoundEvents.GENERIC_EAT,
                SoundSource.NEUTRAL, 0.7F,
                0.9F + mob.getRandom().nextFloat() * 0.2F);
        orangeParticles(level);
        actionTicks = 0;
    }

    private void restockOrange() {
        if (!(mob.level() instanceof ServerLevel level)) {
            actionTicks = 0;
            return;
        }
        if (!CreatureSettlementService.canRestockOrangePileAt(level, target)
                || !CreatureSettlementService.consumeOrangeSupply(mob)) {
            actionTicks = 0;
            return;
        }
        if (!CreatureSettlementService.addOneOrangeToPile(level, target)) {
            // Preserve the consumed resource if another system rejected the block change.
            mob.spawnAtLocation(ChangedItems.ORANGE.get());
            actionTicks = 0;
            return;
        }
        mob.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, target, SoundEvents.ITEM_PICKUP,
                SoundSource.NEUTRAL, 0.45F, 1.2F);
        orangeParticles(level);
        CreatureLifeMemory.incrementRoleStat(mob, 1);
        actionTicks = 0;
    }

    private boolean decrementOrangePile(ServerLevel level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        if (!isOrangePile(state)) {
            return false;
        }
        int count = state.getValue(DroppedOrange.ORANGES);
        if (count <= RESERVED_ORANGES) {
            return false;
        }
        return level.setBlock(
                position,
                state.setValue(DroppedOrange.ORANGES, count - 1),
                3);
    }

    private void orangeParticles(ServerLevel level) {
        level.sendParticles(
                new ItemParticleOption(
                        ParticleTypes.ITEM,
                        ChangedItems.ORANGE.get().getDefaultInstance()),
                mob.getX(),
                mob.getY() + mob.getBbHeight() * 0.65D,
                mob.getZ(),
                7,
                0.20D, 0.18D, 0.20D,
                0.02D);
    }

    private boolean targetStillValid() {
        if (target == null) {
            return false;
        }
        BlockState state = mob.level().getBlockState(target);
        return switch (mode) {
            case REST -> isAvailableRestSeat(state, target);
            case HIDE_BOX -> state.getBlock() instanceof CardboardBoxTall
                    && state.hasProperty(CardboardBoxTall.HALF)
                    && state.getValue(CardboardBoxTall.HALF)
                            == DoubleBlockHalf.LOWER
                    && mob.level().getBlockEntity(target.above())
                            instanceof CardboardBoxTallBlockEntity box
                    && box.getSeatedEntity() == null;
            case EAT_ORANGE -> hasConsumableOrange(state);
            case RESTOCK_ORANGE -> mob.level() instanceof ServerLevel level
                    && CreatureSettlementService.canRestockOrangePileAt(
                            level, target);
            default -> false;
        };
    }

    private boolean idleAvailable(boolean allowOwnSeat) {
        if (!mob.isAlive()
                || mob.isNoAi()
                || mob.isLeashed()
                || CreatureSettlementService.hasCargo(mob)
                || mob.getTarget() != null
                || SocialAudienceGoal.isActive(mob)
                || ChangedAddonCompat.isGrabberBusy(mob)
                || HypnosisQteService.getActiveVictim(mob) != null) {
            return false;
        }
        if (mob.isPassenger()
                && !(allowOwnSeat && seatedByGoal
                        && mob.getVehicle() instanceof SeatEntity)) {
            return false;
        }
        if (LatexSocialMemory.getPetOwner(mob) != null
                && LatexSocialMemory.isFollowingOwner(mob)) {
            return false;
        }
        if (CreaturePersonality.socialPartner(mob) != null) {
            return false;
        }
        return !(mob instanceof TamableLatexEntity pet
                && pet.isTame() && pet.isFollowingOwner());
    }

    private static boolean isOrangePile(BlockState state) {
        return state.is(ChangedBlocks.DROPPED_ORANGE.get())
                && state.hasProperty(DroppedOrange.ORANGES);
    }

    private static boolean hasConsumableOrange(BlockState state) {
        return isOrangePile(state)
                && state.getValue(DroppedOrange.ORANGES) > RESERVED_ORANGES;
    }

    private static boolean isAvailableRestSeatState(BlockState state) {
        if (state.getBlock() instanceof Pillow) {
            return state.hasProperty(Pillow.OCCUPIED)
                    && !state.getValue(Pillow.OCCUPIED);
        }
        return state.getBlock() instanceof OfficeChair
                && state.hasProperty(OfficeChair.HALF)
                && state.getValue(OfficeChair.HALF) == DoubleBlockHalf.LOWER;
    }

    private boolean isAvailableRestSeat(BlockState state, BlockPos position) {
        if (!isAvailableRestSeatState(state)) {
            return false;
        }
        if (state.getBlock() instanceof OfficeChair) {
            return mob.level().getBlockEntity(position) instanceof ChairBlockEntity chair
                    && chair.getSeatedEntity() == null;
        }
        return true;
    }

    private void scheduleNext(long now, int minimum, int spread) {
        mob.getPersistentData().putLong(
                NEXT_ACTION,
                now + minimum + mob.getRandom().nextInt(Math.max(1, spread)));
    }

    /** Prevents a discovered creature from immediately diving back inside. */
    public static void delayAfterBoxDiscovery(ChangedEntity mob) {
        mob.getPersistentData().putLong(
                NEXT_ACTION,
                mob.level().getGameTime() + 500L
                        + mob.getRandom().nextInt(401));
    }

    private void resetSelection() {
        mode = Mode.NONE;
        target = null;
        path = null;
        repathTicks = 0;
    }

    private enum Mode {
        NONE,
        REST,
        HIDE_BOX,
        EAT_ORANGE,
        RESTOCK_ORANGE
    }
}
