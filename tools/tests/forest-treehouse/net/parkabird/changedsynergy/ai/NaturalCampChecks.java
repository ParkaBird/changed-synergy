package net.parkabird.changedsynergy.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.init.ChangedSynergyEntities;
import net.parkabird.changedsynergy.entity.LatexSpottedHyena;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostCargoInventory;

/** Real AI ticks transfer, harvest, replant and eat real finite items in a disposable authored camp. */
public final class NaturalCampChecks {
    private static final BlockPos BASE=new BlockPos(14000,120,14000), STORE=BASE.offset(5,1,7), CROP=BASE.offset(7,1,6);
    private static LatexSpottedHyena worker;
    private static int checks;
    private static boolean sawMeal;
    private static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);checks++;}
    public static void prepare(ServerLevel level) {
        ChangedSynergyConfig.COMMON.backgroundScanInterval.set(1);
        ChangedSynergyConfig.COMMON.distantAiThrottling.set(false);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,level.getServer());
        for(int cx=BASE.getX()>>4;cx<=(BASE.getX()+15)>>4;cx++)for(int cz=BASE.getZ()>>4;cz<=(BASE.getZ()+15)>>4;cz++) {
            level.getChunk(cx,cz);level.setChunkForced(cx,cz,true);
        }
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)level.setBlock(BASE.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
        level.setBlock(STORE,Blocks.BARREL.defaultBlockState(),2);
        level.setBlock(CROP.below(),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7),2);
        level.setBlock(CROP,Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_7,7),2);
        worker=ChangedSynergyEntities.LATEX_SPOTTED_HYENA_MALE.get().create(level);
        worker.moveTo(BASE.getX()+6.5,BASE.getY()+1,BASE.getZ()+6.5);
        var data=new CompoundTag();data.putBoolean("Camp",true);data.putBoolean("Resolved",true);
        data.putDouble("MinX",BASE.getX());data.putDouble("MinY",BASE.getY());data.putDouble("MinZ",BASE.getZ());
        data.putDouble("MaxX",BASE.getX()+16);data.putDouble("MaxY",BASE.getY()+10);data.putDouble("MaxZ",BASE.getZ()+16);
        data.putDouble("HomeX",worker.getX());data.putDouble("HomeY",worker.getY());data.putDouble("HomeZ",worker.getZ());
        worker.getPersistentData().put(StructureResidentGoal.DATA,data);
        level.addFreshEntity(worker);
        var boundary=worker.goalSelector.getAvailableGoals().stream().map(g->g.getGoal()).filter(g->g instanceof StructureResidentGoal).findFirst().orElseThrow();
        var outside=EntityType.ZOMBIE.create(level);outside.moveTo(BASE.getX()+100,BASE.getY()+1,BASE.getZ());worker.setTarget(outside);
        check(!boundary.canUse() && worker.getTarget()==null,"resident already at home drops outside target without repeatedly pathing home");
        worker.goalSelector.removeAllGoals(g->!(g instanceof NaturalCampGoal));worker.targetSelector.removeAllGoals(g->true);
        var drop=new net.minecraft.world.entity.item.ItemEntity(level,worker.getX()-1,worker.getY(),worker.getZ(),new ItemStack(Items.COBBLESTONE,5));level.addFreshEntity(drop);
        var livestock=EntityType.SHEEP.create(level);livestock.moveTo(worker.getX()+1,worker.getY(),worker.getZ()+1);livestock.setNoAi(true);
        livestock.addTag("changed_synergy:camp_livestock");level.addFreshEntity(livestock);
        float health=livestock.getHealth();worker.setTarget(livestock);
        check(worker.getTarget()==null,"camp livestock cannot become a hunt target");
        livestock.hurt(worker.damageSources().mobAttack(worker),3);check(livestock.getHealth()==health,"camp livestock cannot be damaged by resident hunting");
        check(NaturalCampService.ownsStorage(worker,STORE),"all structural storage belongs to the camp");
        check(CreatureCommunityData.bind(worker).isEmpty(),"authored camp is not a constructed outpost community");
        check(CreatureSettlementService.ensureCachePosition(worker).filter(STORE::equals).isPresent(),"resident uses camp store without building an outpost");
    }
    public static void tick(ServerLevel level,int tick) {
        if(tick==450) {
            var store=PlayerOutpostData.container(level,STORE);
            check(store.countItem(Items.COBBLESTONE)==5,"picked-up items deposited: ticks="+worker.tickCount+" pos="+worker.position()+" cargo="+PlayerOutpostCargoInventory.inventory(worker).workItems()+" alive="+worker.isAlive()+" grounded="+worker.onGround()+" enabled="+CreatureLifeMemory.enabled(worker)+" target="+worker.getTarget());
            check(store.countItem(Items.WHEAT)>0,"mature crop yield deposited");
            check(level.getBlockState(CROP).getBlock()==Blocks.WHEAT && level.getBlockState(CROP).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_7)<7,"crop replanted instead of destroyed");
            check(PlayerOutpostCargoInventory.inventory(worker).isEmpty(),"completed cargo is empty");
            store.setItem(4,new ItemStack(Items.APPLE,2));
            level.setBlock(BASE.offset(10,1,6),Blocks.CAMPFIRE.defaultBlockState(),2);
            level.setBlock(BASE.offset(13,1,6),BuiltInRegistries.BLOCK.get(new ResourceLocation("changed:white_pillow")).defaultBlockState(),2);
            level.setBlock(BASE.offset(10,1,9),Blocks.RED_CARPET.defaultBlockState(),2);
            // Refresh the finite site cache after adding the test campfire.
            worker.getPersistentData().putLong("ChangedSynergyNaturalCampNextMeal",0);
        }
        if(tick>450 && worker.isPassenger()) {
            sawMeal=true;
            check(worker.getVehicle() instanceof net.ltxprogrammer.changed.entity.SeatEntity seat
                    && seat.getAttachedBlockPos().equals(BASE.offset(13,1,6)),"native seat wins over carpet and open ground");
        }
        if(tick==1100) {
            var store=PlayerOutpostData.container(level,STORE);
            check(sawMeal,"resident sat down for a campfire meal");
            check(store.countItem(Items.APPLE)==1,"exactly one food portion consumed");
            check(!worker.isPassenger() && !CompanionAutoEatGoal.active(worker),"meal releases seat and restores offhand state");
            ChangedSynergyConfig.COMMON.naturalCampRoutines.set(false);
            var cargo=PlayerOutpostCargoInventory.inventory(worker);cargo.setItem(0,new ItemStack(Items.IRON_INGOT,3));
            check(!new NaturalCampGoal(worker).canUse() && cargo.countItem(Items.IRON_INGOT)==3,"disabled routines preserve cargo");
            var saved=new CompoundTag();worker.save(saved);worker.discard();
            var reloaded=(LatexSpottedHyena)EntityType.loadEntityRecursive(saved,level,e->e);level.addFreshEntity(reloaded);
            check(PlayerOutpostCargoInventory.inventory(reloaded).countItem(Items.IRON_INGOT)==3,"camp cargo survives entity unload and reload");
            ChangedSynergyConfig.COMMON.naturalCampRoutines.set(true);
            System.out.println("NATURAL_CAMP_ACTIONS_PASS: "+checks+" real pickup, deposit, crop care, finite meal, livestock, ownership, separation and config checks");
        }
    }
}
