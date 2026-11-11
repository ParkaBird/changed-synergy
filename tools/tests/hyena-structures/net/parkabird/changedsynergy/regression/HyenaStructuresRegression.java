package net.parkabird.changedsynergy.regression;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.world.ResearchLabStructure;

/** Actual data-pack decoding, registries, natural starts and persisted placement. */
@Mod.EventBusSubscriber(modid = "changed_synergy", value = Dist.DEDICATED_SERVER)
public final class HyenaStructuresRegression {
    private static final String[] NAMES = {"hyena_hideout", "hyena_camp_tropical", "hyena_camp_open", "hyena_camp_snowy"};
    private static int checks;

    @SubscribeEvent public static void run(ServerStartedEvent event) {
        var server = event.getServer();
        try {
            var level = server.overworld();
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            var structures = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
            var biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
            for (var name : NAMES) {
                var structure = structures.get(id(name));
                check(structure instanceof ResearchLabStructure, "structure registry and codec: " + name);
                for (var biome : biomes.holders().toList()) {
                    var key = biome.key().location().toString();
                    boolean expected = switch (name) {
                        case "hyena_hideout" -> biome.is(BiomeTags.IS_SAVANNA);
                        case "hyena_camp_tropical" -> biome.is(BiomeTags.IS_JUNGLE) || biome.is(BiomeTags.IS_SAVANNA);
                        case "hyena_camp_open" -> Set.of("minecraft:plains", "minecraft:sunflower_plains").contains(key);
                        default -> key.equals("minecraft:snowy_plains");
                    };
                    check(structure.biomes().contains(biome) == expected, "exact biome eligibility: " + name + " " + key);
                }
                check(structure.biomes().size() > 0, "nonempty allowed biomes");
                check(structure.terrainAdaptation() == net.minecraft.world.level.levelgen.structure.TerrainAdjustment.NONE,
                        "template foundations do not change the surrounding terrain surface");
                var raw = read(level, name);
                validate(level, raw);
                for (var stateTag : raw.getList("palette", Tag.TAG_COMPOUND)) validateState((CompoundTag)stateTag);
                var palette = raw.getList("palette", Tag.TAG_COMPOUND);
                Set<BlockPos> seen = new HashSet<>();
                for (var value : raw.getList("blocks", Tag.TAG_COMPOUND)) {
                    var block = (CompoundTag)value;
                    var p = block.getList("pos", Tag.TAG_INT);
                    check(seen.add(new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2))), "unique block position");
                    check(block.getInt("state") >= 0 && block.getInt("state") < palette.size(), "valid palette index");
                }
                check(raw.getList("latexCovers", Tag.TAG_COMPOUND).isEmpty(), "empty latex overlays are omitted");
                check(raw.getList("entities", Tag.TAG_COMPOUND).size() == (name.equals("hyena_hideout") ? 12 : 2), "authored occupants plus fixed hyena residents");
                long females=raw.getList("entities", Tag.TAG_COMPOUND).stream().map(t -> ((CompoundTag)t).getCompound("nbt"))
                        .filter(t -> t.getString("id").equals("changed_synergy:latex_spotted_hyena_female")).count();
                check(females == (name.equals("hyena_hideout") ? 1 : 0), "one female exclusively in the hideout");
                for (var rotation : Rotation.values()) {
                    var piece = new ResearchLabStructure.Piece(server.getStructureManager(), id(name), new BlockPos(1000, 90, 1000), rotation, false);
                    var restored = new ResearchLabStructure.Piece(server.getStructureManager(), piece.createTag(StructurePieceSerializationContext.fromLevel(level)));
                    check(piece.getBoundingBox().equals(restored.getBoundingBox()) && restored.getRotation() == rotation
                            && restored.templatePosition().equals(piece.templatePosition()), "rotation and underground origin survive reload");
                }
                var start = findNatural(level, structure, name);
                check(start != null && start.isValid(), "real seeded natural start: " + name);
                var piece = (ResearchLabStructure.Piece)start.getPieces().get(0);
                check(structure.biomes().contains(level.getBiome(start.getChunkPos().getMiddleBlockPosition(piece.templatePosition().getY()))),
                        "natural start is in an eligible biome");
                var generator = level.getChunkSource().getGenerator();
                int[] floor = new int[9]; int at = 0;
                var size = raw.getList("size", Tag.TAG_INT);
                for (int x : new int[]{0, size.getInt(0) / 2, size.getInt(0) - 1})
                    for (int z : new int[]{0, size.getInt(2) / 2, size.getInt(2) - 1}) {
                        var sample = net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(
                                new BlockPos(x, 0, z), net.minecraft.world.level.block.Mirror.NONE, piece.getRotation(), BlockPos.ZERO).offset(piece.templatePosition());
                        floor[at++] = generator.getBaseHeight(sample.getX(), sample.getZ(), Heightmap.Types.OCEAN_FLOOR_WG,
                                level, level.getChunkSource().randomState());
                    }
                Arrays.sort(floor);
                check(piece.templatePosition().getY() + (name.equals("hyena_hideout") ? 3 : 0) == floor[4] - 1,
                        "authored surface, rather than lowest basement block, meets natural terrain");
                check(floor[8] - floor[0] <= (name.equals("hyena_hideout") ? 3 : 2), "steep terrain excluded");
                var box=piece.getBoundingBox();
                for(int cx=(box.minX()>>4)-1;cx<=(box.maxX()>>4)+1;cx++)
                    for(int cz=(box.minZ()>>4)-1;cz<=(box.maxZ()>>4)+1;cz++)level.getChunk(cx,cz,ChunkStatus.FULL);
                int outsideChecks=0;
                for(int axis=0;axis<2;axis++)for(int side:new int[]{-1,1})for(int fraction:new int[]{1,2,3}) {
                    int x=axis==0?(side<0?box.minX()-1:box.maxX()+1):box.minX()+(box.maxX()-box.minX())*fraction/4;
                    int z=axis==1?(side<0?box.minZ()-1:box.maxZ()+1):box.minZ()+(box.maxZ()-box.minZ())*fraction/4;
                    int expected=generator.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,level.getChunkSource().randomState())-1;
                    int actual=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1;
                    while(actual>level.getMinBuildHeight()) {
                        var p=new BlockPos(x,actual,z);var state=level.getBlockState(p);
                        if(state.isFaceSturdy(level,p,net.minecraft.core.Direction.UP)
                                &&!state.is(net.minecraft.tags.BlockTags.LOGS)&&!state.is(net.minecraft.tags.BlockTags.LEAVES)
                                &&state.getFluidState().isEmpty())break;
                        actual--;
                    }
                    // Natural surface decorators can add snow; a foundation must not lower its surroundings.
                    check(actual>=expected,"actual generated perimeter has no beard-cut moat: "+name+" "+x+","+z+" expected="+expected+" actual="+actual);
                    outsideChecks++;
                }
                check(outsideChecks==12,"actual FULL-chunk terrain inspected on every side");
                System.out.println("HYENA_NATURAL_START " + name + " " + piece.templatePosition() + " " + piece.getRotation());
            }
            System.out.println("HYENA_STRUCTURES_REGRESSION_PASS: " + checks + " registry, palette, contents, biome, terrain, rotation and natural-start checks");
        } catch (Throwable failure) {
            System.err.println("HYENA_STRUCTURES_REGRESSION_FAIL"); failure.printStackTrace();
        } finally { server.halt(false); }
    }

    private static StructureStart findNatural(ServerLevel level, Structure structure, String name) {
        var server = level.getServer();
        var generator = level.getChunkSource().getGenerator();
        var state = level.getChunkSource().randomState();
        var placement = (RandomSpreadStructurePlacement)server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                .get(id(name.equals("hyena_hideout") ? "hyena_hideouts" : "hyena_camps")).placement();
        for (int ring = 0; ring <= 28; ring++) for (int x = -ring; x <= ring; x++) for (int z = -ring; z <= ring; z++) {
            if (Math.max(Math.abs(x), Math.abs(z)) != ring) continue;
            var candidate = placement.getPotentialStructureChunk(level.getSeed(), x * placement.spacing(), z * placement.spacing());
            if (!placement.isStructureChunk(level.getChunkSource().getGeneratorState(), candidate.x, candidate.z)) continue;
            var biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(candidate.getMiddleBlockX()),
                    QuartPos.fromBlock(70), QuartPos.fromBlock(candidate.getMiddleBlockZ()), state.sampler());
            if (!structure.biomes().contains(biome)) continue;
            var start = structure.generate(server.registryAccess(), generator, generator.getBiomeSource(), state,
                    server.getStructureManager(), level.getSeed(), candidate, 0, level, structure.biomes()::contains);
            if (!start.isValid()) continue;
            var natural = level.getChunk(candidate.x, candidate.z, ChunkStatus.STRUCTURE_STARTS).getStartForStructure(structure);
            if (natural != null && natural.isValid()) return natural;
        }
        return null;
    }

    private static CompoundTag read(ServerLevel level, String name) throws Exception {
        try (var resource = level.getServer().getResourceManager().open(id("structures/" + name + ".nbt"))) {
            return NbtIo.readCompressed(resource);
        }
    }
    private static void validate(ServerLevel level, Tag value) {
        if (value instanceof CompoundTag tag) {
            if (tag.contains("LootTable")) {
                check(level.getServer().getLootData().getLootTable(new ResourceLocation(tag.getString("LootTable"))) != LootTable.EMPTY, "loot table resolves");
                check(tag.getList("Items", Tag.TAG_COMPOUND).isEmpty(), "generated loot does not overwrite authored items");
            }
            if (tag.contains("Count") && tag.contains("id", Tag.TAG_STRING))
                check(BuiltInRegistries.ITEM.containsKey(new ResourceLocation(tag.getString("id"))), "authored item exists");
            if (tag.contains("Pos") && tag.contains("id", Tag.TAG_STRING)) {
                check(BuiltInRegistries.ENTITY_TYPE.containsKey(new ResourceLocation(tag.getString("id"))), "authored occupant exists");
                check(!tag.contains("UUID") && !tag.contains("Attributes") && !tag.contains("ForgeCaps"), "no old-world identity or foreign attributes");
            }
            for (var key : tag.getAllKeys()) validate(level, tag.get(key));
        } else if (value instanceof ListTag list) for (var child : list) validate(level, child);
    }
    private static void validateState(CompoundTag tag) {
        var name = new ResourceLocation(tag.getString("Name"));
        check(BuiltInRegistries.BLOCK.containsKey(name), "block exists: " + name);
        var block = BuiltInRegistries.BLOCK.get(name);
        var props = tag.getCompound("Properties");
        for (var key : props.getAllKeys()) {
            var property = block.getStateDefinition().getProperty(key);
            check(property != null && property.getValue(props.getString(key)).isPresent(), "valid state: " + name + " " + key);
        }
    }
    private static ResourceLocation id(String path) { return new ResourceLocation("changed_synergy", path); }
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
}
