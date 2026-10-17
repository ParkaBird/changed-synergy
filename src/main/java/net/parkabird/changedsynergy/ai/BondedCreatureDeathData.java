package net.parkabird.changedsynergy.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent per-player tombstones for known creatures that die while the
 * player is offline. They are consumed the next time that player is available,
 * preventing a dead UUID from being mistaken for either an unloaded companion
 * or an unloaded relationship contact.
 */
public final class BondedCreatureDeathData extends SavedData {
    private static final String DATA_NAME = "changed_synergy_bond_deaths";
    private static final String PENDING = "Pending";

    private CompoundTag pending = new CompoundTag();

    public static BondedCreatureDeathData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                BondedCreatureDeathData::load,
                BondedCreatureDeathData::new,
                DATA_NAME);
    }

    private static BondedCreatureDeathData load(CompoundTag tag) {
        BondedCreatureDeathData data = new BondedCreatureDeathData();
        if (tag.contains(PENDING, Tag.TAG_COMPOUND)) {
            data.pending = tag.getCompound(PENDING).copy();
        }
        return data;
    }

    public void markDead(UUID owner, UUID creature) {
        markDead(owner, creature, new CompoundTag());
    }

    public void markDead(UUID owner, UUID creature, CompoundTag notice) {
        String ownerKey = owner.toString();
        CompoundTag creatures = pending.contains(ownerKey, Tag.TAG_COMPOUND)
                ? pending.getCompound(ownerKey)
                : new CompoundTag();
        creatures.put(creature.toString(), notice.copy());
        pending.put(ownerKey, creatures);
        setDirty();
    }

    public void clear(UUID owner, UUID creature) {
        String ownerKey = owner.toString();
        if (!pending.contains(ownerKey, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag creatures = pending.getCompound(ownerKey);
        creatures.remove(creature.toString());
        if (creatures.isEmpty()) {
            pending.remove(ownerKey);
        } else {
            pending.put(ownerKey, creatures);
        }
        setDirty();
    }

    /** True while a final-death tombstone protects this UUID from stale cards. */
    public boolean isDead(UUID owner, UUID creature) {
        String ownerKey = owner.toString();
        return pending.contains(ownerKey, Tag.TAG_COMPOUND)
                && pending.getCompound(ownerKey).contains(creature.toString());
    }

    public Map<UUID, CompoundTag> consume(UUID owner) {
        String ownerKey = owner.toString();
        if (!pending.contains(ownerKey, Tag.TAG_COMPOUND)) {
            return Map.of();
        }
        CompoundTag creatures = pending.getCompound(ownerKey);
        Map<UUID, CompoundTag> result = new LinkedHashMap<>();
        for (String key : creatures.getAllKeys()) {
            try {
                result.put(UUID.fromString(key), creatures.contains(key, Tag.TAG_COMPOUND)
                        ? creatures.getCompound(key).copy() : new CompoundTag());
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed legacy data rather than retaining it forever.
            }
        }
        pending.remove(ownerKey);
        setDirty();
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put(PENDING, pending.copy());
        return tag;
    }
}
