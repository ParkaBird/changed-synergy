package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.ai.EagleOwlTraits;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=GrabEntityAbilityInstance.class,remap=false)
public abstract class EagleOwlGrabMixin {
    @Shadow private int instructionTicks;
    @Unique private LivingEntity changedSynergy$airReleased;
    @Unique private long changedSynergy$lastGrabRequest=-1000;
    @Inject(method={"canUse","canKeepUsing"},at=@At("HEAD"),cancellable=true)
    private void changedSynergy$releaseCooldown(CallbackInfoReturnable<Boolean> c){var grab=(GrabEntityAbilityInstance)(Object)this;if(grab.grabbedEntity==null&&EagleOwlTraits.regrabBlocked(grab.entity.getEntity()))c.setReturnValue(false);}
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void changedSynergy$noAutomaticRegrab(CallbackInfo c){var grab=(GrabEntityAbilityInstance)(Object)this;if(grab.grabbedEntity==null&&EagleOwlTraits.regrabBlocked(grab.entity.getEntity()))c.cancel();}
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void changedSynergy$serverAcquisition(CallbackInfo c){
        var grab=(GrabEntityAbilityInstance)(Object)this;var actor=grab.entity.getEntity();
        if(grab.grabbedEntity!=null||EagleOwlTraits.body(actor)==null
                ||net.parkabird.changedsynergy.ai.PlayerHugService.mode(grab))return;
        if(!actor.level().isClientSide&&actor instanceof net.minecraft.server.level.ServerPlayer){
            if(!EagleOwlTraits.airborne(actor))return;
            c.cancel();
            var target=EagleOwlTraits.contactTarget(grab);
            if(target!=null&&grab.grabEntity(target)){
                instructionTicks=180;
                net.ltxprogrammer.changed.init.ChangedSounds.broadcastSound(actor,net.ltxprogrammer.changed.init.ChangedSounds.LATEX_GRAB_ENTITY,1.0F,1.0F);
                net.ltxprogrammer.changed.Changed.PACKET_HANDLER.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->actor),
                        net.ltxprogrammer.changed.network.packet.GrabEntityPacket.initialGrab((net.minecraft.world.entity.player.Player)actor,target));
            }
            return;
        }
        if(!net.ltxprogrammer.changed.util.UniversalDist.isLocalPlayer(actor))return;
        c.cancel();
        // The active server-side hold detects aerial contact, without a delayed target packet.
        if(EagleOwlTraits.airborne(actor))return;
        long now=actor.level().getGameTime();
        if(EagleOwlTraits.regrabBlocked(actor)||now-changedSynergy$lastGrabRequest<7)return;
        var target=grab.getHoveredEntity(grab.entity);
        if(target==null||!target.isAlive()||target.isRemoved())return;
        boolean reachable=actor.getBoundingBox().inflate(.5,0,.5).intersects(target.getBoundingBox());
        if(!reachable||target instanceof net.minecraft.world.entity.player.Player&&!net.ltxprogrammer.changed.Changed.config.server.isGrabEnabled.get())return;
        // Wait for the server ACK: a predicted hold can consume an older empty ability snapshot and release itself.
        changedSynergy$lastGrabRequest=now;
        net.ltxprogrammer.changed.Changed.PACKET_HANDLER.sendToServer(net.ltxprogrammer.changed.network.packet.GrabEntityPacket.initialGrab((net.minecraft.world.entity.player.Player)actor,target));
        instructionTicks=180;
    }
    @ModifyVariable(method="releaseEntity",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private boolean changedSynergy$onlyPenalizeEscape(boolean debuffs){
        var grab=(GrabEntityAbilityInstance)(Object)this;
        return debuffs&&!(EagleOwlTraits.body(grab.entity.getEntity())!=null&&!grab.suited&&grab.grabStrength>0);
    }
    @Inject(method="getHoveredEntity",at=@At("HEAD"),cancellable=true)
    private void changedSynergy$contactAcquisition(net.ltxprogrammer.changed.ability.IAbstractChangedEntity entity,CallbackInfoReturnable<LivingEntity> c){
        if(EagleOwlTraits.airborne(entity.getEntity())&&net.ltxprogrammer.changed.util.UniversalDist.isLocalPlayer(entity.getEntity()))
            c.setReturnValue(EagleOwlTraits.contactTarget((GrabEntityAbilityInstance)(Object)this));
    }
    @Inject(method="grabEntity",at=@At("HEAD"),cancellable=true)
    private void changedSynergy$validateAerialGrab(LivingEntity target,CallbackInfoReturnable<Boolean> c){
        var grab=(GrabEntityAbilityInstance)(Object)this;
        if(grab.grabbedEntity!=target&&EagleOwlTraits.regrabBlocked(grab.entity.getEntity())){c.setReturnValue(false);return;}
        if(!grab.entity.getLevel().isClientSide&&grab.grabbedEntity!=target&&EagleOwlTraits.airborne(grab.entity.getEntity())
                &&(!EagleOwlTraits.canReach(grab.entity.getEntity(),target)
                    ||net.parkabird.changedsynergy.ai.PlayerHugService.owner(grab.entity.getEntity())!=null&&!EagleOwlTraits.validContact(grab,target)))c.setReturnValue(false);
    }
    @Inject(method="releaseEntity",at=@At("HEAD"))
    private void changedSynergy$dropFromCurrentPosition(boolean debuffs,CallbackInfo c){
        var grab=(GrabEntityAbilityInstance)(Object)this;var actor=grab.entity.getEntity();
        changedSynergy$airReleased=grab.grabbedEntity!=null&&!grab.suited&&!grab.grabbedHasControl&&!actor.onGround()&&!actor.isInWaterOrBubble()&&!actor.isPassenger()?grab.grabbedEntity:null;
        if(changedSynergy$airReleased!=null&&changedSynergy$airReleased.level()==actor.level())
            net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance.syncEntityPosRotWithEntity(changedSynergy$airReleased,actor);
    }
    @Inject(method="releaseEntity",at=@At("RETURN"))
    private void changedSynergy$finishAirRelease(boolean debuffs,CallbackInfo c){
        var held=changedSynergy$airReleased;changedSynergy$airReleased=null;if(held==null)return;
        var grab=(GrabEntityAbilityInstance)(Object)this;var actor=grab.entity.getEntity();
        actor.getPersistentData().putLong(EagleOwlTraits.REGRAB_AFTER,actor.level().getGameTime()+20);
        grab.getController().deactivateAbility();
        if(held.isRemoved()||held.level()!=actor.level())return;
        // Clear the interpolation destination from before acquisition as well as the held render link.
        held.lerpTo(held.getX(),held.getY(),held.getZ(),held.getYRot(),held.getXRot(),0,false);held.setOldPosAndRot();
        if(actor.level() instanceof net.minecraft.server.level.ServerLevel level){
            held.hurtMarked=true;
            net.ltxprogrammer.changed.Changed.PACKET_HANDLER.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->actor),new net.ltxprogrammer.changed.network.packet.GrabEntityPacket(actor,held,net.ltxprogrammer.changed.network.packet.GrabEntityPacket.GrabType.RELEASE));
            var packet=new net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket(held);level.getChunkSource().broadcastAndSend(held,packet);
            var owner=net.parkabird.changedsynergy.ai.PlayerHugService.owner(actor);if(owner!=null)owner.connection.send(packet);
            if(held instanceof net.minecraft.server.level.ServerPlayer player)player.connection.teleport(held.getX(),held.getY(),held.getZ(),held.getYRot(),held.getXRot());
        }
    }
}
