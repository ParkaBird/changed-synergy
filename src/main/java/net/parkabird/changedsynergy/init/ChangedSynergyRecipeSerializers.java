package net.parkabird.changedsynergy.init;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.recipe.RevivalMaskRepairRecipe;

public final class ChangedSynergyRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> REGISTRY =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ChangedSynergyMod.MOD_ID);

    public static final RegistryObject<RecipeSerializer<RevivalMaskRepairRecipe>>
            REVIVAL_MASK_REPAIR = REGISTRY.register(
                    "revival_mask_repair",
                    () -> new SimpleCraftingRecipeSerializer<>(RevivalMaskRepairRecipe::new));

    private ChangedSynergyRecipeSerializers() {
    }
}
