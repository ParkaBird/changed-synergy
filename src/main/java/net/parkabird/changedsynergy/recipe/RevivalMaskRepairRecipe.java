package net.parkabird.changedsynergy.recipe;

import net.ltxprogrammer.changed.init.ChangedItems;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.parkabird.changedsynergy.ai.BondedRevivalService;
import net.parkabird.changedsynergy.init.ChangedSynergyItems;
import net.parkabird.changedsynergy.init.ChangedSynergyRecipeSerializers;

/** Repairs one identity-bearing mask while preserving its revival token. */
public final class RevivalMaskRepairRecipe extends CustomRecipe {
    public RevivalMaskRepairRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        ItemStack broken = ItemStack.EMPTY;
        int fragments = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ChangedSynergyItems.BROKEN_DARK_LATEX_MASK.get())
                    && BondedRevivalService.hasRevivalToken(stack)
                    && broken.isEmpty()) {
                broken = stack;
            } else if (stack.is(ChangedItems.DARK_LATEX_CRYSTAL_FRAGMENT.get())) {
                fragments++;
            } else {
                return false;
            }
        }
        return !broken.isEmpty() && fragments == 4;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registries) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(ChangedSynergyItems.BROKEN_DARK_LATEX_MASK.get())
                    && BondedRevivalService.hasRevivalToken(stack)) {
                return BondedRevivalService.repairMask(stack);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 5;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ChangedSynergyRecipeSerializers.REVIVAL_MASK_REPAIR.get();
    }
}
