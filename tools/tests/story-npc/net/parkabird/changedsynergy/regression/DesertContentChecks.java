package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.ltxprogrammer.changed.data.*;
import net.ltxprogrammer.changed.entity.*;
import net.ltxprogrammer.changed.init.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.init.*;
import net.parkabird.changedsynergy.npc.*;
import net.parkabird.changedsynergy.origin.*;

final class DesertContentChecks {
    private static int checks;
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    static void run(MinecraftServer server)throws Exception {
        var level=server.overworld();var type=ChangedSynergyEntities.LATEX_JERBOA.get();var body=type.create(level);
        check(body.getMaxHealth()==16&&body.getAttributeBaseValue(Attributes.ATTACK_DAMAGE)==2&&body.getAttributeBaseValue(Attributes.MOVEMENT_SPEED)==1.18,"fragile and fast native attributes");
        check(body.getSelfVariant()==ChangedSynergyTransfurVariants.LATEX_JERBOA.get(),"public transfur variant maps to new entity");
        var entry=NativeOriginCatalog.find(level.registryAccess(),body.getSelfVariant().getFormId());check(entry!=null&&entry.region().equals("arid"),"playable arid origin");
        check(NpcDefinitions.get("changed_synergy:desert_chief").chief()&&NpcDefinitions.get("changed_synergy:desert_chief").dialogue()&&NpcDefinitions.get("changed_synergy:desert_chief").nodes().containsKey("confirm_permanent"),"Chief has the authored graph and separate consent");
        for(var biome:List.of(net.minecraft.world.level.biome.Biomes.DESERT,net.minecraft.world.level.biome.Biomes.BADLANDS))check(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME).getOrThrow(biome).getMobSettings().getMobs(MobCategory.MONSTER).unwrap().stream().anyMatch(s->s.type==type),"native desert spawn entry");
        var p=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"DesertCheck"));p.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),p);p.setPos(0,100,0);
        var identity=new NativeOriginData.Identity();identity.state="human";NativeOriginData.get(server).players.put(p.getUUID(),identity);
        for(String id:List.of("changed_synergy:latex_jerboa","changed_synergy:latex_silk_moth","changed_synergy:latex_muntjac_female","changed_synergy:latex_spotted_hyena_female","changed:white_latex_wolf_female","changed:latex_snake","changed:latex_mermaid_shark","changed:gas_wolf_pup","changed:dark_latex_wolf_pup","changed:milk_pudding")){
            var mob=(ChangedEntity)ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(id)).create(level);mob.setPos(0,100,0);level.getChunk(mob.blockPosition());level.addFreshEntity(mob);
            AccessoryEntities.INSTANCE.forceReloadAccessories(mob);var slots=AccessorySlots.getForEntity(mob).orElseThrow();
            for(var item:List.of(ChangedSynergyItems.DESERT_NOMAD_OUTFIT.get())){
                slots.getSlotTypes().toList().forEach(s->slots.setItem(s,ItemStack.EMPTY));var stack=new ItemStack(item);
                check(slots.quickMoveStack(stack),"native equip: "+id+" "+item);
                check(AccessorySlots.isWearing(mob,s->s.is(item)),"one real native garment is worn");
                var saved=slots.save();slots.load(saved);check(slots.getItems().filter(s->s.is(item)).count()==1,"native persistence does not duplicate outfit");
                if(net.parkabird.changedsynergy.item.HyenaTrailclothItem.hasLowerSection(mob)&&slots.hasSlot(ChangedAccessorySlots.LEGS.get()))check(!slots.isItemAllowedWithOthers(ChangedAccessorySlots.LEGS.get(),new ItemStack(ChangedItems.BLACK_PANTS.get())),"outfit blocks trousers for bipedal wearer");
                if(net.parkabird.changedsynergy.compat.curios.CuriosCompat.isLoaded())check(top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(mob).orElseThrow(()->new AssertionError("Curios inventory")).findCurios(item).size()==1,"Curios and Changed share one outfit inventory");
            }
            mob.discard();
        }
        int index=0;var keys=new HashSet<String>();
        for(var rotation:net.minecraft.world.level.block.Rotation.values()){
            var template=server.getStructureManager().getOrCreate(ResourceLocation.parse("changed_synergy:desert_refuge"));var origin=new BlockPos(6000+index++*96,100,6000);var settings=new StructurePlaceSettings().setRotation(rotation);var box=template.getBoundingBox(settings,origin);
            for(int x=box.minX()>>4;x<=box.maxX()>>4;x++)for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++)activate(level,new BlockPos(x*16,100,z*16));
            check(template.placeInWorld(level,origin,origin,settings,net.minecraft.util.RandomSource.create(44),2),"real structure placement");
            var residents=level.getEntitiesOfClass(ChangedEntity.class,AABB.of(box),NaturalCampService::member);check(residents.size()==6,"five original residents plus one Chief");
            var chiefs=residents.stream().filter(StoryNpcService::isNpc).toList();check(chiefs.size()==1,"one fixed NPC per structure instance");var chief=chiefs.get(0);check(chief.getType()==type,"Chief is a jerboa");
            var expected=StructureTemplate.transform(new Vec3(29.5,20,19.5),net.minecraft.world.level.block.Mirror.NONE,rotation,BlockPos.ZERO).add(Vec3.atLowerCornerOf(origin));check(chief.position().distanceToSqr(expected)<.001,"Chief stays at reviewed office position in every rotation");check(level.noCollision(chief),"Chief spawns clear of structure blocks");
            check(keys.add(StoryNpcService.key(chief))&&CreatureLifeMemory.role(chief)==CreatureLifeMemory.GroupRole.CHIEF,"unique stable Chief identity");
            check(CreaturePersonality.traits(chief).containsAll(List.of(CreaturePersonality.Trait.CAUTIOUS,CreaturePersonality.Trait.POLITE)),"provisional cautious and polite preset");
            check(chief.getBasicPlayerInfo().getEyeStyle()==EyeStyle.TALL&&chief.getBasicPlayerInfo().getLeftIrisColor().toInt()==0x56BDB2,"authored tall turquoise eyes");
            check(AccessorySlots.isWearing(chief,s->s.is(ChangedSynergyItems.DESERT_NOMAD_OUTFIT.get())&&StoryNpcService.bound(s)),"default Pathfinder outfit is equipped and bound");
            check(NpcConversations.canOpen(chief)&&CampMembershipService.canJoin(chief,p),"authored Chief has dialogue and admission routes");
            check(CampLifeService.stores(chief).size()==1,"Chief uses independent community reserve");
            for(var resident:residents)check(NaturalCampService.member(resident)&&FactionReputation.groupId(resident).equals("light:arid"),"residents retain their camp and share the arid regional affiliation");
            var ordinary=residents.stream().filter(m->!StoryNpcService.isNpc(m)).findFirst().orElseThrow();check(NpcConversations.canOpen(ordinary)&&CampLifeService.profile(ordinary)==StoryNpcService.profile(chief),"ordinary residents share the real household profile");
            var saved=chief.saveWithoutId(new CompoundTag());var copy=(ChangedEntity)type.create(level);copy.load(saved);check(StoryNpcService.key(copy).equals(StoryNpcService.key(chief))&&copy.getBasicPlayerInfo().getLeftIrisColor().toInt()==0x56BDB2,"identity and appearance survive NBT load");
            var record=StoryNpcData.get(server).records.get(StoryNpcService.key(chief));check(record!=null&&record.hasUUID("Actor"),"persistent return record registered");
            if(index==1){
                chief.hurt(level.damageSources().genericKill(),Float.MAX_VALUE);chief.discard();check(record.getLong("Due")>level.getGameTime(),"death schedules return instead of instant duplicate");
                server.getWorldData().overworldData().setGameTime(record.getLong("Due")+20);
                flushEntities(level);
                for(int attempt=0;attempt<1000&&!level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(BlockPos.containing(expected)).toLong());attempt++){activate(level,BlockPos.containing(expected));server.pollTask();level.getChunkSource().pollTask();Thread.sleep(1);}
                check(StoryNpcService.restore(server,record),"new Chief profile still returns safely");var returned=StoryNpcService.actor(server,record);check(returned!=null&&AccessorySlots.isWearing(returned,s->s.is(ChangedSynergyItems.DESERT_NOMAD_OUTFIT.get())&&StoryNpcService.bound(s)),"returned Chief restores bound default clothing");check(!StoryNpcService.restore(server,record),"cannot duplicate living Chief");
            }
        }
        p.discard();System.out.println("DESERT_CONTENT_SERVER_PASS: "+checks+" registrations, stats, origin, anatomy, Curios, rotations, authored dialogue, storage and return checks");
    }
    private static void flushEntities(ServerLevel level)throws Exception{
        var field=Arrays.stream(ServerLevel.class.getDeclaredFields()).filter(f->net.minecraft.world.level.entity.PersistentEntitySectionManager.class.isAssignableFrom(f.getType())).findFirst().orElseThrow();field.setAccessible(true);
        var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)field.get(level);
        var storage=Arrays.stream(manager.getClass().getDeclaredFields()).filter(f->net.minecraft.world.level.entity.EntityPersistentStorage.class.isAssignableFrom(f.getType())).findFirst().orElseThrow();storage.setAccessible(true);
        ((net.minecraft.world.level.entity.EntityPersistentStorage<?>)storage.get(manager)).flush(false);manager.tick();
    }
    private static void activate(ServerLevel level,BlockPos pos)throws Exception{
        level.getChunk(pos);level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);var field=Arrays.stream(ServerLevel.class.getDeclaredFields()).filter(f->net.minecraft.world.level.entity.PersistentEntitySectionManager.class.isAssignableFrom(f.getType())).findFirst().orElseThrow();field.setAccessible(true);var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)field.get(level);manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(pos),FullChunkStatus.ENTITY_TICKING);manager.tick();
    }
}
