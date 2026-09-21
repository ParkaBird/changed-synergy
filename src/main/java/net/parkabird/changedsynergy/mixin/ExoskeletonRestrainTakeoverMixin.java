package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.data.AccessorySlotType;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.entity.robot.Exoskeleton;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.parkabird.changedsynergy.ai.ExoskeletonTakeoverAdapter;
import net.parkabird.changedsynergy.ai.TakeoverService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Register manually alongside ExoskeletonTakeoverItemStateMixin. Never hooks voluntary equip. */
@Mixin(value = Exoskeleton.ExoskeletonRestrainGoal.class, remap = false)
public abstract class ExoskeletonRestrainTakeoverMixin {
    @Shadow @Final protected Exoskeleton exoskeleton;
    @Unique private ServerPlayer changedSynergy$forcedPlayer;
    @Unique private BlockPos changedSynergy$sourceCharger;

    @Inject(method = {"start", "m_8056_"}, at = @At("HEAD"), require = 1)
    private void changedSynergy$rememberAttachedCharger(CallbackInfo ci) {
        changedSynergy$sourceCharger = exoskeleton.isCharging()
                ? exoskeleton.getSleepingPos().map(BlockPos::immutable).orElse(null) : null;
    }

    @Inject(method = {"checkAndPerformAttack", "m_6739_"}, at = @At("HEAD"), require = 1)
    private void changedSynergy$clearAttempt(LivingEntity target, double distance, CallbackInfo ci) {
        changedSynergy$forcedPlayer = null;
    }

    @Redirect(method = {"checkAndPerformAttack", "m_6739_"}, at = @At(value = "INVOKE",
            target = "Lnet/ltxprogrammer/changed/data/AccessorySlots;tryReplaceSlot(Lnet/minecraft/world/entity/LivingEntity;Lnet/ltxprogrammer/changed/data/AccessorySlotType;Lnet/minecraft/world/item/ItemStack;)Z"), require = 1)
    private boolean changedSynergy$trackActualEquipment(LivingEntity target, AccessorySlotType slot, ItemStack stack) {
        ServerPlayer player = target instanceof ServerPlayer p && ExoskeletonTakeoverAdapter.isBenign(p) ? p : null;
        if (player != null) {
            ExoskeletonTakeoverAdapter.markForcedStack(player, exoskeleton, stack);
            ExoskeletonTakeoverAdapter.recordSourceCharger(stack, changedSynergy$sourceCharger);
        }
        boolean success = AccessorySlots.tryReplaceSlot(target, slot, stack);
        if (success && player != null) {
            ItemStack worn = AccessorySlots.getForEntity(player)
                    .flatMap(slots -> slots.getItem(slot)).orElse(ItemStack.EMPTY);
            if (ExoskeletonTakeoverAdapter.isForcedStack(player, exoskeleton, worn)) {
                changedSynergy$forcedPlayer = player;
            }
        }
        return success;
    }

    @Inject(method = {"checkAndPerformAttack", "m_6739_"}, at = @At("RETURN"), require = 1)
    private void changedSynergy$beginAfterDiscard(LivingEntity target, double distance, CallbackInfo ci) {
        ServerPlayer player = changedSynergy$forcedPlayer;
        changedSynergy$forcedPlayer = null;
        if (player != null && ExoskeletonTakeoverAdapter.capture(player, exoskeleton).isPresent()) {
            TakeoverService.beginExoskeleton(player, exoskeleton);
        }
    }
}
