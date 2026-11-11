package net.parkabird.changedsynergy.npc;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ai.StructureResidentGoal;
import net.parkabird.changedsynergy.world.ResearchLabStructure;

/** Registers missing legacy residents only when their real structure-start chunk is loaded. */
@Mod.EventBusSubscriber(modid="changed_synergy")
public final class StoryNpcStructures {
    @SubscribeEvent public static void load(ChunkEvent.Load event) {
        if(!(event.getLevel() instanceof ServerLevel level))return;
        level.getServer().execute(()->{
            for(var start:event.getChunk().getAllStarts().values())for(var piece:start.getPieces())
                if(piece instanceof ResearchLabStructure.Piece research)register(level,research);
        });
    }
    public static void register(ServerLevel level,ResearchLabStructure.Piece piece) {
        var saved=piece.createTag(StructurePieceSerializationContext.fromLevel(level));String template=saved.getString("Template");
        for(var profile:NpcDefinitions.all()) {
            if(!template.equals("changed_synergy:"+profile.template()))continue;
            var settings=new StructurePlaceSettings().setRotation(piece.getRotation());var origin=piece.templatePosition();
            var model=level.getServer().getStructureManager().get(ResourceLocation.parse(template)).orElse(null);if(model==null)continue;
            var box=model.getBoundingBox(settings,origin);String homeKey=level.dimension().location()+":"+new BlockPos(box.minX(),box.minY(),box.minZ()).asLong();
            String key=profile.id()+"@"+homeKey;var store=StoryNpcData.get(level.getServer());if(store.records.containsKey(key))continue;
            CompoundTag raw;
            try(var input=level.getServer().getResourceManager().open(ResourceLocation.parse("changed_synergy:structures/"+profile.template()+".nbt"))){raw=NbtIo.readCompressed(input);}
            catch(java.io.IOException e){net.parkabird.changedsynergy.ChangedSynergyMod.LOGGER.warn("Cannot read NPC template {}",template,e);continue;}
            for(var value:raw.getList("entities",10)) {
                var entity=(CompoundTag)value;var tag=entity.getCompound("nbt").copy();
                if(!tag.getString("id").equals(profile.species())||!tag.getCompound("ForgeData").getCompound(StructureResidentGoal.DATA).getBoolean("Character"))continue;
                var local=entity.getList("pos",6);var pos=StructureTemplate.transform(new Vec3(local.getDouble(0),local.getDouble(1),local.getDouble(2)),Mirror.NONE,piece.getRotation(),BlockPos.ZERO).add(Vec3.atLowerCornerOf(origin));
                var position=new ListTag();position.add(DoubleTag.valueOf(pos.x));position.add(DoubleTag.valueOf(pos.y));position.add(DoubleTag.valueOf(pos.z));tag.put("Pos",position);tag.remove("UUID");
                var rotation=tag.getList("Rotation",5);rotation.set(0,FloatTag.valueOf(rotation.getFloat(0)+piece.getRotation().ordinal()*90));
                var marker=new CompoundTag();marker.putString("Profile",profile.id());marker.putString("Key",key);tag.getCompound("ForgeData").put(StoryNpcService.DATA,marker);
                var record=new CompoundTag();record.putString("Key",key);record.putString("Profile",profile.id());record.putString("Dimension",level.dimension().location().toString());record.putString("LastDimension",level.dimension().location().toString());record.putString("HomeKey",homeKey);
                record.putDouble("HomeX",pos.x);record.putDouble("HomeY",pos.y);record.putDouble("HomeZ",pos.z);record.putLong("LastPos",BlockPos.containing(pos).asLong());
                record.putDouble("MinX",box.minX());record.putDouble("MinY",box.minY());record.putDouble("MinZ",box.minZ());record.putDouble("MaxX",box.maxX()+1);record.putDouble("MaxY",box.maxY()+1);record.putDouble("MaxZ",box.maxZ()+1);
                record.put("Snapshot",tag);record.putLong("Due",level.getServer().overworld().getGameTime()+StoryNpcService.RETURN_DELAY);store.records.put(key,record);store.setDirty();break;
            }
        }
    }
}
