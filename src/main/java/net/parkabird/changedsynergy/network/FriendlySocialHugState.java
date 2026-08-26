package net.parkabird.changedsynergy.network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small client-side marker shared with the common grab mixin. Keeping this
 * class free of client-only Minecraft types also makes dedicated-server class
 * loading safe.
 */
public final class FriendlySocialHugState {
    private static final Map<Integer, Entry> ACTIVE = new ConcurrentHashMap<>();

    private FriendlySocialHugState() {
    }

    public static void update(
            int grabberId,
            int grabbedId,
            boolean active,
            int durationTicks) {
        if (!active) {
            ACTIVE.remove(grabberId);
            return;
        }
        long durationNanos =
                Math.max(1L, Math.min(600L, durationTicks)) * 50_000_000L;
        ACTIVE.put(
                grabberId,
                new Entry(grabbedId, System.nanoTime() + durationNanos));
    }

    public static boolean isActive(int grabberId, int grabbedId) {
        Entry entry = ACTIVE.get(grabberId);
        if (entry == null) {
            return false;
        }
        if (entry.expiresAtNanos() <= System.nanoTime()) {
            ACTIVE.remove(grabberId, entry);
            return false;
        }
        return entry.grabbedId() == grabbedId;
    }

    private record Entry(int grabbedId, long expiresAtNanos) {
    }
}
