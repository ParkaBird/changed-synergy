package net.parkabird.changedsynergy.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.ltxprogrammer.changed.init.ChangedSounds;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.ai.TakeoverService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A takeover already retains its carrier; Addon's ordinary split must not clone it. */
@Pseudo
@Mixin(targets = "net.foxyas.changedaddon.item.DiffusionSyringeItem", remap = false)
public abstract class AddonTakeoverSeparationMixin {
    @Inject(method = "applyEffectsAfterUse(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;"
            + "Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void changedSynergy$releaseOriginalCarrier(ItemStack stack, Level level, LivingEntity entity,
            CallbackInfo callback) {
        if (!(entity instanceof ServerPlayer player)) return;
        boolean takeover = TakeoverService.active(player);
        var carrier = BondedSuitService.getWrappingPet(player);
        if (!takeover && carrier == null) return;
        ChangedSounds.broadcastSound(player, ChangedSounds.SYRINGE_PRICK, 1.0F, 1.0F);
        if (takeover) TakeoverService.forceReleaseByCommand(player);
        else BondedSuitService.releaseOwner(carrier, player, BondedSuitService.ReleaseReason.MANUAL);
        // Intercept before Addon downgrades double Yufeng or creates a new creature.
        // Suppress spawning even if a safe release is temporarily unavailable.
        callback.cancel();
    }
}
