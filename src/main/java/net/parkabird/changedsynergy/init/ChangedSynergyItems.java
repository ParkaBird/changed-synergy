package net.parkabird.changedsynergy.init;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.item.BrokenDarkLatexMaskItem;
import net.parkabird.changedsynergy.item.RepairedDarkLatexMaskItem;

/** Items owned by Synergy. */
public final class ChangedSynergyItems {
    public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(
            ForgeRegistries.ITEMS, ChangedSynergyMod.MOD_ID);

    public static final RegistryObject<Item> BROKEN_DARK_LATEX_MASK = REGISTRY.register(
            "broken_dark_latex_mask",
            () -> new BrokenDarkLatexMaskItem(new Item.Properties()
                    .stacksTo(1)
                    .fireResistant()));

    public static final RegistryObject<Item> REPAIRED_DARK_LATEX_MASK = REGISTRY.register(
            "repaired_dark_latex_mask",
            RepairedDarkLatexMaskItem::new);

    private ChangedSynergyItems() {
    }
}
