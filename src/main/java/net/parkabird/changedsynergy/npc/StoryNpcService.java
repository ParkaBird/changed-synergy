package net.parkabird.changedsynergy.npc;

import java.util.*;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ai.*;

@Mod.EventBusSubscriber(modid="changed_synergy")
public final class StoryNpcService {
    private static int scanCursor;
    public static final String DATA="ChangedSynergyStoryNpc",BOUND="ChangedSynergyNpcEquipment";
    public static final long RETURN_DELAY=1200,CAMP_MEMBER_DELAY=3600,SMALL_CAMP_DELAY=6000;
    public static NpcDefinitions.Profile profile(ChangedEntity mob) {
        String explicit=mob.getPersistentData().getCompound(DATA).getString("Profile");
        var saved=NpcDefinitions.get(explicit);if(saved!=null)return saved;
        var resident=mob.getPersistentData().getCompound(StructureResidentGoal.DATA);
        if(!resident.getBoolean("Character"))return null;
        String species=BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        return NpcDefinitions.all().stream().filter(p->p.template().equals(resident.getString("Template"))&&p.species().equals(species)).findFirst().orElse(null);
    }
    public static boolean isNpc(ChangedEntity mob){return profile(mob)!=null;}
    private static boolean isExcludedResident(ChangedEntity mob){return CreatureCommunityData.isPlayerCompanion(mob)||PlayerOutpostService.hasAssignment(mob);}
    private static boolean isBuiltInCampResident(ChangedEntity mob){
        var r=mob.getPersistentData().getCompound(StructureResidentGoal.DATA);
        return mob.getUnderlyingPlayer()==null&&r.getBoolean("Resolved")&&r.getBoolean("Camp")
                &&(CampLifeService.mainCamp(mob)||NaturalCampService.smallCamp(mob)||r.getBoolean("ReserveOnly"));
    }
    public static boolean keepsIdentityOnDeath(ChangedEntity mob){
        if(mob==null||mob.getUnderlyingPlayer()!=null)return false;
        if(isNpc(mob))return true;
        return !key(mob).isEmpty()&&!isExcludedResident(mob);
    }
    private static String builtInKey(ChangedEntity mob,CompoundTag resident){
        String species=BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        long spawn=BlockPos.containing(resident.getDouble("HomeX"),resident.getDouble("HomeY"),resident.getDouble("HomeZ")).asLong();
        return "resident:"+species+"#"+spawn+"@"+homeKey(mob);
    }
    private static long delay(CompoundTag record,ChangedEntity mob){
        if(!record.getString("Profile").isEmpty()||mob!=null&&isNpc(mob))return RETURN_DELAY;
        return record.getBoolean("SmallCamp")||mob!=null&&NaturalCampService.smallCamp(mob)?SMALL_CAMP_DELAY:CAMP_MEMBER_DELAY;
    }
    private static void retire(MinecraftServer server,ChangedEntity mob){
        if(profile(mob)!=null)return;
        String key=key(mob);
        if(!key.isEmpty()){
            var data=StoryNpcData.get(server);
            if(data.records.remove(key)!=null)data.setDirty();
            mob.getPersistentData().remove(DATA);
        }
        AccessorySlots.getForEntity(mob).ifPresent(slots->slots.getSlotTypes().toList().forEach(slot->slots.getItem(slot).ifPresent(stack->{
            if(bound(stack)){var copy=stack.copy();copy.getTag().remove(BOUND);if(copy.getTag().isEmpty())copy.setTag(null);slots.setItem(slot,copy);}
        })));
    }
    private static void bindEquipment(ChangedEntity mob,String key,Collection<String> ids){
        if(ids.isEmpty())return;
        AccessorySlots.getForEntity(mob).ifPresent(slots->slots.getSlotTypes().toList().forEach(slot->slots.getItem(slot).ifPresent(stack->{
            if(!bound(stack)&&ids.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())){
                var copy=stack.copy();copy.getOrCreateTag().putString(BOUND,key);slots.setItem(slot,copy);
            }
        })));
    }
    public static String key(ChangedEntity mob){return mob.getPersistentData().getCompound(DATA).getString("Key");}
    public static String homeKey(ChangedEntity mob) {
        var r=mob.getPersistentData().getCompound(StructureResidentGoal.DATA);
        if(!r.getString("Dimension").isEmpty())return r.getString("Dimension")+":"+BlockPos.containing(r.getDouble("MinX"),r.getDouble("MinY"),r.getDouble("MinZ")).asLong();
        String key=mob.getPersistentData().getCompound(DATA).getString("Key");
        if(key.contains("@"))return key.substring(key.indexOf('@')+1);
        return mob.level().dimension().location()+":"+BlockPos.containing(r.getDouble("MinX"),r.getDouble("MinY"),r.getDouble("MinZ")).asLong();
    }
    public static ChangedEntity actor(MinecraftServer server,CompoundTag record) {
        if(!record.hasUUID("Actor"))return null;
        for(var level:server.getAllLevels())if(level.getEntity(record.getUUID("Actor")) instanceof ChangedEntity mob && mob.isAlive())return mob;
        return null;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void join(EntityJoinLevelEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getEntity() instanceof ChangedEntity mob))return;
        var profile=profile(mob);
        if(profile!=null) {
            if(profile.chief() && CreatureLifeMemory.role(mob) != CreatureLifeMemory.GroupRole.CHIEF) {
                CreatureLifeMemory.setRole(mob, CreatureLifeMemory.GroupRole.CHIEF);
            }
            installConversationGoal(mob);
            StructureResidentGoal.installOnJoin(mob);
        } else {
            if(mob.getUnderlyingPlayer()!=null||mob.getPersistentData().getCompound(StructureResidentGoal.DATA).isEmpty())return;
            StructureResidentGoal.installOnJoin(mob);
            if(isExcludedResident(mob)){retire(level.getServer(),mob);return;}
            if(!isBuiltInCampResident(mob))return;
        }
        var resident=mob.getPersistentData().getCompound(StructureResidentGoal.DATA);
        if(!resident.getBoolean("Resolved"))return;
        var tag=mob.getPersistentData().getCompound(DATA);
        String key=tag.getString("Key");if(key.isEmpty())key=profile!=null?profile.id()+"@"+homeKey(mob):builtInKey(mob,resident);
        var store=StoryNpcData.get(level.getServer());
        var record=store.records.get(key);
        if(record!=null && (tag.getInt("Generation")!=record.getInt("Generation")
                || record.hasUUID("Actor")&&!record.getUUID("Actor").equals(mob.getUUID()))) {
            event.setCanceled(true);mob.discard();return;
        }
        if(profile!=null)tag.putString("Profile",profile.id());
        tag.putString("Key",key);mob.getPersistentData().put(DATA,tag);
        if(profile!=null) {
            if(tag.getInt("NameVersion")<1) {
                mob.setCustomName(net.minecraft.network.chat.Component.translatable("npc."+profile.id().replace(':','.')+".title"));
                tag.putInt("NameVersion",1);
            }
            int presetVersion=switch(profile.id()){case "changed_synergy:hyena_chief"->4;case "changed_synergy:canopy_chief"->3;case "changed_synergy:lab_researcher"->3;case "changed_synergy:desert_chief"->2;default->1;};
            if(tag.getInt("PresetVersion")<presetVersion) {
                CreaturePersonality.setNpcPreset(mob,CreaturePersonality.Trait.valueOf(profile.trait()));
                CreatureLifeMemory.setRole(mob,profile.chief()?CreatureLifeMemory.GroupRole.CHIEF:CreatureLifeMemory.GroupRole.WANDERER);
                if(profile.id().equals("changed_synergy:hyena_chief")||profile.id().equals("changed_synergy:lab_researcher")||profile.id().equals("changed_synergy:canopy_chief")||profile.id().equals("changed_synergy:desert_chief")){
                    var eyes=new net.ltxprogrammer.changed.entity.BasicPlayerInfo();eyes.copyFrom(mob.getBasicPlayerInfo());
                    boolean researcher=profile.id().equals("changed_synergy:lab_researcher");
                    boolean moth=profile.id().equals("changed_synergy:canopy_chief");
                    boolean desert=profile.id().equals("changed_synergy:desert_chief");
                    eyes.setEyeStyle(researcher?net.ltxprogrammer.changed.entity.EyeStyle.V1:moth||desert?net.ltxprogrammer.changed.entity.EyeStyle.TALL:net.ltxprogrammer.changed.entity.EyeStyle.V2);
                    int iris=researcher?0x65C98C:moth?0x90B8C2:desert?0x70B7A5:0xB8744C;
                    if(desert)eyes.setScleraColor(net.ltxprogrammer.changed.util.Color3.WHITE);
                    eyes.setLeftIrisColor(net.ltxprogrammer.changed.util.Color3.fromInt(iris));eyes.setRightIrisColor(net.ltxprogrammer.changed.util.Color3.fromInt(iris));
                    mob.getEntityData().set(net.parkabird.changedsynergy.mixin.ChangedEntityAppearanceAccessor.changedSynergy$appearanceData(),eyes);
                }
                bindEquipment(mob,key,profile.equipment());
                tag.putInt("PresetVersion",presetVersion);
            }
            installConversationGoal(mob);
        } else if(tag.getInt("BoundOutfitVersion")<1) {
            var outfits=new HashSet<String>();
            for(String f:List.of("Outfit","NeckOutfit")){String item=resident.getString(f);if(!item.isEmpty())outfits.add(item);}
            bindEquipment(mob,key,outfits);
            if(outfits.isEmpty()||resident.getBoolean("OutfitApplied")||resident.getBoolean("NeckOutfitApplied"))tag.putInt("BoundOutfitVersion",1);
        }
        if(record==null){record=new CompoundTag();record.putString("Key",key);record.putString("Dimension",level.dimension().location().toString());record.putString("HomeKey",homeKey(mob));
            if(profile!=null)record.putString("Profile",profile.id());
            else{record.putBoolean("Resident",true);record.putBoolean("SmallCamp",NaturalCampService.smallCamp(mob)||resident.getBoolean("ReserveOnly"));}
            for(String axis:List.of("X","Y","Z")){record.putDouble("Home"+axis,resident.getDouble("Home"+axis));record.putDouble("Min"+axis,resident.getDouble("Min"+axis));record.putDouble("Max"+axis,resident.getDouble("Max"+axis));}
            store.records.put(key,record);
        }
        record.putUUID("Actor",mob.getUUID());record.putLong("Due",0);record.putBoolean("ConfirmedGone",false);snapshot(mob,record);store.setDirty();
    }
    private static void snapshot(ChangedEntity mob,CompoundTag record) {
        var saved=new CompoundTag();mob.saveWithoutId(saved);saved.putString("id",BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
        record.put("Snapshot",saved);record.putLong("LastPos",mob.blockPosition().asLong());record.putString("LastDimension",mob.level().dimension().location().toString());
    }
    @SubscribeEvent public static void living(LivingEvent.LivingTickEvent event) {
        if(!(event.getEntity() instanceof ChangedEntity mob)||!(mob.level() instanceof ServerLevel level)||key(mob).isEmpty())return;
        if(mob.tickCount%200==Math.floorMod(mob.getId(),200)) {
            if(isExcludedResident(mob)){retire(level.getServer(),mob);return;}
            var data=StoryNpcData.get(level.getServer());var record=data.records.get(key(mob));
            if(record!=null&&mob.isAlive()){snapshot(mob,record);record.putLong("Due",0);record.putBoolean("ConfirmedGone",false);data.setDirty();}
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if(!event.isCanceled() && event.getEntity() instanceof ChangedEntity mob && mob.level() instanceof ServerLevel level && !key(mob).isEmpty()) {
            if(isExcludedResident(mob)){retire(level.getServer(),mob);return;}
            var data=StoryNpcData.get(level.getServer());var record=data.records.get(key(mob));if(record==null)return;
            snapshot(mob,record);record.putLong("Due",level.getServer().overworld().getGameTime()+delay(record,mob));record.putBoolean("ConfirmedGone",true);data.setDirty();
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void drops(LivingDropsEvent event) {
        if(!(event.getEntity() instanceof ChangedEntity mob)||!(mob.level() instanceof ServerLevel level)||key(mob).isEmpty())return;
        event.getDrops().removeIf(drop->bound(drop.getItem()));
        var data=StoryNpcData.get(level.getServer());var record=data.records.get(key(mob));
        // Curios may already have cleared the dying entity's clothing inventory.
        if(record!=null){stripDroppedItems(record.getCompound("Snapshot"));data.setDirty();}
    }
    public static boolean bound(ItemStack stack){return stack.hasTag()&&stack.getTag().contains(BOUND);}
    private static void stripDroppedItems(Tag value) {
        if(value instanceof CompoundTag tag) {
            if(tag.contains("Count")&&tag.contains("id")&&!tag.getCompound("tag").contains(BOUND)){for(String key:new ArrayList<>(tag.getAllKeys()))tag.remove(key);return;}
            for(var child:tag.getAllKeys().stream().toList())stripDroppedItems(tag.get(child));
        }else if(value instanceof ListTag list)for(var child:list)stripDroppedItems(child);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if(!(event.getEntity() instanceof ChangedEntity mob)||!(event.getLevel() instanceof ServerLevel level)||key(mob).isEmpty())return;
        var reason=mob.getRemovalReason();
        if(isExcludedResident(mob)){if(reason!=null&&reason.shouldDestroy())retire(level.getServer(),mob);return;}
        var data=StoryNpcData.get(level.getServer());var record=data.records.get(key(mob));if(record==null)return;
        if(reason==null||reason==Entity.RemovalReason.UNLOADED_TO_CHUNK||reason==Entity.RemovalReason.UNLOADED_WITH_PLAYER||reason==Entity.RemovalReason.CHANGED_DIMENSION){snapshot(mob,record);data.setDirty();return;}
        if(record.getLong("Due")==0){snapshot(mob,record);record.putLong("Due",level.getServer().overworld().getGameTime()+delay(record,mob));stripDroppedItems(record.getCompound("Snapshot"));}
        if(reason.shouldDestroy())record.putBoolean("ConfirmedGone",true);
        data.setDirty();
    }
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;var server=event.getServer();long now=server.overworld().getGameTime();
        NpcConversations.tick(server);
        if(now%20!=0)return;
        var data=StoryNpcData.get(server);var records=new ArrayList<>(data.records.values());
        int batch=Math.min(records.size(),Math.max(16,(records.size()+3)/4));
        for(int checked=0;checked<batch;checked++) {
            var record=records.get(Math.floorMod(scanCursor++,records.size()));
            if(actor(server,record)!=null){if(record.getLong("Due")!=0||record.getBoolean("ConfirmedGone")){record.putLong("Due",0);record.putBoolean("ConfirmedGone",false);data.setDirty();}continue;}
            var level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse(record.getString("Dimension"))));
            var home=BlockPos.containing(record.getDouble("HomeX"),record.getDouble("HomeY"),record.getDouble("HomeZ"));
            if(level==null||!loaded(level,home))continue;
            if(!record.getBoolean("ConfirmedGone")){
                var last=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse(record.getString("LastDimension"))));
                if(last==null||!loaded(last,BlockPos.of(record.getLong("LastPos"))))continue;
            }
            if(record.getLong("Due")==0){record.putLong("Due",now+delay(record,null));data.setDirty();}
            else if(record.getLong("Due")<=now)restore(server,record);
        }
    }
    private static boolean loaded(ServerLevel level,BlockPos pos){return level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null&&level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(pos).toLong());}
    private static boolean canRestoreRecord(CompoundTag record){
        String profileId=record.getString("Profile");
        if(!profileId.isEmpty())return NpcDefinitions.get(profileId)!=null;
        return record.getBoolean("Resident")&&!record.getCompound("Snapshot").getString("id").isEmpty();
    }
    public static boolean restore(MinecraftServer server,CompoundTag record) {
        if(!canRestoreRecord(record)||actor(server,record)!=null
                ||record.getLong("Due")==0||record.getLong("Due")>server.overworld().getGameTime())return false;
        var level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse(record.getString("Dimension"))));if(level==null)return false;
        if(!loaded(level,BlockPos.containing(record.getDouble("HomeX"),record.getDouble("HomeY"),record.getDouble("HomeZ"))))return false;
        if(!record.getBoolean("ConfirmedGone")) {
            var last=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse(record.getString("LastDimension"))));
            if(last==null||!loaded(last,BlockPos.of(record.getLong("LastPos"))))return false;
        }
        var tag=record.getCompound("Snapshot").copy();
        for(String field:List.of("UUID","Passengers","Leash","Brain","ActiveEffects","SleepingX","SleepingY","SleepingZ","Health","Fire","FallDistance","Air","Motion","PortalCooldown","HurtByTimestamp","TicksFrozen"))tag.remove(field);
        tag.putShort("DeathTime",(short)0);tag.putShort("HurtTime",(short)0);tag.putBoolean("NoAI",false);tag.putBoolean("NoGravity",false);
        var id=UUID.randomUUID();tag.putUUID("UUID",id);int generation=record.getInt("Generation")+1;
        tag.getCompound("ForgeData").remove("ChangedSynergyNaturalCampBed");
        tag.getCompound("ForgeData").getCompound(DATA).putInt("Generation",generation);
        var entity=EntityType.loadEntityRecursive(tag,level,e->e);if(!(entity instanceof ChangedEntity mob))return false;
        mob.setHealth(mob.getMaxHealth());mob.setTarget(null);mob.setLastHurtByMob(null);mob.stopSleeping();
        mob.clearFire();mob.fallDistance=0;mob.setAirSupply(mob.getMaxAirSupply());mob.setDeltaMovement(Vec3.ZERO);mob.setPortalCooldown(0);
        var spot=findSpawn(level,record,mob);
        if(spot!=null) {
            mob.setPos(spot.x,spot.y,spot.z);
            var previous=record.copy();record.putInt("Generation",generation);record.putUUID("Actor",id);
            if(level.addFreshEntity(mob)){
                if(previous.hasUUID("Actor")){
                    var aliases=CreatureMorphAliasData.get(server);aliases.record(previous.getUUID("Actor"),id);
                    for(var player:server.getPlayerList().getPlayers())aliases.apply(player);
                }
                record.putLong("Due",0);record.putBoolean("ConfirmedGone",false);StoryNpcData.get(server).setDirty();return true;
            }
            for(String field:new ArrayList<>(record.getAllKeys()))record.remove(field);record.merge(previous);return false;
        }
        mob.discard();return false;
    }
    private static Vec3 findSpawn(ServerLevel level,CompoundTag record,ChangedEntity mob) {
        double hx=record.getDouble("HomeX"),hy=record.getDouble("HomeY"),hz=record.getDouble("HomeZ");
        if(canSpawnAt(level,record,mob,hx,hy,hz))return new Vec3(hx,hy,hz);
        Vec3 best=null;double bestDist=Double.MAX_VALUE;
        int baseY=(int)Math.floor(hy-.01);
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
            double x=hx+dx,z=hz+dz;
            if(x<record.getDouble("MinX")||x>=record.getDouble("MaxX")||z<record.getDouble("MinZ")||z>=record.getDouble("MaxZ"))continue;
            for(int dy:new int[]{0,1,-1,2,-2}) {
                double yExact=hy+dy;
                if(canSpawnAt(level,record,mob,x,yExact,z)) {
                    double d=(x-hx)*(x-hx)+(yExact-hy)*(yExact-hy)+(z-hz)*(z-hz);
                    if(d<bestDist){bestDist=d;best=new Vec3(x,yExact,z);}
                }
                var floorPos=BlockPos.containing(x,baseY+dy,z);
                if(!loaded(level,floorPos))continue;
                var shape=level.getBlockState(floorPos).getCollisionShape(level,floorPos);
                if(shape.isEmpty())continue;
                double top=shape.max(Direction.Axis.Y);
                if(!Double.isFinite(top)||top<=0)continue;
                double yTop=floorPos.getY()+top;
                if(Math.abs(yTop-yExact)<1e-4)continue;
                if(canSpawnAt(level,record,mob,x,yTop,z)) {
                    double d=(x-hx)*(x-hx)+(yTop-hy)*(yTop-hy)+(z-hz)*(z-hz);
                    if(d<bestDist){bestDist=d;best=new Vec3(x,yTop,z);}
                }
            }
        }
        return best;
    }
    private static boolean canSpawnAt(ServerLevel level,CompoundTag record,ChangedEntity mob,double x,double y,double z) {
        if(x<record.getDouble("MinX")||x>=record.getDouble("MaxX")||z<record.getDouble("MinZ")||z>=record.getDouble("MaxZ")
                ||y<record.getDouble("MinY")||y+mob.getBbHeight()>record.getDouble("MaxY"))return false;
        var floor=BlockPos.containing(x,y-.05,z);
        if(!loaded(level,floor))return false;
        var state=level.getBlockState(floor);
        if(state.getCollisionShape(level,floor).isEmpty()||BondedTeleportSafety.isDangerous(state))return false;
        boolean supported=state.getCollisionShape(level,floor).toAabbs().stream().map(b->b.move(floor)).anyMatch(b->
                b.maxY>=y-.06&&b.maxY<=y+.02&&b.minX<=x+.001&&b.maxX>=x-.001&&b.minZ<=z+.001&&b.maxZ>=z-.001);
        if(!supported)return false;
        mob.setPos(x,y,z);
        var box=mob.getBoundingBox();
        if(!level.hasChunksAt(BlockPos.containing(box.minX,y,box.minZ),BlockPos.containing(box.maxX,y+mob.getBbHeight(),box.maxZ)))return false;
        return level.noCollision(mob)&&BlockPos.betweenClosedStream(box).noneMatch(p->!level.getFluidState(p).isEmpty()||BondedTeleportSafety.isDangerous(level.getBlockState(p)));
    }
    public static void installConversationGoal(ChangedEntity mob){if(mob.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof ConversationGoal))mob.goalSelector.addGoal(-8,new ConversationGoal(mob));}
    private static final class ConversationGoal extends Goal {
        private final ChangedEntity mob;
        ConversationGoal(ChangedEntity mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){return mob.getTarget()==null&&!mob.isSleeping()&&NpcConversations.holds(mob);}
        @Override public void start(){
            mob.getNavigation().stop();
            mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.0);
            mob.setDeltaMovement(0.0, mob.getDeltaMovement().y, 0.0);
            CreaturePeerInteractionService.cancel(mob);
        }
        @Override public void tick(){
            mob.getNavigation().stop();
            mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.0);
            mob.setDeltaMovement(0.0, mob.getDeltaMovement().y, 0.0);
            var player=NpcConversations.listener(mob);
            if(player!=null)mob.getLookControl().setLookAt(player,30,30);
        }
    }
}
