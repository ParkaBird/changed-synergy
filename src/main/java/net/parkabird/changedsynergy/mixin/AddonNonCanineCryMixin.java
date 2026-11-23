package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.entity.LatexJerboa;
import net.parkabird.changedsynergy.entity.LatexSpidtaur;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps Jerboa silent and Spidtaur spider-only despite AbstractLatexWolf inheritance. */
@Pseudo
@Mixin(targets = "net.foxyas.changedaddon.util.PlayerUtil", remap = false)
public abstract class AddonNonCanineCryMixin {
    @Inject(method = "isWolfTransfur(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private static void changedSynergy$excludeNonCaninePlayer(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (player == null) return;
        var variant = ProcessTransfur.getPlayerTransfurVariant(player);
        if (variant != null && (variant.getChangedEntity() instanceof LatexJerboa || variant.getChangedEntity() instanceof LatexSpidtaur)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isWolfTransfur(Lnet/ltxprogrammer/changed/entity/ChangedEntity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private static void changedSynergy$excludeNonCanineEntity(ChangedEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof LatexJerboa || entity instanceof LatexSpidtaur) {
            cir.setReturnValue(false);
        }
    }
}
