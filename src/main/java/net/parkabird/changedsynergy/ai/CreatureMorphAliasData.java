package net.parkabird.changedsynergy.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/** Durable old-to-new UUID aliases for relationships whose owner was offline during a morph. */
public final class CreatureMorphAliasData extends SavedData {
    private static final String DATA_NAME = "changed_synergy_creature_morph_aliases";
    private static final String ALIASES = "Aliases";
    private final Map<UUID, UUID> aliases = new LinkedHashMap<>();

    public static CreatureMorphAliasData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                CreatureMorphAliasData::load,
                CreatureMorphAliasData::new,
                DATA_NAME);
    }

    private static CreatureMorphAliasData load(CompoundTag tag) {
        CreatureMorphAliasData data = new CreatureMorphAliasData();
        if (!tag.contains(ALIASES, Tag.TAG_COMPOUND)) {
            return data;
        }
        CompoundTag stored = tag.getCompound(ALIASES);
        for (String key : stored.getAllKeys()) {
            try {
                UUID previous = UUID.fromString(key);
                UUID replacement = UUID.fromString(stored.getString(key));
                if (!previous.equals(replacement)) {
                    data.aliases.put(previous, replacement);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed legacy entries instead of preventing world load.
            }
        }
        return data;
    }

    /** Records a morph and collapses older chains so every alias points at the newest body. */
    public void record(UUID previousId, UUID replacementId) {
        if (previousId.equals(replacementId)) {
            return;
        }
        UUID resolved = resolve(replacementId);
        aliases.replaceAll((ignored, current) ->
                current.equals(previousId) ? resolved : current);
        aliases.put(previousId, resolved);
        setDirty();
    }

    public void apply(ServerPlayer player) {
        for (Map.Entry<UUID, UUID> entry : aliases.entrySet()) {
            UUID previous = entry.getKey();
            UUID replacement = resolve(entry.getValue());
            PlayerRelationshipSettings.replaceContactReference(
                    player, previous, replacement);
            LatexSocialMemory.replacePlayerBondReference(
                    player, previous, replacement);
            BondedCreatureLifecycle.replaceReference(
                    player, previous, replacement);
            InvoluntaryTransfurNegotiation.replaceSourceReference(
                    player, previous, replacement);
        }
    }

    /** Returns the newest known body for a creature UUID. */
    public UUID resolveAlias(UUID start) {
        return resolve(start);
    }

    private UUID resolve(UUID start) {
        UUID current = start;
        for (int guard = 0; guard < 32; guard++) {
            UUID next = aliases.get(current);
            if (next == null || next.equals(current)) {
                break;
            }
            current = next;
        }
        return current;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag stored = new CompoundTag();
        aliases.forEach((previous, replacement) ->
                stored.putString(previous.toString(), replacement.toString()));
        tag.put(ALIASES, stored);
        return tag;
    }
}
