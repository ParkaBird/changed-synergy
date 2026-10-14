package net.parkabird.changedsynergy.origin;

import net.ltxprogrammer.changed.init.ChangedDamageSources;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.FactionReputation;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class NativeOriginCombatEvents {
    private NativeOriginCombatEvents() {}
    private static ServerPlayer player(Entity entity) {
        if (entity instanceof ServerPlayer player) return player;
        return entity instanceof ChangedEntity body && TrueTransfurCompat.isInstinctBody(body)
                ? TrueTransfurCompat.bodyOwner(body) : null;
    }
    private static boolean eligible(ServerPlayer victim, ServerPlayer attacker) {
        if (victim == null || attacker == null) return false;
        var attackerBody = TrueTransfurCompat.interactionBody(attacker);
        var victimBody = TrueTransfurCompat.interactionBody(victim);
        return !attacker.getUUID().equals(victim.getUUID()) && NativeOriginService.isNative(victim) && victim.server.isPvpAllowed()
                && victim.server.overworld().getGameRules().getBoolean(ChangedSynergyGameRules.NATIVE_PVP_REPUTATION)
                && !(attackerBody.getLastHurtByMob() == victimBody
                        && attackerBody.tickCount - attackerBody.getLastHurtByMobTimestamp() < 200);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hurt(LivingDamageEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0 || event.getSource().is(ChangedDamageSources.GRAB_ESCAPE.key())) return;
        ServerPlayer victim = player(event.getEntity()), attacker = player(event.getSource().getEntity());
        if (eligible(victim, attacker)) {
            var tag = victim.getPersistentData();
            String key = "SynergyNativeHit:" + attacker.getStringUUID();
            long now = victim.server.overworld().getGameTime();
            if (tag.contains(key) && now - tag.getLong(key) < 40) return;
            tag.putLong(key, now);
            FactionReputation.damageNativeMember(attacker, NativeOriginService.account(NativeOriginService.identity(victim)), -2);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void killed(LivingDeathEvent event) {
        if (event.isCanceled() || event.getSource().is(ChangedDamageSources.GRAB_ESCAPE.key())) return;
        // TrueTransfur forwards physical-body death to ServerPlayer#die. Count only
        // that event, while still attributing an Instinct attacker to the real owner.
        ServerPlayer attacker = player(event.getSource().getEntity());
        if (event.getEntity() instanceof ServerPlayer victim && eligible(victim, attacker)) FactionReputation.damageNativeMember(attacker,
                        NativeOriginService.account(NativeOriginService.identity(victim)), -15);
    }
}
