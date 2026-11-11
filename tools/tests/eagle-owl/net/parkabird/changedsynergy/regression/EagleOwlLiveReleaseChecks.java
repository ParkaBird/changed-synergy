package net.parkabird.changedsynergy.regression;

import java.util.concurrent.CompletableFuture;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;

/** Exercises actual network tracking; direct ability calls alone miss relative-packet drift. */
final class EagleOwlLiveReleaseChecks {
    private static int stage,ticks,id,mode,checks;
    private static Vec3 origin,drop,returnPos;
    private static CompletableFuture<Void> work;
    static boolean tick(Minecraft mc){
        if(stage==9)return true;
        var server=mc.getSingleplayerServer();
        var player=server.getPlayerList().getPlayer(mc.player.getUUID());
        var grab=ProcessTransfur.getPlayerTransfurVariant(mc.player).getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
        if(stage==0){
            work=CompletableFuture.runAsync(()->{
                if(returnPos==null)returnPos=player.position();player.connection.teleport(8,140,8,0,0);player.setOnGround(false);player.getAbilities().flying=true;player.onUpdateAbilities();
                var target=mode!=1?EntityType.ZOMBIE.create(player.serverLevel()):net.parkabird.changedsynergy.init.ChangedSynergyEntities.LATEX_EAGLE_OWL.get().create(player.serverLevel());
                target.setNoAi(true);target.setNoGravity(true);target.setPos(8,140,8.5);player.serverLevel().addFreshEntity(target);id=target.getId();origin=target.position();
                var serverGrab=ProcessTransfur.getPlayerTransfurVariant(player).getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
                if(net.parkabird.changedsynergy.ai.PlayerHugService.mode(serverGrab)!=(mode==1))net.parkabird.changedsynergy.ai.PlayerHugService.action(player,net.parkabird.changedsynergy.network.PlayerHugActionPacket.TOGGLE,-1);
            },server);stage=1;ticks=0;
        }else if(stage==1){
            if(!work.isDone())return false;work.join();if(++ticks<20||!(mc.level.getEntity(id) instanceof LivingEntity))return false;
            var target=(LivingEntity)mc.level.getEntity(id);mc.player.setOnGround(false);
            if(mode==0){
                var oldSnapshot=new net.minecraft.nbt.CompoundTag();grab.saveData(oldSnapshot);
                ProcessTransfur.getPlayerTransfurVariant(mc.player).setSelectedAbility(ChangedAbilities.GRAB_ENTITY_ABILITY.get());net.ltxprogrammer.changed.init.ChangedKeyMappings.USE_ABILITY.setDown(true);check(grab.grabbedEntity==null,"ordinary owl acquisition waits for authority instead of predicting a hold");
                grab.readData(oldSnapshot);check(grab.grabbedEntity==null,"in-flight empty snapshot cannot release a predicted target");
            }else if(mode==2){check(grab.grabEntity(target),"client predicts deliberately rejected target");server.execute(()->{player.setOnGround(false);player.serverLevel().getEntity(id).setPos(16,140,8.5);});net.ltxprogrammer.changed.Changed.PACKET_HANDLER.sendToServer(net.ltxprogrammer.changed.network.packet.GrabEntityPacket.initialGrab(mc.player,target));}
            else net.parkabird.changedsynergy.network.ChangedSynergyNetwork.CHANNEL.sendToServer(new net.parkabird.changedsynergy.network.PlayerHugActionPacket(net.parkabird.changedsynergy.network.PlayerHugActionPacket.GRAB,id));
            stage=2;ticks=0;
        }else if(stage==2&&++ticks>=15){
            if(mode==2){
                check(grab.grabbedEntity==null,"server-rejected airborne acquisition clears client prediction");
                work=CompletableFuture.runAsync(()->{var rejected=player.serverLevel().getEntity(id);check(rejected.position().distanceTo(new Vec3(16,140,8.5))<.01&&!rejected.noPhysics,"rejected grab keeps authoritative target position and physics");rejected.discard();player.connection.teleport(returnPos.x,returnPos.y,returnPos.z,0,0);},server);stage=5;return false;
            }
            if(mode==0)net.ltxprogrammer.changed.init.ChangedKeyMappings.USE_ABILITY.setDown(false);
            work=CompletableFuture.runAsync(()->{
                var serverGrab=ProcessTransfur.getPlayerTransfurVariant(player).getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
                check(serverGrab.grabbedEntity!=null&&serverGrab.grabbedEntity.getId()==id,"server accepted the network grab");
                player.connection.teleport(20,148,20,0,0);
            },server);stage=3;ticks=0;
        }else if(stage==3){
            if(!work.isDone())return false;work.join();if(++ticks<35)return false;
            check(grab.grabbedEntity!=null,"client keeps the replicated hold");drop=mc.player.position();server.execute(()->{var falling=(Mob)player.serverLevel().getEntity(id);falling.setNoGravity(false);falling.setNoAi(false);falling.goalSelector.disableControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE);falling.goalSelector.disableControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.JUMP);});
            if(mode==0)grab.releaseEntity(true);
            else net.parkabird.changedsynergy.network.ChangedSynergyNetwork.CHANNEL.sendToServer(new net.parkabird.changedsynergy.network.PlayerHugActionPacket(net.parkabird.changedsynergy.network.PlayerHugActionPacket.RELEASE,-1));
            stage=4;ticks=0;
        }else if(stage==4){
            ticks++;var target=mc.level.getEntity(id);
            if(ticks>4){check(target!=null,"released target remains tracked");check(target.position().subtract(drop).horizontalDistanceSqr()<2,"tracked release stays at drop X/Z: "+target.position()+" drop="+drop+" origin="+origin);}
            if(ticks>25)check(target.getY()<drop.y-.5&&!target.noPhysics,"released target falls with ordinary physics: mode="+mode+" client="+target.position()+" drop="+drop+" noPhysics="+target.noPhysics+" noGravity="+target.isNoGravity()+" server="+player.serverLevel().getEntity(id).position());
            if(ticks<65)return false;
            work=CompletableFuture.runAsync(()->{
                check(!player.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN)&&!player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),"manual release does not apply escape effects");
                var targetOnServer=player.serverLevel().getEntity(id);check(targetOnServer!=null&&targetOnServer.position().subtract(drop).horizontalDistanceSqr()<2,"server also retains current release X/Z");targetOnServer.discard();
            },server);stage=5;
        }else if(stage==5&&work.isDone()){
            work.join();if(++mode<3){stage=0;}else{stage=6;ticks=0;System.out.println("OWL_LIVE_RELEASE_CLIENT_PASS: "+checks+" actual packet and delayed tracking checks");}
        }
        if(stage==6&&++ticks>25)stage=9;
        return stage==9;
    }
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
}
