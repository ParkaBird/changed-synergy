package net.parkabird.changedsynergy.regression;

import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="changed_synergy",value=Dist.DEDICATED_SERVER)
public final class LandmarkLootRegression {
    private static int checks;
    private static ResourceLocation id(String path){return ResourceLocation.parse("changed_synergy:"+path);}
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    @SubscribeEvent public static void run(ServerStartedEvent event) {
        var server=event.getServer();var level=server.overworld();
        try {
            var sets=server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
            for(var pair:Map.of("forest_treehouses","woodland_mansions","hyena_hideouts","ocean_monuments","observatories","woodland_mansions").entrySet()) {
                var actual=(RandomSpreadStructurePlacement)sets.get(id(pair.getKey())).placement();
                var nativePlacement=(RandomSpreadStructurePlacement)sets.get(ResourceLocation.parse("minecraft:"+pair.getValue())).placement();
                check(actual.spacing()==nativePlacement.spacing()&&actual.separation()==nativePlacement.separation()
                        &&actual.spreadType()==nativePlacement.spreadType(),"matches vanilla landmark placement: "+pair.getKey());
            }
            rotations(server);
            var loot=server.getLootData();
            var gson=net.minecraft.world.level.storage.loot.Deserializers.createFunctionSerializer().create();
            var function=net.parkabird.changedsynergy.world.NearbyObservatoryMapFunction.create().build();
            var restored=gson.fromJson(gson.toJson(function,net.minecraft.world.level.storage.loot.functions.LootItemFunction.class),net.minecraft.world.level.storage.loot.functions.LootItemFunction.class);
            check(restored instanceof net.parkabird.changedsynergy.world.NearbyObservatoryMapFunction,"bounded explorer function survives native loot serialization");
            var netherParams=new LootParams.Builder(server.getLevel(net.minecraft.world.level.Level.NETHER)).withParameter(LootContextParams.ORIGIN,Vec3.ZERO).create(LootContextParamSets.CHEST);
            check(restored.apply(new net.minecraft.world.item.ItemStack(Items.MAP),new LootContext.Builder(netherParams).create(null)).isEmpty(),"a dimension without a destination emits no blank pretend observatory map");
            check(loot.getLootTable(id("chests/forest_watchtower_cartographer")).getPool("changed_synergy_forest_treehouse_map")!=null,"watchtower map pool registered");
            check(loot.getLootTable(id("chests/hyena_camp_tropical")).getPool("changed_synergy_hyena_hideout_map")!=null,"tropical camp map pool registered");
            check(loot.getLootTable(id("chests/forest_watchtower_cartographer")).getPool("changed_synergy_observatory_map")!=null,"watchtower observatory map pool registered");
            check(loot.getLootTable(ResourceLocation.parse("minecraft:chests/ancient_city")).getPool("changed_synergy_observatory_map")!=null,"mountain-associated ancient city map pool registered");
            check(loot.getLootTable(ResourceLocation.parse("minecraft:chests/simple_dungeon")).getPool("changed_synergy_forest_treehouse_map")!=null,"vanilla chest map pool registered");
            check(loot.getLootTable(ResourceLocation.parse("minecraft:chests/village/village_plains_house")).getPool("changed_synergy_hyena_hideout_map")==null,"nested supplies do not roll duplicate maps");
            for(var name:List.of("forest_treehouse","hyena_hideout","observatory")) {
                var origin=natural(server,name,name.equals("forest_treehouse")?"forest_treehouses":name.equals("observatory")?"observatories":"hyena_hideouts");
                var tag=TagKey.create(Registries.STRUCTURE,id("on_"+name+"_maps"));
                var target=level.findNearestMapStructure(tag,origin,2,false);
                check(target!=null&&target.closerThan(origin,256),"native explorer lookup finds the nearby real landmark: "+name);
                level.getChunk(origin);
                System.out.println("LANDMARK_LOOT_SOURCE "+name+" "+origin+" biome="+level.getBiome(origin).unwrapKey());
                var params=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(origin)).create(LootContextParamSets.CHEST);
                var table=loot.getLootTable(id(name.equals("hyena_hideout")?"chests/hyena_camp_tropical":"chests/forest_watchtower_cartographer"));
                boolean found=false;
                var random=RandomSource.create(883779);
                for(int seed=0;seed<64&&!found;seed++)for(var stack:table.getRandomItems(params,random.nextLong()))if(stack.is(Items.FILLED_MAP)&&stack.getHoverName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents label&&label.getKey().equals("item.changed_synergy."+name+"_map")) {
                    var saved=MapItem.getSavedData(stack,level);
                    check(saved!=null,"explorer map has persistent map data");
                    check(stack.hasCustomHoverName(),"explorer map has its own translated name");
                    var markers=stack.getTag().getList("Decorations",10);
                    check(markers.size()==1&&markers.getCompound(0).getDouble("x")==target.getX()
                            &&markers.getCompound(0).getDouble("z")==target.getZ(),"map marker matches the actual located landmark");found=true;
                }
                check(found,"actual chest loot produces a filled destination map: "+name);
                System.out.println("LANDMARK_MAP_TARGET "+name+" "+origin+" -> "+target);
            }
            // A nonmatching biome must never get either map, even with many loot rolls.
            var wrong=new BlockPos(0,-50,0);
            for(int attempt=0;attempt<32&&level.getBiome(wrong).is(net.minecraft.world.level.biome.Biomes.FOREST);attempt++)wrong=wrong.offset(128,0,128);
            check(!level.getBiome(wrong).is(net.minecraft.world.level.biome.Biomes.FOREST),"negative biome fixture");
            var params=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(wrong)).create(LootContextParamSets.CHEST);
            for(int seed=0;seed<32;seed++)check(server.getLootData().getLootTable(id("chests/forest_watchtower_cartographer")).getRandomItems(params,seed).stream().noneMatch(s->s.is(Items.FILLED_MAP)&&s.getHoverName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents label&&label.getKey().equals("item.changed_synergy.forest_treehouse_map")),"forest map stays in the intended biome");
            System.out.println("LANDMARK_LOOT_REGRESSION_PASS: "+checks+" rarity, native locating, real explorer maps, biome filters, rotated eyes/chests and save/reload checks");
        }catch(Throwable error){System.err.println("LANDMARK_LOOT_REGRESSION_FAIL");error.printStackTrace();}
        finally{server.halt(false);}
    }
    private static BlockPos natural(MinecraftServer server,String name,String set) {
        var level=server.overworld();var generator=level.getChunkSource().getGenerator();var state=level.getChunkSource().randomState();
        var structure=server.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id(name));
        var placement=(RandomSpreadStructurePlacement)server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(id(set)).placement();
        for(int ring=0;ring<40;ring++)for(int x=-ring;x<=ring;x++)for(int z=-ring;z<=ring;z++) {
            if(Math.max(Math.abs(x),Math.abs(z))!=ring)continue;
            var candidate=placement.getPotentialStructureChunk(level.getSeed(),x*placement.spacing(),z*placement.spacing());
            if(!placement.isStructureChunk(level.getChunkSource().getGeneratorState(),candidate.x,candidate.z))continue;
            var biome=generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(candidate.getMiddleBlockX()),QuartPos.fromBlock(70),QuartPos.fromBlock(candidate.getMiddleBlockZ()),state.sampler());
            if(!structure.biomes().contains(biome))continue;
            var start=structure.generate(server.registryAccess(),generator,generator.getBiomeSource(),state,server.getStructureManager(),level.getSeed(),candidate,0,level,structure.biomes()::contains);
            if(!start.isValid())continue;
            var generated=level.getChunk(candidate.x,candidate.z,ChunkStatus.STRUCTURE_STARTS).getStartForStructure(structure);
            if(generated==null||!generated.isValid())continue;
            return candidate.getMiddleBlockPosition(80);
        }
        throw new AssertionError("No natural landmark: "+name);
    }
    private static void rotations(MinecraftServer server) throws Exception {
        var level=server.overworld();var template=server.getStructureManager().getOrCreate(id("forest_treehouse"));int i=0;
        var managerField=Arrays.stream(ServerLevel.class.getDeclaredFields()).filter(f->net.minecraft.world.level.entity.PersistentEntitySectionManager.class.isAssignableFrom(f.getType())).findFirst().orElseThrow();managerField.setAccessible(true);
        var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)managerField.get(level);
        for(var rotation:Rotation.values()) {
            var origin=new BlockPos(20000+i++*128,100,20000);var settings=new StructurePlaceSettings().setRotation(rotation);var box=template.getBoundingBox(settings,origin);
            for(int x=box.minX()>>4;x<=box.maxX()>>4;x++)for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) {
                level.getChunk(x,z);level.setChunkForced(x,z,true);manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(x,z),net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING);
            }manager.tick();
            check(template.placeInWorld(level,origin,origin,settings,RandomSource.create(1),2),"rotated template placement");
            for(int x:List.of(16,17)) {
                var local=new BlockPos(x,29,9);var pos=StructureTemplate.transform(local,Mirror.NONE,rotation,BlockPos.ZERO).offset(origin);
                var data=level.getBlockEntity(pos).saveWithFullMetadata();
                check(data.getString("LootTable").equals("minecraft:chests/village/village_shepherd"),"both rotated chest halves use shepherd loot");
            }
            var mobs=level.getEntitiesOfClass(ChangedEntity.class,AABB.of(box));check(mobs.size()==10,"seven original Changed residents plus three hive bees, with no duplicates");
            var fixed=mobs.stream().filter(m->m.getPersistentData().getCompound("ChangedSynergyStructureResident").getBoolean("Character")).toList();check(fixed.size()==1,"one canopy character");
            var moth=fixed.get(0);var tag=moth.saveWithoutId(new CompoundTag());var info=tag.getCompound("LocalVariantInfo");
            check(info.getInt("eyeStyle")==2&&info.getInt("irisLeft")==0x90B8C2&&info.getInt("irisRight")==0x90B8C2,"mist-blue tall eyes survive native entity loading");
            check(!moth.isNoAi()&&!moth.isInvulnerable()&&!moth.isNoGravity(),"editor restrictions removed");
            var clone=moth.getType().create(level);clone.load(tag);check(clone.saveWithoutId(new CompoundTag()).getCompound("LocalVariantInfo").equals(info),"appearance survives save/reload");clone.discard();mobs.forEach(ChangedEntity::discard);
        }
    }
}
