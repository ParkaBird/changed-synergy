package net.parkabird.changedsynergy.compat;

import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** A visible Instinct body observes the same PvP rules as its real owner. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class TrueTransfurPvpGuard {
    private TrueTransfurPvpGuard() {}

    private static boolean proxy(@Nullable Entity entity) {
        return entity instanceof ChangedEntity body
                && body.getPersistentData().getBoolean("TrueTransfurControlled");
    }

    @Nullable
    private static ServerPlayer owner(@Nullable Entity entity) {
        if (entity instanceof ServerPlayer player) return player;
        return entity instanceof ChangedEntity body && TrueTransfurCompat.isInstinctBody(body)
                ? TrueTransfurCompat.bodyOwner(body) : null;
    }

    public static boolean allows(@Nullable Entity attacker, @Nullable Entity target) {
        boolean attackerProxy = proxy(attacker), targetProxy = proxy(target);
        if (!attackerProxy && !targetProxy) return true;
        ServerPlayer first = owner(attacker), second = owner(target);
        // A stale proxy must not be treated as an ordinary mob attacking a player.
        if ((attackerProxy && first == null || targetProxy && second == null)
                && (first != null || second != null)) return false;
        if (first == null || second == null) return true; // Normal NPC combat.
        return !first.getUUID().equals(second.getUUID()) && first.server == second.server
                && first.server.isPvpAllowed() && first.canHarmPlayer(second);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(LivingAttackEvent event) {
        if (!event.getEntity().level().isClientSide
                && !allows(event.getSource().getEntity(), event.getEntity())) event.setCanceled(true);
    }

    /** Some integrations apply damage directly at the hurt stage. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hurt(LivingHurtEvent event) {
        if (!event.getEntity().level().isClientSide
                && !allows(event.getSource().getEntity(), event.getEntity())) event.setCanceled(true);
    }
}
