package net.parkabird.changedsynergy.origin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-owned identities survive death, offline periods and gamerule changes. */
public final class NativeOriginData extends SavedData {
    public final Map<UUID, Identity> players = new HashMap<>();
    public static final class Identity {
        public String state = "pending";
        public String form = "";
        public String current = "";
        public String faction = "";
        public String region = "";
        public BlockPos home = BlockPos.ZERO;
        public String roleName = "";
        public boolean globalChat;
    }

    public static NativeOriginData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(NativeOriginData::load,
                NativeOriginData::new, "changed_synergy_origins");
    }

    public static NativeOriginData load(CompoundTag root) {
        NativeOriginData data = new NativeOriginData();
        for (Tag value : root.getList("Players", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag)value;
            if (!tag.hasUUID("Id")) continue;
            Identity identity = new Identity();
            identity.state = tag.getString("State");
            // Unknown saved states must never reopen selection on an existing player.
            if (!identity.state.equals("pending") && !identity.state.equals("native")) identity.state = "human";
            identity.form = tag.getString("Form");
            identity.current = tag.getString("Current");
            identity.faction = tag.getString("Faction");
            identity.region = tag.getString("Region");
            identity.home = BlockPos.of(tag.getLong("Home"));
            identity.roleName = tag.getString("RoleName");
            identity.globalChat = tag.getBoolean("GlobalChat");
            data.players.put(tag.getUUID("Id"), identity);
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        players.forEach((id, identity) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putString("State", identity.state);
            tag.putString("Form", identity.form);
            tag.putString("Current", identity.current);
            tag.putString("Faction", identity.faction);
            tag.putString("Region", identity.region);
            tag.putLong("Home", identity.home.asLong());
            tag.putString("RoleName", identity.roleName);
            tag.putBoolean("GlobalChat", identity.globalChat);
            list.add(tag);
        });
        root.put("Players", list);
        return root;
    }
}
