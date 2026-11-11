package net.parkabird.changedsynergy.npc;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Structure households remain registered while their residents are unloaded or returning. */
public final class CampLifeData extends SavedData {
    public final Map<String,CompoundTag> camps=new LinkedHashMap<>();
    public static CampLifeData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(CampLifeData::load,CampLifeData::new,"changed_synergy_camp_life");}
    public static CampLifeData load(CompoundTag root){var data=new CampLifeData();for(var value:root.getList("Camps",10)){var tag=(CompoundTag)value;if(!tag.getString("Key").isEmpty())data.camps.put(tag.getString("Key"),tag.copy());}return data;}
    @Override public CompoundTag save(CompoundTag root){var list=new ListTag();camps.values().forEach(t->list.add(t.copy()));root.put("Camps",list);return root;}
}
