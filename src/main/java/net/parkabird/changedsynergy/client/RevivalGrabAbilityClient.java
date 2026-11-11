package net.parkabird.changedsynergy.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.LivingEntityDataExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.network.FriendlySocialHugState;

@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RevivalGrabAbilityClient {
    private record Pending(int targetId, int ticks) {}
    private static final Map<Integer, Pending> PENDING = new HashMap<>();

    private RevivalGrabAbilityClient() {
    }

    public static void register(int entityId) {
        PENDING.putIfAbsent(entityId, new Pending(-1, 100));
    }

    /** Forge entity spawning can finish after Changed's native ARMS packet is handled. */
    public static void socialHold(int entityId, int targetId, boolean active) {
        if (active) PENDING.put(entityId, new Pending(targetId, 100));
        else PENDING.computeIfPresent(entityId, (id, pending) -> pending.targetId() == targetId ? null : pending);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        PENDING.clear();
        FriendlySocialHugState.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        var level = Minecraft.getInstance().level;
        if (level == null) {
            PENDING.clear();
            return;
        }
        Iterator<Map.Entry<Integer, Pending>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            var pending = iterator.next();
            Pending state = pending.getValue();
            if (state.targetId() >= 0 && !FriendlySocialHugState.isActive(pending.getKey(), state.targetId())) {
                iterator.remove();
                continue;
            }
            if (level.getEntity(pending.getKey()) instanceof ChangedEntity creature) {
                BondedSuitService.ensureGrabAbility(creature);
                var grab = BondedSuitService.ability(creature);
                if (state.targetId() < 0) {
                    iterator.remove();
                    continue;
                }
                if (level.getEntity(state.targetId()) instanceof LivingEntity target) {
                    // Reconcile once, never reapply a hold after a later native release.
                    if (grab.grabbedEntity == null) grab.grabEntity(target);
                    if (grab.grabbedEntity == target) {
                        grab.suited = grab.grabbedHasControl = false;
                        if (target instanceof LivingEntityDataExtension data) data.setGrabbedBy(creature);
                    }
                    iterator.remove();
                    continue;
                }
            }
            if (state.ticks() <= 1) {
                iterator.remove();
            } else {
                pending.setValue(new Pending(state.targetId(), state.ticks() - 1));
            }
        }
    }
}
