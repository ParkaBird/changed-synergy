package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/** A limited personal boundary, never an unlimited way to neutralize combat. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class HumanBoundaryService {
    private static final String ROOT = "SynergyHumanBoundary";
    private HumanBoundaryService() {}

    private static CompoundTag state(ChangedEntity mob) {
        if (!mob.getPersistentData().contains(ROOT)) mob.getPersistentData().put(ROOT, new CompoundTag());
        return mob.getPersistentData().getCompound(ROOT);
    }

    private static boolean same(CompoundTag data, ServerPlayer player) {
        return data.hasUUID("Player") && data.getUUID("Player").equals(player.getUUID());
    }

    public static boolean isBackingOff(ChangedEntity mob, ServerPlayer player) {
        CompoundTag data = state(mob);
        return same(data, player) && data.getLong("RetreatUntil") > mob.level().getGameTime()
                && !LatexSocialMemory.isProvoked(mob, player);
    }

    public static boolean isChallenged(ChangedEntity mob, ServerPlayer player) {
        CompoundTag data = state(mob);
        return same(data, player) && data.getLong("ChallengeUntil") > mob.level().getGameTime()
                && LatexSocialMemory.isProvoked(mob, player);
    }

    private static boolean candidate(ChangedEntity mob, ServerPlayer player) {
        if (!mob.level().getGameRules().getBoolean(ChangedSynergyGameRules.FRIENDSHIP_SYSTEM)
                || ProcessTransfur.isPlayerTransfurred(player) || player.isCreative() || player.isSpectator()
                || !LatexSocialMemory.isSocialLatex(mob) || !CreatureSocialProfile.allowsSynergySystems(mob)
                || LatexSocialMemory.isBonded(mob, player) || LatexSocialMemory.isPetOwner(mob, player)
                || FactionReputation.isHostile(mob, player) || LatexSocialMemory.isProvoked(mob, player)
                || CreatureCacheGuardService.isDefendingAgainst(mob, player)
                || ChangedAddonCompat.isGrabberBusy(mob) || FactionPursuitService.isPursuer(mob)
                || mob.distanceToSqr(player) > 25.0D) return false;
        return isBackingOff(mob, player) || PoliteHumanInteraction.isActiveWith(mob, player)
                || mob.getTarget() == player && HumanIntent.of(mob) != HumanIntent.GREET
                    && player.getLastHurtByMob() != mob;
    }

    public static boolean isSoftBoundaryHit(ChangedEntity mob, ServerPlayer player,
            DamageSource source, float amount) {
        CompoundTag data = state(mob);
        int hits = same(data, player) && data.getLong("WindowUntil") > mob.level().getGameTime()
                ? data.getInt("Hits") : 0;
        return candidate(mob, player) && source.getDirectEntity() == player
                && player.getMainHandItem().isEmpty() && amount > 0 && amount <= 2.0F
                && amount < mob.getHealth() && hits < 2
                && !CreaturePersonality.has(mob, CreaturePersonality.Trait.COMPETITIVE);
    }

    /** Called for real health damage before the generic friendly-fire response. */
    public static boolean handleHit(ChangedEntity mob, ServerPlayer player, DamageSource source, float amount) {
        if (!candidate(mob, player)) return false;
        CompoundTag data = state(mob);
        long now = mob.level().getGameTime();
        boolean soft = isSoftBoundaryHit(mob, player, source, amount);
        boolean competitive = CreaturePersonality.has(mob, CreaturePersonality.Trait.COMPETITIVE);
        boolean repeated = same(data, player) && data.getLong("WindowUntil") > now && data.getInt("Hits") >= 2;
        boolean greeting = HumanIntent.of(mob) == HumanIntent.GREET;
        PoliteHumanInteraction.cancelApproach(mob, player);
        LatexSocialEvents.clearPendingCombatReactions(mob, player);
        if (soft) {
            int hits = same(data, player) && data.getLong("WindowUntil") > now ? data.getInt("Hits") : 0;
            data.putUUID("Player", player.getUUID());
            data.putInt("Hits", hits + 1);
            data.putLong("WindowUntil", now + 1200L);
            data.putLong("RetreatUntil", now + 600L);
            LatexSocialEvents.calmTowards(mob, player);
            SafeEntityMutationQueue.queue(mob, "boundary-calm", () -> {
                if (isBackingOff(mob, player)) LatexSocialEvents.calmTowards(mob, player);
            });
            NpcDialogue.context(mob, player, hits == 1 ? "boundary.last_warning"
                    : greeting ? "boundary.greeting" : "boundary.transfur");
        } else {
            data.remove("RetreatUntil");
            boolean challenge = competitive && source.getDirectEntity() == player
                    && player.getMainHandItem().isEmpty()
                    && amount <= 2.0F && !repeated;
            if (challenge) {
                data.putUUID("Player", player.getUUID());
                data.putLong("ChallengeUntil", now + 1800L);
            } else {
                data.remove("ChallengeUntil");
            }
            LatexSocialMemory.clearWarningGrace(mob, player);
            LatexSocialMemory.clearTruce(mob, player);
            if (!challenge) CreaturePersonality.rememberHarm(mob, player, amount);
            LatexSocialMemory.markProvoked(mob, player);
            if (!challenge) FactionHostilityGrace.noteAggression(mob, player);
            mob.setTarget(player);
            HuntMemory.seeTarget(mob, player);
            NpcDialogue.context(mob, player, amount > 2.0F ? "boundary.heavy"
                    : repeated ? "boundary.repeated"
                    : challenge ? "boundary.competitive" : "boundary.weapon");
        }
        return true;
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof ChangedEntity mob
                && CreatureSocialProfile.allowsSynergySystems(mob)) {
            SafeEntityMutationQueue.queue(mob, "install-boundary-goal", () -> {
                if (mob.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof RetreatGoal))
                    mob.goalSelector.addGoal(-4, new RetreatGoal(mob));
            });
        }
    }

    private static final class RetreatGoal extends Goal {
        private final ChangedEntity mob;
        private ServerPlayer player;
        private int repath;
        private RetreatGoal(ChangedEntity mob) { this.mob = mob; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() {
            CompoundTag data = state(mob);
            player = data.hasUUID("Player") ? mob.getServer().getPlayerList().getPlayer(data.getUUID("Player")) : null;
            return canContinueToUse();
        }
        @Override public boolean canContinueToUse() {
            return player != null && player.isAlive() && player.level() == mob.level()
                    && isBackingOff(mob, player) && !mob.isPassenger() && !mob.isVehicle()
                    && !ChangedAddonCompat.isGrabberBusy(mob)
                    && (mob.getTarget() == null || mob.getTarget() == player);
        }
        @Override public void start() { repath = 0; mob.getNavigation().stop(); }
        @Override public void tick() {
            mob.getLookControl().setLookAt(player);
            if (mob.distanceToSqr(player) >= 49) { mob.getNavigation().stop(); return; }
            if (--repath <= 0) {
                repath = 20;
                var pos = DefaultRandomPos.getPosAway(mob, 7, 3, player.position());
                if (pos != null) mob.getNavigation().moveTo(pos.x, pos.y, pos.z, 0.45D);
            }
        }
        @Override public void stop() { mob.getNavigation().stop(); player = null; }
    }
}
