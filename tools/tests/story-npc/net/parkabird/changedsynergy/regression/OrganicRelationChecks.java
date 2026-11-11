package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.event.*;
import net.parkabird.changedsynergy.origin.*;

/** Exercises the real swipe, target and hostile-grab paths for organic NPCs. */
final class OrganicRelationChecks {
    private static int checks, outsiders;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    static void run(MinecraftServer server)throws Exception {
        var level=server.overworld();
        var player=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"OrganicRelations"));
        player.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),player);
        var identity=new NativeOriginData.Identity();identity.state="human";NativeOriginData.get(server).players.put(player.getUUID(),identity);
        var field=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.players.PlayerList.class,"f_11197_");field.setAccessible(true);
        @SuppressWarnings("unchecked")var players=(Map<UUID,ServerPlayer>)field.get(server.getPlayerList());players.put(player.getUUID(),player);level.addNewPlayer(player);player.tickCount=100;
        player.setPos(.5,100,.5);level.getChunk(player.blockPosition());
        boolean secondary=ChangedSynergyConfig.COMMON.allowSecondaryTransfur.get();ChangedSynergyConfig.COMMON.allowSecondaryTransfur.set(true);
        try {
            for(String type:List.of("changed:gas_wolf_male","changed:gas_tiger","changed_addon:mirror_white_tiger")) {
                var entityType=ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(type));if(entityType==null||entityType==EntityType.PIG)continue;
                var mob=(ChangedEntity)entityType.create(level);mob.setPos(1.5,100,.5);mob.setNoAi(true);level.addFreshEntity(mob);
                try {
                    check(LatexSocialMemory.isOrganic(mob),"real organic NPC: "+type);
                    for(String form:List.of(type,"changed:dark_latex_wolf_male","changed:latex_hypno_cat","changed_synergy:latex_eagle_owl","changed_synergy:latex_swordfish")) {
                        var formType=ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(form));if(formType==null||formType==EntityType.PIG)continue;
                        var sample=(ChangedEntity)formType.create(level);var variant=sample.getSelfVariant();sample.discard();if(variant==null)continue;
                        ProcessTransfur.setPlayerTransfurVariant(player,variant,null,1,false);
                        LatexSocialMemory.reconcileRelationship(mob,player);FactionReputation.set(mob,player,0);
                        var relation=LatexSocialRelation.between(mob,player);
                        System.out.println("ORGANIC_RELATION_CASE: "+type+" -> "+form+" "+relation);
                        if(relation.isNormallyNeutral()) {
                            if(relation==LatexSocialRelation.OUTSIDER)outsiders++;
                            check(LatexSocialMemory.shouldRemainNeutral(mob,player),"neutral organic relation must remain neutral: "+type+" -> "+form+" "+relation);
                            check(NpcDispositionEvents.mustRejectTarget(mob,player)&&!NpcDispositionEvents.hasHostileDisposition(mob,player),"target acquisition respects neutral relation");
                            check(!LatexSocialMemory.mayInitiateHostileGrab(mob,player)&&!HuntAIEvents.canReacquire(mob,player,true),"grab and pursuit respect neutral relation");
                            mob.setTarget(player);check(mob.getTarget()!=player,"native target assignment rejects a neutral player");
                            var before=ProcessTransfur.getPlayerTransfurVariant(player).getParent();float health=player.getHealth();player.invulnerableTime=0;
                            mob.doHurtTarget(player);
                            check(ProcessTransfur.getPlayerTransfurVariant(player).getParent()==before&&player.getHealth()==health,"stale native swipe cannot change a neutral player's form or health");
                            LatexSocialMemory.markProvoked(mob,player);
                            check(!LatexSocialMemory.shouldRemainNeutral(mob,player)&&NpcDispositionEvents.hasHostileDisposition(mob,player),"fresh aggression still permits organic self-defense");
                            LatexSocialMemory.reconcileRelationship(mob,player);FactionReputation.set(mob,player,-80);
                            check(!LatexSocialMemory.shouldRemainNeutral(mob,player)&&NpcDispositionEvents.hasHostileDisposition(mob,player),"genuine hostile standing still permits organic combat");
                            FactionReputation.set(mob,player,0);
                        }
                    }
                    LatexSocialMemory.reconcileRelationship(mob,player);FactionReputation.set(mob,player,0);CreaturePersonality.establishRelationship(mob,player);
                    check(CreaturePersonality.hasTrustedRelationship(mob,player),"trusted organic acquaintance is retained");
                    LatexSocialMemory.addBond(mob,player);
                    check(LatexSocialMemory.shouldRemainNeutral(mob,player)&&!LatexSocialMemory.mayInitiateHostileGrab(mob,player),"bonded organic never re-transfurs its player");
                }finally{mob.discard();}
            }
            check(outsiders>0,"matrix includes the reported neutral outsider path");
            System.out.println("ORGANIC_RELATIONS_PASS: "+checks+" actual species relations, targeting, swipes, provocation and bond checks");
        }finally{ChangedSynergyConfig.COMMON.allowSecondaryTransfur.set(secondary);players.remove(player.getUUID());player.discard();}
    }
}
