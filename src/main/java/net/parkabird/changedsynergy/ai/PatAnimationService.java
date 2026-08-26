package net.parkabird.changedsynergy.ai;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.init.ChangedSynergySoundEvents;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.PatAnimationPacket;

/** Repeats a gentle brush-like pat stroke without repeating relationship effects. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PatAnimationService {
    private static final int ORGANIC_CYCLE_TICKS = 8;
    private static final int LATEX_CYCLE_TICKS = 10;
    private static final int CONTINUOUS_TIMEOUT_TICKS = 16;
    private static final int CONTINUOUS_SYNC_TICKS = 6;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private PatAnimationService() {
    }

    public static void startFixed(
            LivingEntity actor,
            LivingEntity target,
            int strokes) {
        if (actor.level().isClientSide || !actor.isAlive() || !target.isAlive()) {
            return;
        }
        long now = actor.level().getGameTime();
        boolean organic = isOrganicTarget(target);
        int cycleTicks = organic
                ? ORGANIC_CYCLE_TICKS : LATEX_CYCLE_TICKS;
        int duration = Math.max(1, strokes) * cycleTicks;
        SESSIONS.put(actor.getUUID(), new Session(
                actor,
                target,
                now + duration + 1L,
                now + soundInterval(cycleTicks),
                now,
                false,
                organic,
                cycleTicks));
        sync(actor, true, duration + 1, cycleTicks);
    }

    public static void refreshContinuous(
            ServerPlayer actor,
            LivingEntity target) {
        if (!actor.isAlive()
                || !target.isAlive()
                || actor.distanceToSqr(target) > 36.0D
                || !actor.hasLineOfSight(target)) {
            stop(actor);
            return;
        }
        long now = actor.level().getGameTime();
        Session current = SESSIONS.get(actor.getUUID());
        if (current != null
                && current.continuous
                && current.target == target) {
            current.expiresAt = now + CONTINUOUS_TIMEOUT_TICKS;
            if (now - current.lastSyncAt >= CONTINUOUS_SYNC_TICKS) {
                current.lastSyncAt = now;
                sync(actor, true, CONTINUOUS_TIMEOUT_TICKS, current.cycleTicks);
            }
            return;
        }
        boolean organic = isOrganicTarget(target);
        int cycleTicks = organic
                ? ORGANIC_CYCLE_TICKS : LATEX_CYCLE_TICKS;
        SESSIONS.put(actor.getUUID(), new Session(
                actor,
                target,
                now + CONTINUOUS_TIMEOUT_TICKS,
                now + soundInterval(cycleTicks),
                now,
                true,
                organic,
                cycleTicks));
        sync(actor, true, CONTINUOUS_TIMEOUT_TICKS, cycleTicks);
    }

    public static void stop(LivingEntity actor) {
        if (SESSIONS.remove(actor.getUUID()) != null) {
            sync(actor, false, 0, LATEX_CYCLE_TICKS);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) {
            return;
        }
        Iterator<Session> iterator = SESSIONS.values().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            LivingEntity actor = session.actor;
            LivingEntity target = session.target;
            long now = actor.level().getGameTime();
            if (!actor.isAlive()
                    || !target.isAlive()
                    || actor.level() != target.level()
                    || actor.distanceToSqr(target) > 49.0D
                    || now >= session.expiresAt) {
                iterator.remove();
                sync(actor, false, 0, session.cycleTicks);
                continue;
            }
            if (now < session.nextSoundAt) {
                continue;
            }
            int interval = soundInterval(session.cycleTicks);
            do {
                session.nextSoundAt += interval;
            } while (session.nextSoundAt <= now);
            if (session.organic) {
                playContactSound(
                        target,
                        ChangedSynergySoundEvents.PAT_ORGANIC.get(),
                        0.38F,
                        1.02F);
            } else {
                // Keep the sticky layer in the background while the hand
                // movement remains the clearer part of the contact sound.
                playContactSound(
                        target,
                        ChangedSynergySoundEvents.PAT_LATEX_STICKY.get(),
                        0.20F,
                        0.88F);
                playContactSound(
                        target,
                        ChangedSynergySoundEvents.PAT_LATEX_BRUSH.get(),
                        0.50F,
                        1.00F);
            }
        }
    }

    private static void playContactSound(
            LivingEntity target,
            SoundEvent sound,
            float volume,
            float basePitch) {
        target.level().playSound(
                null,
                target.getX(), target.getY(0.7D), target.getZ(),
                sound,
                SoundSource.PLAYERS,
                volume,
                basePitch + target.getRandom().nextFloat() * 0.08F);
    }

    private static int soundInterval(int cycleTicks) {
        // Emit one contact sound only after a complete left-right stroke.
        return Math.max(1, cycleTicks);
    }

    private static boolean isOrganicTarget(LivingEntity target) {
        if (target instanceof ChangedEntity changed) {
            return LatexSocialMemory.isOrganic(changed);
        }
        if (target instanceof ServerPlayer player) {
            return ProcessTransfur.getPlayerTransfurVariantSafe(player)
                    .map(instance -> LatexSocialMemory.isOrganic(
                            instance.getChangedEntity()))
                    .orElse(true);
        }
        return true;
    }

    private static void sync(
            LivingEntity actor,
            boolean active,
            int duration,
            int cycleTicks) {
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> actor),
                new PatAnimationPacket(
                        actor.getId(), active, duration, cycleTicks));
    }

    private static final class Session {
        private final LivingEntity actor;
        private final LivingEntity target;
        private long expiresAt;
        private long nextSoundAt;
        private long lastSyncAt;
        private final boolean continuous;
        private final boolean organic;
        private final int cycleTicks;

        private Session(
                LivingEntity actor,
                LivingEntity target,
                long expiresAt,
                long nextSoundAt,
                long lastSyncAt,
                boolean continuous,
                boolean organic,
                int cycleTicks) {
            this.actor = actor;
            this.target = target;
            this.expiresAt = expiresAt;
            this.nextSoundAt = nextSoundAt;
            this.lastSyncAt = lastSyncAt;
            this.continuous = continuous;
            this.organic = organic;
            this.cycleTicks = cycleTicks;
        }
    }
}
