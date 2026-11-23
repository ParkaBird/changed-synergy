package net.parkabird.changedsynergy.init;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.block.SurveillanceCameraBlockEntity;

public final class ChangedSynergyBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTRY =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ChangedSynergyMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<SurveillanceCameraBlockEntity>> SURVEILLANCE_CAMERA =
            REGISTRY.register("surveillance_camera",
                    () -> BlockEntityType.Builder.of(
                            SurveillanceCameraBlockEntity::new,
                            ChangedSynergyBlocks.SURVEILLANCE_CAMERA.get()
                    ).build(null));

    private ChangedSynergyBlockEntities() {}
}
