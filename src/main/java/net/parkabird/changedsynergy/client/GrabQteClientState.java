package net.parkabird.changedsynergy.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.ltxprogrammer.changed.ability.AbstractAbility;
import net.ltxprogrammer.changed.ability.AbstractAbilityInstance.KeyReference;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.network.GrabQteSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Applies QTE key sync packets after the corresponding grab packet is ready. */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class GrabQteClientState {
    private static final Map<Integer, Pending> PENDING = new HashMap<>();

    private GrabQteClientState() {
    }

    public static void receive(GrabQteSyncPacket packet) {
        Pending pending = new Pending(packet, clientGameTime());
        if (!apply(packet)) {
            PENDING.put(packet.grabberId(), pending);
        } else {
            PENDING.remove(packet.grabberId());
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().level == null) {
            return;
        }
        long now = clientGameTime();
        Iterator<Pending> iterator = PENDING.values().iterator();
        while (iterator.hasNext()) {
            Pending pending = iterator.next();
            if (now - pending.receivedAt() > 40L || apply(pending.packet())) {
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        PENDING.clear();
    }

    private static boolean apply(GrabQteSyncPacket packet) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }
        Entity source = level.getEntity(packet.grabberId());
        Entity target = level.getEntity(packet.grabbedId());
        if (!(source instanceof LivingEntity grabber)
                || !(target instanceof LivingEntity grabbed)) {
            return false;
        }
        GrabEntityAbilityInstance ability = AbstractAbility.getAbilityInstance(
                grabber, ChangedAbilities.GRAB_ENTITY_ABILITY.get());
        if (ability == null || ability.grabbedEntity != grabbed) {
            return false;
        }
        ability.currentEscapeKey = decodeKey(packet.currentKey());
        ability.lastEscapeKey = decodeKey(packet.lastKey());
        ability.ticksUnpressed = Math.max(0, packet.ticksUnpressed());
        return true;
    }

    private static KeyReference decodeKey(int ordinal) {
        KeyReference[] values = KeyReference.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    private static long clientGameTime() {
        return Minecraft.getInstance().level == null
                ? 0L : Minecraft.getInstance().level.getGameTime();
    }

    private record Pending(GrabQteSyncPacket packet, long receivedAt) {
    }
}
