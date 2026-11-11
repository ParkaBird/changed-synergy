package net.parkabird.changedsynergy.regression;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;

final class DesertCampGenerationChecks {
    private static int checks;
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    private static ResourceLocation id(String p){return ResourceLocation.parse("changed_synergy:"+p);}
    static void run(MinecraftServer server){
        var level=server.overworld();var generator=level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();var registry=server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        BlockPos refuge=null;
        for(String name:List.of("oasis","muddy_campsite","desert_refuge")){
            var structure=registry.get(id(name));var placement=(RandomSpreadStructurePlacement)server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(id(name.equals("desert_refuge")?"desert_refuges":"desert_camps")).placement();
            BlockPos found=null;
            outer:for(int ring=0;ring<32;ring++)for(int x=-ring;x<=ring;x++)for(int z=-ring;z<=ring;z++){
                if(Math.max(Math.abs(x),Math.abs(z))!=ring)continue;
                var candidate=placement.getPotentialStructureChunk(level.getSeed(),x*placement.spacing(),z*placement.spacing());
                if(!placement.isStructureChunk(level.getChunkSource().getGeneratorState(),candidate.x,candidate.z))continue;
                var biome=generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(candidate.getMiddleBlockX()),QuartPos.fromBlock(70),QuartPos.fromBlock(candidate.getMiddleBlockZ()),random.sampler());if(!structure.biomes().contains(biome))continue;
                var start=structure.generate(server.registryAccess(),generator,generator.getBiomeSource(),random,server.getStructureManager(),level.getSeed(),candidate,0,level,structure.biomes()::contains);if(!start.isValid())continue;
                var actual=level.getChunk(candidate.x,candidate.z,ChunkStatus.STRUCTURE_STARTS).getStartForStructure(structure);if(actual==null||!actual.isValid())continue;
                var piece=(net.parkabird.changedsynergy.world.ResearchLabStructure.Piece)actual.getPieces().get(0);int ground=name.equals("oasis")?2:name.equals("muddy_campsite")?4:25;
                found=candidate.getMiddleBlockPosition(piece.templatePosition().getY()+ground);
                check(structure.biomes().contains(biome),"natural start respects the requested biome");
                check(!name.equals("oasis")||biome.is(net.minecraft.world.level.biome.Biomes.DESERT),"oasis is desert-only");
                check(!name.equals("muddy_campsite")||biome.is(net.minecraft.world.level.biome.Biomes.BADLANDS)||biome.is(net.minecraft.world.level.biome.Biomes.WOODED_BADLANDS)||biome.is(net.minecraft.world.level.biome.Biomes.ERODED_BADLANDS),"muddy camp stays in badlands variants");
                if(name.equals("muddy_campsite")){
                    var box=piece.getBoundingBox();
                    for(int sx=box.minX()-2;sx<=box.maxX()+2;sx+=2)for(int sz=box.minZ()-2;sz<=box.maxZ()+2;sz+=2){
                        var terrain=level.getChunk(sx>>4,sz>>4,ChunkStatus.SURFACE);
                        int top=terrain.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,sx&15,sz&15);
                        check(terrain.getBlockState(new BlockPos(sx,top,sz)).is(net.minecraft.world.level.block.Blocks.RED_SAND),"muddy camp has natural red-sand ground, not terracotta: "+sx+","+top+","+sz+" "+terrain.getBlockState(new BlockPos(sx,top,sz)));
                    }
                }
                System.out.println("DESERT_NATURAL_START "+name+" at="+found+" biome="+biome.unwrapKey()+" template_origin="+piece.templatePosition()+" ground_level="+ground);break outer;
            }
            check(found!=null,"real seeded natural start exists: "+name);if(name.equals("desert_refuge"))refuge=found;
        }
        var params=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(refuge)).create(LootContextParamSets.CHEST);
        var context=new LootContext.Builder(params).create(null);var function=net.parkabird.changedsynergy.world.NearbyObservatoryMapFunction.create("desert_refuge").build();
        var result=function.apply(new ItemStack(Items.MAP),context);check(result.is(Items.FILLED_MAP)&&MapItem.getSavedData(result,level)!=null,"bounded search yields a real explorer map to a generated refuge");
        var target=level.findNearestMapStructure(TagKey.create(Registries.STRUCTURE,id("on_desert_refuge_maps")),refuge,2,false);check(target!=null&&target.closerThan(refuge,256),"map destination points to the nearby real refuge");
        for(String table:List.of("chests/desert_camp_oasis","chests/desert_camp_muddy")){
            boolean found=false;for(int i=0;i<64&&!found;i++)found=server.getLootData().getLootTable(id(table)).getRandomItems(params,i*1337L).stream().anyMatch(s->s.is(Items.FILLED_MAP)&&s.getHoverName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getKey().equals("item.changed_synergy.desert_refuge_map"));
            check(found,"camp supplies can roll the real refuge map");
        }
        System.out.println("DESERT_CAMP_GENERATION_PASS: "+checks+" natural starts, biome restrictions and real map loot checks");
    }
}
