package net.parkabird.changedsynergy;

import com.mojang.logging.LogUtils;
import net.parkabird.changedsynergy.client.ChangedSynergyScreens;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.compat.FirearmCompat;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.init.ChangedSynergyEntities;
import net.parkabird.changedsynergy.init.ChangedSynergyMenus;
import net.parkabird.changedsynergy.init.ChangedSynergyMobEffects;
import net.parkabird.changedsynergy.init.ChangedSynergySoundEvents;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.world.LatexTerritoryBiomes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(ChangedSynergyMod.MOD_ID)
public final class ChangedSynergyMod {
    public static final String MOD_ID = "changed_synergy";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ChangedSynergyMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ChangedSynergyConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ChangedSynergyClientConfig.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ChangedSynergyScreens.registerConfigScreen(
                        ModLoadingContext.get()));
        ChangedSynergyGameRules.bootstrap();
        ChangedSynergyNetwork.register();
        ChangedAddonCompat.registerOptionalEvents();
        FirearmCompat.registerOptionalEvents();

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(ChangedSynergyMod::onConfigLoading);
        modBus.addListener(ChangedSynergyMod::onCommonSetup);
        ChangedSynergyEntities.REGISTRY.register(modBus);
        ChangedSynergyMenus.REGISTRY.register(modBus);
        ChangedSynergyMobEffects.REGISTRY.register(modBus);
        ChangedSynergySoundEvents.REGISTRY.register(modBus);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(LatexTerritoryBiomes::register);
    }

    private static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ChangedSynergyConfig.SPEC) {
            ChangedSynergyConfig.migrateBehaviourBalance(event.getConfig());
        }
    }
}
