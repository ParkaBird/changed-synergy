package net.parkabird.changedsynergy.regression;

import java.util.concurrent.CompletableFuture;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.init.*;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.*;
import net.parkabird.changedsynergy.ai.EagleOwlTraits;

/** Hold the real native input before contact, including a rejected late acquisition packet. */
final class FollowupContactChecks {
    private static int stage,ticks,targetId,cowId;private static CompletableFuture<Void> work;
    static boolean tick(Minecraft mc){
        if(stage==9)return true;
        var server=mc.getSingleplayerServer();var player=server.getPlayerList().getPlayer(mc.player.getUUID());
        var grab=ProcessTransfur.getPlayerTransfurVariant(mc.player).getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
        if(stage==0){
            work=CompletableFuture.runAsync(()->{
                player.connection.teleport(8,160,8,0,0);player.getAbilities().flying=true;player.onUpdateAbilities();player.setOnGround(false);
                var target=EntityType.ZOMBIE.create(player.serverLevel());target.setNoAi(true);target.setNoGravity(true);target.setPos(15,160,8);player.serverLevel().addFreshEntity(target);targetId=target.getId();
                var cow=EntityType.COW.create(player.serverLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(8.6,160,8);player.serverLevel().addFreshEntity(cow);cowId=cow.getId();
            },server);stage=1;
        }else if(stage==1){
            if(!work.isDone())return false;work.join();if(++ticks<25||mc.level.getEntity(targetId)==null)return false;
            mc.setScreen(null);ProcessTransfur.getPlayerTransfurVariant(mc.player).setSelectedAbility(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
            ChangedKeyMappings.USE_ABILITY.setDown(true);stage=2;ticks=0;
        }else if(stage==2){
            ticks++;
            if(ticks==8||ticks==16)Changed.PACKET_HANDLER.sendToServer(GrabEntityPacket.initialGrab(mc.player,(LivingEntity)mc.level.getEntity(targetId)));
            check(grab.grabbedEntity==null,"ordinary airborne contact does not accept the nearby cow");
            if(ticks<24)return false;
            work=CompletableFuture.runAsync(()->{
                var g=ProcessTransfur.getPlayerTransfurVariant(player).getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
                check(g.getController().getHoldTicks()>0&&!EagleOwlTraits.regrabBlocked(player),"rejected packets do not disable a held ability or prolong release cooldown");
                player.serverLevel().getEntity(targetId).setPos(player.getX()+.7,player.getY(),player.getZ());
            },server);stage=3;ticks=0;
        }else if(stage==3){
            if(!work.isDone())return false;work.join();
            if(grab.grabbedEntity==null){if(++ticks>15)throw new AssertionError("held native ability failed to grab sideways contact without mouse aiming");return false;}
            check(grab.grabbedEntity.getId()==targetId,"server accepts only the eligible humanoid on contact");
            ChangedKeyMappings.USE_ABILITY.setDown(false);stage=4;ticks=0;
        }else if(stage==4){
            check(grab.grabbedEntity!=null&&grab.grabbedEntity.getId()==targetId,"contact hold persists after releasing the acquisition key");
            if(++ticks<15)return false;
            grab.releaseEntity(true);
            work=CompletableFuture.runAsync(()->{
                var g=ProcessTransfur.getPlayerTransfurVariant(player).getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
                if(g.grabbedEntity!=null)g.releaseEntity(true);
                check(!player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),"voluntary aerial release remains harmless");
                player.serverLevel().getEntity(targetId).discard();player.serverLevel().getEntity(cowId).discard();
            },server);stage=5;ticks=0;
        }else if(stage==5&&work.isDone()){
            work.join();if(++ticks<30)return false;stage=9;System.out.println("FOLLOWUP_NATIVE_CONTACT_PASS: real held key, delayed/rejected requests, eligible contact, stable hold and manual release");
        }
        return stage==9;
    }
    private static void check(boolean condition,String why){if(!condition)throw new AssertionError(why);}
}
