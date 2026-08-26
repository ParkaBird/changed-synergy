package net.parkabird.changedsynergy.ai;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent release requests for bonded creatures in unloaded chunks.  The
 * request is consumed when the creature next joins a server level, allowing
 * both its Synergy bond and any Changed/Addon native pet state to be cleared.
 */
public final class BondedCreatureReleaseData extends SavedData {
    private static final String DATA_NAME = "changed_synergy_bond_releases";
    private static final String PENDING = "Pending";

    private CompoundTag pending = new CompoundTag();

    public static BondedCreatureReleaseData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                BondedCreatureReleaseData::load,
                BondedCreatureReleaseData::new,
                DATA_NAME);
    }

    private static BondedCreatureReleaseData load(CompoundTag tag) {
        BondedCreatureReleaseData data = new BondedCreatureReleaseData();
        if (tag.contains(PENDING, Tag.TAG_COMPOUND)) {
            data.pending = tag.getCompound(PENDING).copy();
        }
        return data;
    }

    public void markReleased(UUID owner, UUID creature) {
        String creatureKey = creature.toString();
        CompoundTag owners = pending.contains(creatureKey, Tag.TAG_COMPOUND)
                ? pending.getCompound(creatureKey)
                : new CompoundTag();
        owners.putBoolean(owner.toString(), true);
        pending.put(creatureKey, owners);
        setDirty();
    }

    public void clear(UUID owner, UUID creature) {
        String creatureKey = creature.toString();
        if (!pending.contains(creatureKey, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag owners = pending.getCompound(creatureKey);
        owners.remove(owner.toString());
        if (owners.isEmpty()) {
            pending.remove(creatureKey);
        } else {
            pending.put(creatureKey, owners);
        }
        setDirty();
    }

    public Set<UUID> consume(UUID creature) {
        String creatureKey = creature.toString();
        if (!pending.contains(creatureKey, Tag.TAG_COMPOUND)) {
            return Set.of();
        }
        CompoundTag owners = pending.getCompound(creatureKey);
        Set<UUID> result = new LinkedHashSet<>();
        for (String key : owners.getAllKeys()) {
            try {
                result.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {
                // Drop malformed old entries instead of retaining them forever.
            }
        }
        pending.remove(creatureKey);
        setDirty();
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put(PENDING, pending.copy());
        return tag;
    }
}
