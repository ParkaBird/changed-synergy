package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.init.*;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket;
import net.ltxprogrammer.changed.Changed;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.entity.LatexEagleOwl;

/** Existing combat targets only; acquiring a hold never starts a new fight. */
public final class EagleOwlAirGrabGoal extends Goal {
    private final LatexEagleOwl owl;private long nextGrab;
    public EagleOwlAirGrabGoal(LatexEagleOwl owl){this.owl=owl;}
    @Override public boolean canUse(){var target=owl.getTarget();return target!=null&&target.isAlive()&&owl.getUnderlyingPlayer()==null&&!net.parkabird.changedsynergy.compat.TrueTransfurCompat.isInstinctBody(owl)
            &&!EagleOwlTraits.regrabBlocked(owl)&&owl.level().getGameTime()>=nextGrab&&EagleOwlTraits.airborne(owl)&&EagleOwlTraits.canReach(owl,target)
            &&(target instanceof Player player?!player.isSpectator()&&!player.isCreative()&&Changed.config.server.isGrabEnabled.get()&&!net.ltxprogrammer.changed.process.ProcessTransfur.isPlayerTransfurred(player):target.getType().is(ChangedTags.EntityTypes.HUMANOIDS))&&!CreatureGrabService.isGrabberBusy(owl);}
    @Override public boolean canContinueToUse(){return false;}
    @Override public void start(){if(!canUse())return;nextGrab=owl.level().getGameTime()+80;var grab=CreatureGrabService.ensureAbility(owl);if(grab!=null&&owl.getTarget()!=null&&grab.grabEntity(owl.getTarget())){Changed.PACKET_HANDLER.send(PacketDistributor.TRACKING_ENTITY.with(()->owl),new GrabEntityPacket(owl,owl.getTarget(),GrabEntityPacket.GrabType.ARMS));ChangedSounds.broadcastSound(owl,ChangedSounds.LATEX_GRAB_ENTITY,1,1);}}
}
