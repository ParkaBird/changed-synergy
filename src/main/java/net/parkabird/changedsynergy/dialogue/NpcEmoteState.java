package net.parkabird.changedsynergy.dialogue;

import java.util.concurrent.atomic.AtomicInteger;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.Emote;
import net.ltxprogrammer.changed.init.ChangedParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.EmoteTransitionPacket;

/**
 * Server-authoritative current emotion. Higher-priority reactions replace
 * ambient ones immediately and expired reactions are explicitly cleared.
 */
public final class NpcEmoteState {
    private static final String ROOT = "ChangedSynergyEmotion";
    private static final String SELF_STATE = "Self";
    private static final String TARGET_STATE = "Target";
    private static final String EMOTE = "Emote";
    private static final String PRIORITY = "Priority";
    private static final String TARGET_ID = "TargetId";
    private static final String UNTIL = "Until";
    private static final String TOKEN = "Token";
    private static final AtomicInteger NEXT_TOKEN = new AtomicInteger(1);

    private NpcEmoteState() {
    }

    public static void reset(ChangedEntity speaker) {
        speaker.getPersistentData().remove(ROOT);
    }

    public static boolean show(
            ServerLevel level,
            ChangedEntity speaker,
            LivingEntity target,
            Emote emote,
            int priority,
            int durationTicks,
            boolean forceTransition) {
        long now = level.getGameTime();
        if (!target.isAlive() || target.isRemoved()) {
            return false;
        }
        CompoundTag state = state(speaker, target);
        boolean active = state.getLong(UNTIL) > now;
        int currentPriority = active ? state.getInt(PRIORITY) : Integer.MIN_VALUE;
        int targetId = target.getId();
        if (active && priority < currentPriority) {
            return false;
        }
        if (active
                && !forceTransition
                && priority == currentPriority
                && state.getInt(EMOTE) == emote.ordinal()
                && state.getInt(TARGET_ID) == targetId) {
            state.putLong(UNTIL, Math.max(
                    state.getLong(UNTIL), now + Math.max(1, durationTicks)));
            return true;
        }

        endTransition(
                level,
                speaker,
                state.getInt(TARGET_ID),
                state.getInt(TOKEN));
        int token = nextToken();
        state.putInt(EMOTE, emote.ordinal());
        state.putInt(PRIORITY, priority);
        state.putInt(TARGET_ID, targetId);
        state.putLong(UNTIL, now + Math.max(1, durationTicks));
        state.putInt(TOKEN, token);

        beginTransition(target, token);
        level.sendParticles(
                ChangedParticles.emote(target, emote),
                target.getX(), target.getY() + target.getBbHeight() + 0.65D, target.getZ(),
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        return true;
    }

    public static void tick(ChangedEntity speaker) {
        if (!(speaker.level() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag persistent = speaker.getPersistentData();
        if (!persistent.contains(ROOT, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag root = persistent.getCompound(ROOT);
        tickSlot(level, speaker, root, SELF_STATE);
        tickSlot(level, speaker, root, TARGET_STATE);
        if (!root.contains(SELF_STATE, Tag.TAG_COMPOUND)
                && !root.contains(TARGET_STATE, Tag.TAG_COMPOUND)) {
            persistent.remove(ROOT);
        }
    }

    private static void tickSlot(
            ServerLevel level,
            ChangedEntity speaker,
            CompoundTag root,
            String slot) {
        if (!root.contains(slot, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag state = root.getCompound(slot);
        Entity target = level.getEntity(state.getInt(TARGET_ID));
        boolean targetValid = target instanceof LivingEntity living
                ? living.isAlive() && !living.isRemoved()
                : target != null && !target.isRemoved();
        if (state.getLong(UNTIL) > level.getGameTime() && targetValid) {
            return;
        }
        endTransition(
                level,
                speaker,
                state.getInt(TARGET_ID),
                state.getInt(TOKEN));
        root.remove(slot);
    }

    public static void clear(ChangedEntity speaker) {
        if (!(speaker.level() instanceof ServerLevel level)) {
            speaker.getPersistentData().remove(ROOT);
            return;
        }
        CompoundTag persistent = speaker.getPersistentData();
        if (!persistent.contains(ROOT, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag root = persistent.getCompound(ROOT);
        clearSlot(level, speaker, root, SELF_STATE);
        clearSlot(level, speaker, root, TARGET_STATE);
        persistent.remove(ROOT);
    }

    private static void clearSlot(
            ServerLevel level,
            ChangedEntity speaker,
            CompoundTag root,
            String slot) {
        if (!root.contains(slot, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag state = root.getCompound(slot);
        endTransition(
                level,
                speaker,
                state.getInt(TARGET_ID),
                state.getInt(TOKEN));
        root.remove(slot);
    }

    private static void endTransition(
            ServerLevel level,
            ChangedEntity speaker,
            int targetId,
            int token) {
        if (targetId <= 0 || token <= 0) {
            return;
        }
        Entity target = level.getEntity(targetId);
        if (target != null) {
            sendEnd(target, token);
        } else {
            ChangedSynergyNetwork.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> speaker),
                    new EmoteTransitionPacket(targetId, token, false));
        }
    }

    private static void beginTransition(Entity target, int token) {
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new EmoteTransitionPacket(target.getId(), token, true));
    }

    private static void sendEnd(Entity target, int token) {
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new EmoteTransitionPacket(target.getId(), token, false));
    }

    private static int nextToken() {
        return NEXT_TOKEN.updateAndGet(current ->
                current == Integer.MAX_VALUE ? 1 : current + 1);
    }

    private static CompoundTag state(
            ChangedEntity speaker,
            LivingEntity target) {
        CompoundTag persistent = speaker.getPersistentData();
        if (!persistent.contains(ROOT, Tag.TAG_COMPOUND)) {
            persistent.put(ROOT, new CompoundTag());
        }
        CompoundTag root = persistent.getCompound(ROOT);
        String slot = target == speaker ? SELF_STATE : TARGET_STATE;
        if (!root.contains(slot, Tag.TAG_COMPOUND)) {
            root.put(slot, new CompoundTag());
        }
        return root.getCompound(slot);
    }
}
