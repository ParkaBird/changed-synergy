package net.parkabird.changedsynergy.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.entity.Entity;

/** Keeps at most one live Changed emote bubble over each entity. */
public final class EmoteClientState {
    private static final long PENDING_LIFETIME_NANOS = 2_000_000_000L;
    private static final Map<Integer, ActiveEmote> ACTIVE = new HashMap<>();
    private static final Map<Integer, PendingEmote> PENDING = new HashMap<>();

    private EmoteClientState() {
    }

    public static void begin(int entityId, int token) {
        ActiveEmote previous = ACTIVE.remove(entityId);
        if (previous != null) {
            previous.particle().remove();
        }
        PENDING.put(entityId, new PendingEmote(token, System.nanoTime()));
    }

    public static void end(int entityId, int token) {
        PendingEmote pending = PENDING.get(entityId);
        if (pending != null && pending.token() == token) {
            PENDING.remove(entityId);
        }
        ActiveEmote active = ACTIVE.get(entityId);
        if (active != null && active.token() == token) {
            ACTIVE.remove(entityId);
            active.particle().remove();
        }
    }

    public static void register(Entity target, Particle particle) {
        int entityId = target.getId();
        PendingEmote pending = PENDING.remove(entityId);
        int token = pending != null
                        && System.nanoTime() - pending.createdNanos()
                                <= PENDING_LIFETIME_NANOS
                ? pending.token()
                : 0;
        ActiveEmote previous = ACTIVE.put(
                entityId, new ActiveEmote(particle, token));
        if (previous != null && previous.particle() != particle) {
            previous.particle().remove();
        }
    }

    public static boolean isCurrent(Entity target, Particle particle) {
        ActiveEmote active = ACTIVE.get(target.getId());
        return active != null && active.particle() == particle;
    }

    public static void release(Entity target, Particle particle) {
        ActiveEmote active = ACTIVE.get(target.getId());
        if (active != null && active.particle() == particle) {
            ACTIVE.remove(target.getId());
        }
    }

    private record ActiveEmote(Particle particle, int token) {
    }

    private record PendingEmote(int token, long createdNanos) {
    }
}
