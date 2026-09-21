package net.parkabird.changedsynergy.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.BondedSuitService;

@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RevivalGrabAbilityClient {
    private static final Map<Integer, Integer> PENDING = new HashMap<>();

    private RevivalGrabAbilityClient() {
    }

    public static void register(int entityId) {
        PENDING.put(entityId, 100);
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
        Iterator<Map.Entry<Integer, Integer>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            var pending = iterator.next();
            if (level.getEntity(pending.getKey()) instanceof ChangedEntity creature) {
                BondedSuitService.ensureRevivalGrabAbility(creature);
                iterator.remove();
            } else if (pending.getValue() <= 1) {
                iterator.remove();
            } else {
                pending.setValue(pending.getValue() - 1);
            }
        }
    }
}
