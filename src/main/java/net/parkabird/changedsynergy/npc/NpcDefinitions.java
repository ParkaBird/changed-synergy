package net.parkabird.changedsynergy.npc;

import com.google.gson.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Small, declarative conversation graphs. Text remains in the language files. */
@Mod.EventBusSubscriber(modid="changed_synergy")
public final class NpcDefinitions extends SimpleJsonResourceReloadListener {
    public record Part(String text,String effect,int pause,String emotion,float speed,float intensity,boolean beat) {}
    public record Line(String channel,String condition,List<Part> parts) {}
    public record Choice(String id,String text,String next,String condition,String effect,List<String> requires) {}
    public record Node(List<Line> lines,List<Choice> choices) {}
    public record Profile(String id,String template,String species,String trait,boolean chief,boolean dialogue,
                          List<String> equipment,Map<String,Node> nodes) {}
    private static Map<String,Profile> profiles=Map.of();
    private static final Set<String> CONDITIONS=Set.of("always","chief","can_join","cannot_join","member","can_reversible","can_permanent","understands","stranger","human","transformed","day","night_human","night_transformed","human_join","transformed_join");
    private static final Set<String> EFFECTS=Set.of("none","close","join","reversible","permanent","leave","reverse_rite");
    private NpcDefinitions(){super(new Gson(),"story_npcs");}
    @SubscribeEvent public static void reload(AddReloadListenerEvent event){event.addListener(new NpcDefinitions());}
    public static Profile get(String id){return profiles.get(id);}
    public static Collection<Profile> all(){return profiles.values();}
    @Override protected void apply(Map<ResourceLocation,JsonElement> files,ResourceManager manager,ProfilerFiller profiler) {
        var loaded=new HashMap<String,Profile>();
        for(var file:files.entrySet()) {
            var json=file.getValue().getAsJsonObject();var nodes=new LinkedHashMap<String,Node>();
            for(var entry:json.getAsJsonObject("nodes").entrySet()) {
                var object=entry.getValue().getAsJsonObject();var lines=new ArrayList<Line>();
                for(var value:object.getAsJsonArray("lines")) {
                    var line=value.getAsJsonObject();var parts=new ArrayList<Part>();
                    for(var p:line.getAsJsonArray("parts")) {
                        var part=p.getAsJsonObject();String effect=string(part,"effect","auto"),emotion=string(part,"emotion","neutral");
                        if(!NpcTextStyle.supportsEffect(effect)||!NpcTextStyle.supportsEmotion(emotion))throw new JsonParseException("Unknown text style "+effect+"/"+emotion);
                        int pause=part.has("pause")?part.get("pause").getAsInt():0;
                        if(pause<0||pause>2000)throw new JsonParseException("Invalid pause");
                        float speed=part.has("speed")?part.get("speed").getAsFloat():1;
                        float intensity=part.has("intensity")?part.get("intensity").getAsFloat():1;
                        if(!Float.isFinite(speed)||speed<.25F||speed>3||!Float.isFinite(intensity)||intensity<0||intensity>2)throw new JsonParseException("Invalid text speed or intensity");
                        parts.add(new Part(string(part,"text",""),effect,pause,emotion,speed,intensity,part.has("beat")&&part.get("beat").getAsBoolean()));
                    }
                    String channel=string(line,"channel","spoken");
                    String condition=string(line,"condition","always");
                    if(!Set.of("spoken","mental").contains(channel)||!CONDITIONS.contains(condition)||parts.isEmpty()||parts.size()>32)throw new JsonParseException("Invalid line");
                    lines.add(new Line(channel,condition,List.copyOf(parts)));
                }
                var choices=new ArrayList<Choice>();var ids=new HashSet<String>();
                for(var value:object.getAsJsonArray("choices")) {
                    var c=value.getAsJsonObject();String condition=string(c,"condition","always"),effect=string(c,"effect","none"),id=string(c,"id","");
                    if(!CONDITIONS.contains(condition)||!EFFECTS.contains(effect)||!ids.add(id)||id.isBlank()||id.length()>64)throw new JsonParseException("Invalid choice");
                    var requires=new ArrayList<String>();
                    if(c.has("requires"))for(var required:c.getAsJsonArray("requires"))requires.add(required.getAsString());
                    if(requires.size()>16)throw new JsonParseException("Too many prerequisites");
                    choices.add(new Choice(id,string(c,"text",""),string(c,"next","root"),condition,effect,List.copyOf(requires)));
                }
                if(lines.isEmpty()||lines.size()>32||choices.isEmpty()||choices.size()>12)throw new JsonParseException("Invalid node");
                nodes.put(entry.getKey(),new Node(List.copyOf(lines),List.copyOf(choices)));
            }
            boolean dialogue=!json.has("dialogue")||json.get("dialogue").getAsBoolean();
            if(dialogue&&!nodes.containsKey("root")||!dialogue&&!nodes.isEmpty()||nodes.size()>128)throw new JsonParseException("Invalid dialogue root or disabled profile nodes");
            for(var node:nodes.values())for(var c:node.choices()) {
                if(!nodes.containsKey(c.next()))throw new JsonParseException("Missing destination "+c.next());
                if(c.requires().stream().anyMatch(required->!nodes.containsKey(required)))throw new JsonParseException("Missing prerequisite");
            }
            var equipment=new ArrayList<String>();for(var e:json.getAsJsonArray("equipment"))equipment.add(e.getAsString());
            var profile=new Profile(file.getKey().toString(),string(json,"template",""),string(json,"species",""),
                    string(json,"trait","CURIOUS"),json.get("chief").getAsBoolean(),dialogue,List.copyOf(equipment),Map.copyOf(nodes));
            net.parkabird.changedsynergy.ai.CreaturePersonality.Trait.valueOf(profile.trait());
            if(loaded.put(profile.id(),profile)!=null)throw new JsonParseException("Duplicate profile");
        }
        profiles=Map.copyOf(loaded);
    }
    private static String string(JsonObject object,String name,String fallback){return object.has(name)?object.get(name).getAsString():fallback;}
}
