package net.parkabird.changedsynergy.regression;

import java.util.UUID;
import net.ltxprogrammer.changed.entity.LivingEntityDataExtension;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.init.*;

final class EagleOwlReleaseChecks {
    static void run(net.minecraft.server.MinecraftServer server){
        var level=server.overworld();int checks=0;
        for(boolean owl:new boolean[]{false,true})for(boolean friendly:new boolean[]{false,true}){
            var player=new ServerPlayer(server,level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"AirRelease"));
            player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player);
            player.setPos(1,150,1);player.setOnGround(false);
            var form=ProcessTransfur.setPlayerTransfurVariant(player,owl?ChangedSynergyTransfurVariants.LATEX_EAGLE_OWL.get():net.ltxprogrammer.changed.init.ChangedTransfurVariants.LATEX_CROW.get(),null,1,false);var grab=form.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
            var target=friendly?ChangedSynergyEntities.LATEX_EAGLE_OWL.get().create(level):EntityType.ZOMBIE.create(level);target.setPos(1.4,150,1);level.getChunk(target.blockPosition());level.addFreshEntity(target);
            try{
                if(friendly)PlayerHugService.action(player,net.parkabird.changedsynergy.network.PlayerHugActionPacket.TOGGLE,-1);
                check(grab.grabEntity(target),"air grab begins, friendly="+friendly);checks++;
                grab.tickIdle();player.setPos(80,165,80);var drop=player.position();
                // Release before another hold tick: the victim still has its old tracking position.
                grab.releaseEntity(false);
                check(target.position().distanceTo(drop)<.001,"air release uses current carrier location rather than acquisition point");checks++;
                check(grab.grabbedEntity==null&&!target.noPhysics&&((LivingEntityDataExtension)target).getGrabbedBy()==null,"release clears render/physics ownership");checks++;
                check(EagleOwlTraits.regrabBlocked(player)&&!grab.canUse()&&!grab.grabEntity(target)&&EagleOwlTraits.contactTarget(grab)==null,"release cooldown blocks contact and explicit reacquisition");checks++;
                check(player.getPersistentData().getLong(EagleOwlTraits.REGRAB_AFTER)-level.getGameTime()==20,"one-second airborne regrab cooldown");checks++;
                player.getPersistentData().putLong(EagleOwlTraits.REGRAB_AFTER,level.getGameTime());check(!EagleOwlTraits.regrabBlocked(player),"cooldown expires on the game clock");checks++;
            }finally{target.discard();ProcessTransfur.removePlayerTransfurVariant(player);player.discard();}
        }
        var player=new ServerPlayer(server,level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"EscapePenalty"));
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player);
        player.setPos(1,150,1);player.setOnGround(false);
        var form=ProcessTransfur.setPlayerTransfurVariant(player,ChangedSynergyTransfurVariants.LATEX_EAGLE_OWL.get(),null,1,false);var grab=form.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
        var zombie=EntityType.ZOMBIE.create(level);zombie.setPos(1,150,1);level.addFreshEntity(zombie);
        try{
            check(grab.grabEntity(zombie),"ordinary hold for manual release");grab.grabStrength=.5F;grab.releaseEntity(true);
            check(!player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)&&!player.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN),"manual release is harmless before prey escapes");checks++;
            player.getPersistentData().remove(EagleOwlTraits.REGRAB_AFTER);
            check(grab.grabEntity(zombie),"fresh hold for actual escape");grab.grabStrength=0;grab.releaseEntity(true);
            check(player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)&&player.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN),"genuine depleted-strength escape retains the penalty");checks++;
        }finally{zombie.discard();ProcessTransfur.removePlayerTransfurVariant(player);player.discard();}
        System.out.println("OWL_AIR_RELEASE_SERVER_PASS: "+checks+" normal/friendly current-position release and reacquisition checks");
    }
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
}
