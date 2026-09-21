package net.parkabird.changedsynergy.ai;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Applies roster removals to creatures whose chunks were unloaded at the time. */
public final class CreatureRelationshipForgetData extends SavedData {
    private static final String DATA_NAME = "changed_synergy_relationship_forgets";
    private static final String PENDING = "Pending";
    private CompoundTag pending = new CompoundTag();

    public static CreatureRelationshipForgetData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                CreatureRelationshipForgetData::load,
                CreatureRelationshipForgetData::new,
                DATA_NAME);
    }

    private static CreatureRelationshipForgetData load(CompoundTag tag) {
        CreatureRelationshipForgetData data = new CreatureRelationshipForgetData();
        if (tag.contains(PENDING, Tag.TAG_COMPOUND)) {
            data.pending = tag.getCompound(PENDING).copy();
        }
        return data;
    }

    public void queue(UUID player, UUID creature) {
        String key = creature.toString();
        CompoundTag players = pending.contains(key, Tag.TAG_COMPOUND)
                ? pending.getCompound(key) : new CompoundTag();
        players.putBoolean(player.toString(), true);
        pending.put(key, players);
        setDirty();
    }

    public Set<UUID> consume(UUID creature) {
        String key = creature.toString();
        if (!pending.contains(key, Tag.TAG_COMPOUND)) {
            return Set.of();
        }
        Set<UUID> players = new LinkedHashSet<>();
        for (String player : pending.getCompound(key).getAllKeys()) {
            try {
                players.add(UUID.fromString(player));
            } catch (IllegalArgumentException ignored) {
                // Discard malformed saved entries.
            }
        }
        pending.remove(key);
        setDirty();
        return players;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put(PENDING, pending.copy());
        return tag;
    }
}
