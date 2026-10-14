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
    private static final String DEFENSE = "SynergySelfDefenseCases";
    private TakeoverConflictMemory() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeAttack(LivingAttackEvent event) {
        if (event.getSource().is(net.ltxprogrammer.changed.init.ChangedDamageSources.GRAB_ESCAPE.key())
                || !(event.getEntity() instanceof ChangedEntity creature)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || PlayfulFriendTransfurGoal.isDefense(creature, player)) return;
        CompoundTag pending = new CompoundTag();
        pending.putUUID("Player", player.getUUID());
        pending.putLong("Tick", creature.level().getGameTime());
        boolean defense = defending(creature, player);
        pending.putBoolean("Defense", defense);
        // Use the boundary service's complete hit-count/personality check. Merely
        // checking RetreatUntil made a third repeated punch look like another tolerated
        // warning even though that same hit immediately provoked retaliation.
        boolean boundary = HumanBoundaryService.isSoftBoundaryHit(
                creature, player, event.getSource(), event.getAmount());
        pending.putBoolean("Initiated", event.getAmount() > 0 && !boundary
                && !defense);
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
        if (pending.getBoolean("Defense")) {
            remember(root, DEFENSE, player, creature.level().getGameTime() + 600L);
            return;
        }
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

    /** Read before hurt/death handlers assign retaliation targets. */
    public static boolean protectsSelfDefense(ChangedEntity creature, ServerPlayer player) {
        if (!net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.selfDefensePenaltyProtection.get()) return false;
        CompoundTag pending = creature.getPersistentData().getCompound(PENDING);
        if (pending.hasUUID("Player") && pending.getUUID("Player").equals(player.getUUID())
                && pending.getLong("Tick") == creature.level().getGameTime()) return pending.getBoolean("Defense");
        return defending(creature, player);
    }

    private static boolean defending(ChangedEntity creature, ServerPlayer player) {
        if (initiated(creature, player) || LatexSocialMemory.isTruced(creature, player)) return false;
        if (creature.getPersistentData().getCompound(DEFENSE).getLong(player.getUUID().toString())
                > creature.level().getGameTime()) return true;
        var body = net.parkabird.changedsynergy.compat.TrueTransfurCompat.interactionBody(player);
        return creature.getTarget() == body
                || body.getLastHurtByMob() == creature && body.tickCount - body.getLastHurtByMobTimestamp() < 600;
    }

    public static void clear(ChangedEntity creature, ServerPlayer player) {
        var root = creature.getPersistentData();
        root.getCompound(CASES).remove(player.getStringUUID());
        root.getCompound(DEFENSE).remove(player.getStringUUID());
        var pending = root.getCompound(PENDING);
        if (pending.hasUUID("Player") && player.getUUID().equals(pending.getUUID("Player"))) root.remove(PENDING);
    }

    private static void remember(CompoundTag root, String key, ServerPlayer player, long until) {
        CompoundTag cases = root.getCompound(key);
        long now = player.level().getGameTime();
        for (String id : java.util.Set.copyOf(cases.getAllKeys())) if (cases.getLong(id) <= now) cases.remove(id);
        cases.putLong(player.getUUID().toString(), until);
        root.put(key, cases);
    }
}
