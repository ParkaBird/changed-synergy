package net.parkabird.changedsynergy.mixin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.ltxprogrammer.changed.ability.AbstractAbility;
import net.ltxprogrammer.changed.network.VariantAbilityActivate;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent.Context;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Generic Addon-mixed bonded creatures have no native tamedInteract
 * implementation. Preserve Changed's temporary-suit behavior by routing only
 * the radial-open request to the bonded creature menu; form abilities remain
 * disabled exactly as they are for a native Changed suit.
 */
@Mixin(value = VariantAbilityActivate.class, remap = false)
public abstract class VariantAbilityActivateMixin {
    @Shadow(remap = false)
    @Final
    private int id;

    @Shadow(remap = false)
    @Final
    private boolean keyDown;

    @Shadow(remap = false)
    @Final
    private AbstractAbility<?> ability;

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$openBondedSuitMenu(
            Context context,
            CompletableFuture<Level> levelFuture,
            Executor sidedExecutor,
            CallbackInfoReturnable<CompletableFuture<Void>> callback) {
        if (context.getDirection().getReceptionSide() != LogicalSide.SERVER) {
            return;
        }

        ServerPlayer sender = context.getSender();
        if (sender == null) {
            return;
        }
        var pet = BondedSuitService.getFriendlySuitPet(sender);
        if (pet == null || keyDown || ability != null) {
            return;
        }

        context.setPacketHandled(true);
        if (sender.getId() != id) {
            callback.setReturnValue(CompletableFuture.failedFuture(
                    new IllegalArgumentException("Incorrect UUID for sending player")));
            return;
        }

        callback.setReturnValue(levelFuture.thenAccept(
                level -> LatexSocialEvents.openBondedPetMenu(sender, pet)));
    }
}
