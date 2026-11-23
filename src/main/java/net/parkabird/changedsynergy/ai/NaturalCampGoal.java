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

/**
 * Camp tasks that may leave the idle station: deposit, pickup, crop (farming), berries,
 * glow berries, hunt, and meal. On {@link #stop()} the resident is marked to walk HomeX/Y/Z
 * again via {@link StructureResidentGoal.IdleStationGoal}. Solo meals use NEXT_MEAL; chief
 * summons use {@link NaturalCampService#hasActiveMealCall}.
 */
public final class NaturalCampGoal extends Goal {
    private enum Work { DEPOSIT, PICKUP, CROP, BERRIES, GLOW_BERRIES, HUNT, MEAL }
    private final ChangedEntity mob;
    private Work work;
    private BlockPos target, fire, harvestBlock;
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
    private static final int MAX_MEAL_TRAVEL_TICKS = 1200;
    private static final int MAX_STALLED_TICKS = 140;
    /** Same as ChangedEntity RandomStrollGoal — ordinary latex walk, not a sprint modifier. */
    private static final double MEAL_WALK_SPEED = 0.3D;
    private static final java.util.Map<ChangedEntity, BlockPos> ACTIVE_MEALS = new java.util.WeakHashMap<>();
    private net.minecraft.world.level.pathfinder.Path initialPath;
    private Vec3 lastPos;
    private int stalledTicks;
    public void resetWait() { nextCheck = 0L; }
    private static boolean mealSeatFree(ChangedEntity mob, BlockPos pos) {
        ACTIVE_MEALS.entrySet().removeIf(e -> e.getKey() == null || !e.getKey().isAlive() || e.getKey().isRemoved());
        for (var entry : ACTIVE_MEALS.entrySet()) {
            if (entry.getKey() != mob && entry.getKey().level() == mob.level() && pos.equals(entry.getValue())) return false;
        }
        return true;
    }
    @Override public boolean canUse() {
        boolean mealCall = NaturalCampService.hasActiveMealCall(mob);
        if (!(mob.level() instanceof ServerLevel level) || (!mealCall && level.getGameTime()<nextCheck)) return false;
        if (!mealCall && NaturalCampService.restsHere(mob) && PlayerOutpostRestGoal.sleepTime(mob)) return false;
        if(!available() || mob.getTarget()!=null || mob.isUsingItem() || !PlayerOutpostGoal.canNavigate(mob)
                || (!mealCall && !SynergyPerformanceTracker.allowBackground(mob,SynergyPerformanceTracker.Feature.COMMUNITY,
                    SynergyPerformanceTracker.configuredBackgroundInterval())))return false;
        if (!mealCall) nextCheck=level.getGameTime()+60+mob.getRandom().nextInt(61);
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
        if((NaturalCampService.hasActiveMealCall(mob) || level.getGameTime()>=mob.getPersistentData().getLong(NEXT_MEAL)) && selectMeal(level,box)) {work=Work.MEAL;return true;}
        for(var item:level.getEntitiesOfClass(ItemEntity.class,area,e->e.isAlive() && !e.hasPickUpDelay() && PlayerOutpostWorkGoal.unownedDrop(e))
                .stream().sorted(Comparator.comparingDouble(mob::distanceToSqr)).limit(8).toList()) {
            if(reachable(item.blockPosition())) {drop=item;target=item.blockPosition();work=Work.PICKUP;return true;}
        }
        if(ForgeEventFactory.getMobGriefingEvent(level,mob)) {
            // Resume a bounded local scan; blocked farms never trigger a whole-building scan each tick.
            for(int scanned=0;scanned<160;scanned++) {
                int i=Math.floorMod(scanCursor++,17*17*7);
                int dy=new int[]{0,1,-1,2,-2,3,4}[i/(17*17)];
                var pos=mob.blockPosition().offset(i%17-8,dy,(i/17)%17-8);
                if(!area.contains(Vec3.atCenterOf(pos)) || !level.hasChunkAt(pos))continue;
                var state=level.getBlockState(pos);
                if(state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) && reachable(pos)) {target=pos;work=Work.CROP;return true;}
                if(state.getBlock() instanceof SweetBerryBushBlock && state.getValue(SweetBerryBushBlock.AGE)>=2 && reachable(pos)) {target=pos;work=Work.BERRIES;return true;}
                if(hasRipeGlowBerries(state)) {
                    var stand=findGlowBerryStand(level,pos,area);
                    if(stand!=null) {harvestBlock=pos.immutable();target=stand;work=Work.GLOW_BERRIES;return true;}
                }
            }
            if(!character && mob.getRandom().nextInt(4)==0) {
                long now=level.getGameTime();
                for(var animal:level.getEntitiesOfClass(Animal.class,area,
                        a->PlayerOutpostWorkGoal.huntable(a) && !a.getTags().contains("changed_synergy:camp_livestock"))
                        .stream().sorted(Comparator.comparingDouble(mob::distanceToSqr)).limit(4).toList()) {
                    var data=animal.getPersistentData();
                    if(data.getLong(CreatureSettlementService.HUNT_CLAIMED_UNTIL)>now
                            && data.hasUUID(CreatureSettlementService.HUNT_CLAIMED_BY)
                            && !data.getUUID(CreatureSettlementService.HUNT_CLAIMED_BY).equals(mob.getUUID())) continue;
                    if(!reachable(animal.blockPosition())) continue;
                    prey=animal;target=animal.blockPosition();work=Work.HUNT;
                    data.putUUID(CreatureSettlementService.HUNT_CLAIMED_BY, mob.getUUID());
                    data.putLong(CreatureSettlementService.HUNT_CLAIMED_UNTIL, now+640L);
                    return true;
                }
            }
        }
        return false;
    }
    private List<BlockPos> orderedStorage() {return NaturalCampService.sites(mob).storage().stream()
            .filter(p->net.parkabird.changedsynergy.npc.CampMembershipService.usableStorage(mob,p))
            .sorted(Comparator.comparingDouble(p->mob.distanceToSqr(Vec3.atCenterOf(p)))).toList();}
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
    private BlockPos findGlowBerryStand(ServerLevel level,BlockPos vinePos,net.minecraft.world.phys.AABB area) {
        for(int drop=0;drop<=4;drop++) for(int radius=0;radius<=1;radius++) for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
            if(radius>0 && Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            var stand=vinePos.offset(dx,-drop,dz);
            if(!area.contains(Vec3.atCenterOf(stand)) || !level.hasChunkAt(stand))continue;
            var body=level.getBlockState(stand);var head=level.getBlockState(stand.above());var below=level.getBlockState(stand.below());
            if(body.blocksMotion() || head.blocksMotion() || !level.getFluidState(stand).isEmpty() || !level.getFluidState(stand.below()).isEmpty())continue;
            if(below.isFaceSturdy(level,stand.below(),Direction.UP) && reachable(stand))return stand.immutable();
        }
        return null;
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
        boolean mealCall = NaturalCampService.hasActiveMealCall(mob);
        if(!food && !mealCall)return false;
        var campBounds = NaturalCampService.bounds(mob);
        var preferredToFire = new java.util.LinkedHashMap<BlockPos, BlockPos>();
        var fallbackToFire = new java.util.LinkedHashMap<BlockPos, BlockPos>();
        var points = new ArrayList<>(NaturalCampService.mealPoints(mob));
        points.sort(Comparator.comparingDouble(p->mob.distanceToSqr(Vec3.atCenterOf(p))));
        for(var candidate:points) {
            boolean openFire=level.getBlockState(candidate).getBlock() instanceof CampfireBlock;
            var places=new ArrayList<BlockPos>();
            for(var p:BlockPos.betweenClosed(candidate.offset(-8,-2,-8),candidate.offset(8,2,8))) {
                if((!bounds.contains(Vec3.atCenterOf(p)) && !campBounds.contains(Vec3.atCenterOf(p)))
                        || (openFire && p.distSqr(candidate)<4) || !level.hasChunkAt(p))continue;
                var immutable = p.immutable();
                if(!mealSeatFree(mob, immutable))continue;
                if(PlayerOutpostRestGoal.isRestPoint(level.getBlockState(p))) {
                    if(PlayerOutpostRestGoal.free(mob,level,p))places.add(immutable);
                } else if(NaturalCampService.walkableSurface(level,mob,immutable)) {
                    places.add(immutable);
                }
            }
            places.sort(Comparator.<BlockPos>comparingInt(p->CreatureRestPlaces.rank(level,p)).thenComparingDouble(p->p.distSqr(candidate)));
            for(var pos:places) {
                if(CreatureRestPlaces.rank(level,pos)<=2) {
                    if(preferredToFire.size()<24)preferredToFire.putIfAbsent(pos, candidate);
                } else if(fallbackToFire.size()<24) {
                    fallbackToFire.putIfAbsent(pos, candidate);
                }
            }
        }
        if(tryMealRoute(preferredToFire))return true;
        return tryMealRoute(fallbackToFire);
    }
    private boolean tryMealRoute(java.util.Map<BlockPos, BlockPos> seatToFire) {
        if(seatToFire.isEmpty())return false;
        for(var seat:seatToFire.keySet()) {
            if(mob.distanceToSqr(Vec3.atCenterOf(seat))<=4.0D) {
                target=seat;fire=seatToFire.get(seat);initialPath=null;return true;
            }
        }
        var path=NaturalCampService.pathAny(mob, seatToFire.keySet(), 1);
        if(path!=null && path.canReach()) {
            var chosen=path.getTarget();
            var candidateFire=seatToFire.get(chosen);
            if(candidateFire==null)candidateFire=seatToFire.values().iterator().next();
            target=chosen;fire=candidateFire;initialPath=path;return true;
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
    @Override public void start(){
        ticks=seatedTicks=stalledTicks=0;seated=false;lastPos=mob.position();
        CreatureComfortGoal.endSeatedActivity(mob);
        if(mob.isPassenger()&&mob.getVehicle() instanceof net.ltxprogrammer.changed.entity.SeatEntity&&!CompanionSeatingService.commandedSeat(mob))mob.stopRiding();
        if(work==Work.MEAL && target!=null)ACTIVE_MEALS.put(mob,target);
        if(work==Work.MEAL && initialPath!=null && target!=null && mob.distanceToSqr(Vec3.atCenterOf(target))>4.0D)mob.getNavigation().moveTo(initialPath,MEAL_WALK_SPEED);
        else mob.getNavigation().stop();
        initialPath=null;
    }
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public boolean canContinueToUse(){
        if(!available() || (mob.getTarget()!=null && mob.getTarget()!=prey))return false;
        if(work==Work.MEAL) {
            if(seated)return seatedTicks<160;
            return ticks<MAX_MEAL_TRAVEL_TICKS && stalledTicks<MAX_STALLED_TICKS && target!=null;
        }
        if(NaturalCampService.restsHere(mob) && PlayerOutpostRestGoal.sleepTime(mob) && !NaturalCampService.hasActiveMealCall(mob))return false;
        return ticks<300;
    }
    @Override public void tick() {
        ticks++;var level=(ServerLevel)mob.level();
        if(work==Work.HUNT) {
            var claim=prey==null?null:prey.getPersistentData();
            if(prey==null || !prey.isAlive() || !NaturalCampService.activityBounds(mob).contains(prey.position())
                    || !ForgeEventFactory.getMobGriefingEvent(level,mob)
                    || claim.hasUUID(CreatureSettlementService.HUNT_CLAIMED_BY)
                        && !claim.getUUID(CreatureSettlementService.HUNT_CLAIMED_BY).equals(mob.getUUID())) {ticks=300;return;}
            target=prey.blockPosition();
            if(mob.distanceToSqr(prey)<5 && ticks%20==0) {
                mob.swing(InteractionHand.MAIN_HAND);
                prey.hurt(mob.damageSources().mobAttack(mob),(float)Math.max(1,mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)));
            }
        }
        if(work==Work.PICKUP && (drop==null || !drop.isAlive())){ticks=300;return;}
        if(work==Work.GLOW_BERRIES && (harvestBlock==null || !hasRipeGlowBerries(level.getBlockState(harvestBlock)))){ticks=300;return;}
        if(seated) {
            mob.getNavigation().stop();seatedTicks++;
            mob.getLookControl().setLookAt(fire.getX()+.5,fire.getY()+.75,fire.getZ()+.5);
            if(seatedTicks==20) for(var pos:orderedStorage()) {
                var chest=PlayerOutpostData.container(level,pos);if(chest==null)continue;
                int slot=foodSlot(chest);if(slot>=0){CompanionAutoEatGoal.beginEating(mob,chest,slot);break;}
            }
            return;
        }
        double reachSq=work==Work.MEAL?4.0D:6.0D;
        if(mob.distanceToSqr(Vec3.atCenterOf(target))>reachSq) {
            if(work==Work.MEAL) {
                if(ticks%20==0) {
                    double movedSqr=lastPos==null?1.0D:mob.position().distanceToSqr(lastPos);
                    lastPos=mob.position();
                    stalledTicks=movedSqr<0.09D?stalledTicks+20:0;
                }
                if(stalledTicks>=MAX_STALLED_TICKS)return;
                if((mob.getNavigation().isDone() || stalledTicks>=40) && PlayerOutpostGoal.canNavigate(mob)) {
                    // Ordinary walk + failure backoff — avoid accuracy-0 grinding into blocked cells.
                    if(!CreatureTaskNavigation.moveTo(mob,target,1,MEAL_WALK_SPEED))stalledTicks=Math.min(MAX_STALLED_TICKS,stalledTicks+20);
                }
            } else if(ticks%40==1 && !CreatureTaskNavigation.moveTo(mob,target,1,.35))ticks=300;
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
            case GLOW_BERRIES -> {
                var state=harvestBlock==null?null:level.getBlockState(harvestBlock);
                if(state!=null && hasRipeGlowBerries(state) && ForgeEventFactory.getMobGriefingEvent(level,mob)
                        && level.setBlock(harvestBlock,state.setValue(CaveVines.BERRIES,false),Block.UPDATE_ALL)) {
                    carry(new ItemStack(Items.GLOW_BERRIES,1+mob.getRandom().nextInt(2)));
                    level.playSound(null,harvestBlock,net.minecraft.sounds.SoundEvents.CAVE_VINES_PICK_BERRIES,net.minecraft.sounds.SoundSource.BLOCKS,0.9F,1.0F);
                    mob.swing(InteractionHand.MAIN_HAND);
                }
                ticks=300;
            }
            case MEAL -> {
                mob.getNavigation().stop();
                if(mob.getPose()==net.minecraft.world.entity.Pose.CROUCHING)mob.setPose(net.minecraft.world.entity.Pose.STANDING);
                if(PlayerOutpostRestGoal.isRestPoint(level.getBlockState(target)))seated=PlayerOutpostRestGoal.sit(mob,level,target);
                else seated=CreatureRestPlaces.sitOnGround(mob,level,target);
                if(!seated){stalledTicks=MAX_STALLED_TICKS;}
                else mob.getPersistentData().putLong(NEXT_MEAL,level.getGameTime()+6000);
            }
            case HUNT -> { }
        }
    }
    private static boolean hasRipeGlowBerries(net.minecraft.world.level.block.state.BlockState state) {
        return state.getBlock() instanceof CaveVines && state.hasProperty(CaveVines.BERRIES) && state.getValue(CaveVines.BERRIES);
    }
    private void carry(ItemStack item){var remaining=PlayerOutpostWorkGoal.acceptCargo(mob,item);if(!remaining.isEmpty())mob.spawnAtLocation(remaining);}
    @Override public void stop() {
        ACTIVE_MEALS.remove(mob);
        mob.getNavigation().stop();CreatureTaskNavigation.clearFailure(mob);
        if(seated){CompanionAutoEatGoal.cleanup(mob);CreatureComfortGoal.endSeatedActivity(mob);}
        // Always drop the meal call on exit (arrive, stall, or travel timeout) so sleep / station-return can resume.
        if(work==Work.MEAL){
            mob.getPersistentData().remove(NaturalCampService.MEAL_CALL_UNTIL);
            mob.getPersistentData().remove(NaturalCampService.MEAL_CALL_LEADER);
        }
        if(work==Work.HUNT) CreatureSettlementService.releaseHuntClaim(mob, prey);
        if(mob.getPose()==net.minecraft.world.entity.Pose.CROUCHING&&mob.level().noCollision(mob,mob.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(mob.position())))mob.setPose(net.minecraft.world.entity.Pose.STANDING);
        if(prey!=null && mob.getTarget()==prey)mob.setTarget(null);
        prey=null;drop=null;harvestBlock=null;seated=false;initialPath=null;lastPos=null;stalledTicks=0;
        // Harvest / hunt / deposit / meal all leave station; ask IdleStation to reclaim once no higher job runs.
        NaturalCampService.markWantsStation(mob);
    }
}
