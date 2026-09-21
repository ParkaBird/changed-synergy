package net.parkabird.changedsynergy.client;

import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.FishingVisualEffects;
import net.parkabird.changedsynergy.init.ChangedSynergyMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ChangedSynergyScreens {
    private ChangedSynergyScreens() {
    }

    public static void registerConfigScreen(ModLoadingContext context) {
        context.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        ChangedSynergyConfigScreen::new));
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(
                    Items.FISHING_ROD,
                    ResourceLocation.fromNamespaceAndPath("minecraft", "cast"),
                    (stack, level, holder, seed) -> {
                        if (FishingVisualEffects.usesCastRodModel(stack)) {
                            return 1.0F;
                        }
                        if (holder == null) {
                            return 0.0F;
                        }
                        boolean mainHand = holder.getMainHandItem() == stack;
                        boolean offHand = holder.getOffhandItem() == stack;
                        if (holder.getMainHandItem().getItem()
                                instanceof FishingRodItem) {
                            offHand = false;
                        }
                        return (mainHand || offHand)
                                && holder instanceof Player player
                                && player.fishing != null
                                ? 1.0F : 0.0F;
                    });
            MenuScreens.register(
                    ChangedSynergyMenus.BONDED_LATEX.get(), BondedLatexScreen::new);
            MenuScreens.register(
                    ChangedSynergyMenus.BONDED_CREATURE_INVENTORY.get(),
                    BondedCreatureInventoryScreen::new);
            MenuScreens.register(
                    ChangedSynergyMenus.SOCIAL_INTERACTION.get(),
                    SocialInteractionScreen::new);
            MenuScreens.register(
                    ChangedSynergyMenus.PROVISIONER_TRADE.get(),
                    ProvisionerTradeScreen::new);
            MenuScreens.register(
                    ChangedSynergyMenus.PLAYER_RELATIONSHIPS.get(),
                    PlayerRelationshipScreen::new);
            MenuScreens.register(
                    ChangedSynergyMenus.CENTAUR_MOUNT_CONFIG.get(),
                    CentaurMountConfigScreen::new);
        });
    }
}
