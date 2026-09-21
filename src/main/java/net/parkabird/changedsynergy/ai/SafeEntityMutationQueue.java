package net.parkabird.changedsynergy.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Runs entity mutations after every GoalSelector has finished ticking. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SafeEntityMutationQueue {
    private static final Map<MutationKey, PendingMutation> PENDING =
            new LinkedHashMap<>();

    private SafeEntityMutationQueue() {
    }

    /** Replaces an older pending operation of the same kind for this entity. */
    public static void queue(Entity entity, String kind, Runnable operation) {
        if (entity == null || kind == null || operation == null) {
            return;
        }
        synchronized (PENDING) {
            PENDING.put(
                    new MutationKey(entity.getUUID(), kind),
                    new PendingMutation(entity, operation));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Map<MutationKey, PendingMutation> pending;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) {
                return;
            }
            pending = new LinkedHashMap<>(PENDING);
            PENDING.clear();
        }
        pending.values().forEach(PendingMutation::runIfPresent);
    }

    private record MutationKey(UUID entity, String kind) {
    }

    private record PendingMutation(Entity entity, Runnable operation) {
        private void runIfPresent() {
            if (!entity.isRemoved()) {
                operation.run();
            }
        }
    }
}
