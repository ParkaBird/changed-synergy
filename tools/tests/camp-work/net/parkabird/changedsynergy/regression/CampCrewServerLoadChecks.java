package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedBlocks;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.*;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/** Actual ticks of four independent six-member mixed crews, not a manual goal-loop benchmark. */
@Mod.EventBusSubscriber(modid=ChangedSynergyMod.MOD_ID)
public final class CampCrewServerLoadChecks {
    private static final int WARMUP=80, SAMPLES=200;
    private static Run active;
    private static final class Run {
        final MinecraftServer server;
        final List<ServerPlayer> owners=new ArrayList<>();
        final List<LivingEntity> bodies=new ArrayList<>();
        final List<ChangedEntity> crew=new ArrayList<>();
        final List<LivingEntity> targets=new ArrayList<>();
        final long[] nanos=new long[SAMPLES];
        long tickStart; int elapsed, busyTicks, combatTicks;
        Run(MinecraftServer server) { this.server=server; }
        void cleanup() {
            var data=PlayerOutpostData.get(server);
            for(var mob:crew) { PlayerOutpostService.clearAssignment(mob); mob.discard(); }
            targets.forEach(LivingEntity::discard);
            bodies.stream().filter(body->!(body instanceof ServerPlayer)).forEach(LivingEntity::discard);
            for(var owner:owners) { data.disband(owner.getUUID()); server.getPlayerList().remove(owner); }
        }
    }
    @SuppressWarnings("unchecked")
    static void start(MinecraftServer server) throws Exception {
        var run=new Run(server); active=run;
        try {
            var level=server.overworld(); var data=PlayerOutpostData.get(server);
            level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false,server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
            level.setDayTime(6000); level.updateSkyBrightness();
            ChangedSynergyConfig.COMMON.takeoverEnabled.set(false);
            for(int group=0;group<4;group++) {
                BlockPos center=new BlockPos(160+96*group,180,160);
                for(int x=-16;x<=16;x++)for(int z=-16;z<=16;z++) {
                    level.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
                    if(Math.abs(x)==16 || Math.abs(z)==16) for(int y=0;y<3;y++)
                        level.setBlock(center.offset(x,y,z),Blocks.GLASS.defaultBlockState(),2);
                }
                BlockPos marker=center.offset(-13,0,-13);
                level.setBlock(marker,Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT,false),2);
                var owner=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"CrewLoad"+group));
                owner.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),owner);
                owner.setPos(center.getX()+.5,center.getY(),center.getZ()+.5); owner.setOnGround(true);
                for(Field field:net.minecraft.server.players.PlayerList.class.getDeclaredFields()) {
                    if(Map.class.isAssignableFrom(field.getType()) && field.getGenericType().getTypeName().contains("UUID")
                            && field.getGenericType().getTypeName().contains("ServerPlayer")) {
                        field.setAccessible(true); ((Map<UUID,ServerPlayer>)field.get(server.getPlayerList())).put(owner.getUUID(),owner);
                    }
                }
                for(Field field:net.minecraft.server.players.PlayerList.class.getDeclaredFields()) {
                    if(List.class.isAssignableFrom(field.getType()) && field.getGenericType().getTypeName().contains("ServerPlayer")) {
                        field.setAccessible(true); ((List<ServerPlayer>)field.get(server.getPlayerList())).add(owner); break;
                    }
                }
                level.addNewPlayer(owner); run.owners.add(owner);
                LivingEntity body=owner;
                if(net.minecraftforge.fml.ModList.get().isLoaded("true_transfur")) {
                    net.ltxprogrammer.changed.process.ProcessTransfur.setPlayerTransfurVariant(owner,
                            net.ltxprogrammer.changed.init.ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(),null,1,false);
                    var capability=(net.minecraftforge.common.capabilities.Capability)Class.forName("com.lugert.true_transfur.transfur.TransfurCapabilities")
                            .getField("TRANSFUR_DATA").get(null);
                    Object state=owner.getCapability(capability).orElse(null);
                    if(state==null)throw new AssertionError("Missing TrueTransfur state for load player");
                    state.getClass().getMethod("setTransfurred",boolean.class).invoke(state,true);
                    Class mode=Class.forName("com.lugert.true_transfur.transfur.ControlMode");
                    Object instinct=Enum.valueOf(mode,"INSTINCT");
                    Class.forName("com.lugert.true_transfur.transfur.TransfurController")
                            .getMethod("switchControlMode",ServerPlayer.class,mode).invoke(null,owner,instinct);
                    body=net.parkabird.changedsynergy.compat.TrueTransfurCompat.interactionBody(owner);
                    if(!(body instanceof ChangedEntity proxy) || !net.parkabird.changedsynergy.compat.TrueTransfurCompat.isInstinctBody(proxy))
                        throw new AssertionError("TrueTransfur did not create a real Instinct body");
                    proxy.setNoAi(true); // Keep the four owners stationary; their crews still run every native goal.
                }
                run.bodies.add(body);
                if(!data.claim(level,owner.getUUID(),marker).equals("claimed"))throw new AssertionError("Load camp cannot be established");
                var camp=data.byOwner(owner.getUUID()).orElseThrow(); camp.meals=false;
                for(int x=6;x<12;x++)for(int z=-6;z<0;z++)level.setBlock(center.offset(x,0,z),Blocks.IRON_ORE.defaultBlockState(),2);
                for(int x=-10;x<-5;x++)for(int z=3;z<8;z++)level.setBlock(center.offset(x,2,z),ChangedBlocks.ORANGE_TREE_LEAVES.get().defaultBlockState(),2);
                var cow=EntityType.COW.create(level); cow.setPos(center.getX()+8.5,center.getY(),center.getZ()+8.5);
                level.addFreshEntity(cow); run.targets.add(cow);
                var enemy=EntityType.ZOMBIE.create(level); enemy.setPos(center.getX()+8.5,center.getY(),center.getZ()+.5); enemy.setNoAi(true);
                enemy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(2000); enemy.setHealth(2000);
                enemy.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.CARVED_PUMPKIN)); level.addFreshEntity(enemy); run.targets.add(enemy);
                body.setLastHurtMob(enemy);
                for(int i=0;i<6;i++) {
                    ChangedEntity mob=(i<2?ChangedEntities.DARK_LATEX_WOLF_MALE.get():ChangedEntities.LATEX_LEAF.get()).create(level);
                    mob.setPos(center.getX()-2.5+(i%3)*2,center.getY(),center.getZ()-3.5+(i/3)*2); mob.setOnGround(true); level.addFreshEntity(mob);
                    CreaturePersonality.establishRelationship(mob,owner); CreaturePersonality.setFamiliarity(mob,owner,60);
                    if(!data.add(camp,mob.getUUID(),"LoadCrew"+i,camp.dimension,mob.blockPosition(),PlayerOutpostSupplyType.of(mob)))throw new AssertionError("Load member cannot join");
                    CrewJob job=i<2?CrewJob.COMBAT:i<4?CrewJob.MINE:i==4?CrewJob.FORAGE:CrewJob.HUNT;
                    data.crewJob(camp,mob.getUUID(),job); PlayerOutpostService.applyRole(mob,owner,Role.CREW); run.crew.add(mob);
                }
            }
            System.out.println("CAMP_CREW_LOAD_START: owners=4 crew=24 instinctBodies="+run.bodies.stream().filter(body->body instanceof ChangedEntity).count()+" jobs=combat,mine,forage,hunt warmup="+WARMUP+" samples="+SAMPLES);
        } catch(Throwable failure) { active=null; run.cleanup(); throw failure; }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void begin(TickEvent.ServerTickEvent event) {
        if(active!=null && event.phase==TickEvent.Phase.START && event.getServer()==active.server)active.tickStart=System.nanoTime();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void end(TickEvent.ServerTickEvent event) {
        var run=active;
        if(run==null || event.phase!=TickEvent.Phase.END || event.getServer()!=run.server)return;
        if(run.elapsed>=WARMUP) {
            run.nanos[run.elapsed-WARMUP]=System.nanoTime()-run.tickStart;
            run.busyTicks+=(int)run.crew.stream().filter(mob->mob.goalSelector.getRunningGoals().anyMatch(g->g.getGoal() instanceof PlayerOutpostCrewGatherGoal)).count();
            run.combatTicks+=(int)run.crew.stream().filter(mob->mob.getTarget()!=null).count();
        }
        if(run.elapsed%40==0)for(int group=0;group<4;group++)run.bodies.get(group).setLastHurtMob(run.targets.get(group*2+1));
        if(++run.elapsed<WARMUP+SAMPLES)return;
        active=null;
        try {
            int minimumTicks=run.crew.stream().mapToInt(mob->mob.tickCount).min().orElse(0);
            int cargoItems=run.crew.stream().mapToInt(mob->net.parkabird.changedsynergy.world.inventory.PlayerOutpostCargoInventory.inventory(mob)
                    .workItems().stream().mapToInt(ItemStack::getCount).sum()).sum();
            long damagedEnemies=run.targets.stream().filter(target->target instanceof net.minecraft.world.entity.monster.Zombie
                    && target.getHealth()<target.getMaxHealth()).count();
            if(minimumTicks<SAMPLES || run.crew.stream().anyMatch(mob->!mob.isAlive()) || run.bodies.stream().anyMatch(body->!body.isAlive())
                    || run.busyTicks==0 || run.combatTicks==0 || cargoItems==0 || damagedEnemies!=4)
                throw new AssertionError("Mixed crews did not remain alive and execute both work and combat: min="+minimumTicks+" work="+run.busyTicks+" combat="+run.combatTicks);
            Arrays.sort(run.nanos);
            System.out.printf(Locale.ROOT,"CAMP_CREW_SERVER_LOAD_PASS: owners=4 crew=24 ticks=%d samples=%d workEntityTicks=%d combatEntityTicks=%d cargoItems=%d damagedEnemies=%d meanMs=%.3f p95Ms=%.3f maxMs=%.3f%n",
                    minimumTicks,SAMPLES,run.busyTicks,run.combatTicks,cargoItems,damagedEnemies,Arrays.stream(run.nanos).average().orElseThrow()/1e6,run.nanos[189]/1e6,run.nanos[199]/1e6);
        } catch(Throwable failure) { System.err.println("CAMP_CREW_SERVER_LOAD_FAIL"); failure.printStackTrace(); }
        finally { run.cleanup(); run.server.halt(false); }
    }
}
