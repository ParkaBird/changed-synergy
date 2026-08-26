package net.parkabird.changedsynergy.ai;

import java.util.Comparator;
import java.util.EnumSet;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TamableLatexEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.dialogue.LatexTerritory;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/** Idle pure-white latex can dissolve into its territory and reform nearby. */
public final class PureWhiteReformationGoal extends Goal {
    public static final int PRIORITY = 3;
    private static final String NEXT_REFORMATION =
            "ChangedSynergyNextWhiteReformation";
    private static final double OBSERVER_RANGE_SQR = 20.0D * 20.0D;

    private final ChangedEntity mob;
    private Vec3 destination;
    private int ticks;
    private boolean wasInvisible;

    public PureWhiteReformationGoal(ChangedEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel level)
                || HunterFaction.of(mob) != HunterFaction.WHITE
                || !ChangedSynergyGameRules.enabled(
                        level, ChangedSynergyGameRules.CREATURE_LIFE)
                || !movementAvailable()
                || level.getGameTime()
                        < mob.getPersistentData().getLong(NEXT_REFORMATION)
                || mob.getRandom().nextInt(100) != 0
                || LatexTerritory.dominantFactionAt(
                        level, mob.blockPosition()) != HunterFaction.WHITE) {
            return false;
        }

        CreatureLifeMemory.refreshConsensusFocus(mob);
        BlockPos focus = chooseFocus(level);
        destination = BondedTeleportSafety.findSafeLandingNear(
                        level, mob, focus, 2, 8, 5)
                .filter(position -> mob.distanceToSqr(position) >= 6.0D * 6.0D)
                .or(() -> BondedTeleportSafety.findSafeLandingNear(
                        level, mob, mob.blockPosition(), 6, 12, 5))
                .filter(position -> LatexTerritory.dominantFactionAt(
                        level, BlockPos.containing(position)) == HunterFaction.WHITE)
                .orElse(null);
        return destination != null;
    }

    @Override
    public boolean canContinueToUse() {
        return ticks < 26 && destination != null && movementAvailable();
    }

    @Override
    public void start() {
        ticks = 0;
        wasInvisible = mob.isInvisible();
        mob.getNavigation().stop();
        mob.setDeltaMovement(Vec3.ZERO);
        mob.getPersistentData().putLong(
                NEXT_REFORMATION,
                mob.level().getGameTime() + 1200L + mob.getRandom().nextInt(1201));
        particles(mob.position(), 12);
        mob.level().playSound(
                null,
                mob.blockPosition(),
                SoundEvents.SLIME_SQUISH_SMALL,
                SoundSource.NEUTRAL,
                0.7F,
                1.25F);
    }

    @Override
    public void tick() {
        ticks++;
        mob.getNavigation().stop();
        mob.setDeltaMovement(Vec3.ZERO);
        if (ticks == 6) {
            particles(mob.position(), 18);
            mob.setInvisible(true);
        } else if (ticks == 10) {
            mob.teleportTo(destination.x, destination.y, destination.z);
            BondedTeleportSafety.settleAfterTeleport(mob);
            particles(destination, 18);
        } else if (ticks == 14) {
            mob.setInvisible(wasInvisible);
            particles(mob.position(), 12);
            presentReformation();
        }
    }

    @Override
    public void stop() {
        mob.setInvisible(wasInvisible);
        mob.setDeltaMovement(Vec3.ZERO);
        destination = null;
        ticks = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private BlockPos chooseFocus(ServerLevel level) {
        ChangedEntity peer = level.getEntitiesOfClass(
                        ChangedEntity.class,
                        mob.getBoundingBox().inflate(28.0D),
                        candidate -> candidate != mob
                                && candidate.isAlive()
                                && HunterFaction.of(candidate) == HunterFaction.WHITE
                                && candidate.getTarget() == null
                                && mob.distanceToSqr(candidate) >= 8.0D * 8.0D)
                .stream()
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .orElse(null);
        return peer != null
                ? peer.blockPosition()
                : mob.blockPosition();
    }

    private boolean movementAvailable() {
        if (!mob.isAlive()
                || mob.isNoAi()
                || mob.isPassenger()
                || mob.isLeashed()
                || mob.getTarget() != null
                || mob.hurtTime > 0
                || mob.getHealth() < mob.getMaxHealth() * 0.5F
                || SocialAudienceGoal.isActive(mob)
                || ChangedAddonCompat.isGrabberBusy(mob)
                || HypnosisQteService.getActiveVictim(mob) != null
                || LatexSocialMemory.hasActiveBond(mob)
                || LatexSocialMemory.petOwnerUuid(mob).isPresent()
                || CreaturePersonality.socialPartner(mob) != null) {
            return false;
        }
        return !(mob instanceof TamableLatexEntity pet && pet.isTame());
    }

    private void particles(Vec3 position, int count) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(
                ParticleTypes.CLOUD,
                position.x,
                position.y + mob.getBbHeight() * 0.45D,
                position.z,
                count,
                0.35D,
                0.28D,
                0.35D,
                0.025D);
        level.sendParticles(
                ParticleTypes.END_ROD,
                position.x,
                position.y + 0.15D,
                position.z,
                Math.max(2, count / 4),
                0.28D,
                0.08D,
                0.28D,
                0.01D);
    }

    private void presentReformation() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer observer = level.players().stream()
                .filter(player -> player.isAlive()
                        && !player.isSpectator()
                        && player.distanceToSqr(mob) <= OBSERVER_RANGE_SQR)
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .orElse(null);
        if (observer != null) {
            NpcDialogue.trigger(mob, observer, Cue.WHITE_REFORMATION);
        } else {
            NpcDialogue.emoteOnly(mob, Cue.WHITE_REFORMATION);
        }
    }
}
