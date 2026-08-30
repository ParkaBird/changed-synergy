package net.parkabird.changedsynergy.event;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.world.data.ActiveFacilityInstance;
import net.ltxprogrammer.changed.world.data.ChangedGameDataAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.HunterFaction;
import net.parkabird.changedsynergy.ai.FactionReputation;
import net.parkabird.changedsynergy.dialogue.LatexTerritory;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.TerritorySyncPacket;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/** Throttled server lookup for biome, territory and facility-room context. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TerritoryContextEvents {
    private static final int UPDATE_INTERVAL = 20;
    private static final ResourceLocation CAVE_REGION =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "cave_region");
    private static final Map<UUID, Context> LAST_CONTEXT = new HashMap<>();
    private static final Context EMPTY_CONTEXT = new Context(
            false, "", "", "", "", -1, "", 0);

    private TerritoryContextEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || Math.floorMod(
                                player.tickCount
                                        + player.getId(),
                                UPDATE_INTERVAL)
                        != 0) {
            return;
        }
        synchronize(player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_CONTEXT.remove(player.getUUID());
            player.server.execute(() -> synchronize(player));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_CONTEXT.remove(player.getUUID());
            player.server.execute(() -> synchronize(player));
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_CONTEXT.remove(player.getUUID());
            player.server.execute(() -> synchronize(player));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_CONTEXT.remove(event.getEntity().getUUID());
    }

    private static void synchronize(ServerPlayer player) {
        if (!player.isAlive() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Context context = ChangedSynergyGameRules.enabled(
                        level, ChangedSynergyGameRules.TERRITORY_DISPLAY)
                ? inspect(level, player)
                : EMPTY_CONTEXT;
        Context previous = LAST_CONTEXT.put(player.getUUID(), context);
        if (context.equals(previous)) {
            return;
        }
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                context.packet());
    }

    private static Context inspect(
            ServerLevel level,
            ServerPlayer player) {
        BlockPos position = player.blockPosition();
        BiomeSample biome = displayedBiome(level, player);
        String biomeId = biome.displayId() == null
                ? ""
                : biome.displayId().toString();

        FacilitySnapshot room = facilityAt(level, position);
        if (room != null) {
            HunterFaction faction =
                    LatexTerritory.dominantFaction(room.piece().zone());
            return new Context(
                    true,
                    room.facilityCode(),
                    room.piece().zone().toString(),
                    room.piece().pieceName().toString(),
                    biomeId,
                    faction == null ? -1 : faction.ordinal(),
                    "",
                    FactionReputation.scoreAt(
                            faction, player, level, position));
        }

        if (!Level.OVERWORLD.equals(level.dimension())) {
            return new Context(
                    false,
                    "",
                    "",
                    "",
                    "",
                    -1,
                    "",
                    0);
        }

        LatexTerritory.BiomePopulation population =
                LatexTerritory.naturalPopulationAt(
                        level,
                        biome.samplePosition(),
                        biome.underground());
        HunterFaction faction = population == null
                ? null
                : population.faction();
        return new Context(
                false,
                "",
                "",
                "",
                biomeId,
                faction == null ? -1 : faction.ordinal(),
                population == null
                        ? ""
                        : population.populationRegion(),
                FactionReputation.scoreAt(
                        faction, player, level, biome.samplePosition()));
    }

    /**
     * Biomes are three-dimensional in modern world generation. Sampling only
     * the player's feet can leave the title on the surface biome while their
     * eyes have already crossed a cave-biome cell boundary. Prefer the eye
     * cell, then nearby cave cells while the player is genuinely underground.
     */
    private static BiomeSample displayedBiome(
            ServerLevel level,
            ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        BlockPos eye = BlockPos.containing(player.getEyePosition());
        ResourceLocation exact = biomeId(level, eye);
        int surface = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                feet.getX(),
                feet.getZ());
        boolean underground = isCaveBiome(exact)
                || eye.getY() <= surface - 6
                        && !level.canSeeSky(eye);
        if (!underground) {
            return new BiomeSample(exact, eye, false);
        }

        BlockPos[] samples = {
            eye,
            feet,
            eye.above(4),
            eye.below(4),
            eye.above(8),
            eye.below(8),
            eye.above(12),
            eye.below(12),
            eye.north(4),
            eye.south(4),
            eye.west(4),
            eye.east(4),
            eye.north(8),
            eye.south(8),
            eye.west(8),
            eye.east(8)
        };
        for (BlockPos sample : samples) {
            ResourceLocation candidate = biomeId(level, sample);
            if (isCaveBiome(candidate)) {
                return new BiomeSample(candidate, sample, true);
            }
        }
        // Most vanilla caves retain the surface biome registry value. Keep the
        // real eye-cell for spawn lookup, but present a stable cave context.
        return new BiomeSample(CAVE_REGION, eye, true);
    }

    private static ResourceLocation biomeId(
            ServerLevel level,
            BlockPos position) {
        Holder<Biome> biome = level.getBiome(position);
        return biome.unwrapKey()
                .map(ResourceKey::location)
                .orElseGet(() -> level.registryAccess()
                        .registryOrThrow(Registries.BIOME)
                        .getKey(biome.value()));
    }

    private static boolean isCaveBiome(ResourceLocation biome) {
        if (biome == null) {
            return false;
        }
        if (Biomes.LUSH_CAVES.location().equals(biome)
                || Biomes.DRIPSTONE_CAVES.location().equals(biome)
                || Biomes.DEEP_DARK.location().equals(biome)) {
            return true;
        }
        String path = biome.getPath();
        return path.contains("cave")
                || path.contains("cavern")
                || path.contains("grotto")
                || path.contains("underground");
    }

    public static FacilitySnapshot facilityAt(
            ServerLevel level,
            BlockPos position) {
        if (!(level instanceof ChangedGameDataAccessor accessor)) {
            return null;
        }
        ChunkPos chunk = new ChunkPos(position);
        for (ActiveFacilityInstance facility
                : accessor.getChangedGameData().facilities) {
            ActiveFacilityInstance.Header header = facility.getHeader();
            if (header != null
                    && header.minimum != null
                    && header.maximum != null
                    && (chunk.x < header.minimum.x
                            || chunk.x > header.maximum.x
                            || chunk.z < header.minimum.z
                            || chunk.z > header.maximum.z)) {
                continue;
            }
            for (ActiveFacilityInstance.PieceGenerationInfo piece
                    : facility.getPieceGenerationInfos()) {
                if (piece.region().isInside(position)) {
                    return new FacilitySnapshot(
                            facility,
                            piece,
                            facilityCode(facility));
                }
            }
        }
        return null;
    }

    public static String facilityCode(ActiveFacilityInstance facility) {
        ActiveFacilityInstance.Header header = facility.getHeader();
        String identity = header == null
                ? Integer.toHexString(System.identityHashCode(facility))
                : header.name
                        + ":"
                        + header.minimum
                        + ":"
                        + header.maximum;
        return String.format(
                java.util.Locale.ROOT,
                "SY-%06d",
                Math.floorMod(identity.hashCode(), 1_000_000));
    }

    /** Stable coloured work section encoded by Changed's facility templates. */
    public static String facilitySectionId(@Nullable FacilitySnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        String path = snapshot.piece().pieceName().getPath()
                .toLowerCase(Locale.ROOT);
        boolean blue = hasFacilityMarker(path, "blue");
        boolean gray = hasFacilityMarker(path, "gray")
                || hasFacilityMarker(path, "grey");
        boolean red = hasFacilityMarker(path, "red");
        boolean maintenance = hasFacilityMarker(path, "maintenance");
        int matches = (blue ? 1 : 0)
                + (gray ? 1 : 0)
                + (red ? 1 : 0)
                + (maintenance ? 1 : 0);
        // Transition templates intentionally belong to neither adjacent work
        // section. Treating e.g. blue_stairs_to_red as blue made workers from
        // both populations intermittently share one community at the border.
        if (matches != 1) {
            return "";
        }
        if (blue) {
            return "blue";
        }
        if (gray) {
            return "gray";
        }
        if (red) {
            return "red";
        }
        if (maintenance) {
            return "maintenance";
        }
        return "";
    }

    private static boolean hasFacilityMarker(String path, String marker) {
        int slash = path.lastIndexOf('/');
        String file = slash >= 0 ? path.substring(slash + 1) : path;
        return path.contains("/" + marker + "/")
                || file.startsWith(marker + "_")
                || file.contains("_" + marker + "_")
                || file.endsWith("_" + marker);
    }

    public record FacilitySnapshot(
            ActiveFacilityInstance facility,
            ActiveFacilityInstance.PieceGenerationInfo piece,
            String facilityCode) {
    }

    private record Context(
            boolean facility,
            String facilityCode,
            String zoneId,
            String templateId,
            String biomeId,
            int factionOrdinal,
            String populationRegion,
            int reputationScore) {
        private TerritorySyncPacket packet() {
            return new TerritorySyncPacket(
                    facility,
                    facilityCode,
                    zoneId,
                    templateId,
                    biomeId,
                    factionOrdinal,
                    populationRegion,
                    reputationScore);
        }
    }

    private record BiomeSample(
            ResourceLocation displayId,
            BlockPos samplePosition,
            boolean underground) {
    }
}
