package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/**
 * Owns provisioner work-tool presentation independently of an AI goal object.
 * Goal selectors can be rebuilt by compatibility layers while a task is active;
 * a short heartbeat lets us restore the original hand item even when the old
 * goal never receives {@code stop()}.
 */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GatheringToolPresentation {
    private static final String ROOT = "ChangedSynergyGatheringTool";
    private static final String ORIGINAL = "Original";
    private static final String LAST_HEARTBEAT = "LastHeartbeat";
    private static final String TEMPORARY_TOOL =
            "ChangedSynergyTemporaryGatheringTool";
    private static final long STALE_AFTER_TICKS = 3L;

    private GatheringToolPresentation() {
    }

    public static void show(ChangedEntity creature, ItemStack requestedTool) {
        if (requestedTool.isEmpty() || creature.level().isClientSide) {
            return;
        }
        CompoundTag persistent = creature.getPersistentData();
        if (persistent.contains(ROOT)) {
            restore(creature);
        }

        CompoundTag state = new CompoundTag();
        state.put(ORIGINAL, creature.getItemBySlot(EquipmentSlot.MAINHAND)
                .save(new CompoundTag()));
        state.putLong(LAST_HEARTBEAT, creature.level().getGameTime());
        persistent.put(ROOT, state);

        ItemStack visual = requestedTool.copy();
        visual.setCount(1);
        visual.getOrCreateTag().putBoolean(TEMPORARY_TOOL, true);
        creature.setItemSlot(EquipmentSlot.MAINHAND, visual);
    }

    public static void heartbeat(ChangedEntity creature) {
        CompoundTag persistent = creature.getPersistentData();
        if (persistent.contains(ROOT)) {
            persistent.getCompound(ROOT).putLong(
                    LAST_HEARTBEAT, creature.level().getGameTime());
        }
    }

    public static void restore(ChangedEntity creature) {
        CompoundTag persistent = creature.getPersistentData();
        if (!persistent.contains(ROOT)) {
            return;
        }
        CompoundTag state = persistent.getCompound(ROOT);
        ItemStack current = creature.getItemBySlot(EquipmentSlot.MAINHAND);
        // Do not overwrite equipment deliberately installed by another system.
        // Only replace the temporary presentation item that this service owns.
        if (current.hasTag()
                && current.getTag().getBoolean(TEMPORARY_TOOL)) {
            ItemStack original = ItemStack.of(state.getCompound(ORIGINAL));
            creature.setItemSlot(EquipmentSlot.MAINHAND, original);
        }
        persistent.remove(ROOT);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity creature)
                || creature.level().isClientSide) {
            return;
        }
        CompoundTag persistent = creature.getPersistentData();
        if (!persistent.contains(ROOT)) {
            return;
        }
        long now = creature.level().getGameTime();
        long heartbeat = persistent.getCompound(ROOT)
                .getLong(LAST_HEARTBEAT);
        if (heartbeat > now || now - heartbeat > STALE_AFTER_TICKS) {
            if (creature.level() instanceof ServerLevel level) {
                FishingVisualEffects.cancel(level, creature);
            }
            restore(creature);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ChangedEntity creature
                && !creature.level().isClientSide) {
            restore(creature);
        }
    }
}
