package net.parkabird.changedsynergy.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.Optional;
import net.ltxprogrammer.changed.world.features.structures.HangingBlockFixerProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Contributed surface and sea-level templates, using Changed's template cover support. */
public final class ResearchLabStructure extends Structure {
    public static final DeferredRegister<StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, ChangedSynergyMod.MOD_ID);
    public static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, ChangedSynergyMod.MOD_ID);
    public static final RegistryObject<StructureType<ResearchLabStructure>> TYPE =
            TYPES.register("research_lab", () -> () -> ResearchLabStructure.CODEC);
    public static final RegistryObject<StructurePieceType> PIECE = PIECES.register("research_lab",
            () -> (StructurePieceType.StructureTemplateType) Piece::new);
    public static final Codec<ResearchLabStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            settingsCodec(instance),
            ResourceLocation.CODEC.fieldOf("template").forGetter(s -> s.template),
            Codec.STRING.optionalFieldOf("required_mod", "").forGetter(s -> s.requiredMod),
            Codec.intRange(-1, 64).optionalFieldOf("waterline", -1).forGetter(s -> s.waterline),
            Codec.intRange(0, 64).optionalFieldOf("ground_level", 0).forGetter(s -> s.groundLevel),
            Codec.intRange(0, 16).optionalFieldOf("max_terrain_slope", 6).forGetter(s -> s.maxTerrainSlope),
            Codec.intRange(0, 8).optionalFieldOf("clearing_radius", 0).forGetter(s -> s.clearingRadius)
    ).apply(instance, ResearchLabStructure::new));

    private final ResourceLocation template;
    private final String requiredMod;
    private final int waterline;
    private final int groundLevel;
    private final int maxTerrainSlope;
    private final int clearingRadius;

    public ResearchLabStructure(StructureSettings settings, ResourceLocation template, String requiredMod, int waterline,
                                int groundLevel, int maxTerrainSlope, int clearingRadius) {
        super(settings);
        this.template = template;
        this.requiredMod = requiredMod;
        this.waterline = waterline;
        this.groundLevel = groundLevel;
        this.maxTerrainSlope = maxTerrainSlope;
        this.clearingRadius = clearingRadius;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // Check before reading the palette: missing optional blocks must never become air.
        if (!requiredMod.isEmpty() && !ModList.get().isLoaded(requiredMod)) return Optional.empty();
        var loaded = context.structureTemplateManager().get(template);
        if (loaded.isEmpty()) return Optional.empty();
        var size = loaded.get().getSize();
        if (groundLevel >= size.getY()) return Optional.empty();
        var rotation = Rotation.getRandom(context.random());
        // A snowy mountain rarely has a large flat patch exactly at the candidate chunk center.
        int[] offsets = mountainCamp() || redSandCamp() ? new int[]{0, -16, 16} : new int[]{0};
        for (int dx : offsets) for (int dz : offsets) {
            var anchor = context.chunkPos().getMiddleBlockPosition(0).offset(dx, 0, dz);
            int probeY = waterline >= 0
                    ? context.chunkGenerator().getSeaLevel() - 1 - waterline
                    : Math.min(context.heightAccessor().getMaxBuildHeight() - 1, 256);
            if (!context.validBiome().test(context.biomeSource().getNoiseBiome(
                    net.minecraft.core.QuartPos.fromBlock(anchor.getX()), net.minecraft.core.QuartPos.fromBlock(probeY),
                    net.minecraft.core.QuartPos.fromBlock(anchor.getZ()), context.randomState().sampler()))) continue;
            var position = groundPosition(context, size, rotation, anchor);
            if (position.isEmpty()) continue;
            var pos = position.get();
            if (!context.validBiome().test(context.biomeSource().getNoiseBiome(
                    net.minecraft.core.QuartPos.fromBlock(anchor.getX()), net.minecraft.core.QuartPos.fromBlock(pos.getY()),
                    net.minecraft.core.QuartPos.fromBlock(anchor.getZ()), context.randomState().sampler()))) continue;
            return Optional.of(new GenerationStub(anchor.atY(pos.getY()), builder -> builder.addPiece(
                    new Piece(context.structureTemplateManager(), template, pos, rotation, waterline >= 0, clearingRadius))));
        }
        return Optional.empty();
    }

    private boolean mountainCamp() { return template.getPath().equals("observatory"); }
    private boolean redSandCamp() { return template.getPath().equals("muddy_campsite"); }

    private boolean drySurface(GenerationContext context, BlockPos sample, int floor, int surface) {
        if (surface == floor) return true;
        if (!mountainCamp() || surface - floor > 4) return false;
        var top = context.chunkGenerator().getBaseColumn(sample.getX(), sample.getZ(), context.heightAccessor(),
                context.randomState()).getBlock(surface - 1);
        return top.is(Blocks.POWDER_SNOW) || top.is(Blocks.SNOW) || top.is(Blocks.SNOW_BLOCK);
    }

    private Optional<BlockPos> groundPosition(GenerationContext context, net.minecraft.core.Vec3i size,
                                              Rotation rotation, BlockPos anchor) {
        var center = StructureTemplate.transform(new BlockPos(size.getX() / 2, 0, size.getZ() / 2),
                Mirror.NONE, rotation, BlockPos.ZERO);
        var origin = anchor.subtract(center);
        int[] ground = new int[9];
        int index = 0;
        int minFloor = Integer.MAX_VALUE;
        int maxFloor = Integer.MIN_VALUE;
        for (int x : new int[]{0, size.getX() / 2, size.getX() - 1}) {
            for (int z : new int[]{0, size.getZ() / 2, size.getZ() - 1}) {
                var sample = StructureTemplate.transform(new BlockPos(x, 0, z), Mirror.NONE, rotation, BlockPos.ZERO).offset(origin);
                int floor = context.chunkGenerator().getBaseHeight(sample.getX(), sample.getZ(),
                        Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
                // Vanilla badlands turn to terracotta at the upper fringe of the red-sand lowlands.
                if (redSandCamp() && floor > 70) return Optional.empty();
                minFloor = Math.min(minFloor, floor);
                maxFloor = Math.max(maxFloor, floor);
                if (waterline < 0 && maxFloor - minFloor > maxTerrainSlope) return Optional.empty();
                int surface = context.chunkGenerator().getBaseHeight(sample.getX(), sample.getZ(),
                        Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
                if (waterline < 0 ? !drySurface(context, sample, floor, surface) : surface - floor < waterline - 2) return Optional.empty();
                ground[index++] = floor;
            }
        }
        Arrays.sort(ground);
        int y;
        if (waterline >= 0) {
            y = context.chunkGenerator().getSeaLevel() - 1 - waterline;
            if (ground[8] > y + 3 || ground[0] < y - 24) return Optional.empty();
        } else {
            if (ground[8] - ground[0] > maxTerrainSlope) return Optional.empty();
            y = ground[4] - 1 - groundLevel;
            if (clearingRadius > 0) {
                // Sample the whole clearing, not only the nine template corners.
                for (int x = -clearingRadius; x < size.getX() + clearingRadius; x += 6)
                    for (int z = -clearingRadius; z < size.getZ() + clearingRadius; z += 6) {
                        var sample = StructureTemplate.transform(new BlockPos(x, 0, z), Mirror.NONE, rotation, BlockPos.ZERO).offset(origin);
                        int floor = context.chunkGenerator().getBaseHeight(sample.getX(), sample.getZ(),
                                Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
                        if (Math.abs(floor - ground[4]) > maxTerrainSlope) return Optional.empty();
                        int surface = context.chunkGenerator().getBaseHeight(sample.getX(), sample.getZ(),
                                Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
                        if (!drySurface(context, sample, floor, surface)) return Optional.empty();
                    }
            }
        }
        if (y <= context.heightAccessor().getMinBuildHeight() + 1
                || y + size.getY() >= context.heightAccessor().getMaxBuildHeight()) return Optional.empty();
        return Optional.of(new BlockPos(origin.getX(), y, origin.getZ()));
    }

    @Override
    public StructureType<?> type() {
        return TYPE.get();
    }
    public boolean hasClearing() { return clearingRadius > 0; }

    public static final class Piece extends TemplateStructurePiece {
        private final boolean ocean;
        private final int clearingRadius;
        // Used only during placement; the piece saves its radius and rotation, not this processor.
        private static final BlockIgnoreProcessor RESTORE_CHANGED = new BlockIgnoreProcessor(java.util.List.of()) {
            @Override public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos origin, BlockPos pivot,
                    StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo placed, StructurePlaceSettings settings) {
                if(settings.getBoundingBox()!=null && !settings.getBoundingBox().isInside(placed.pos()))return null;
                var expected=placed.state().mirror(settings.getMirror()).rotate(settings.getRotation());
                var current=level.getBlockState(placed.pos());
                return current.equals(expected) || placed.nbt()!=null && current.is(expected.getBlock()) ? null : placed;
            }
        };

        public Piece(StructureTemplateManager manager, ResourceLocation template, BlockPos position, Rotation rotation, boolean ocean) {
            this(manager, template, position, rotation, ocean, template.getPath().equals("forest_treehouse") ? 3 : 0);
        }

        public Piece(StructureTemplateManager manager, ResourceLocation template, BlockPos position, Rotation rotation, boolean ocean, int clearingRadius) {
            super(PIECE.get(), 0, manager, template, template.toString(), settings(rotation), position);
            this.ocean = ocean;
            this.clearingRadius = clearingRadius;
            expandClearing();
        }

        public Piece(StructureTemplateManager manager, CompoundTag tag) {
            super(PIECE.get(), tag, manager, location -> settings(Rotation.valueOf(tag.getString("Rot"))));
            this.ocean = tag.getBoolean("Ocean");
            this.clearingRadius = tag.getInt("Clearing");
            expandClearing();
        }

        private void expandClearing() {
            if (clearingRadius > 0) boundingBox = new BoundingBox(boundingBox.minX() - clearingRadius, boundingBox.minY(),
                    boundingBox.minZ() - clearingRadius, boundingBox.maxX() + clearingRadius, boundingBox.maxY(), boundingBox.maxZ() + clearingRadius);
        }

        public boolean hasClearing() { return clearingRadius > 0; }
        private boolean mountainCamp() { return templateName.equals("changed_synergy:observatory"); }

        private static StructurePlaceSettings settings(Rotation rotation) {
            return new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE)
                    .setKeepLiquids(false).setKnownShape(true).setIgnoreEntities(false)
                    .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
                    .addProcessor(HangingBlockFixerProcessor.INSTANCE);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putString("Rot", placeSettings.getRotation().name());
            tag.putBoolean("Ocean", ocean);
            tag.putInt("Clearing", clearingRadius);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                                RandomSource random, BoundingBox clip, ChunkPos chunk, BlockPos pivot) {
            // Neighbor shapes must wait until adjoining chunks finish their multipart furniture.
            placeSettings.setBoundingBox(clip);
            if (hasClearing()) clearLandscape(level, clip, false);
            template.placeInWorld(level, templatePosition, pivot, placeSettings, random,
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            // Fill only beneath actual load-bearing base blocks, inside the current chunk.
            int base = templatePosition.getY() + (ocean ? 1 : 0);
            for (int x = Math.max(clip.minX(), boundingBox.minX()); x <= Math.min(clip.maxX(), boundingBox.maxX()); x++) {
                for (int z = Math.max(clip.minZ(), boundingBox.minZ()); z <= Math.min(clip.maxZ(), boundingBox.maxZ()); z++) {
                    var top = new BlockPos(x, base, z);
                    var state = level.getBlockState(top);
                    if (!state.isFaceSturdy(level, top, Direction.DOWN) || !state.getFluidState().isEmpty()) continue;
                    // Gravel/sand must not also serve as their own unsupported foundation.
                    var support = state.getBlock() instanceof FallingBlock ? Blocks.DIRT.defaultBlockState() : state;
                    for (int depth = 1; depth <= (ocean ? 26 : mountainCamp() ? 16 : 8); depth++) {
                        var below = top.below(depth);
                        if (below.getY() <= level.getMinBuildHeight() || !clip.isInside(below)) break;
                        var old = level.getBlockState(below);
                        if (!old.isAir() && old.getFluidState().isEmpty()) break;
                        level.setBlock(below, support, 2);
                    }
                }
            }
        }

        private void clearLandscape(WorldGenLevel level, BoundingBox clip, boolean placed) {
            for (int x = Math.max(clip.minX(), boundingBox.minX()); x <= Math.min(clip.maxX(), boundingBox.maxX()); x++)
                for (int z = Math.max(clip.minZ(), boundingBox.minZ()); z <= Math.min(clip.maxZ(), boundingBox.maxZ()); z++) {
                    boolean inside = x >= boundingBox.minX() + clearingRadius && x <= boundingBox.maxX() - clearingRadius
                            && z >= boundingBox.minZ() + clearingRadius && z <= boundingBox.maxZ() - clearingRadius;
                    int ground = templatePosition.getY();
                    if (!inside) {
                        int distance = Math.max(Math.max(boundingBox.minX()+clearingRadius-x,x-(boundingBox.maxX()-clearingRadius)),
                                Math.max(boundingBox.minZ()+clearingRadius-z,z-(boundingBox.maxZ()-clearingRadius)));
                        int steps = Math.max(0, distance-1);
                        var server = level.getLevel();
                        int natural = server.getChunkSource().getGenerator().getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,
                                level,server.getChunkSource().randomState())-1;
                        ground += Math.max(-steps,Math.min(steps,natural-ground));
                    }
                    for (int y = ground + 1; y <= boundingBox.maxY() + 8; y++) {
                        var pos = new BlockPos(x, y, z);
                        if (placed && inside && y <= boundingBox.maxY()) continue;
                        if (clip.isInside(pos) && !level.getBlockState(pos).isAir())
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS);
                    }
                    var top = new BlockPos(x, ground, z);
                    if (clip.isInside(top) && !(placed && inside)) level.setBlock(top,
                            (mountainCamp() ? Blocks.SNOW_BLOCK : Blocks.GRASS_BLOCK).defaultBlockState(), 2);
                    for (int depth = 1; depth <= (mountainCamp() ? 16 : 8); depth++) {
                        var below = top.below(depth);
                        if (!clip.isInside(below)) break;
                        var state = level.getBlockState(below);
                        if (state.isFaceSturdy(level, below, Direction.UP) && state.getFluidState().isEmpty()
                                && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)) break;
                        level.setBlock(below, Blocks.DIRT.defaultBlockState(), 2);
                    }
                }
        }

        /** Trees decorate after surface structures; repair only newly generated chunks, without respawning entities. */
        public void finishLandscape(WorldGenLevel level, BoundingBox clip) {
            if (!hasClearing()) return;
            clearLandscape(level, clip, true);
            var restore = settings(placeSettings.getRotation()).setIgnoreEntities(true).setBoundingBox(clip).addProcessor(RESTORE_CHANGED);
            template.placeInWorld(level, templatePosition, templatePosition, restore, RandomSource.create(0),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }

        @Override
        protected void handleDataMarker(String marker, BlockPos position, ServerLevelAccessor level,
                                        RandomSource random, BoundingBox box) {
        }
    }
}
