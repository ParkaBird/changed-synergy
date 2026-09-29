package net.parkabird.changedsynergy.mixin;

import net.minecraft.world.inventory.MerchantMenu;
import net.parkabird.changedsynergy.api.SynergyMerchantMenuMarker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla's shift-trade sound assumes every merchant is also an entity. */
@Mixin(MerchantMenu.class)
public abstract class NonEntityMerchantSoundMixin implements SynergyMerchantMenuMarker {
    @Unique private boolean changedSynergy$skipTradeSound;

    @Override
    public void changedSynergy$skipTradeSound() {
        changedSynergy$skipTradeSound = true;
    }

    @Inject(method = {"playTradeSound", "m_40077_"}, at = @At("HEAD"), cancellable = true)
    private void changedSynergy$skipNonEntityMerchantSound(CallbackInfo callback) {
        if (changedSynergy$skipTradeSound) callback.cancel();
    }
}
