package net.parkabird.changedsynergy.init;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.block.DarkLatexMaskBlockEntity;
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

    public static final RegistryObject<BlockEntityType<DarkLatexMaskBlockEntity>> DARK_LATEX_MASK =
            REGISTRY.register("dark_latex_mask",
                    () -> BlockEntityType.Builder.of(
                            DarkLatexMaskBlockEntity::new,
                            ChangedSynergyBlocks.DARK_LATEX_MASK.get()
                    ).build(null));

    private ChangedSynergyBlockEntities() {}
}
