package net.parkabird.changedsynergy.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Activates the guarded interface bridge in ChangedSynergyMixinPlugin. */
@Pseudo
@Mixin(targets = {
        "mezz.jei.gui.input.handlers.BookmarkCommands",
        "mezz.jei.gui.input.handlers.ChatCommands",
        "mezz.jei.gui.input.handlers.DeleteItemInputHandler",
        "mezz.jei.gui.input.handlers.GuiAreaInputHandler",
        "mezz.jei.gui.input.handlers.IngredientCommands",
        "mezz.jei.gui.input.handlers.SearchInputLayer",
        "mezz.jei.gui.overlay.bookmarks.BookmarkPreviewTooltip",
        "mezz.jei.gui.overlay.bookmarks.BookmarkPreviewTooltipController",
        "mezz.jei.gui.overlay.bookmarks.history.LookupHistoryOverlay",
        "mezz.jei.gui.overlay.ingredients.IngredientGrid",
        "mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigation",
        "mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigationController",
        "mezz.jei.gui.overlay.ingredients.IngredientListSlot",
        "mezz.jei.gui.recipes.InteractiveIngredientGridTooltipComponent",
        "mezz.jei.gui.recipes.InteractiveIngredientTooltip",
        "mezz.jei.gui.recipes.InteractiveIngredientTooltipController",
        "mezz.jei.gui.recipes.RecipeCatalysts",
        "mezz.jei.gui.recipes.RecipeCategoryTab",
        "mezz.jei.gui.recipes.RecipeGuiLayouts",
        "mezz.jei.gui.recipes.RecipeLayoutWithButtons",
        "mezz.jei.gui.recipes.RecipeLayoutWithButtons$RecipeLayoutInputTarget",
        "mezz.jei.gui.recipes.RecipeLayoutWithButtonsErrored$ErroredRecipeInputTarget",
        "mezz.jei.gui.recipes.RecipeSlotClickTargetFactory",
        "mezz.jei.gui.recipes.RecipesGui",
        "mezz.jei.gui.recipes.RecipesGui$RecipeNavigationInputHandler"
}, remap = false)
public abstract class JeiMouseOverMappingMixin {}
