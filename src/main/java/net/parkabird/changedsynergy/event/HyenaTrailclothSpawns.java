package net.parkabird.changedsynergy.event;

import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.entity.AccessoryEntities;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.entity.LatexSpottedHyena;
import net.parkabird.changedsynergy.init.ChangedSynergyItems;

@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class HyenaTrailclothSpawns {
    private static final String ROLLED = "ChangedSynergyTrailclothRolled";
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void spawned(MobSpawnEvent.FinalizeSpawn event) {
        if (!(event.getEntity() instanceof LatexSpottedHyena hyena) || event.isSpawnCancelled()
                || !(event.getSpawnType() == MobSpawnType.NATURAL || event.getSpawnType() == MobSpawnType.CHUNK_GENERATION
                    || event.getSpawnType() == MobSpawnType.STRUCTURE)) return;
        roll(hyena);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void structureResident(EntityJoinLevelEvent event) {
        // Template residents are loaded from NBT without vanilla spawn finalization.
        if (!event.getLevel().isClientSide() && !event.loadedFromDisk() && event.getEntity() instanceof LatexSpottedHyena hyena
                && hyena.getTags().contains("changed_synergy:hyena_structure_resident")) roll(hyena);
    }

    private static void roll(LatexSpottedHyena hyena) {
        if(net.parkabird.changedsynergy.ai.NaturalCampService.smallCamp(hyena))return;
        if (hyena.getPersistentData().getBoolean(ROLLED)) return;
        hyena.getPersistentData().putBoolean(ROLLED, true);
        if (hyena.getRandom().nextDouble() >= ChangedSynergyConfig.COMMON.hyenaTrailclothSpawnChance.get()) return;
        AccessoryEntities.INSTANCE.forceReloadAccessories(hyena);
        AccessorySlots.getForEntity(hyena).ifPresent(slots -> {
            if (slots.getItem(ChangedAccessorySlots.BODY.get()).map(ItemStack::isEmpty).orElse(false))
                slots.quickMoveStack(new ItemStack(ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get()));
        });
    }
}
