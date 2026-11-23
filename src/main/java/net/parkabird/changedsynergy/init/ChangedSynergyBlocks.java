package net.parkabird.changedsynergy.init;

import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.parkabird.changedsynergy.block.PerchBlock;
import net.parkabird.changedsynergy.block.SurveillanceCameraBlock;

@Mod.EventBusSubscriber(modid="changed_synergy",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ChangedSynergyBlocks {
    public static final DeferredRegister<Block> REGISTRY=DeferredRegister.create(ForgeRegistries.BLOCKS,"changed_synergy");
    public static final RegistryObject<PerchBlock> EAGLE_OWL_PERCH_STAND=REGISTRY.register("eagle_owl_perch_stand",()->new PerchBlock(true));
    public static final RegistryObject<PerchBlock> EAGLE_OWL_PERCH_RAIL=REGISTRY.register("eagle_owl_perch_rail",()->new PerchBlock(false));
    public static final RegistryObject<SurveillanceCameraBlock> SURVEILLANCE_CAMERA =
            REGISTRY.register("surveillance_camera", SurveillanceCameraBlock::new);
    @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent e){if(e.getTabKey()==CreativeModeTabs.FUNCTIONAL_BLOCKS){e.accept(ChangedSynergyItems.EAGLE_OWL_PERCH_STAND);e.accept(ChangedSynergyItems.EAGLE_OWL_PERCH_RAIL);e.accept(ChangedSynergyItems.SURVEILLANCE_CAMERA);}}
}
