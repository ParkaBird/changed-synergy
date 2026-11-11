package net.parkabird.changedsynergy.event;

import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.entity.AccessoryEntities;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.entity.LatexEagleOwl;
import net.parkabird.changedsynergy.init.ChangedSynergyItems;

@Mod.EventBusSubscriber(modid="changed_synergy")
public final class EagleOwlOutfitSpawns {
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void spawn(MobSpawnEvent.FinalizeSpawn event){
        if(!(event.getEntity() instanceof LatexEagleOwl owl)||event.isSpawnCancelled()||!(event.getSpawnType()==MobSpawnType.NATURAL||event.getSpawnType()==MobSpawnType.CHUNK_GENERATION||event.getSpawnType()==MobSpawnType.STRUCTURE)
                ||owl.getPersistentData().getBoolean("ChangedSynergyAviatorRolled"))return;
        owl.getPersistentData().putBoolean("ChangedSynergyAviatorRolled",true);
        if(owl.getRandom().nextDouble()>=ChangedSynergyConfig.COMMON.eagleOwlOutfitSpawnChance.get())return;
        AccessoryEntities.INSTANCE.forceReloadAccessories(owl);
        AccessorySlots.getForEntity(owl).ifPresent(slots->{if(slots.getItem(ChangedAccessorySlots.BODY.get()).map(ItemStack::isEmpty).orElse(false))slots.quickMoveStack(new ItemStack(ChangedSynergyItems.EAGLE_OWL_AVIATOR_OUTFIT.get()));});
    }
}
