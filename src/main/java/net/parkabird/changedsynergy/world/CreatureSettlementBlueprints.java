package net.parkabird.changedsynergy.world;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory.AnchorKind;
import net.parkabird.changedsynergy.ai.HunterFaction;
import net.parkabird.changedsynergy.ai.LightFactionGroup;

/** Datapack-loaded block blueprints for small community caches. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class CreatureSettlementBlueprints extends SimpleJsonResourceReloadListener {
    public static final CreatureSettlementBlueprints INSTANCE =
            new CreatureSettlementBlueprints();
    private static final String DIRECTORY = "settlement_blueprints";

    private volatile Map<ResourceLocation, Blueprint> blueprints = Map.of();

    private CreatureSettlementBlueprints() {
        super(new Gson(), DIRECTORY);
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    public Optional<Blueprint> forCreature(ChangedEntity creature) {
        HunterFaction faction = HunterFaction.of(creature);
        String lightGroup = LightFactionGroup.of(creature);
        return blueprints.values().stream()
                .filter(blueprint -> blueprint.matches(faction, lightGroup))
                .sorted(Comparator.comparingInt(
                        (Blueprint blueprint) -> blueprint.lightGroups().isEmpty() ? 0 : 1)
                        .reversed())
                .findFirst();
    }

    /** Resolves an explicitly selected layout, used by habitat-specific caches. */
    public Optional<Blueprint> byId(ResourceLocation id) {
        return Optional.ofNullable(blueprints.get(id));
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> resources,
            ResourceManager manager,
            ProfilerFiller profiler) {
        Map<ResourceLocation, Blueprint> loaded = new LinkedHashMap<>();
        resources.forEach((id, json) -> {
            try {
                Blueprint blueprint = parse(id, json.getAsJsonObject());
                if (!blueprint.blocks().isEmpty()) {
                    loaded.put(id, blueprint);
                }
            } catch (RuntimeException exception) {
                ChangedSynergyMod.LOGGER.error(
                        "Could not load settlement blueprint {}", id, exception);
            }
        });
        blueprints = Collections.unmodifiableMap(loaded);
        ChangedSynergyMod.LOGGER.info(
                "Loaded {} Changed: Synergy settlement blueprints", loaded.size());
    }

    private static Blueprint parse(ResourceLocation id, JsonObject json) {
        Set<HunterFaction> factions = EnumSet.noneOf(HunterFaction.class);
        JsonArray factionArray = json.getAsJsonArray("factions");
        if (factionArray != null) {
            for (JsonElement element : factionArray) {
                HunterFaction faction = HunterFaction.fromId(element.getAsString());
                if (faction != null) {
                    factions.add(faction);
                }
            }
        }

        Set<AnchorKind> anchors = EnumSet.noneOf(AnchorKind.class);
        JsonArray anchorArray = json.getAsJsonArray("anchors");
        if (anchorArray != null) {
            for (JsonElement element : anchorArray) {
                String value = element.getAsString();
                for (AnchorKind anchor : AnchorKind.values()) {
                    if (anchor.id().equalsIgnoreCase(value)) {
                        anchors.add(anchor);
                        break;
                    }
                }
            }
        }

        Set<String> lightGroups = new java.util.LinkedHashSet<>();
        JsonArray lightGroupArray = json.getAsJsonArray("light_groups");
        if (lightGroupArray != null) {
            for (JsonElement element : lightGroupArray) {
                lightGroups.add(LightFactionGroup.normalize(element.getAsString()));
            }
        }

        BlockPos cache = parsePosition(json.getAsJsonObject("cache"));
        List<BlockPos> restPoints = new ArrayList<>();
        JsonArray restArray = json.getAsJsonArray("rest_points");
        if (restArray != null) {
            for (JsonElement element : restArray) {
                restPoints.add(parsePosition(element.getAsJsonObject()));
            }
        }
        boolean dry = !json.has("requires_dry")
                || json.get("requires_dry").getAsBoolean();
        List<Cell> blocks = new ArrayList<>();
        JsonArray blockArray = json.getAsJsonArray("blocks");
        if (blockArray != null) {
            for (JsonElement element : blockArray) {
                JsonObject blockJson = element.getAsJsonObject();
                ResourceLocation blockId = ResourceLocation.tryParse(
                        blockJson.get("block").getAsString());
                if (blockId == null || !ForgeRegistries.BLOCKS.containsKey(blockId)) {
                    throw new IllegalArgumentException(
                            "Unknown block in " + id + ": " + blockJson.get("block"));
                }
                Map<String, String> properties = new LinkedHashMap<>();
                JsonObject propertyJson = blockJson.getAsJsonObject("properties");
                if (propertyJson != null) {
                    propertyJson.entrySet().forEach(entry ->
                            properties.put(entry.getKey(), entry.getValue().getAsString()));
                }
                CompoundTag blockEntityData = new CompoundTag();
                if (blockJson.has("nbt")) {
                    try {
                        blockEntityData = TagParser.parseTag(
                                blockJson.get("nbt").getAsString());
                    } catch (CommandSyntaxException exception) {
                        throw new IllegalArgumentException(
                                "Invalid block entity NBT in " + id + ": "
                                        + blockJson.get("nbt"),
                                exception);
                    }
                }
                blocks.add(new Cell(
                        new BlockPos(
                                blockJson.get("x").getAsInt(),
                                blockJson.get("y").getAsInt(),
                                blockJson.get("z").getAsInt()),
                        blockId,
                        Map.copyOf(properties),
                        blockEntityData));
            }
        }
        List<Display> displays = new ArrayList<>();
        JsonArray displayArray = json.getAsJsonArray("displays");
        if (displayArray != null) {
            for (JsonElement element : displayArray) {
                JsonObject displayJson = element.getAsJsonObject();
                ResourceLocation itemId = ResourceLocation.tryParse(
                        displayJson.get("item").getAsString());
                if (itemId == null || !ForgeRegistries.ITEMS.containsKey(itemId)) {
                    throw new IllegalArgumentException(
                            "Unknown display item in " + id + ": " + displayJson.get("item"));
                }
                Direction facing = Direction.byName(displayJson.has("facing")
                        ? displayJson.get("facing").getAsString() : "up");
                if (facing == null) {
                    throw new IllegalArgumentException(
                            "Unknown display facing in " + id + ": " + displayJson.get("facing"));
                }
                int rotation = displayJson.has("rotation")
                        ? Math.floorMod(displayJson.get("rotation").getAsInt(), 8) : 0;
                displays.add(new Display(
                        new BlockPos(
                                displayJson.get("x").getAsInt(),
                                displayJson.get("y").getAsInt(),
                                displayJson.get("z").getAsInt()),
                        itemId,
                        facing,
                        rotation));
            }
        }
        return new Blueprint(
                id,
                Set.copyOf(factions),
                Set.copyOf(anchors),
                Set.copyOf(lightGroups),
                cache,
                List.copyOf(restPoints),
                dry,
                List.copyOf(blocks),
                List.copyOf(displays));
    }

    private static BlockPos parsePosition(JsonObject json) {
        if (json == null) {
            return BlockPos.ZERO;
        }
        return new BlockPos(
                json.get("x").getAsInt(),
                json.get("y").getAsInt(),
                json.get("z").getAsInt());
    }

    public record Blueprint(
            ResourceLocation id,
            Set<HunterFaction> factions,
            Set<AnchorKind> anchors,
            Set<String> lightGroups,
            BlockPos cacheOffset,
            List<BlockPos> restPoints,
            boolean requiresDry,
            List<Cell> blocks,
            List<Display> displays) {
        private boolean matches(HunterFaction faction, String lightGroup) {
            // "anchors" remains a readable datapack field for old packs, but
            // no longer selects a personal activity-centre variant.
            return factions.contains(faction)
                    && (lightGroups.isEmpty() || lightGroups.contains(lightGroup));
        }
    }

    /** An invisible, fixed item frame used as a low-cost cache prop. */
    public record Display(
            BlockPos offset,
            ResourceLocation item,
            Direction facing,
            int rotation) {
    }

    public record Cell(
            BlockPos offset,
            ResourceLocation block,
            Map<String, String> properties,
            CompoundTag blockEntityData) {
        public BlockState state() {
            Block resolved = ForgeRegistries.BLOCKS.getValue(block);
            BlockState state = resolved == null
                    ? Blocks.AIR.defaultBlockState() : resolved.defaultBlockState();
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                Property<?> property = state.getBlock().getStateDefinition()
                        .getProperty(entry.getKey());
                if (property != null) {
                    state = applyProperty(state, property, entry.getValue());
                }
            }
            return state;
        }

        private static <T extends Comparable<T>> BlockState applyProperty(
                BlockState state,
                Property<T> property,
                String value) {
            return property.getValue(value)
                    .map(parsed -> state.setValue(property, parsed))
                    .orElse(state);
        }
    }
}
