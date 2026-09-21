package net.parkabird.changedsynergy.mixin;

import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.compat.DomesticationInnovationCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets DI treat Synergy-owned Changed creatures as pets without a hard dependency. */
@Pseudo
@Mixin(targets = "com.github.alexthe668.domesticationinnovation.server.entity.TameableUtils",
        remap = false)
public abstract class DomesticationInnovationTameableUtilsMixin {
    @Inject(method = "couldBeTamed", at = @At("HEAD"), cancellable = true, require = 0)
    private static void changedSynergy$couldBeTamed(
            Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (DomesticationInnovationCompat.isCompatiblePet(entity)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "isTamed", at = @At("HEAD"), cancellable = true, require = 0)
    private static void changedSynergy$isTamed(
            Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (DomesticationInnovationCompat.isCompatiblePet(entity)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "isPetOf", at = @At("HEAD"), cancellable = true, require = 0)
    private static void changedSynergy$isPetOf(
            Player player, Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (DomesticationInnovationCompat.isPetOf(player, entity)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "hasSameOwnerAs", at = @At("HEAD"), cancellable = true, require = 0)
    private static void changedSynergy$hasSameOwnerAs(
            LivingEntity first, Entity second,
            CallbackInfoReturnable<Boolean> callback) {
        if ((first instanceof ChangedEntity || second instanceof ChangedEntity)
                && DomesticationInnovationCompat.hasSameOwner(first, second)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "getOwnerOf", at = @At("HEAD"), cancellable = true, require = 0)
    private static void changedSynergy$getOwnerOf(
            Entity entity, CallbackInfoReturnable<Entity> callback) {
        Entity owner = DomesticationInnovationCompat.owner(entity);
        if (owner != null) {
            callback.setReturnValue(owner);
        }
    }

    @Inject(method = "getOwnerUUIDOf", at = @At("HEAD"), cancellable = true, require = 0)
    private static void changedSynergy$getOwnerUuidOf(
            Entity entity, CallbackInfoReturnable<UUID> callback) {
        if (entity instanceof ChangedEntity) {
            callback.setReturnValue(DomesticationInnovationCompat.ownerUuid(entity));
        }
    }

    @Inject(method = "setOwnerUUIDOf", at = @At("HEAD"), cancellable = true, require = 0)
    private static void changedSynergy$setOwnerUuidOf(
            Entity entity, UUID ownerUuid, CallbackInfo callback) {
        if (DomesticationInnovationCompat.setOwner(entity, ownerUuid)) {
            callback.cancel();
        }
    }
}
