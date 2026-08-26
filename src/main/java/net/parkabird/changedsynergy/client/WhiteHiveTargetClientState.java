package net.parkabird.changedsynergy.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Short-lived client state prevents entity-ID reuse after world changes. */
public final class WhiteHiveTargetClientState {
    private static final Set<Integer> TARGETS = new HashSet<>();
    private static int expiresAtTick;

    private WhiteHiveTargetClientState() {
    }

    public static void receive(List<Integer> entityIds) {
        TARGETS.clear();
        TARGETS.addAll(entityIds);
        var player = Minecraft.getInstance().player;
        expiresAtTick = player == null ? 0 : player.tickCount + 40;
    }

    public static boolean isMarked(Entity entity) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.level == null
                || minecraft.player.tickCount > expiresAtTick) {
            TARGETS.clear();
            return false;
        }
        return entity.level() == minecraft.level
                && TARGETS.contains(entity.getId());
    }
}
