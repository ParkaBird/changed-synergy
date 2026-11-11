package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.ability.*;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.entity.LatexEagleOwl;
import net.parkabird.changedsynergy.network.*;
import net.minecraftforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Add only an owl wing beat when the shared ability actually supplies an impulse. */
@Pseudo
@Mixin(targets="net.foxyas.changedaddon.ability.WingFlapAbility$AbilityInstance",remap=false)
public abstract class EagleOwlWingFlapMixin extends AbstractAbilityInstance {
    protected EagleOwlWingFlapMixin(AbstractAbility<?> ability,IAbstractChangedEntity entity){super(ability,entity);}
    @Unique private Vec3 changedSynergy$beforeFlap;
    @Inject(method={"startUsing","stopUsing"},at=@At("HEAD"))
    private void changedSynergy$beforeImpulse(CallbackInfo ci){changedSynergy$beforeFlap=entity.getEntity().getDeltaMovement();}
    @Inject(method={"startUsing","stopUsing"},at=@At("RETURN"))
    private void changedSynergy$animateImpulse(CallbackInfo ci){
        var actor=entity.getEntity();
        if(entity.getChangedEntity() instanceof LatexEagleOwl owl&&changedSynergy$beforeFlap!=null&&actor.getDeltaMovement().distanceToSqr(changedSynergy$beforeFlap)>1e-6){
            owl.flapWings();
            if(!actor.level().isClientSide)ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->actor),new SilkMothWingFlapPacket(actor.getId()));
        }
    }
}
