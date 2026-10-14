package net.parkabird.changedsynergy.origin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import com.mojang.serialization.Codec;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.Util;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.dialogue.LatexTerritory;

/** One global queued search; noise probes do not load chunks or scan entities. */
final class OriginSpawnSearch {
    private static final int WAIT_LIMIT = 1200;
    private static final long LOCATE_LIMIT_NANOS = 60_000_000_000L;
    private final ServerLevel level;
    private final NativeOriginCatalog.Entry entry;
    private final BlockPos center;
    private final boolean fallback;
    private int probes;
    private int loads;
    private int waitingTicks;
    private CompletableFuture<?> chunk;
    private CompletableFuture<BlockPos> locating;
    private BlockPos habitat;
    private BlockPos candidate;
    private volatile boolean cancelled;
    Vec3 result;
    boolean finished;

    OriginSpawnSearch(ServerLevel level, NativeOriginCatalog.Entry entry, boolean fallback, BlockPos origin) {
        this.level = level;
        this.entry = entry;
        this.center = fallback ? level.getSharedSpawnPos() : origin.immutable();
        this.fallback = fallback;
    }

    void tick() {
        if (finished) return;
        if (!fallback && habitat == null) {
            if (locating == null) {
                var source = level.getChunkSource().getGenerator().getBiomeSource();
                var sampler = level.getChunkSource().randomState().sampler();
                BlockPos origin = center.atY(entry.region().equals("cave") ? 0 : level.getSeaLevel());
                long deadline = System.nanoTime() + LOCATE_LIMIT_NANOS;
                // Vanilla's locator calls its predicate only once, before the noise loop.
                // Check cancellation at each sample so cancelled searches really stop working.
                BiomeSource bounded = new BiomeSource() {
                    @Override protected Codec<? extends BiomeSource> codec() { throw new UnsupportedOperationException("Search-only biome source"); }
                    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() { return source.possibleBiomes().stream(); }
                    @Override public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler query) {
                        if (cancelled || Thread.currentThread().isInterrupted() || System.nanoTime() - deadline >= 0)
                            throw new CancellationException("Origin biome search expired");
                        return source.getNoiseBiome(x, y, z, query);
                    }
                };
                waitingTicks = 0;
                // Same noise-only locator as /locate biome, off the server thread; no chunks are read here.
                locating = CompletableFuture.supplyAsync(() -> {
                    var found = bounded.findClosestBiome3d(origin, 8192, 32, 64, this::matches, sampler, level);
                    return found == null ? null : found.getFirst();
                }, Util.backgroundExecutor());
            }
            if (!locating.isDone()) {
                if (++waitingTicks > WAIT_LIMIT) cancel();
                return;
            }
            if (locating.isCompletedExceptionally()) { finished = true; return; }
            habitat = locating.join();
            locating = null;
            if (habitat == null) { finished = true; return; }
        }
        if (chunk != null) {
            if (!chunk.isDone()) {
                if (++waitingTicks > WAIT_LIMIT) cancel();
                return;
            }
            if (!chunk.isCompletedExceptionally() && level.hasChunkAt(candidate)) inspect();
            chunk = null;
            if (result != null || loads >= (fallback ? 8 : 24)) {
                finished = true;
                return;
            }
        }
        // Eight validation probes/tick, one chunk request at a time. Try the located habitat first.
        for (int budget = 0; budget < 8; budget++) {
            int index = probes++;
            int maximum = fallback ? 32 : 2048;
            if (index >= maximum) { finished = true; return; }
            double angle = index * 2.399963229728653;
            boolean nearby = habitat != null && index < 25;
            BlockPos origin = nearby ? habitat : center;
            double radius = fallback || nearby ? Math.sqrt(index) * 16 : Math.sqrt(index / 2047.0) * 8192;
            int x = origin.getX() + (int)(Math.cos(angle) * radius);
            int z = origin.getZ() + (int)(Math.sin(angle) * radius);
            candidate = new BlockPos(x, center.getY(), z);
            if (!level.getWorldBorder().isWithinBounds(candidate)) continue;
            Holder<Biome> biome = level.getChunkSource().getGenerator().getBiomeSource()
                    .getNoiseBiome(x >> 2, (nearby ? habitat.getY() : entry.region().equals("cave") ? 0 : 64) >> 2, z >> 2,
                            level.getChunkSource().randomState().sampler());
            if (!fallback && !matches(biome)) continue;
            loads++;
            waitingTicks = 0;
            chunk = level.getChunkSource().getChunkFuture(x >> 4, z >> 4, ChunkStatus.FULL, true);
            return;
        }
    }

    /** Does not cancel Minecraft's shared chunk future or unload anyone else's chunk. */
    void cancel() { cancelled = true; finished = true; }

    private boolean matches(Holder<Biome> biome) {
        String id = biome.unwrapKey().map(key -> key.location().getPath()).orElse("");
        if (entry.faction().equals("white")) return id.equals("white_latex_forest");
        if (entry.faction().equals("dark")) return id.equals("dark_latex_plains");
        boolean aquatic = entry.faction().equals("aquatic");
        String region = LatexTerritory.populationRegion(biome,
                biome.unwrapKey().map(key -> key.location()).orElse(null), entry.region().equals("cave"));
        if (aquatic) return region.equals("ocean") || region.equals("river")
                || region.equals("beach") && entry.variant().breatheMode != TransfurVariant.BreatheMode.WATER;
        return entry.region().equals(region);
    }

    private void inspect() {
        for (int dx = -3; dx <= 3; dx += 3) {
            for (int dz = -3; dz <= 3; dz += 3) {
                int x = candidate.getX() + dx;
                int z = candidate.getZ() + dz;
                // Edge probes may cross outside the one asynchronously requested chunk.
                if (!level.hasChunkAt(new BlockPos(x, level.getMinBuildHeight(), z))) continue;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                boolean water = entry.variant().breatheMode == TransfurVariant.BreatheMode.WATER
                        || entry.faction().equals("aquatic") && entry.variant().breatheMode.canBreatheWater();
                int start = water ? top : entry.region().equals("cave") && !fallback ? top - 12 : top + 2;
                int end = water ? Math.max(level.getMinBuildHeight() + 1, top - 32)
                        : entry.region().equals("cave") && !fallback ? level.getMinBuildHeight() + 1 : top - 8;
                for (int y = start; y >= end; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!fallback && !matches(level.getBiome(pos))) continue;
                    if (LatexTerritory.facilityFactionAt(level, pos) != null) continue;
                    Vec3 point = Vec3.atBottomCenterOf(pos);
                    if (safe(point, water)) { result = point; return; }
                }
            }
        }
    }

    private boolean safe(Vec3 point, boolean water) {
        return safe(level, entry.variant(), point, water, null);
    }

    static boolean safe(ServerLevel level, TransfurVariant<?> variant, Vec3 point, boolean water, net.minecraft.world.entity.Entity exclude) {
        EntityDimensions size = variant.getEntityType().getDimensions();
        AABB box = size.makeBoundingBox(point);
        if (!level.getWorldBorder().isWithinBounds(box)) return false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (!level.hasChunkAt(pos)) return false;
            var state = level.getBlockState(pos);
            if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                    || state.is(Blocks.CACTUS) || state.is(Blocks.POWDER_SNOW)) return false;
        }
        if (!level.noCollision(box) || !level.getEntities(exclude, box.inflate(1)).isEmpty()) return false;
        BlockPos feet = BlockPos.containing(point);
        if (water) return level.getFluidState(feet).is(FluidTags.WATER)
                && level.getFluidState(BlockPos.containing(point.add(0, size.height, 0))).is(FluidTags.WATER);
        if (!level.getFluidState(feet).isEmpty()) return false;
        var floor = level.getBlockState(feet.below());
        if (floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS)) return false;
        return floor.isFaceSturdy(level, feet.below(), Direction.UP);
    }
}
