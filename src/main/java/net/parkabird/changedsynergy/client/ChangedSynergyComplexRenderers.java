package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.client.RegisterComplexRenderersEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.init.ChangedSynergyEntities;

@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ChangedSynergyComplexRenderers {
    private ChangedSynergyComplexRenderers() {}

    @SubscribeEvent
    public static void register(RegisterComplexRenderersEvent event) {
        event.registerEntityRenderer(
                ChangedSynergyEntities.PURE_WHITE_LATEX_WOLF_PARTIAL.get(),
                "default",
                WhiteLatexWolfPartialRenderer.forModelSize(false));
        event.registerEntityRenderer(
                ChangedSynergyEntities.PURE_WHITE_LATEX_WOLF_PARTIAL.get(),
                "slim",
                WhiteLatexWolfPartialRenderer.forModelSize(true));
    }
}
