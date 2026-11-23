package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.client.renderer.layers.AccessoryLayer;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;

@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ArmorStandClothingClient {
    private ArmorStandClothingClient() {}

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        addAccessoryLayer(event.getRenderer(EntityType.ARMOR_STAND));
        addAccessoryLayer(event.getRenderer(ChangedEntities.BIPED_ARMOR_STAND.get()));
        addAccessoryLayer(event.getRenderer(ChangedEntities.CENTAUR_ARMOR_STAND.get()));
        addAccessoryLayer(event.getRenderer(ChangedEntities.LEGLESS_ARMOR_STAND.get()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T extends LivingEntity> void addAccessoryLayer(LivingEntityRenderer<?, ?> renderer) {
        if (renderer != null) {
            LivingEntityRenderer living = renderer;
            living.addLayer(new AccessoryLayer<>(living));
        }
    }
}
