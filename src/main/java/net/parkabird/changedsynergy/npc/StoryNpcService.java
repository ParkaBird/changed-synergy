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
    public static final long RETURN_DELAY=24000;
    public static NpcDefinitions.Profile profile(ChangedEntity mob) {
        String explicit=mob.getPersistentData().getCompound(DATA).getString("Profile");
        var saved=NpcDefinitions.get(explicit);if(saved!=null)return saved;
        var resident=mob.getPersistentData().getCompound(StructureResidentGoal.DATA);
        if(!resident.getBoolean("Character"))return null;
        String species=BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        return NpcDefinitions.all().stream().filter(p->p.template().equals(resident.getString("Template"))&&p.species().equals(species)).findFirst().orElse(null);
    }
    public static boolean isNpc(ChangedEntity mob){return profile(mob)!=null;}
    public static String key(ChangedEntity mob){return mob.getPersistentData().getCompound(DATA).getString("Key");}
    public static String homeKey(ChangedEntity mob) {
        String key=mob.getPersistentData().getCompound(DATA).getString("Key");
        if(key.contains("@"))return key.substring(key.indexOf('@')+1);
        var r=mob.getPersistentData().getCompound(StructureResidentGoal.DATA);
        return (r.getString("Dimension").isEmpty()?mob.level().dimension().location().toString():r.getString("Dimension"))+":"+BlockPos.containing(r.getDouble("MinX"),r.getDouble("MinY"),r.getDouble("MinZ")).asLong();
    }
    public static ChangedEntity actor(MinecraftServer server,CompoundTag record) {
        if(!record.hasUUID("Actor"))return null;
        for(var level:server.getAllLevels())if(level.getEntity(record.getUUID("Actor")) instanceof ChangedEntity mob && mob.isAlive())return mob;
        return null;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void join(EntityJoinLevelEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getEntity() instanceof ChangedEntity mob))return;
        var profile=profile(mob);if(profile==null)return;
        StructureResidentGoal.install(mob);
        var resident=mob.getPersistentData().getCompound(StructureResidentGoal.DATA);
        if(!resident.getBoolean("Resolved"))return;
        var tag=mob.getPersistentData().getCompound(DATA);
        String key=tag.getString("Key");if(key.isEmpty())key=profile.id()+"@"+homeKey(mob);
        var store=StoryNpcData.get(level.getServer());
        var record=store.records.get(key);
        if(record!=null && (tag.getInt("Generation")!=record.getInt("Generation")
                || record.hasUUID("Actor")&&!record.getUUID("Actor").equals(mob.getUUID()))) {
            event.setCanceled(true);mob.discard();return;
        }
        tag.putString("Profile",profile.id());tag.putString("Key",key);mob.getPersistentData().put(DATA,tag);
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
            AccessorySlots.getForEntity(mob).ifPresent(slots->slots.getSlotTypes().toList().forEach(slot->slots.getItem(slot).ifPresent(stack->{
                if(profile.equipment().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())){
                    var copy=stack.copy();copy.getOrCreateTag().putString(BOUND,tag.getString("Key"));slots.setItem(slot,copy);
                }
            })));
            tag.putInt("PresetVersion",presetVersion);
        }
        installConversationGoal(mob);
        if(record==null){record=new CompoundTag();record.putString("Key",key);record.putString("Profile",profile.id());record.putString("Dimension",level.dimension().location().toString());record.putString("HomeKey",homeKey(mob));
            for(String axis:List.of("X","Y","Z")){record.putDouble("Home"+axis,resident.getDouble("Home"+axis));record.putDouble("Min"+axis,resident.getDouble("Min"+axis));record.putDouble("Max"+axis,resident.getDouble("Max"+axis));}
            store.records.put(key,record);
        }
        record.putUUID("Actor",mob.getUUID());record.putLong("Due",0);snapshot(mob,record);store.setDirty();
    }
    private static void snapshot(ChangedEntity mob,CompoundTag record) {
        var saved=new CompoundTag();mob.saveWithoutId(saved);saved.putString("id",BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
        record.put("Snapshot",saved);record.putLong("LastPos",mob.blockPosition().asLong());record.putString("LastDimension",mob.level().dimension().location().toString());
    }
    @SubscribeEvent public static void living(LivingEvent.LivingTickEvent event) {
        if(!(event.getEntity() instanceof ChangedEntity mob)||!(mob.level() instanceof ServerLevel level)||key(mob).isEmpty())return;
        if(mob.tickCount%200==Math.floorMod(mob.getId(),200)) {
            var data=StoryNpcData.get(level.getServer());var record=data.records.get(key(mob));
            if(record!=null&&mob.isAlive()){snapshot(mob,record);record.putLong("Due",0);data.setDirty();}
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if(event.getEntity() instanceof ChangedEntity mob && mob.level() instanceof ServerLevel level && !key(mob).isEmpty()) {
            var data=StoryNpcData.get(level.getServer());var record=data.records.get(key(mob));if(record==null)return;
            snapshot(mob,record);record.putLong("Due",level.getServer().overworld().getGameTime()+RETURN_DELAY);data.setDirty();
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
        var data=StoryNpcData.get(level.getServer());var record=data.records.get(key(mob));if(record==null)return;
        var reason=mob.getRemovalReason();
        if(reason==null||reason==Entity.RemovalReason.UNLOADED_TO_CHUNK||reason==Entity.RemovalReason.UNLOADED_WITH_PLAYER||reason==Entity.RemovalReason.CHANGED_DIMENSION){snapshot(mob,record);data.setDirty();return;}
        if(record.getLong("Due")==0){snapshot(mob,record);record.putLong("Due",level.getServer().overworld().getGameTime()+RETURN_DELAY);stripDroppedItems(record.getCompound("Snapshot"));data.setDirty();}
    }
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;var server=event.getServer();long now=server.overworld().getGameTime();
        NpcConversations.tick(server);
        if(now%20!=0)return;
        var data=StoryNpcData.get(server);var records=new ArrayList<>(data.records.values());
        for(int checked=0;checked<Math.min(16,records.size());checked++) {
            var record=records.get(Math.floorMod(scanCursor++,records.size()));
            if(actor(server,record)!=null){if(record.getLong("Due")!=0){record.putLong("Due",0);data.setDirty();}continue;}
            var level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse(record.getString("Dimension"))));
            var last=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse(record.getString("LastDimension"))));
            var home=BlockPos.containing(record.getDouble("HomeX"),record.getDouble("HomeY"),record.getDouble("HomeZ"));
            if(level==null||last==null||!loaded(level,home)||!loaded(last,BlockPos.of(record.getLong("LastPos"))))continue;
            if(record.getLong("Due")==0){record.putLong("Due",now+RETURN_DELAY);data.setDirty();}
            else if(record.getLong("Due")<=now)restore(server,record);
        }
    }
    private static boolean loaded(ServerLevel level,BlockPos pos){return level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null&&level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(pos).toLong());}
    public static boolean restore(MinecraftServer server,CompoundTag record) {
        if(NpcDefinitions.get(record.getString("Profile"))==null||actor(server,record)!=null
                ||record.getLong("Due")==0||record.getLong("Due")>server.overworld().getGameTime())return false;
        var level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse(record.getString("Dimension"))));if(level==null)return false;
        var last=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse(record.getString("LastDimension"))));
        if(last==null||!loaded(level,BlockPos.containing(record.getDouble("HomeX"),record.getDouble("HomeY"),record.getDouble("HomeZ")))
                ||!loaded(last,BlockPos.of(record.getLong("LastPos"))))return false;
        var tag=record.getCompound("Snapshot").copy();
        for(String field:List.of("UUID","Passengers","Leash","Brain","ActiveEffects","SleepingX","SleepingY","SleepingZ"))tag.remove(field);
        tag.putShort("DeathTime",(short)0);tag.putShort("HurtTime",(short)0);tag.putBoolean("NoAI",false);tag.putBoolean("NoGravity",false);
        var id=UUID.randomUUID();tag.putUUID("UUID",id);int generation=record.getInt("Generation")+1;
        tag.getCompound("ForgeData").getCompound(DATA).putInt("Generation",generation);
        var entity=EntityType.loadEntityRecursive(tag,level,e->e);if(!(entity instanceof ChangedEntity mob))return false;
        mob.setHealth(mob.getMaxHealth());mob.setTarget(null);mob.setLastHurtByMob(null);mob.stopSleeping();
        for(int dy:new int[]{0,1,-1,2})for(int dx:new int[]{0,-1,1,-2,2})for(int dz:new int[]{0,-1,1,-2,2}) {
            double x=record.getDouble("HomeX")+dx,y=record.getDouble("HomeY")+dy,z=record.getDouble("HomeZ")+dz;
            var pos=BlockPos.containing(x,y-.01,z);
            if(!loaded(level,pos)||x<record.getDouble("MinX")||x>=record.getDouble("MaxX")||z<record.getDouble("MinZ")||z>=record.getDouble("MaxZ")
                    ||y<record.getDouble("MinY")||y+mob.getBbHeight()>record.getDouble("MaxY"))continue;
            mob.setPos(x,y,z);
            if(level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()||BondedTeleportSafety.isDangerous(level.getBlockState(pos))
                    ||!level.hasChunksAt(BlockPos.containing(mob.getBoundingBox().minX,y,mob.getBoundingBox().minZ),BlockPos.containing(mob.getBoundingBox().maxX,y+mob.getBbHeight(),mob.getBoundingBox().maxZ))
                    ||!level.noCollision(mob)||BlockPos.betweenClosedStream(mob.getBoundingBox()).anyMatch(p->!level.getFluidState(p).isEmpty()||BondedTeleportSafety.isDangerous(level.getBlockState(p))))continue;
            var previous=record.copy();record.putInt("Generation",generation);record.putUUID("Actor",id);
            if(level.addFreshEntity(mob)){
                if(previous.hasUUID("Actor")){
                    var aliases=CreatureMorphAliasData.get(server);aliases.record(previous.getUUID("Actor"),id);
                    for(var player:server.getPlayerList().getPlayers())aliases.apply(player);
                }
                record.putLong("Due",0);StoryNpcData.get(server).setDirty();return true;
            }
            for(String field:new ArrayList<>(record.getAllKeys()))record.remove(field);record.merge(previous);return false;
        }
        mob.discard();return false;
    }
    public static void installConversationGoal(ChangedEntity mob){if(mob.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof ConversationGoal))mob.goalSelector.addGoal(-8,new ConversationGoal(mob));}
    private static final class ConversationGoal extends Goal {
        private final ChangedEntity mob;
        ConversationGoal(ChangedEntity mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){return mob.getTarget()==null&&!mob.isSleeping()&&NpcConversations.holds(mob);}
        @Override public void start(){mob.getNavigation().stop();CreaturePeerInteractionService.cancel(mob);}
        @Override public void tick(){mob.getNavigation().stop();var player=NpcConversations.listener(mob);if(player!=null)mob.getLookControl().setLookAt(player,30,30);}
    }
}
