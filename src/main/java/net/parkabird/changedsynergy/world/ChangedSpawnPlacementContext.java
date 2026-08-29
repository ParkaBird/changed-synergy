package net.parkabird.changedsynergy.world;

import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.registries.ForgeRegistries;

/** Limits the daylight exception to one active Changed spawn-rule check. */
public final class ChangedSpawnPlacementContext {
    /** Roughly one daylight surface spawn for every seven valid attempts. */
    private static final float DAYLIGHT_SURFACE_PASS_CHANCE = 0.15F;

    private static final ThreadLocal<ArrayDeque<Boolean>> ACTIVE =
            ThreadLocal.withInitial(ArrayDeque::new);

    private ChangedSpawnPlacementContext() {
    }

    public static void push(EntityType<?> type, MobSpawnType reason) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        boolean natural = reason == MobSpawnType.NATURAL
                || reason == MobSpawnType.CHUNK_GENERATION;
        boolean changedCreature = id != null
                && ("changed".equals(id.getNamespace())
                        || "changed_addon".equals(id.getNamespace())
                        || "changed_additions".equals(id.getNamespace()));
        ACTIVE.get().push(natural && changedCreature);
    }

    public static void pop() {
        ArrayDeque<Boolean> stack = ACTIVE.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            ACTIVE.remove();
        }
    }

    public static boolean allowsDaylight() {
        ArrayDeque<Boolean> stack = ACTIVE.get();
        return !stack.isEmpty() && stack.peek();
    }

    /**
     * Adds a small daylight-only surface exception without touching native
     * darkness checks underground. Cave spawn rates therefore remain exactly
     * those supplied by Changed/Changed Addon regardless of time of day.
     */
    public static boolean passesDaylightSurfaceException(
            ServerLevelAccessor level,
            BlockPos position,
            RandomSource random) {
        return allowsDaylight()
                && level.getLevel().isDay()
                && level.canSeeSky(position)
                && random.nextFloat() < DAYLIGHT_SURFACE_PASS_CHANCE;
    }
}
