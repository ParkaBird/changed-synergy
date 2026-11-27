package net.parkabird.changedsynergy.ai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Server-authoritative records behind identity-bearing revival masks. */
public final class BondedRevivalData extends SavedData {
    private static final String DATA_NAME = "changed_synergy_bonded_revivals";
    private static final String RECORDS = "Records";
    private final Map<UUID, CompoundTag> records = new LinkedHashMap<>();

    public static BondedRevivalData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                BondedRevivalData::load,
                BondedRevivalData::new,
                DATA_NAME);
    }

    private static BondedRevivalData load(CompoundTag root) {
        BondedRevivalData data = new BondedRevivalData();
        CompoundTag stored = root.getCompound(RECORDS);
        for (String key : stored.getAllKeys()) {
            try {
                UUID token = UUID.fromString(key);
                if (stored.contains(key, Tag.TAG_COMPOUND)) {
                    data.records.put(token, stored.getCompound(key).copy());
                }
            } catch (IllegalArgumentException ignored) {
                // A damaged entry must not prevent the world from loading.
            }
        }
        return data;
    }

    public boolean hasOldEntity(UUID oldEntity) {
        return records.values().stream()
                .anyMatch(record -> record.hasUUID("OldEntity")
                        && oldEntity.equals(record.getUUID("OldEntity")));
    }

    public UUID create(CompoundTag record) {
        UUID token = UUID.randomUUID();
        records.put(token, record.copy());
        setDirty();
        return token;
    }

    @Nullable
    public CompoundTag record(UUID token) {
        CompoundTag record = records.get(token);
        return record == null ? null : record.copy();
    }

    public Collection<CompoundTag> records() {
        Collection<CompoundTag> copies = new ArrayList<>();
        records.values().forEach(record -> copies.add(record.copy()));
        return copies;
    }

    public Map<UUID, CompoundTag> recordsWithTokens() {
        Map<UUID, CompoundTag> copies = new LinkedHashMap<>();
        records.forEach((token, record) -> copies.put(token, record.copy()));
        return copies;
    }

    public static boolean isPlayerApplied(CompoundTag record, UUID player) {
        if (!record.contains("AppliedPlayers", Tag.TAG_LIST)) return false;
        String wanted = player.toString();
        for (Tag tag : record.getList("AppliedPlayers", Tag.TAG_STRING)) {
            if (wanted.equals(tag.getAsString())) return true;
        }
        return false;
    }

    public void markPlayerApplied(UUID token, UUID player) {
        CompoundTag record = records.get(token);
        if (record == null) return;
        ListTag applied = record.contains("AppliedPlayers", Tag.TAG_LIST)
                ? record.getList("AppliedPlayers", Tag.TAG_STRING)
                : new ListTag();
        String wanted = player.toString();
        for (Tag tag : applied) {
            if (wanted.equals(tag.getAsString())) return;
        }
        applied.add(StringTag.valueOf(wanted));
        record.put("AppliedPlayers", applied);
        setDirty();
    }

    /** Only unused death records belonging to this player can be recalled. */
    public Map<UUID, CompoundTag> availableTo(UUID player) {
        Map<UUID, CompoundTag> available = new LinkedHashMap<>();
        records.forEach((token, record) -> {
            if (!record.hasUUID("OldEntity") || !record.contains("Snapshot", Tag.TAG_COMPOUND)) return;
            String state = record.getString("State");
            if (!(state.equals("broken") || state.equals("repaired") || state.equals("archived"))) return;
            boolean bonded = record.hasUUID("Owner") && player.equals(record.getUUID("Owner"));
            for (Tag bond : record.getList("Bonds", Tag.TAG_STRING)) bonded |= player.toString().equals(bond.getAsString());
            if (bonded) available.put(token, record.copy());
        });
        return available;
    }

    public boolean transition(UUID token, String expected, String next) {
        CompoundTag record = records.get(token);
        if (record == null || !expected.equals(record.getString("State"))) {
            return false;
        }
        record.putString("State", next);
        setDirty();
        return true;
    }

    public boolean transitionFromEither(
            UUID token, String first, String second, String next) {
        CompoundTag record = records.get(token);
        if (record == null) {
            return false;
        }
        String state = record.getString("State");
        if (!first.equals(state) && !second.equals(state)) {
            return false;
        }
        record.putString("State", next);
        setDirty();
        return true;
    }

    public void putUuid(UUID token, String key, UUID value) {
        CompoundTag record = records.get(token);
        if (record != null) {
            record.putUUID(key, value);
            setDirty();
        }
    }

    public void setState(UUID token, String state) {
        CompoundTag record = records.get(token);
        if (record != null) {
            record.putString("State", state);
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        CompoundTag stored = new CompoundTag();
        records.forEach((token, record) ->
                stored.put(token.toString(), record.copy()));
        root.put(RECORDS, stored);
        return root;
    }
}
