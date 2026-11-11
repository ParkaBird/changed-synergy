package net.parkabird.changedsynergy.ai;

import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.entity.SpottedHyenaDiet;
import net.parkabird.changedsynergy.performance.SynergyPerformanceTracker;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostCargoInventory;

/** Bounded jobs use the player's existing saved cargo, storage adapters, meal and seat lifecycle. */
public final class NaturalCampGoal extends Goal {
    private enum Work { DEPOSIT, PICKUP, CROP, BERRIES, HUNT, MEAL }
    private final ChangedEntity mob;
    private Work work;
    private BlockPos target, fire;
    private ItemEntity drop;
    private Animal prey;
    private long nextCheck;
    private int ticks, seatedTicks;
    private boolean seated;
    private int scanCursor;
    private int pathProbes;
    private static final String NEXT_MEAL="ChangedSynergyNaturalCampNextMeal";

    public NaturalCampGoal(ChangedEntity mob) { this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
    private boolean available() {
        return NaturalCampService.available(mob) && !mob.isSleeping()
                && (!mob.isPassenger() || seated);
    }
    @Override public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel level) || level.getGameTime()<nextCheck) return false;
        if(!available() || mob.getTarget()!=null || mob.isUsingItem() || !PlayerOutpostGoal.canNavigate(mob)
                || !SynergyPerformanceTracker.allowBackground(mob,SynergyPerformanceTracker.Feature.COMMUNITY,
                    SynergyPerformanceTracker.configuredBackgroundInterval()))return false;
        nextCheck=level.getGameTime()+60+mob.getRandom().nextInt(61);
        var cargo=PlayerOutpostCargoInventory.inventory(mob);
        pathProbes=0;
        if(!cargo.isEmpty()) {
            for(var pos:orderedStorage()) {
                var chest=PlayerOutpostData.container(level,pos);
                if(chest!=null && accepts(chest,cargo) && reachable(pos)) {target=pos;work=Work.DEPOSIT;return true;}
            }
            return false; // Full or unloaded storage retains every item and stops further gathering.
        }
        var box=NaturalCampService.bounds(mob);
        boolean character=mob.getPersistentData().getCompound(StructureResidentGoal.DATA).getBoolean("Character");
        var area=character?box:NaturalCampService.activityBounds(mob);
        if(level.getGameTime()>=mob.getPersistentData().getLong(NEXT_MEAL) && selectMeal(level,box)) {work=Work.MEAL;return true;}
        for(var item:level.getEntitiesOfClass(ItemEntity.class,area,e->e.isAlive() && !e.hasPickUpDelay() && PlayerOutpostWorkGoal.unownedDrop(e))
                .stream().sorted(Comparator.comparingDouble(mob::distanceToSqr)).limit(8).toList()) {
            if(reachable(item.blockPosition())) {drop=item;target=item.blockPosition();work=Work.PICKUP;return true;}
        }
        if(ForgeEventFactory.getMobGriefingEvent(level,mob)) {
            // Resume a bounded local scan; blocked farms never trigger a whole-building scan each tick.
            for(int scanned=0;scanned<160;scanned++) {
                int i=Math.floorMod(scanCursor++,17*17*5);
                int dy=new int[]{0,1,-1,2,-2}[i/(17*17)];
                var pos=mob.blockPosition().offset(i%17-8,dy,(i/17)%17-8);
                if(!area.contains(Vec3.atCenterOf(pos)) || !level.hasChunkAt(pos))continue;
                var state=level.getBlockState(pos);
                if(state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) && reachable(pos)) {target=pos;work=Work.CROP;return true;}
                if(state.getBlock() instanceof SweetBerryBushBlock && state.getValue(SweetBerryBushBlock.AGE)>=2 && reachable(pos)) {target=pos;work=Work.BERRIES;return true;}
            }
        }
        if(!character && mob.getRandom().nextInt(4)==0) for(var animal:level.getEntitiesOfClass(Animal.class,area,
                a->PlayerOutpostWorkGoal.huntable(a) && !a.getTags().contains("changed_synergy:camp_livestock"))
                .stream().sorted(Comparator.comparingDouble(mob::distanceToSqr)).limit(4).toList()) {
            if(reachable(animal.blockPosition())) {prey=animal;target=animal.blockPosition();work=Work.HUNT;return true;}
        }
        return false;
    }
    private List<BlockPos> orderedStorage() {return NaturalCampService.sites(mob).storage().stream()
            .filter(p->net.parkabird.changedsynergy.npc.CampMembershipService.usableStorage(mob,p))
            .sorted(Comparator.comparingDouble(p->mob.distanceToSqr(Vec3.atCenterOf(p)))).limit(16).toList();}
    private boolean accepts(Container chest,Container cargo) {
        for(int i=0;i<cargo.getContainerSize();i++) {
            var item=cargo.getItem(i);if(item.isEmpty())continue;
            for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.canPlaceItem(slot,item)) {
                var old=chest.getItem(slot);
                if(old.isEmpty() || ItemStack.isSameItemSameTags(old,item) && old.getCount()<Math.min(old.getMaxStackSize(),chest.getMaxStackSize()))return true;
            }
        }
        return false;
    }
    private boolean reachable(BlockPos pos) {
        if(!mob.level().hasChunkAt(pos))return false;
        if(mob.distanceToSqr(Vec3.atCenterOf(pos))<6)return true;
        if(pathProbes++>=8)return false;
        var path=NaturalCampService.path(mob,pos,1);return path!=null && path.canReach();
    }
    private boolean selectMeal(ServerLevel level,net.minecraft.world.phys.AABB bounds) {
        boolean food=false;
        for(var pos:orderedStorage()) {
            var chest=PlayerOutpostData.container(level,pos);
            if(chest!=null && foodSlot(chest)>=0){food=true;break;}
        }
        if(!food)return false;
        for(var candidate:NaturalCampService.mealPoints(mob)) {
            boolean hot=level.getBlockState(candidate).getBlock() instanceof CampfireBlock;
            var places=new ArrayList<BlockPos>();
            for(var p:BlockPos.betweenClosed(candidate.offset(-5,-1,-5),candidate.offset(5,1,5))) {
                if(!bounds.contains(Vec3.atCenterOf(p)) || hot&&p.distSqr(candidate)<6 || !level.hasChunkAt(p))continue;
                if(PlayerOutpostRestGoal.isRestPoint(level.getBlockState(p))) {
                    if(PlayerOutpostRestGoal.free(mob,level,p))places.add(p.immutable());
                } else if(level.getBlockState(p).isAir() && level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP)
                        && !PlayerOutpostGoal.onCampfire(level,mob.getBoundingBox().move(Vec3.atBottomCenterOf(p).subtract(mob.position())))
                        && level.noCollision(mob,mob.getBoundingBox().move(Vec3.atBottomCenterOf(p).subtract(mob.position()))))places.add(p.immutable());
            }
            places.sort(Comparator.<BlockPos>comparingInt(p->CreatureRestPlaces.rank(level,p)).thenComparingDouble(p->mob.distanceToSqr(Vec3.atCenterOf(p))));
            for(var pos:places.stream().limit(6).toList())if(reachable(pos)){target=pos;fire=candidate;return true;}
        }
        return false;
    }
    private int foodSlot(Container chest) {
        for(int i=0;i<chest.getContainerSize();i++) {
            var item=chest.getItem(i);var food=SpottedHyenaDiet.foodProperties(item,mob);
            if((item.isEdible() || SpottedHyenaDiet.isSpeciesFood(item,mob)) && food!=null && food.getNutrition()>0 && !item.is(Items.GOLDEN_APPLE) && !item.is(Items.ENCHANTED_GOLDEN_APPLE)
                    && !item.is(Items.SUSPICIOUS_STEW) && !item.is(Items.CHORUS_FRUIT) && !RelationshipFavorService.isGoldenOrange(item)
                    && food.getEffects().stream().noneMatch(e->e.getFirst()!=null && !e.getFirst().getEffect().isBeneficial()))return i;
        }
        return -1;
    }
    @Override public void start(){ticks=seatedTicks=0;seated=false;mob.getNavigation().stop();}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public boolean canContinueToUse(){return ticks<300 && available() && (mob.getTarget()==null || mob.getTarget()==prey);}
    @Override public void tick() {
        ticks++;var level=(ServerLevel)mob.level();
        if(work==Work.HUNT) {
            if(prey==null || !prey.isAlive() || !NaturalCampService.activityBounds(mob).contains(prey.position())) {ticks=300;return;}
            target=prey.blockPosition();
            if(mob.distanceToSqr(prey)<5 && ticks%20==0) {
                mob.swing(InteractionHand.MAIN_HAND);
                prey.hurt(mob.damageSources().mobAttack(mob),(float)Math.max(1,mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)));
            }
        }
        if(work==Work.PICKUP && (drop==null || !drop.isAlive())){ticks=300;return;}
        if(seated) {
            mob.getNavigation().stop();seatedTicks++;
            mob.getLookControl().setLookAt(fire.getX()+.5,fire.getY()+.75,fire.getZ()+.5);
            if(seatedTicks==30) for(var pos:orderedStorage()) {
                var chest=PlayerOutpostData.container(level,pos);if(chest==null)continue;
                int slot=foodSlot(chest);if(slot>=0){CompanionAutoEatGoal.beginEating(mob,chest,slot);break;}
            }
            if(seatedTicks>=120)ticks=300;
            return;
        }
        if(mob.distanceToSqr(Vec3.atCenterOf(target))>6) {
            if(ticks%40==1 && !CreatureTaskNavigation.moveTo(mob,target,1,.35))ticks=300;
            return;
        }
        var cargo=PlayerOutpostCargoInventory.inventory(mob);
        switch(work) {
            case DEPOSIT -> {
                if(!net.parkabird.changedsynergy.npc.CampMembershipService.usableStorage(mob,target)){ticks=300;return;}
                var chest=PlayerOutpostData.container(level,target);
                if(chest!=null) {
                    boolean moved=false;
                    for(int i=0;i<cargo.getContainerSize();i++) {
                        var item=cargo.getItem(i);
                        if(item.isEmpty())continue;
                        int before=item.getCount();
                        var remainder=CreatureSettlementService.insert(chest,item);
                        cargo.setItem(i,remainder);
                        if(remainder.getCount()<before)moved=true;
                    }
                    if(moved)PlayerOutpostStorageAccess.touch(mob,target);
                }
                ticks=300;
            }
            case PICKUP -> {
                if(drop.isAlive() && mob.distanceToSqr(drop)<6) {
                    var remaining=PlayerOutpostWorkGoal.acceptCargo(mob,drop.getItem());
                    if(remaining.isEmpty())drop.discard();else drop.setItem(remaining);
                }
                ticks=300;
            }
            case CROP -> {
                var state=level.getBlockState(target);
                if(state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) && ForgeEventFactory.getMobGriefingEvent(level,mob)) {
                    var drops=new ArrayList<>(Block.getDrops(state,level,target,level.getBlockEntity(target),mob,mob.getMainHandItem()));
                    var seed=crop.getCloneItemStack(level,target,state);
                    var planting=drops.stream().filter(s->ItemStack.isSameItemSameTags(s,seed) && !s.isEmpty()).findFirst().orElse(null);
                    if(planting!=null && crop.getStateForAge(0).canSurvive(level,target) && level.setBlock(target,crop.getStateForAge(0),Block.UPDATE_ALL)) {
                        planting.shrink(1);
                        for(var item:drops)carry(item);
                        mob.swing(InteractionHand.MAIN_HAND);
                    }
                }
                ticks=300;
            }
            case BERRIES -> {
                var state=level.getBlockState(target);
                if(state.getBlock() instanceof SweetBerryBushBlock && state.getValue(SweetBerryBushBlock.AGE)>=2 && ForgeEventFactory.getMobGriefingEvent(level,mob)
                        && level.setBlock(target,state.setValue(SweetBerryBushBlock.AGE,1),Block.UPDATE_ALL))carry(new ItemStack(Items.SWEET_BERRIES,2));
                ticks=300;
            }
            case MEAL -> {
                if(PlayerOutpostRestGoal.isRestPoint(level.getBlockState(target)))seated=PlayerOutpostRestGoal.sit(mob,level,target);
                else seated=CreatureRestPlaces.sitOnGround(mob,level,target);
                if(!seated)ticks=300;
                else mob.getPersistentData().putLong(NEXT_MEAL,level.getGameTime()+6000);
            }
            case HUNT -> { }
        }
    }
    private void carry(ItemStack item){var remaining=PlayerOutpostWorkGoal.acceptCargo(mob,item);if(!remaining.isEmpty())mob.spawnAtLocation(remaining);}
    @Override public void stop() {
        mob.getNavigation().stop();CreatureTaskNavigation.clearFailure(mob);
        if(seated){CompanionAutoEatGoal.cleanup(mob);CreatureComfortGoal.endSeatedActivity(mob);}
        if(prey!=null && mob.getTarget()==prey)mob.setTarget(null);
        prey=null;drop=null;seated=false;
    }
}
