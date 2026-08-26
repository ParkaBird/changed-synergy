package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.util.EntityUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/** Gives a player a reliable safety window after winning a grab escape QTE. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GrabEscapeStunService {
    private static final int STUN_TICKS = 60;
    private static final String STUN_UNTIL = "ChangedSynergyGrabEscapeStunUntil";

    private GrabEscapeStunService() {
    }

    public static void stun(Mob grabber, ServerPlayer escapedPlayer) {
        if (!ChangedSynergyGameRules.enabled(
                        grabber.level(),
                        ChangedSynergyGameRules.GRAB_QTE_ENHANCEMENTS)
                || grabber.level().isClientSide
                || !grabber.isAlive()) {
            return;
        }
        grabber.getPersistentData().putLong(
                STUN_UNTIL, grabber.level().getGameTime() + STUN_TICKS);
        suppress(grabber);
        if (grabber instanceof ChangedEntity changed) {
            HuntMemory.clear(changed);
        }
    }

    public static boolean isStunned(Mob mob) {
        if (!ChangedSynergyGameRules.enabled(
                mob.level(), ChangedSynergyGameRules.GRAB_QTE_ENHANCEMENTS)) {
            mob.getPersistentData().remove(STUN_UNTIL);
            return false;
        }
        long until = mob.getPersistentData().getLong(STUN_UNTIL);
        if (until > mob.level().getGameTime()) {
            return true;
        }
        if (until != 0L) {
            mob.getPersistentData().remove(STUN_UNTIL);
        }
        return false;
    }

    public static boolean shouldSuppressAttack(Mob attacker, LivingEntity target) {
        return isStunned(attacker);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Mob mob
                && !mob.level().isClientSide
                && isStunned(mob)) {
            suppress(mob);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob mob
                && !mob.level().isClientSide
                && isStunned(mob)
                && event.getNewTarget() != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof Mob mob
                && !mob.level().isClientSide
                && isStunned(mob)) {
            event.setCanceled(true);
        }
    }

    private static void suppress(Mob mob) {
        EntityUtil.setNoControlTicks(mob, 2);
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.getNavigation().stop();
    }
}
