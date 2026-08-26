package net.parkabird.changedsynergy.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

/** Viewer-local scout marks; each target expires unless a scout refreshes it. */
public final class ScoutTargetClientState {
    private static final int MARK_LIFETIME_TICKS = 40;
    private static final Map<Integer, Integer> TARGET_EXPIRY = new HashMap<>();
    private static ClientLevel markedLevel;

    private ScoutTargetClientState() {
    }

    public static void receive(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            clear();
            return;
        }
        if (markedLevel != minecraft.level) {
            TARGET_EXPIRY.clear();
            markedLevel = minecraft.level;
        }
        int now = minecraft.player.tickCount;
        TARGET_EXPIRY.entrySet().removeIf(entry -> entry.getValue() < now);
        TARGET_EXPIRY.put(
                entityId,
                now + MARK_LIFETIME_TICKS);
    }

    public static boolean isMarked(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.player == null
                || markedLevel != minecraft.level) {
            clear();
            return false;
        }
        int now = minecraft.player.tickCount;
        Integer expiry = TARGET_EXPIRY.get(entity.getId());
        if (expiry == null) {
            return false;
        }
        if (expiry < now) {
            TARGET_EXPIRY.remove(entity.getId());
            return false;
        }
        return entity.level() == minecraft.level;
    }

    private static void clear() {
        TARGET_EXPIRY.clear();
        markedLevel = null;
    }
}
