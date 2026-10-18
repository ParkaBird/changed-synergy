package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.beast.AbstractDarkLatexEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A spawned NPC may inherit appearance and traits, never a render body's player ownership. */
@Mixin(value = AbstractDarkLatexEntity.class, remap = false)
public abstract class DarkLatexNpcOwnershipMixin {
    @Redirect(method = "copyTraitsFrom", at = @At(value = "INVOKE",
            target = "Lnet/ltxprogrammer/changed/entity/beast/AbstractDarkLatexEntity;setUnderlyingPlayer(Lnet/minecraft/world/entity/player/Player;)V"))
    private void changedSynergy$keepNpcIndependent(AbstractDarkLatexEntity replacement, Player player) {
        if (player != null && replacement.getUnderlyingPlayer() == null
                && replacement.level() instanceof ServerLevel level
                && level.getEntity(replacement.getUUID()) == replacement) {
            // The native trait copy also inherited the render body's account label.
            replacement.setCustomName(null);
            replacement.setCustomNameVisible(false);
            return;
        }
        replacement.setUnderlyingPlayer(player);
    }
}
