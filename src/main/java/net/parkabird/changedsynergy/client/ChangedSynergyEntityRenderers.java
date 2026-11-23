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
import net.parkabird.changedsynergy.client.model.LatexSpidtaurModel;

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
        event.registerEntityRenderer(ChangedSynergyEntities.LATEX_SPIDTAUR.get(), LatexSpidtaurRenderer::new);
        event.registerEntityRenderer(ChangedSynergyEntities.LATEX_GRIZZLY.get(), LatexGrizzlyRenderer::new);
        event.registerEntityRenderer(ChangedSynergyEntities.LATEX_MANUL.get(), context -> new LatexManulRenderer<>(context, false));
        event.registerEntityRenderer(ChangedSynergyEntities.LATEX_MANUL_FEMALE.get(), context -> new LatexManulRenderer<>(context, true));
        event.registerEntityRenderer(ChangedSynergyEntities.LATEX_GRAY_FOX.get(), context -> new LatexGrayFoxRenderer<>(context, false));
        event.registerEntityRenderer(ChangedSynergyEntities.LATEX_GRAY_FOX_FEMALE.get(), context -> new LatexGrayFoxRenderer<>(context, true));
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        net.parkabird.changedsynergy.client.model.JerboaArmor.SET.registerDefinitions(event::registerLayerDefinition);
        net.parkabird.changedsynergy.client.model.EagleOwlArmor.SET.registerDefinitions(event::registerLayerDefinition);
        event.registerLayerDefinition(net.parkabird.changedsynergy.client.model.LatexJerboaModel.LAYER,net.parkabird.changedsynergy.client.model.JerboaModelLayers::body);
        net.parkabird.changedsynergy.client.model.SynergyFemaleArmor.SLIM.registerDefinitions(event::registerLayerDefinition);
        net.parkabird.changedsynergy.client.model.SynergyFemaleArmor.REGULAR.registerDefinitions(event::registerLayerDefinition);
        net.parkabird.changedsynergy.client.model.SynergyFemaleArmor.HYENA.registerDefinitions(event::registerLayerDefinition);
        event.registerLayerDefinition(net.parkabird.changedsynergy.client.model.LatexEagleOwlModel.LAYER,net.parkabird.changedsynergy.client.model.EagleOwlModelLayers::body);
        event.registerLayerDefinition(LatexSilkMothModel.LAYER_LOCATION, LatexSilkMothModel::createBodyLayer);
        event.registerLayerDefinition(LatexSwordfishModel.LAYER_LOCATION, LatexSwordfishModel::createBodyLayer);
        event.registerLayerDefinition(LatexMuntjacModel.LAYER_LOCATION, LatexMuntjacModel::createBodyLayer);
        event.registerLayerDefinition(LatexMuntjacFemaleModel.LAYER_LOCATION, LatexMuntjacFemaleModel::createBodyLayer);
        event.registerLayerDefinition(LatexSpottedHyenaModel.MALE_LAYER, SpottedHyenaModelLayers::male);
        event.registerLayerDefinition(LatexSpottedHyenaModel.FEMALE_LAYER, SpottedHyenaModelLayers::female);
        event.registerLayerDefinition(LatexSpidtaurModel.LAYER_LOCATION, LatexSpidtaurModel::createBodyLayer);
        net.parkabird.changedsynergy.client.model.SpidtaurArmor.UPPER_SET.registerDefinitions(event::registerLayerDefinition);
        net.parkabird.changedsynergy.client.model.SpidtaurArmor.LOWER_SET.registerDefinitions(event::registerLayerDefinition);
        net.parkabird.changedsynergy.client.model.GrizzlyArmor.SET.registerDefinitions(event::registerLayerDefinition);
        event.registerLayerDefinition(
                net.parkabird.changedsynergy.client.model.LatexGrizzlyModel.LAYER_LOCATION,
                net.parkabird.changedsynergy.client.model.LatexGrizzlyModel::createBodyLayer);
        event.registerLayerDefinition(
                net.parkabird.changedsynergy.client.model.LatexManulModel.MALE_LAYER,
                net.parkabird.changedsynergy.client.model.ManulModelLayers::male);
        event.registerLayerDefinition(
                net.parkabird.changedsynergy.client.model.LatexManulModel.FEMALE_LAYER,
                net.parkabird.changedsynergy.client.model.ManulModelLayers::female);
        event.registerLayerDefinition(
                net.parkabird.changedsynergy.client.model.LatexGrayFoxModel.MALE_LAYER,
                net.parkabird.changedsynergy.client.model.GrayFoxModelLayers::male);
        event.registerLayerDefinition(
                net.parkabird.changedsynergy.client.model.LatexGrayFoxModel.FEMALE_LAYER,
                net.parkabird.changedsynergy.client.model.GrayFoxModelLayers::female);
        event.registerLayerDefinition(
                net.parkabird.changedsynergy.client.model.WhiteLatexWolfPartialModel.LAYER_LOCATION_HUMAN,
                () -> net.parkabird.changedsynergy.client.model.WhiteLatexWolfPartialModel.createHumanLayer(false));
        event.registerLayerDefinition(
                net.parkabird.changedsynergy.client.model.WhiteLatexWolfPartialModel.LAYER_LOCATION_HUMAN_SLIM,
                () -> net.parkabird.changedsynergy.client.model.WhiteLatexWolfPartialModel.createHumanLayer(true));
        event.registerLayerDefinition(
                net.parkabird.changedsynergy.client.model.WhiteLatexWolfPartialModel.LAYER_LOCATION_LATEX,
                () -> net.parkabird.changedsynergy.client.model.WhiteLatexWolfPartialModel.createLatexLayer(false));
        event.registerLayerDefinition(
                net.parkabird.changedsynergy.client.model.WhiteLatexWolfPartialModel.LAYER_LOCATION_LATEX_SLIM,
                () -> net.parkabird.changedsynergy.client.model.WhiteLatexWolfPartialModel.createLatexLayer(true));
    }
}
