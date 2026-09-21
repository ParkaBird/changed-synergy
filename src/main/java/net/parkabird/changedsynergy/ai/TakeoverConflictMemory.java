package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Records responsibility before social hurt handlers turn the victim hostile. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class TakeoverConflictMemory {
    private static final String PENDING = "SynergyTakeoverPendingAttack";
    private static final String CASES = "SynergyTakeoverInitiators";
    private TakeoverConflictMemory() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity creature)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        CompoundTag pending = new CompoundTag();
        pending.putUUID("Player", player.getUUID());
        pending.putLong("Tick", creature.level().getGameTime());
        // Use the boundary service's complete hit-count/personality check. Merely
        // checking RetreatUntil made a third repeated punch look like another tolerated
        // warning even though that same hit immediately provoked retaliation.
        boolean boundary = HumanBoundaryService.isSoftBoundaryHit(
                creature, player, event.getSource(), event.getAmount());
        pending.putBoolean("Initiated", event.getAmount() > 0 && !boundary
                && creature.getTarget() != player && player.getLastHurtByMob() != creature
                && !LatexSocialMemory.isProvoked(creature, player)
                && !FactionReputation.isHostile(creature, player));
        creature.getPersistentData().put(PENDING, pending);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterDamage(LivingDamageEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0
                || !(event.getEntity() instanceof ChangedEntity creature)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        CompoundTag root = creature.getPersistentData();
        CompoundTag pending = root.getCompound(PENDING);
        if (!pending.hasUUID("Player") || !pending.getUUID("Player").equals(player.getUUID())
                || pending.getLong("Tick") != creature.level().getGameTime()) return;
        root.remove(PENDING);
        if (!pending.getBoolean("Initiated")) return;
        CompoundTag cases = root.getCompound(CASES);
        long tick = creature.level().getGameTime();
        for (String key : java.util.Set.copyOf(cases.getAllKeys()))
            if (cases.getLong(key) <= tick) cases.remove(key);
        cases.putLong(player.getUUID().toString(), tick + 6000);
        root.put(CASES, cases);
    }

    public static boolean initiated(ChangedEntity creature, ServerPlayer player) {
        return creature.getPersistentData().getCompound(CASES).getLong(player.getUUID().toString())
                > creature.level().getGameTime();
    }
}
