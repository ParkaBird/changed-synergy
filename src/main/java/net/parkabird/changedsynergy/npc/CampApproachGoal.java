package net.parkabird.changedsynergy.npc;

import java.util.*;
import net.ltxprogrammer.changed.entity.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;

/** Residents offer conversation without taking keyboard focus or leaving their home. */
public final class CampApproachGoal extends Goal {
    private static final Map<UUID,CampApproachGoal> invitations=new HashMap<>();
    private final ChangedEntity mob;private final CompanionFollowNavigation movement;private ServerPlayer player;private long nextScan;private int ticks;private boolean greeted;
    public CampApproachGoal(ChangedEntity mob){this.mob=mob;movement=new CompanionFollowNavigation(mob);setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    public static void dismiss(ServerPlayer player){var offer=invitations.remove(player.getUUID());if(offer!=null)offer.ticks=300;}
    public static void clear(){invitations.clear();}
    public static void left(ChangedEntity npc){invitations.entrySet().removeIf(e->e.getValue().mob==npc);}
    public static void reap(net.minecraft.server.MinecraftServer server){invitations.entrySet().removeIf(e->{var offer=e.getValue();return offer.player==null||server.getPlayerList().getPlayer(e.getKey())!=offer.player||!offer.canContinueToUse();});}
    public static boolean shareMeal(ChangedEntity npc,ServerPlayer player){
        if(!CampMembershipService.protectedMember(npc,player)||!CampLifeService.safe(npc)||CampLifeService.count(npc,CampLifeService.Supply.FOOD)<1)return false;
        if(NaturalCampService.mealPoints(npc).isEmpty())return false;var camp=CampLifeService.register(npc);long now=npc.level().getGameTime();
        if(camp.contains("MealAt")&&now-camp.getLong("MealAt")<6000)return false;
        camp.putLong("MealAt",now);CampLifeData.get(player.server).setDirty();
        for(var resident:CampLifeService.residents(npc))resident.getPersistentData().remove("ChangedSynergyNaturalCampNextMeal");return true;
    }
    private boolean idle(){return CampLifeService.mainCamp(mob)&&net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.naturalCampRoutines.get()&&mob.isAlive()&&!mob.isNoAi()&&!mob.isSleeping()&&!PlayerOutpostRestGoal.sleepTime(mob)&&mob.getTarget()==null&&!mob.isPassenger()&&!mob.isLeashed()&&!NpcConversations.holds(mob)&&!CreaturePeerInteractionService.active(mob)&&!SocialAudienceGoal.isActive(mob)&&!ProvisionerTradeService.isTrading(mob)&&!CreatureGrabService.isGrabberBusy(mob)&&!CreatureCommunityData.isPlayerCompanion(mob)&&!PlayerOutpostService.hasAssignment(mob);}
    private boolean available(ServerPlayer candidate){
        var body=TrueTransfurCompat.interactionBody(candidate);
        return candidate.isAlive()&&!candidate.isSpectator()&&!candidate.isSleeping()&&candidate.containerMenu==candidate.inventoryMenu&&!NpcConversations.talking(candidate)&&!TakeoverService.active(candidate)
            &&body.level()==mob.level()&&mob.distanceToSqr(body)<=144&&NaturalCampService.bounds(mob).contains(body.position())&&mob.hasLineOfSight(body)
            &&CampMembershipService.protectedMember(mob,candidate)&&net.ltxprogrammer.changed.ability.GrabEntityAbility.getGrabber(candidate)==null
            &&(candidate.getLastHurtByMob()==null||candidate.tickCount-candidate.getLastHurtByMobTimestamp()>=100)
            &&(candidate.getLastHurtMob()==null||candidate.tickCount-candidate.getLastHurtMobTimestamp()>=100);
    }
    @Override public boolean canUse(){
        if(!(mob.level() instanceof ServerLevel level)||level.getGameTime()<nextScan)return false;
        if(!idle()||!net.parkabird.changedsynergy.performance.SynergyPerformanceTracker.allowBackground(mob,net.parkabird.changedsynergy.performance.SynergyPerformanceTracker.Feature.SOCIAL,100))return false;
        nextScan=level.getGameTime()+100+mob.getRandom().nextInt(101);
        if(mob.getPersistentData().contains("SynergyCampInviteDay")&&mob.getPersistentData().getLong("SynergyCampInviteDay")==CampLifeService.day(mob))return false;
        var camp=CampLifeService.register(mob);if(camp.getLong("NextInvite")>level.getGameTime()||CampLifeService.forResident(mob)==null)return false;
        player=level.players().stream().filter(p->!invitations.containsKey(p.getUUID())&&available(p)).min(Comparator.comparingDouble(p->mob.distanceToSqr(TrueTransfurCompat.interactionBody(p)))).orElse(null);
        return player!=null;
    }
    @Override public void start(){
        ticks=0;greeted=false;movement.reset();invitations.put(player.getUUID(),this);mob.getPersistentData().putLong("SynergyCampInviteDay",CampLifeService.day(mob));
        CampLifeService.register(mob).putLong("NextInvite",mob.level().getGameTime()+6000);CampLifeData.get(player.server).setDirty();
    }
    @Override public boolean canContinueToUse(){return ticks<300&&player!=null&&invitations.get(player.getUUID())==this&&idle()&&available(player);}
    @Override public void tick(){
        ticks++;var body=TrueTransfurCompat.interactionBody(player);mob.getLookControl().setLookAt(body,25,25);
        if(mob.distanceToSqr(body)>6.25){if(ticks%40==1&&!movement.moveToward(body,.35))ticks=300;}
        else {mob.getNavigation().stop();if(!greeted){greeted=true;NpcDialogue.emoteOnly(mob,Emote.CASUAL);NpcDialogue.context(mob,player,"camp_request_invite");}}
    }
    @Override public void stop(){if(player!=null)invitations.remove(player.getUUID(),this);mob.getNavigation().stop();movement.reset();player=null;}
}
