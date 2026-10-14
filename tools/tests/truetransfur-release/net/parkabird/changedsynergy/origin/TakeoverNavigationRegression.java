package net.parkabird.changedsynergy.origin;

import java.util.List;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;

/** Continuous production ticks, including Addon's baseTick and both real player connections. */
final class TakeoverNavigationRegression {
    static void run(MinecraftServer server, ServerPlayer human, ServerPlayer owner, ChangedEntity body) throws Exception {
        var level=server.overworld();var cfg=ChangedSynergyConfig.COMMON;
        var previous=cfg.creatureTransfurMethod.get();boolean pvp=server.isPvpAllowed();
        var sample=ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        try {
            server.setPvpAllowed(true);cfg.creatureTransfurMethod.set(ChangedSynergyConfig.CreatureTransfurMethod.TAKEOVER);
            Changed.config.server.isGrabEnabled.set(true);
            for(var pos:BlockPos.betweenClosed(new BlockPos(-20,179,-20),new BlockPos(20,179,20)))level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
            for(var pos:BlockPos.betweenClosed(new BlockPos(-20,180,-20),new BlockPos(20,183,20)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
            ProcessTransfur.removePlayerTransfurVariant(human);human.getPersistentData().remove("SynergyInstinctRestraintGrace");
            human.setPos(0.5,180,8.5);human.setHealth(human.getMaxHealth());
            FactionReputation.set(sample,human,-70);body.setPos(.5,180,.5);body.setNoAi(false);body.setOnGround(true);
            if(ModList.get().isLoaded("changed_addon"))Class.forName("net.foxyas.changedaddon.entity.api.IGrabberEntity").getMethod("setCanUseGrab",boolean.class).invoke(body,false);
            body.setTarget(null);int windups=0,lost=0,meleeStops=0;float health=human.getHealth();boolean pursued=false;
            for(int tick=0;tick<640&&!InstinctConfinementService.active(human);tick++) {
                tick(server,human,owner,body);
                if(!InstinctConfinementService.active(human)) {
                    if(body.getTarget()==human)pursued=true;
                    if(pursued&&body.getTarget()!=human)lost++;
                    if(ProcessTransfur.getPlayerTransfurProgress(human)>0
                            &&body.distanceToSqr(human)<2
                            &&body.goalSelector.getRunningGoals().noneMatch(g->g.getGoal() instanceof net.minecraft.world.entity.ai.goal.MeleeAttackGoal))meleeStops++;
                    var ability=BondedSuitService.ability(body);
                    if(ability!=null&&ability.getController().getHoldTicks()>0)windups++;
                }
                if(tick%20==0)trace(body,human,tick);
            }
            if(!InstinctConfinementService.active(human)||windups>0||lost>0||meleeStops>0||human.getHealth()!=health)
                throw new AssertionError("Production pursuit: active="+InstinctConfinementService.active(human)+" windups="+windups+" lost="+lost+" meleeStops="+meleeStops+" health="+human.getHealth()+"/"+health);
            System.out.println("TAKEOVER_NAVIGATION_PASS: continuous instinct pursuit completes normal transfur without wind-up or lost target");
            // A second eligible human remains reachable while the first is inside.
            var target=net.minecraft.world.entity.EntityType.ZOMBIE.create(level);target.setNoAi(true);target.setPos(-12.5,180,body.getZ());level.addFreshEntity(target);
            try {
                body.setTarget(target);double initial=body.distanceToSqr(target);
                for(int tick=0;tick<80;tick++){tick(server,human,owner,body);if(tick%20==0)trace(body,human,tick);}
                if(body.distanceToSqr(target)>=initial*.3)throw new AssertionError("Carrier did not traverse its original melee path: "+initial+" -> "+body.distanceToSqr(target));
                if(human.distanceToSqr(body)>.3||!human.noPhysics)throw new AssertionError("Native suit position/physics stopped ticking: distance="+human.distanceToSqr(body)+" noPhysics="+human.noPhysics);
                System.out.println("TAKEOVER_NAVIGATION_PASS: full carrier ticks retain continuous native combat navigation");
            } finally {target.discard();InstinctConfinementService.release(human);}
        } finally {sample.discard();cfg.creatureTransfurMethod.set(previous);server.setPvpAllowed(pvp);human.getPersistentData().remove("SynergyInstinctRestraintGrace");body.setTarget(null);body.setNoAi(true);body.setPos(human.position());}
    }
    private static void tick(MinecraftServer server,ServerPlayer human,ServerPlayer owner,ChangedEntity body)throws Exception {
        var count=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(MinecraftServer.class,"f_129766_");
        count.setInt(server,server.getTickCount()+1);
        var level=server.overworld();((ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime()+1);
        net.parkabird.changedsynergy.performance.SynergyPerformanceTracker.onServerTickStart(new TickEvent.ServerTickEvent(TickEvent.Phase.START,()->true,server));
        for(var player:List.of(human,owner)) {
            player.tickCount++;
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START,player));
            player.tick();
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));
        }
        body.tickCount++;body.tick();
        InstinctConfinementService.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,server));
    }
    private static void trace(ChangedEntity body,ServerPlayer human,int tick) {
        System.out.println("TAKEOVER_NAV_TRACE "+tick+" pos="+body.position()+" target="+(body.getTarget()==null?"null":body.getTarget().getType())+" progress="+ProcessTransfur.getPlayerTransfurProgress(human)
                +" hold="+(BondedSuitService.ability(body)==null?-1:BondedSuitService.ability(body).getController().getHoldTicks())+" goals="+body.goalSelector.getRunningGoals().map(g->g.getGoal().getClass().getSimpleName()).toList());
    }
}
