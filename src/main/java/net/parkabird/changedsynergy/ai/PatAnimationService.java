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
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
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
    private static final float MIN_SPEED = 0.5F;
    private static final float MAX_SPEED = 2.0F;
    private static final float NORMAL_SPEED = 1.0F;
    private static final double MAX_SCROLL_DELTA = 8.0D;
    private static final double SPEED_PER_SCROLL_STEP = 1.1D;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, PendingPat> PENDING = new HashMap<>();

    private PatAnimationService() {
    }

    public static void startFixed(
            LivingEntity actor,
            LivingEntity target,
            int strokes) {
        if (actor.level().isClientSide || !actor.isAlive() || !target.isAlive()
                || invalidDuringTakeover(actor, target)) {
            return;
        }
        long now = actor.level().getGameTime();
        boolean organic = isOrganicTarget(target);
        int baseCycleTicks = organic
                ? ORGANIC_CYCLE_TICKS : LATEX_CYCLE_TICKS;
        float cycleTicks = cycleTicks(baseCycleTicks, NORMAL_SPEED);
        int duration = Math.max(1,
                Math.round(Math.max(1, strokes) * cycleTicks));
        SESSIONS.put(actor.getUUID(), new Session(
                actor,
                target,
                now + duration + 1L,
                now + soundInterval(cycleTicks),
                now,
                false,
                organic,
                baseCycleTicks,
                NORMAL_SPEED,
                cycleTicks));
        sync(actor, true, duration + 1, cycleTicks);
    }

    public static void scheduleFixed(
            LivingEntity actor,
            LivingEntity target,
            int delayTicks,
            int strokes) {
        if (!actor.level().isClientSide) {
            PENDING.put(actor.getUUID(), new PendingPat(actor, target,
                    actor.level().getGameTime() + Math.max(1, delayTicks), strokes));
        }
    }

    public static void refreshContinuous(
            ServerPlayer actor,
            LivingEntity target) {
        if (!actor.isAlive()
                || !target.isAlive()
                || invalidDuringTakeover(actor, target)
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
        int baseCycleTicks = organic
                ? ORGANIC_CYCLE_TICKS : LATEX_CYCLE_TICKS;
        float cycleTicks = cycleTicks(baseCycleTicks, NORMAL_SPEED);
        SESSIONS.put(actor.getUUID(), new Session(
                actor,
                target,
                now + CONTINUOUS_TIMEOUT_TICKS,
                now + soundInterval(cycleTicks),
                now,
                true,
                organic,
                baseCycleTicks,
                NORMAL_SPEED,
                cycleTicks));
        sync(actor, true, CONTINUOUS_TIMEOUT_TICKS, cycleTicks);
    }

    /** Changes only the actor's current pat session; each new pat starts at 1x. */
    public static void adjustSpeed(ServerPlayer actor, double scrollDelta) {
        Session session = SESSIONS.get(actor.getUUID());
        if (session == null || !Double.isFinite(scrollDelta)
                || scrollDelta == 0.0D) {
            return;
        }
        double safeDelta = Math.max(-MAX_SCROLL_DELTA,
                Math.min(MAX_SCROLL_DELTA, scrollDelta));
        float nextSpeed = (float)Math.max(MIN_SPEED, Math.min(MAX_SPEED,
                session.speedMultiplier
                        * Math.pow(SPEED_PER_SCROLL_STEP, safeDelta)));
        if (Math.abs(nextSpeed - session.speedMultiplier) < 0.0001F) {
            return;
        }

        long now = actor.level().getGameTime();
        SpeedBand oldBand = SpeedBand.of(session.speedMultiplier);
        float oldCycleTicks = session.cycleTicks;
        float newCycleTicks = cycleTicks(session.baseCycleTicks, nextSpeed);
        if (!session.continuous) {
            long remaining = Math.max(1L, session.expiresAt - now);
            session.expiresAt = now + Math.max(1L, Math.round(
                    remaining * (double)newCycleTicks / oldCycleTicks));
        }
        session.speedMultiplier = nextSpeed;
        session.cycleTicks = newCycleTicks;
        session.nextSoundAt = now + soundInterval(newCycleTicks);
        session.lastSyncAt = now;
        sync(actor, true,
                (int)Math.max(1L, Math.min(Integer.MAX_VALUE,
                        session.expiresAt - now)),
                newCycleTicks);
        SpeedBand newBand = SpeedBand.of(nextSpeed);
        if (newBand != oldBand && session.target instanceof ChangedEntity target) {
            NpcDialogue.trigger(target, actor, newBand.cue);
        }
    }

    public static void stop(LivingEntity actor) {
        if (SESSIONS.remove(actor.getUUID()) != null) {
            sync(actor, false, 0, LATEX_CYCLE_TICKS);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Iterator<PendingPat> pending = PENDING.values().iterator();
        while (pending.hasNext()) {
            PendingPat pat = pending.next();
            if (!pat.actor.isAlive() || !pat.target.isAlive()
                    || pat.actor.level() != pat.target.level()) {
                pending.remove();
            } else if (pat.actor.level().getGameTime() >= pat.dueTick) {
                pending.remove();
                startFixed(pat.actor, pat.target, pat.strokes);
            }
        }
        if (SESSIONS.isEmpty()) {
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
                    || invalidDuringTakeover(actor, target)
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

    private static int soundInterval(float cycleTicks) {
        // Emit one contact sound only after a complete left-right stroke.
        return Math.max(1, Math.round(cycleTicks));
    }

    private static float cycleTicks(
            int baseCycleTicks,
            float speedMultiplier) {
        return Math.max(2.0F, baseCycleTicks / speedMultiplier);
    }

    private static boolean invalidDuringTakeover(
            LivingEntity actor,
            LivingEntity target) {
        return target instanceof ServerPlayer player
                        && TakeoverService.active(player)
                || actor instanceof ChangedEntity changed
                        && TakeoverService.carrying(changed);
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
            float cycleTicks) {
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
        private final int baseCycleTicks;
        private float speedMultiplier;
        private float cycleTicks;

        private Session(
                LivingEntity actor,
                LivingEntity target,
                long expiresAt,
                long nextSoundAt,
                long lastSyncAt,
                boolean continuous,
                boolean organic,
                int baseCycleTicks,
                float speedMultiplier,
                float cycleTicks) {
            this.actor = actor;
            this.target = target;
            this.expiresAt = expiresAt;
            this.nextSoundAt = nextSoundAt;
            this.lastSyncAt = lastSyncAt;
            this.continuous = continuous;
            this.organic = organic;
            this.baseCycleTicks = baseCycleTicks;
            this.speedMultiplier = speedMultiplier;
            this.cycleTicks = cycleTicks;
        }
    }

    private record PendingPat(
            LivingEntity actor,
            LivingEntity target,
            long dueTick,
            int strokes) {
    }

    private enum SpeedBand {
        VERY_SLOW(Cue.PAT_SPEED_VERY_SLOW),
        SLOW(Cue.PAT_SPEED_SLOW),
        GENTLE(Cue.PAT_SPEED_GENTLE),
        FAST(Cue.PAT_SPEED_FAST),
        VERY_FAST(Cue.PAT_SPEED_VERY_FAST);

        private final Cue cue;

        SpeedBand(Cue cue) {
            this.cue = cue;
        }

        private static SpeedBand of(float speed) {
            if (speed < 0.7F) return VERY_SLOW;
            if (speed < 0.9F) return SLOW;
            if (speed <= 1.2F) return GENTLE;
            if (speed <= 1.6F) return FAST;
            return VERY_FAST;
        }
    }
}
