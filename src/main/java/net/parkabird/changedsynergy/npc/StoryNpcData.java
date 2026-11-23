package net.parkabird.changedsynergy.npc;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-owned identities outlive their replaceable physical entities. */
public final class StoryNpcData extends SavedData {
    public final Map<String,CompoundTag> records=new LinkedHashMap<>();
    public final Map<UUID,Set<String>> clues=new HashMap<>();
    public static StoryNpcData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(StoryNpcData::load,StoryNpcData::new,"changed_synergy_story_npcs");}
    public static StoryNpcData load(CompoundTag root){
        var data=new StoryNpcData();
        for(var t:root.getList("Records",10)){var r=(CompoundTag)t;data.records.put(r.getString("Key"),r.copy());}
        for(var t:root.getList("Clues",10)){
            var entry=(CompoundTag)t;if(!entry.hasUUID("Player"))continue;
            var set=new HashSet<String>();
            for(var s:entry.getList("Unlocked",Tag.TAG_STRING))set.add(s.getAsString());
            data.clues.put(entry.getUUID("Player"),set);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root){
        var list=new ListTag();records.values().forEach(r->list.add(r.copy()));root.put("Records",list);
        var clueList=new ListTag();
        for(var e:clues.entrySet()){
            if(e.getValue().isEmpty())continue;
            var tag=new CompoundTag();tag.putUUID("Player",e.getKey());
            var unlocked=new ListTag();e.getValue().forEach(c->unlocked.add(StringTag.valueOf(c)));
            tag.put("Unlocked",unlocked);clueList.add(tag);
        }
        root.put("Clues",clueList);
        return root;
    }
}
