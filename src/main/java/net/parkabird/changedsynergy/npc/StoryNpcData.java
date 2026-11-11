package net.parkabird.changedsynergy.npc;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-owned identities outlive their replaceable physical entities. */
public final class StoryNpcData extends SavedData {
    public final Map<String,CompoundTag> records=new LinkedHashMap<>();
    public static StoryNpcData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(StoryNpcData::load,StoryNpcData::new,"changed_synergy_story_npcs");}
    public static StoryNpcData load(CompoundTag root){var data=new StoryNpcData();for(var t:root.getList("Records",10)){var r=(CompoundTag)t;data.records.put(r.getString("Key"),r.copy());}return data;}
    @Override public CompoundTag save(CompoundTag root){var list=new ListTag();records.values().forEach(r->list.add(r.copy()));root.put("Records",list);return root;}
}
