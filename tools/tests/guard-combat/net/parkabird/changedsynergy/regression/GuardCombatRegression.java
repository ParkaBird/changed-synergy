package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.event.NpcDispositionEvents;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

@Mod.EventBusSubscriber(modid="changed_synergy", value=net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER)
public final class GuardCombatRegression {
    private static MinecraftServer server;
    private static ServerLevel level;
    private static ServerPlayer owner;
    private static int checks, missed;
    private static void check(boolean ok,String why){checks++;if(!ok){missed++;System.out.println("GUARD_CHECK_MISSED: "+why);}}
    @SubscribeEvent public static void run(ServerStartedEvent event) {
        server=event.getServer();level=server.overworld();
        try {
            ChangedSynergyConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
            level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false,server);
            level.getGameRules().getRule(ChangedSynergyGameRules.CREATURE_LIFE).set(true,server);
            owner=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"GuardOwner"));
            owner.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),owner);
            var field=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.players.PlayerList.class,"f_11197_");field.setAccessible(true);
            ((Map<UUID,ServerPlayer>)field.get(server.getPlayerList())).put(owner.getUUID(),owner);
            floor(1000);floor(1200);owner.setPos(1000.5,100,1000.5);owner.tickCount=200;
            autonomousGuard();playerGuards();safeTargets();crossbow();excludedBosses();
            if(Boolean.getBoolean("changed_synergy.guardBaseline")){
                if(missed<2)throw new AssertionError("Expected the old release to reproduce both guard acquisition failures");
                System.out.println("GUARD_BASELINE_REPRODUCED: "+missed+" missed checks out of "+checks);
            } else {
                if(missed!=0)throw new AssertionError(missed+" guard/combat checks failed");
                System.out.println("GUARD_COMBAT_SERVER_PASS: "+checks+" guard acquisition, policies, safety, crossbow ammunition and excluded-boss checks");
            }
        }catch(Throwable failure){System.err.println("GUARD_COMBAT_SERVER_FAIL");failure.printStackTrace();}
        finally{server.halt(false);}
    }
    private static void floor(int x){
        for(int dx=-40;dx<=40;dx++)for(int z=960;z<=1040;z++)level.setBlock(new BlockPos(x+dx,99,z),Blocks.STONE.defaultBlockState(),2);
    }
    private static ChangedEntity guard(double x) {
        var mob=(ChangedEntity)ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse("changed:dark_latex_wolf_male")).create(level);
        mob.moveTo(x,100,1000.5);mob.setOnGround(true);mob.setPersistenceRequired();level.addFreshEntity(mob);
        CreatureLifeMemory.setRole(mob,CreatureLifeMemory.GroupRole.GUARD);return mob;
    }
    private static <T extends Mob>T mob(EntityType<T> type,double x){var mob=type.create(level);mob.moveTo(x,100,1000.5);mob.setOnGround(true);level.addFreshEntity(mob);return mob;}
    private static void autonomousGuard()throws Exception{
        var guard=guard(1200.5);var target=mob(EntityType.PILLAGER,1206.5);
        guard.tickCount=200+Math.floorMod(guard.getId(),20);CreatureRoleService.tick(guard);
        check(guard.getTarget()==target,"a wild/community Guard proactively acquires a visible Pillager");
        pursues(guard,target,"wild/community");
        guard.discard();target.discard();
    }
    private static void scan(ChangedEntity guard){guard.tickCount=200+Math.floorMod(guard.getId(),20);PlayerOutpostService.onTick(new LivingEvent.LivingTickEvent(guard));}
    private static void playerGuards()throws Exception{
        var marker=new BlockPos(1000,100,1000);level.setBlock(marker,Blocks.CAMPFIRE.defaultBlockState(),2);
        var data=PlayerOutpostData.get(server);check(data.claim(level,owner.getUUID(),marker).equals("claimed"),"test camp claimed");
        var home=data.byOwner(owner.getUUID()).orElseThrow();var guard=guard(1022.5);var target=mob(EntityType.PILLAGER,1028.5);
        CreaturePersonality.establishRelationship(guard,owner);CreaturePersonality.setFamiliarity(guard,owner,40);
        check(data.add(home,guard.getUUID(),"Gate Guard",level.dimension().location().toString(),guard.blockPosition(),PlayerOutpostSupplyType.of(guard)),"guard assigned");
        data.role(home,guard.getUUID(),PlayerOutpostData.Role.GUARD,guard.blockPosition());PlayerOutpostService.applyRole(guard,owner,PlayerOutpostData.Role.GUARD);
        scan(guard);check(guard.getTarget()==target,"a guard sees threats near itself beyond the marker's twelve-block scan");
        guard.setTarget(null);LatexSocialMemory.addBond(guard,owner);LatexSocialMemory.setFollowingOwner(guard,false);
        ChangedSynergyConfig.COMMON.companionMonsterAssistOnly.set(true);scan(guard);
        check(guard.getTarget()==target,"explicit camp guard duty overrides companion assist-only/default owner-hostile policy");
        owner.setLastHurtByMob(target);guard.setTarget(target);
        check(!LatexSocialMemory.rejectsFollowingCombatTarget(guard,target),"a stationed guard does not abandon defense when its owner is attacked");
        check(BondedOwnerDefenseGoal.allowsConfiguredDefense(guard,owner,target),"guard defense survives both combat policy gates");
        pursues(guard,target,"bonded player-camp");
        owner.setLastHurtByMob(null);guard.setTarget(null);
        data.role(home,guard.getUUID(),PlayerOutpostData.Role.SUPPLY,guard.blockPosition());PlayerOutpostService.applyRole(guard,owner,PlayerOutpostData.Role.SUPPLY);
        guard.setTarget(target);scan(guard);check(guard.getTarget()==null,"a Supply worker does not inherit guard aggression");
        guard.setLastHurtByMob(target);guard.setTarget(target);check(guard.getTarget()==target,"a worker can still defend itself");
        data.remove(home,guard.getUUID());guard.discard();target.discard();ChangedSynergyConfig.COMMON.companionMonsterAssistOnly.set(false);
    }
    private static void pursues(ChangedEntity guard,Mob target,String context)throws Exception{
        target.setNoAi(true);target.setInvulnerable(true);
        double initial=guard.distanceToSqr(target),best=initial;boolean retained=true;
        var ticks=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(MinecraftServer.class,"f_129766_");
        for(int i=0;i<80;i++){
            ticks.setInt(server,server.getTickCount()+1);server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);level.tickNonPassenger(guard);
            best=Math.min(best,guard.distanceToSqr(target));
            boolean engaged=guard.getTarget()==target||target.isRemoved()
                    ||net.ltxprogrammer.changed.ability.GrabEntityAbility.getGrabberSafe(target).filter(grabber->grabber.getEntity()==guard).isPresent();
            if(retained&&!engaged)System.out.println("GUARD_PURSUIT_INTERRUPTED: "+context+" tick="+i+" target="+guard.getTarget()+" busy="+CreatureGrabService.isGrabberBusy(guard)+" distance="+guard.distanceToSqr(target));
            retained&=engaged;
        }
        check(retained,context+" Guard continues pursuit or captures its target through ordinary entity/AI ticks");
        check(best<initial-4,context+" Guard actually moves towards the threat; distance="+initial+" -> "+best);
    }
    private static void acquire(ChangedEntity guard){guard.tickCount=200+Math.floorMod(guard.getId(),20);CreatureRoleService.tick(guard);}
    private static void safeTargets(){
        var guard=guard(1200.5);var creeper=mob(EntityType.CREEPER,1204.5);var neutral=mob(EntityType.ZOMBIFIED_PIGLIN,1205.5);
        acquire(guard);check(guard.getTarget()==null,"guards leave unprovoked neutral mobs and Creepers alone");creeper.discard();neutral.discard();
        var target=mob(EntityType.PILLAGER,1220.5);acquire(guard);check(guard.getTarget()==null,"guard acquisition is local");target.setPos(1206.5,100,1000.5);
        for(int z=998;z<=1002;z++)for(int y=100;y<=103;y++)level.setBlock(new BlockPos(1203,y,z),Blocks.STONE.defaultBlockState(),2);
        acquire(guard);check(guard.getTarget()==null,"guards do not detect Pillagers through a solid wall");
        for(int z=998;z<=1002;z++)for(int y=100;y<=103;y++)level.setBlock(new BlockPos(1203,y,z),Blocks.AIR.defaultBlockState(),2);
        var team=server.getScoreboard().addPlayerTeam("guard_test");server.getScoreboard().addPlayerToTeam(guard.getScoreboardName(),team);server.getScoreboard().addPlayerToTeam(target.getScoreboardName(),team);
        acquire(guard);check(guard.getTarget()==null,"guard duty respects scoreboard alliances");server.getScoreboard().removePlayerTeam(team);
        acquire(guard);check(guard.getTarget()==target,"the same visible hostile becomes a valid target after the alliance ends");guard.discard();target.discard();
    }
    private static void crossbow()throws Exception{
        var guard=guard(1200.5);var pillager=mob(EntityType.PILLAGER,1205.5);var crossbow=new ItemStack(Items.CROSSBOW);pillager.setItemInHand(InteractionHand.MAIN_HAND,crossbow);pillager.setTarget(guard);
        check(pillager.canAttack(guard)&&pillager.getTarget()==guard,"Pillagers retain attacks against latex creatures");
        check(pillager.getProjectile(crossbow).is(Items.ARROW),"a vanilla Pillager supplies an Arrow instead of Air");
        ((CrossbowItem)Items.CROSSBOW).releaseUsing(crossbow,level,pillager,crossbow.getUseDuration()-CrossbowItem.getChargeDuration(crossbow));
        var loaded=crossbow.getOrCreateTag().getList("ChargedProjectiles",10);
        check(CrossbowItem.isCharged(crossbow)&&!loaded.isEmpty()&&loaded.stream().allMatch(t->ItemStack.of((CompoundTag)t).is(Items.ARROW)),"crossbow charge stores real Arrow stacks");
        CrossbowItem.performShooting(level,pillager,InteractionHand.MAIN_HAND,crossbow,1.6F,0);
        check(!level.getEntitiesOfClass(AbstractArrow.class,pillager.getBoundingBox().inflate(8)).isEmpty(),"a charged Pillager actually creates an arrow projectile");
        level.getEntitiesOfClass(AbstractArrow.class,pillager.getBoundingBox().inflate(8)).forEach(Entity::discard);
        guard.setNoAi(true);guard.setInvulnerable(true);
        var ticks=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(MinecraftServer.class,"f_129766_");
        boolean retained=true;
        for(int i=0;i<140;i++){
            ticks.setInt(server,server.getTickCount()+1);level.tickNonPassenger(pillager);
            retained&=pillager.getTarget()==guard;
        }
        check(retained,"native Pillager AI keeps its latex target throughout repeated crossbow charge/attack ticks");
        check(!level.getEntitiesOfClass(AbstractArrow.class,pillager.getBoundingBox().inflate(16)).isEmpty(),"native Pillager AI completes charging and fires without manually releasing the crossbow");
        level.getEntitiesOfClass(AbstractArrow.class,pillager.getBoundingBox().inflate(16)).forEach(Entity::discard);guard.discard();pillager.discard();
    }
    private static void excludedBosses()throws Exception{
        for(String id:List.of("experiment_009_boss","experiment_10_boss","latex_snow_fox_foxyas","void_fox","luminarctic_leopard_male","luminarctic_leopard_female")){
            var key=ResourceLocation.parse("changed_addon:"+id);if(!ForgeRegistries.ENTITY_TYPES.containsKey(key))continue;
            var type=ForgeRegistries.ENTITY_TYPES.getValue(key);
            var boss=(ChangedEntity)type.create(level);boss.moveTo(1200.5,100,1000.5);boss.setOnGround(true);level.addFreshEntity(boss);
            if(id.startsWith("luminarctic_"))boss.getClass().getMethod("setBoss",boolean.class).invoke(boss,true);
            check(!CreatureSocialProfile.allowsSynergySystems(boss),"native Boss excluded: "+id);
            owner.setPos(1210.5,100,1000.5);LatexSocialMemory.beginTruce(boss,owner,400);boss.setTarget(owner);
            boolean targeted=boss.getTarget()==owner;check(targeted,"Boss begins its native pursuit: "+id);
            if(targeted){
                boss.getNavigation().moveTo(owner,.4);var path=boss.getNavigation().getPath();
                for(int i=0;i<4;i++){owner.tickCount=200+i*10;NpcDispositionEvents.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,owner));}
                check(boss.getTarget()==owner,"social awareness scans preserve an excluded Boss's combat target: "+id);
                if(path!=null)check(boss.getNavigation().getPath()==path,"social awareness scans preserve native Boss navigation: "+id);
            }
            boss.discard();
        }
    }
}
