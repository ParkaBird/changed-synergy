package net.parkabird.changedsynergy.ai;

import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Authored camps belong to their structure, separately from player camps and constructed wild outposts. */
@Mod.EventBusSubscriber(modid=ChangedSynergyMod.MOD_ID)
public final class NaturalCampService {
    /** Shared by residents; player access is independently controlled by private storage. */
    public static final String PUBLIC_STORAGE = "ChangedSynergyPublicStorage";
    private static final Set<String> SMALL_CAMPS=Set.of("oasis","muddy_campsite","hyena_camp_tropical","hyena_camp_open","hyena_camp_snowy");
    public static boolean smallCamp(String template){return SMALL_CAMPS.contains(template);}
    public static boolean smallCamp(ChangedEntity mob){return smallCamp(mob.getPersistentData().getCompound(StructureResidentGoal.DATA).getString("Template"));}
    public record Sites(List<BlockPos> storage, List<BlockPos> fires,List<BlockPos> beds, long expires) {}
    private static final Map<ServerLevel, Map<BlockPos, Sites>> SITES = new WeakHashMap<>();
    private NaturalCampService() {}

    public static boolean member(ChangedEntity mob) {
        return mob.getUnderlyingPlayer() == null && mob.getPersistentData().getCompound(StructureResidentGoal.DATA).getBoolean("Camp");
    }
    public static AABB bounds(ChangedEntity mob) {
        var data=mob.getPersistentData().getCompound(StructureResidentGoal.DATA);
        return new AABB(data.getDouble("MinX"),data.getDouble("MinY"),data.getDouble("MinZ"),
                data.getDouble("MaxX"),data.getDouble("MaxY"),data.getDouble("MaxZ"));
    }
    public static AABB activityBounds(ChangedEntity mob) { return bounds(mob).inflate(6,3,6); }
    public static boolean laboratoryResident(ChangedEntity mob) {
        var data=mob.getPersistentData().getCompound(StructureResidentGoal.DATA);
        return data.getBoolean("Character")&&data.getString("Template").equals("green_lab");
    }
    public static boolean restsHere(ChangedEntity mob){return member(mob)||laboratoryResident(mob);}
    public static AABB restBounds(ChangedEntity mob){
        var box=bounds(mob);if(!laboratoryResident(mob))return box;
        double floor=mob.getPersistentData().getCompound(StructureResidentGoal.DATA).getDouble("HomeY");
        return new AABB(box.minX,Math.max(box.minY,floor-.1),box.minZ,box.maxX,Math.min(box.maxY,floor+6),box.maxZ);
    }
    public static boolean publicReserveOnly(ChangedEntity mob){return net.parkabird.changedsynergy.npc.CampLifeService.mainCamp(mob)||mob.getPersistentData().getCompound(StructureResidentGoal.DATA).getBoolean("ReserveOnly")||smallCamp(mob);}
    public static List<BlockPos> authoredPublicStores(ChangedEntity mob){return StructureStorageOwnership.publicPositions(mob);}
    public static net.minecraft.world.level.pathfinder.Path path(ChangedEntity mob,BlockPos target,int accuracy){
        var data=mob.getPersistentData().getCompound(StructureResidentGoal.DATA);
        if(!restsHere(mob)||!data.getBoolean("Resolved")||!activityBounds(mob).contains(Vec3.atCenterOf(target)))return mob.getNavigation().createPath(target,accuracy);
        var box=bounds(mob);
        // Spiral stairs can exceed native pursuit distance while remaining inside the building.
        int distance=(int)Math.max(mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE),Math.min(96,box.getXsize()+box.getYsize()+box.getZsize()));
        return mob.getNavigation().createPath(target,accuracy,distance);
    }
    public static List<BlockPos> mealPoints(ChangedEntity mob){
        var result=new ArrayList<BlockPos>();
        for(var p:sites(mob).fires()){
            var state=mob.level().getBlockState(p);
            if(state.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock&&state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)&&!state.getValue(net.minecraft.world.level.block.CampfireBlock.WATERLOGGED))result.add(p);
        }
        var data=mob.getPersistentData().getCompound(StructureResidentGoal.DATA);
        if(data.getString("Template").equals("desert_refuge")&&data.getBoolean("Resolved")){
            var p=net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(new BlockPos(31,14,17),net.minecraft.world.level.block.Mirror.NONE,net.minecraft.world.level.block.Rotation.values()[Math.floorMod(data.getInt("PlacementRotation"),4)],BlockPos.ZERO).offset(BlockPos.of(data.getLong("PlacementOrigin")));
            if(mob.level().hasChunkAt(p)&&bounds(mob).contains(Vec3.atCenterOf(p))&&!mob.level().getBlockState(p.below()).getCollisionShape(mob.level(),p.below()).isEmpty())result.add(p);
        }
        return List.copyOf(result);
    }
    public static boolean followingOwner(ChangedEntity mob){return CreatureCommunityData.isPlayerCompanion(mob)&&LatexSocialMemory.isFollowingOwner(mob);}
    public static boolean inHomeDimension(ChangedEntity mob){String dimension=mob.getPersistentData().getCompound(StructureResidentGoal.DATA).getString("Dimension");return dimension.isEmpty()||dimension.equals(mob.level().dimension().location().toString());}
    public static void install(ChangedEntity mob) {
        if(publicReserveOnly(mob))net.parkabird.changedsynergy.npc.CampLifeService.register(mob);
        if(net.parkabird.changedsynergy.npc.CampLifeService.mainCamp(mob)){
            net.parkabird.changedsynergy.npc.CampLifeService.register(mob);
            net.parkabird.changedsynergy.npc.CampMembershipService.introduceLoadedResident(mob);
            net.parkabird.changedsynergy.npc.StoryNpcService.installConversationGoal(mob);
            if(mob.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof net.parkabird.changedsynergy.npc.CampApproachGoal))mob.goalSelector.addGoal(0,new net.parkabird.changedsynergy.npc.CampApproachGoal(mob));
        }
        if (member(mob) && mob.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof NaturalCampGoal))
            mob.goalSelector.addGoal(1,new NaturalCampGoal(mob));
        if(restsHere(mob)&&mob.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof NaturalCampSleepGoal))
            mob.goalSelector.addGoal(0,new NaturalCampSleepGoal(mob));
        if(publicReserveOnly(mob))net.parkabird.changedsynergy.npc.CampLifeService.rememberStores(mob);
    }
    static boolean available(ChangedEntity mob) {
        return restsHere(mob) && inHomeDimension(mob) && net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.naturalCampRoutines.get()
                && CreatureLifeMemory.enabled(mob) && !mob.isNoAi() && mob.isAlive() && mob.getUnderlyingPlayer()==null
                && !followingOwner(mob) && !PlayerOutpostService.hasAssignment(mob)
                && !mob.isLeashed() && !CreaturePeerInteractionService.active(mob)
                && !SocialAudienceGoal.isActive(mob) && !ProvisionerTradeService.isTrading(mob)
                && !net.parkabird.changedsynergy.npc.NpcConversations.holds(mob)
                && CreaturePersonality.socialPartner(mob)==null && HypnosisQteService.getActiveVictim(mob)==null
                && !CreatureGrabService.isGrabberBusy(mob) && !TakeoverService.carrying(mob)
                && net.ltxprogrammer.changed.ability.GrabEntityAbility.getGrabberSafe(mob).isEmpty();
    }
    public static Sites sites(ChangedEntity mob) {
        if (!(mob.level() instanceof ServerLevel level) || !restsHere(mob)
                || !mob.getPersistentData().getCompound(StructureResidentGoal.DATA).getBoolean("Resolved")) return new Sites(List.of(),List.of(),List.of(),0);
        var box=restBounds(mob);var key=BlockPos.containing(box.minX,box.minY,box.minZ);
        var cache=SITES.computeIfAbsent(level,ignored->new HashMap<>());
        var found=cache.get(key);
        if(found!=null && found.expires()>level.getGameTime())return found;
        var storage=new ArrayList<BlockPos>();var fires=new ArrayList<BlockPos>();var beds=new ArrayList<BlockPos>();
        for(int cx=key.getX()>>4;cx<=((int)box.maxX-1)>>4;cx++) for(int cz=key.getZ()>>4;cz<=((int)box.maxZ-1)>>4;cz++) {
            var chunk=level.getChunkSource().getChunkNow(cx,cz);if(chunk==null)continue;
            PerchSleepService.addBeds(chunk,box,beds);
            for(var entity:chunk.getBlockEntities().values()) {
                var pos=entity.getBlockPos();if(!box.contains(Vec3.atCenterOf(pos)))continue;
                if(entity instanceof CampfireBlockEntity)fires.add(pos);
                else if(entity instanceof net.minecraft.world.level.block.entity.BedBlockEntity
                        && level.getBlockState(pos).getValue(net.minecraft.world.level.block.BedBlock.PART)==net.minecraft.world.level.block.state.properties.BedPart.HEAD)beds.add(pos);
                else if(member(mob)&&PlayerOutpostData.container(level,pos)!=null){storage.add(pos);StructureStorageOwnership.owns(mob,pos);}
            }
        }
        found=new Sites(List.copyOf(storage),List.copyOf(fires),List.copyOf(beds),level.getGameTime()+200);cache.put(key,found);return found;
    }
    public static Optional<BlockPos> storage(ChangedEntity mob) {
        return sites(mob).storage().stream().filter(p->mob.level().hasChunkAt(p))
                .filter(p->net.parkabird.changedsynergy.npc.CampMembershipService.usableStorage(mob,p))
                .min(Comparator.comparingDouble(p->mob.distanceToSqr(Vec3.atCenterOf(p))));
    }
    public static boolean publicStorage(ServerLevel level,BlockPos pos) {
        if(!publicHalf(level,pos))return false;
        var state=level.getBlockState(pos);
        if(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                &&state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE){
            var mate=pos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state));
            return publicHalf(level,mate);
        }
        return true;
    }
    private static boolean publicHalf(ServerLevel level,BlockPos pos){
        if(!level.hasChunkAt(pos))return false;var be=level.getBlockEntity(pos);if(be==null)return false;
        if(be.getPersistentData().getBoolean("ChangedSynergyPrivate")&&!be.getPersistentData().getBoolean(PUBLIC_STORAGE))return false;
        if(be.getPersistentData().getBoolean(PUBLIC_STORAGE))return true;
        if(!(be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity||be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity))return false;
        for(var camp:net.parkabird.changedsynergy.npc.CampLifeData.get(level.getServer()).camps.values()){
            if(!camp.getString("Dimension").equals(level.dimension().location().toString())||Arrays.stream(camp.getLongArray("PublicStores")).noneMatch(p->p==pos.asLong()))continue;
            be.getPersistentData().putBoolean(PUBLIC_STORAGE,true);
            if(smallCamp(camp.getString("Template")))be.getPersistentData().putBoolean("ChangedSynergyPrivate",true);
            if(be instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity container&&!container.hasCustomName())container.setCustomName(net.minecraft.network.chat.Component.translatable(smallCamp(camp.getString("Template"))?"container.changed_synergy.camp_supplies":"container.changed_synergy.community_supplies"));
            level.blockEntityChanged(pos);return true;
        }return false;
    }
    private static void changed(net.minecraft.world.level.LevelAccessor world,BlockPos pos){
        if(!(world instanceof ServerLevel level))return;SITES.remove(level);
        publicStorage(level,pos);
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void placed(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event){
        if(!event.isCanceled()&&(event.getPlacedBlock().hasBlockEntity()||PlayerOutpostRestGoal.isBed(event.getPlacedBlock())))changed(event.getLevel(),event.getPos());
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void broken(net.minecraftforge.event.level.BlockEvent.BreakEvent event){
        if(!event.isCanceled()&&(event.getState().hasBlockEntity()||PlayerOutpostRestGoal.isBed(event.getState()))&&event.getLevel() instanceof ServerLevel level)SITES.remove(level);
    }
    public static boolean ownsStorage(ChangedEntity mob, BlockPos pos) {
        if(!member(mob) || !(mob.level() instanceof ServerLevel level))return false;
        var container=PlayerOutpostData.container(level,pos);if(container==null)return false;
        StructureStorageOwnership.owns(mob,pos);
        var box=bounds(mob);
        boolean inside=box.contains(Vec3.atCenterOf(pos));
        BlockPos other=null;
        var state=level.getBlockState(pos);
        if(container instanceof net.minecraft.world.CompoundContainer pair && state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                && state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
            other=pos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state));
            if(!inside) inside=box.contains(Vec3.atCenterOf(other)) && level.getBlockEntity(other) instanceof net.minecraft.world.Container half && pair.contains(half);
        }
        if(!inside) return false;
        if(publicStorage(level,pos)||net.parkabird.changedsynergy.npc.CampMembershipService.privateStorage(mob,pos))return true;
        if(net.parkabird.changedsynergy.npc.CampMembershipService.readableStory(mob,pos))return true;
        return StructureStorageOwnership.owns(mob,pos)||other!=null&&StructureStorageOwnership.owns(mob,other);
    }
    public static boolean isStorage(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(ChangedEntity.class,new AABB(pos).inflate(64),m->ownsStorage(m,pos)).size()>0;
    }
    public static boolean livestock(ChangedEntity hunter, LivingEntity target) {
        return member(hunter) && target.getTags().contains("changed_synergy:camp_livestock")
                && bounds(hunter).contains(target.position());
    }
    @SubscribeEvent public static void target(LivingChangeTargetEvent event) {
        if(event.getEntity() instanceof ChangedEntity mob && event.getNewTarget()!=null && livestock(mob,event.getNewTarget()))event.setNewTarget(null);
    }
    @SubscribeEvent public static void attack(LivingAttackEvent event) {
        if(event.getSource().getEntity() instanceof ChangedEntity mob && livestock(mob,event.getEntity()))event.setCanceled(true);
    }
    @SubscribeEvent public static void unload(net.minecraftforge.event.level.LevelEvent.Unload event) { SITES.remove(event.getLevel()); }
}
