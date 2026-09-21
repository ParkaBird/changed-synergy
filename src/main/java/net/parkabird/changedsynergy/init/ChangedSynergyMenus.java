package net.parkabird.changedsynergy.init;

import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.world.inventory.BondedCreatureInventoryMenu;
import net.parkabird.changedsynergy.world.inventory.BondedLatexMenu;
import net.parkabird.changedsynergy.world.inventory.CentaurMountConfigMenu;
import net.parkabird.changedsynergy.world.inventory.PlayerRelationshipMenu;
import net.parkabird.changedsynergy.world.inventory.ProvisionerTradeMenu;
import net.parkabird.changedsynergy.world.inventory.SocialInteractionMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ChangedSynergyMenus {
    public static final DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(
            ForgeRegistries.MENU_TYPES, ChangedSynergyMod.MOD_ID);

    public static final RegistryObject<MenuType<BondedLatexMenu>> BONDED_LATEX = REGISTRY.register(
            "bonded_latex",
            () -> IForgeMenuType.create(BondedLatexMenu::new));

    public static final RegistryObject<MenuType<BondedCreatureInventoryMenu>> BONDED_CREATURE_INVENTORY =
            REGISTRY.register(
                    "bonded_creature_inventory",
                    () -> IForgeMenuType.create(BondedCreatureInventoryMenu::new));

    public static final RegistryObject<MenuType<SocialInteractionMenu>> SOCIAL_INTERACTION =
            REGISTRY.register(
                    "social_interaction",
                    () -> IForgeMenuType.create(SocialInteractionMenu::new));

    public static final RegistryObject<MenuType<ProvisionerTradeMenu>> PROVISIONER_TRADE =
            REGISTRY.register(
                    "provisioner_trade",
                    () -> IForgeMenuType.create(ProvisionerTradeMenu::new));

    public static final RegistryObject<MenuType<PlayerRelationshipMenu>> PLAYER_RELATIONSHIPS =
            REGISTRY.register(
                    "player_relationships",
                    () -> IForgeMenuType.create(PlayerRelationshipMenu::new));

    public static final RegistryObject<MenuType<CentaurMountConfigMenu>> CENTAUR_MOUNT_CONFIG =
            REGISTRY.register(
                    "centaur_mount_config",
                    () -> IForgeMenuType.create(CentaurMountConfigMenu::new));

    private ChangedSynergyMenus() {
    }
}
