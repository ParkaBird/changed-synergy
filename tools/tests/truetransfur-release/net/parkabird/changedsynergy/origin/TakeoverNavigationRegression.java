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
            lethalInstinctConversion(server, body);
        } finally {sample.discard();cfg.creatureTransfurMethod.set(previous);server.setPvpAllowed(pvp);human.getPersistentData().remove("SynergyInstinctRestraintGrace");body.setTarget(null);body.setNoAi(true);body.setPos(human.position());}
    }
    private static void lethalInstinctConversion(MinecraftServer server, ChangedEntity body) throws Exception {
        var rules = server.overworld().getGameRules();
        var key = net.ltxprogrammer.changed.init.ChangedGameRules.RULE_KEEP_BRAIN;
        boolean kept = rules.getBoolean(key);
        var owner = net.parkabird.changedsynergy.compat.TrueTransfurCompat.bodyOwner(body);
        var ownerForm = ProcessTransfur.getPlayerTransfurVariant(owner);
        try {
            for (boolean cached : new boolean[]{false, true}) {
                var human = new ServerPlayer(server, server.overworld(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "InstinctFatal"));
                human.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server,
                        new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), human);
                net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, human, 0, "f_8921_");
                var identity = new NativeOriginData.Identity(); identity.state = "human";
                NativeOriginData.get(server).players.put(human.getUUID(), identity);
                human.setPos(body.position().add(1, 0, 0)); FactionReputation.set(body, human, -70);
                try {
                    rules.getRule(key).set(cached, server);
                    var decision = net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision.strong(
                            net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision.Method.ABSORPTION,
                            body.getSelfVariant(), net.ltxprogrammer.changed.entity.TransfurContext.npcLatexHazard(body,
                                    net.ltxprogrammer.changed.entity.TransfurCause.GRAB_ABSORB), 100F);
                    var behavior = ProcessTransfur.computeAssimilationBehavior(human, decision);
                    rules.getRule(key).set(false, server);
                    if (!InstinctConfinementService.canTarget(body, human) || InstinctConfinementService.begin(body, human))
                        throw new AssertionError("Rule-off Instinct hostility must remain while takeover is rejected");
                    behavior.stepAssimilate();
                    var form = ProcessTransfur.getPlayerTransfurVariant(human);
                    if (form != null) {
                        var tick = form.getClass().getDeclaredMethod("tickTransfurProgress"); tick.setAccessible(true);
                        for (int i=0; i<800 && form.transfurProgression<1 && human.isAlive(); i++) tick.invoke(form);
                    }
                    if (human.isAlive() || TakeoverService.active(human) || !body.isAlive() || body.isRemoved()
                            || net.parkabird.changedsynergy.compat.TrueTransfurCompat.interactionBody(owner)!=body
                            || ProcessTransfur.getPlayerTransfurVariant(owner)!=ownerForm)
                        throw new AssertionError("Rule-off Instinct conversion must kill only the victim, cached="+cached);
                    System.out.println("KEEP_CONSCIOUS_INSTINCT_PASS: victim dies; no takeover; real owner, body, form and mode retained; cached="+cached);
                } finally { human.discard(); }
            }
        } finally { rules.getRule(key).set(kept, server); }
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
