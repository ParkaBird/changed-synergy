package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.ability.HypnosisToggle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=TransfurVariantInstance.class,remap=false)
public abstract class HypnosisRemovalMixin {
    @Inject(method="unhookAll",at=@At("HEAD"))
    private void changedSynergy$removed(Player player,CallbackInfo ci){
        ((TransfurVariantInstance<?>)(Object)this).abilityInstances.values().forEach(i->((HypnosisToggle)i.getController()).changedSynergy$clearHypnosis());
    }
}
