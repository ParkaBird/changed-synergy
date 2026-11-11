package net.parkabird.changedsynergy.npc;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Camp membership, local trust and ritual history are independent of birth identity. */
public final class CampMembershipData extends SavedData {
    public final Map<UUID,CompoundTag> players=new HashMap<>();
    public static CampMembershipData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(CampMembershipData::load,CampMembershipData::new,"changed_synergy_memberships");}
    public CompoundTag player(UUID id){return players.computeIfAbsent(id,k->{var t=new CompoundTag();t.putUUID("Player",id);return t;});}
    public static CampMembershipData load(CompoundTag root){var data=new CampMembershipData();for(var t:root.getList("Players",10)){var p=(CompoundTag)t;if(p.hasUUID("Player"))data.players.put(p.getUUID("Player"),p.copy());}return data;}
    @Override public CompoundTag save(CompoundTag root){var list=new ListTag();players.values().forEach(p->list.add(p.copy()));root.put("Players",list);return root;}
}
