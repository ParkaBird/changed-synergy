package net.parkabird.changedsynergy.init;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.entity.CreatureFishingHookVisual;

/** Entity types owned by Synergy. */
public final class ChangedSynergyEntities {
    public static final DeferredRegister<EntityType<?>> REGISTRY =
            DeferredRegister.create(
                    ForgeRegistries.ENTITY_TYPES,
                    ChangedSynergyMod.MOD_ID);

    public static final RegistryObject<EntityType<CreatureFishingHookVisual>>
            CREATURE_FISHING_HOOK = REGISTRY.register(
                    "creature_fishing_hook",
                    () -> EntityType.Builder
                            .<CreatureFishingHookVisual>of(
                                    CreatureFishingHookVisual::new,
                                    MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build(ChangedSynergyMod.MOD_ID
                                    + ":creature_fishing_hook"));

    private ChangedSynergyEntities() {
    }
}
