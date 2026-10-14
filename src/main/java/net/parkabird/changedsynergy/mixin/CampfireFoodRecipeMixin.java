package net.parkabird.changedsynergy.mixin;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.parkabird.changedsynergy.ai.PlayerOutpostWorkGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only fires actually supplied by a Synergy cook gain the food-only recipe fallback. */
@Mixin(value = CampfireBlockEntity.class, remap = false)
public abstract class CampfireFoodRecipeMixin {
    @Group(name = "synergyCampFoodRecipe", min = 1, max = 1)
    @Redirect(method = {"cookTick", "m_155306_"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/crafting/RecipeManager$CachedCheck;getRecipeFor(Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"),
            remap = false, require = 0)
    private static Optional<CampfireCookingRecipe> synergy$finishFood(
            RecipeManager.CachedCheck<Container, CampfireCookingRecipe> check, Container input, Level level,
            Level tickingLevel, BlockPos pos, BlockState state, CampfireBlockEntity fire) {
        return synergy$foodRecipe(check, input, level, fire);
    }

    @Group(name = "synergyCampFoodRecipe", min = 1, max = 1)
    @Redirect(method = {"cookTick", "m_155306_"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/crafting/RecipeManager$CachedCheck;m_213657_(Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"),
            remap = false, require = 0)
    private static Optional<CampfireCookingRecipe> synergy$finishFoodSrg(
            RecipeManager.CachedCheck<Container, CampfireCookingRecipe> check, Container input, Level level,
            Level tickingLevel, BlockPos pos, BlockState state, CampfireBlockEntity fire) {
        return synergy$foodRecipe(check, input, level, fire);
    }

    @Unique
    private static Optional<CampfireCookingRecipe> synergy$foodRecipe(
            RecipeManager.CachedCheck<Container, CampfireCookingRecipe> check, Container input, Level level,
            CampfireBlockEntity fire) {
        Optional<CampfireCookingRecipe> recipe = check.getRecipeFor(input, level);
        return recipe.isPresent() || !fire.getPersistentData().getBoolean("ChangedSynergyCampFood")
                ? recipe : PlayerOutpostWorkGoal.campfireRecipe(level, input.getItem(0));
    }

    @Inject(method = {"cookTick", "m_155306_"}, at = @At("TAIL"), remap = false, require = 1)
    private static void synergy$clearFinishedBatch(Level level, BlockPos pos, BlockState state,
                                                  CampfireBlockEntity fire, CallbackInfo ci) {
        if (fire.getItems().stream().allMatch(ItemStack::isEmpty))
            fire.getPersistentData().remove("ChangedSynergyCampFood");
    }
}
