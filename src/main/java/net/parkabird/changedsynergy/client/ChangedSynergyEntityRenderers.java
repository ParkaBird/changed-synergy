package net.parkabird.changedsynergy.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.init.ChangedSynergyEntities;
import net.parkabird.changedsynergy.client.model.LatexSilkMothModel;
import net.parkabird.changedsynergy.client.model.LatexSwordfishModel;
import net.parkabird.changedsynergy.client.model.LatexMuntjacModel;
import net.parkabird.changedsynergy.client.model.LatexMuntjacFemaleModel;
import net.parkabird.changedsynergy.client.model.LatexSpottedHyenaModel;
import net.parkabird.changedsynergy.client.model.SpottedHyenaModelLayers;

/** Client-only entity renderer registration. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ChangedSynergyEntityRenderers {
    private ChangedSynergyEntityRenderers() {
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ChangedSynergyEntities.LATEX_JERBOA.get(),LatexJerboaRenderer::new);
        event.registerEntityRenderer(ChangedSynergyEntities.LATEX_EAGLE_OWL.get(),LatexEagleOwlRenderer::new);
        event.registerEntityRenderer(
                ChangedSynergyEntities.CREATURE_FISHING_HOOK.get(),
                CreatureFishingHookRenderer::new);
        event.registerEntityRenderer(ChangedSynergyEntities.LATEX_SILK_MOTH.get(), LatexSilkMothRenderer::new);
        event.registerEntityRenderer(ChangedSynergyEntities.LATEX_SWORDFISH.get(), LatexSwordfishRenderer::new);
        event.registerEntityRenderer(ChangedSynergyEntities.LATEX_MUNTJAC.get(), LatexMuntjacRenderer::new);
        event.registerEntityRenderer(ChangedSynergyEntities.LATEX_MUNTJAC_FEMALE.get(), LatexMuntjacFemaleRenderer::new);
        event.registerEntityRenderer(ChangedSynergyEntities.LATEX_SPOTTED_HYENA_MALE.get(), context -> new LatexSpottedHyenaRenderer(context, false));
        event.registerEntityRenderer(ChangedSynergyEntities.LATEX_SPOTTED_HYENA_FEMALE.get(), context -> new LatexSpottedHyenaRenderer(context, true));
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        net.parkabird.changedsynergy.client.model.JerboaArmor.SET.registerDefinitions(event::registerLayerDefinition);
        net.parkabird.changedsynergy.client.model.EagleOwlArmor.SET.registerDefinitions(event::registerLayerDefinition);
        event.registerLayerDefinition(net.parkabird.changedsynergy.client.model.LatexJerboaModel.LAYER,net.parkabird.changedsynergy.client.model.JerboaModelLayers::body);
        net.parkabird.changedsynergy.client.model.SynergyFemaleArmor.SLIM.registerDefinitions(event::registerLayerDefinition);
        net.parkabird.changedsynergy.client.model.SynergyFemaleArmor.HYENA.registerDefinitions(event::registerLayerDefinition);
        event.registerLayerDefinition(net.parkabird.changedsynergy.client.model.LatexEagleOwlModel.LAYER,net.parkabird.changedsynergy.client.model.EagleOwlModelLayers::body);
        event.registerLayerDefinition(LatexSilkMothModel.LAYER_LOCATION, LatexSilkMothModel::createBodyLayer);
        event.registerLayerDefinition(LatexSwordfishModel.LAYER_LOCATION, LatexSwordfishModel::createBodyLayer);
        event.registerLayerDefinition(LatexMuntjacModel.LAYER_LOCATION, LatexMuntjacModel::createBodyLayer);
        event.registerLayerDefinition(LatexMuntjacFemaleModel.LAYER_LOCATION, LatexMuntjacFemaleModel::createBodyLayer);
        event.registerLayerDefinition(LatexSpottedHyenaModel.MALE_LAYER, SpottedHyenaModelLayers::male);
        event.registerLayerDefinition(LatexSpottedHyenaModel.FEMALE_LAYER, SpottedHyenaModelLayers::female);
    }
}
