package net.parkabird.changedsynergy.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Activates the guarded interface bridge in ChangedSynergyMixinPlugin. */
@Pseudo
@Mixin(targets = {
        "mezz.jei.gui.input.handlers.SearchInputLayer",
        "mezz.jei.gui.overlay.bookmarks.BookmarkPreviewTooltip",
        "mezz.jei.gui.overlay.bookmarks.BookmarkPreviewTooltipController",
        "mezz.jei.gui.overlay.bookmarks.history.LookupHistoryOverlay",
        "mezz.jei.gui.overlay.ingredients.IngredientGrid",
        "mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigation",
        "mezz.jei.gui.recipes.InteractiveIngredientTooltip",
        "mezz.jei.gui.recipes.InteractiveIngredientTooltipController",
        "mezz.jei.gui.recipes.RecipeCatalysts",
        "mezz.jei.gui.recipes.RecipesGui"
}, remap = false)
public abstract class JeiMouseOverMappingMixin {}
