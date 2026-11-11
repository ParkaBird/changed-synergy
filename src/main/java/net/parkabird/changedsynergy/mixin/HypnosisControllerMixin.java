package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.ability.*;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.ability.HypnosisToggle;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value=AbstractAbility.Controller.class, remap=false)
public abstract class HypnosisControllerMixin implements HypnosisToggle {
    @Shadow @Final private AbstractAbilityInstance abilityInstance;
    @Shadow public abstract void deactivateAbility();
    @Unique private boolean changedSynergy$latched;
    @Unique private boolean changedSynergy$held;

    public boolean changedSynergy$isPlayerHypnosis() {
        return abilityInstance.ability instanceof HypnosisAbility && abilityInstance.entity.getEntity() instanceof Player;
    }
    public boolean changedSynergy$isLatched(){return changedSynergy$latched;}
    public void changedSynergy$input(boolean down, boolean wasDown){
        changedSynergy$held=down;
        if(down&&!wasDown)changedSynergy$latched=!changedSynergy$latched;
    }
    public void changedSynergy$clearHypnosis(){
        if(changedSynergy$latched)deactivateAbility();
        changedSynergy$latched=false;changedSynergy$held=false;
    }
    public float changedSynergy$hypnosisBrightness(){return changedSynergy$latched&&!changedSynergy$held?.65F:1F;}

    @Inject(method="getProgressActive",at=@At("HEAD"),cancellable=true)
    private void changedSynergy$active(CallbackInfoReturnable<Float> ci){
        if(changedSynergy$isPlayerHypnosis())ci.setReturnValue(changedSynergy$latched?1F:0F);
    }
    @Inject(method="tickCoolDown",at=@At("HEAD"))
    private void changedSynergy$selection(CallbackInfo ci){
        if(!changedSynergy$latched)return;
        var variant=abilityInstance.entity.getTransfurVariantInstance();
        if(abilityInstance.entity.isDeadOrDying() || variant==null || variant.isTemporaryFromSuit()
                || variant.selectedAbility!=abilityInstance.ability && !ChangedAddonCompat.isSecondaryAbilitySelected(variant,abilityInstance.ability))
            changedSynergy$clearHypnosis();
    }
}
