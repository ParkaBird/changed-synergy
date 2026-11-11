package net.parkabird.changedsynergy.npc;

import com.google.gson.*;
import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ai.*;

/** Authored voices share a small server-owned set of household actions. */
@Mod.EventBusSubscriber(modid="changed_synergy")
public final class CampDialogue extends SimpleJsonResourceReloadListener {
    private static Map<String,List<NpcDefinitions.Part>> lines=Map.of();
    private static final String PREFIX="camp.changed_synergy.";
    private CampDialogue(){super(new Gson(),"camp_dialogue");}
    @SubscribeEvent public static void reload(AddReloadListenerEvent event){event.addListener(new CampDialogue());}
    @Override protected void apply(Map<ResourceLocation,JsonElement> files,ResourceManager manager,ProfilerFiller profiler){
        var loaded=new HashMap<String,List<NpcDefinitions.Part>>();
        for(var file:files.values())for(var entry:file.getAsJsonObject().getAsJsonObject("lines").entrySet()){
            var parts=new ArrayList<NpcDefinitions.Part>();for(var value:entry.getValue().getAsJsonArray()){
                var p=value.getAsJsonObject();String emotion=p.get("emotion").getAsString(),effect=p.get("effect").getAsString();
                if(!NpcTextStyle.supportsEmotion(emotion)||!NpcTextStyle.supportsEffect(effect))throw new JsonParseException("Invalid camp text style");
                int pause=p.get("pause").getAsInt();float intensity=p.get("intensity").getAsFloat();if(pause<0||pause>2000||!Float.isFinite(intensity)||intensity<0||intensity>2||p.get("text").getAsString().isBlank())throw new JsonParseException("Invalid camp text timing or key");
                parts.add(new NpcDefinitions.Part(p.get("text").getAsString(),effect,pause,emotion,1,intensity,false));
            }if(parts.isEmpty()||parts.size()>16||loaded.put(entry.getKey(),List.copyOf(parts))!=null)throw new JsonParseException("Invalid camp line");
        }lines=Map.copyOf(loaded);
    }
    private static NpcDefinitions.Line line(String key){var parts=lines.get(key);if(parts==null)throw new IllegalStateException("Missing camp line "+key);return new NpcDefinitions.Line("spoken","always",parts);}
    private static NpcDefinitions.Choice choice(String id,String text,String next,String effect){return new NpcDefinitions.Choice(id,PREFIX+"choice."+text,next,"always",effect,List.of());}
    public static NpcDefinitions.Choice entry(){return choice("camp_life","life","camp_life","none");}
    public static NpcDefinitions.Choice formEntry(){return choice("camp_body","body","explain_form","none");}
    public static NpcDefinitions.Choice reverseEntry(){return choice("camp_reverse_rite","reverse_rite","camp_reverse_rite","none");}
    public static boolean chief(ChangedEntity npc){var p=StoryNpcService.profile(npc);return p!=null&&p.chief();}
    public static String residentRoot(ChangedEntity npc,ServerPlayer player){
        var profile=CampLifeService.profile(npc);String role=npc.getPersistentData().getCompound(StructureResidentGoal.DATA).getString("DialogueRole");
        return !role.isEmpty()&&profile!=null&&profile.nodes().containsKey("resident_"+role)&&CampLifeService.atHome(npc)&&net.parkabird.changedsynergy.dialogue.NpcDialogue.canUnderstand(npc,player)?"resident_"+role:"camp_resident";
    }
    public static NpcDefinitions.Node node(ChangedEntity npc,ServerPlayer player,String node,String requestId,ItemStack offered,boolean pending){
        var body=new ArrayList<NpcDefinitions.Line>();var choices=new ArrayList<NpcDefinitions.Choice>();String residentVoice=CampLifeService.template(npc).equals("desert_refuge")?"desert_resident":"resident";String voice=chief(npc)?CampLifeService.voice(npc):residentVoice;
        String root=chief(npc)?"root":residentRoot(npc,player);
        if(!chief(npc)&&!net.parkabird.changedsynergy.dialogue.NpcDialogue.canUnderstand(npc,player)){
            body.add(line(residentVoice+".gesture"));if(CampLifeService.profile(npc)!=null)choices.add(choice("camp_chief","ask_chief","camp_seekchief","none"));choices.add(choice("camp_close","later",node,"close"));
            if(node.equals("camp_seekchief")){body.clear();body.add(line(residentVoice+".seekchief"));}
            return new NpcDefinitions.Node(List.copyOf(body),List.copyOf(choices));
        }
        if(!CampLifeService.atHome(npc)){
            body.add(line(residentVoice+".away"));choices.add(choice("camp_close","later",node,"close"));return new NpcDefinitions.Node(List.copyOf(body),List.copyOf(choices));
        }
        switch(node){
            case "camp_life","camp_resident" -> {
                body.add(line(voice+".greeting"));choices.add(choice("camp_status","status","camp_status","none"));choices.add(choice("camp_requests",chief(npc)?"public_requests":"personal_requests","camp_requests","none"));
                if(CampLifeService.canHelp(npc,player))choices.add(choice("camp_donate","donate","camp_donate","none"));
                if(CampMembershipService.protectedMember(npc,player))choices.add(choice("camp_meal","meal","camp_meal","camp_meal"));
                if(chief(npc)&&CampMembershipService.member(npc,player))choices.add(choice("camp_membership","membership","membership","none"));
                if(CampLifeService.canClaimOutfit(npc,player))choices.add(choice("camp_outfit","outfit","camp_outfit","camp_outfit"));
            }
            case "camp_status" -> {
                body.add(line(voice+".food_"+CampLifeService.foodState(npc)));body.add(line("status.beds"));body.add(line(CampLifeService.safe(npc)?voice+".safe":voice+".danger"));
                body.add(line(CampMembershipService.member(npc,player)?"status.member":"status.guest"));
                var helped=CampMembershipService.membership(player).getCompound("Contributions").getCompound(StoryNpcService.homeKey(npc));
                if(helped.contains("LastHelp"))body.add(line(voice+".remember"));
            }
            case "camp_requests" -> {
                body.add(line(voice+".requests"));int active=0;
                for(var r:CampLifeService.visibleRequests(npc)){active++;
                    choices.add(choice("camp_req_"+r.getString("Id"),"request","camp_request","none"));
                }if(active==0)body.add(line(voice+".no_requests"));
            }
            case "camp_request" -> {
                var r=CampLifeService.request(npc,requestId);
                if(r==null){body.add(line(voice+".handled"));break;}
                boolean watch=r.getString("Kind").equals("WATCH");body.add(line(voice+(watch?".watch":".request")));
                if(!CampLifeService.visibleRequests(npc).contains(r)){body.clear();body.add(line(voice+".handled"));break;}
                if(CampLifeService.canHelp(npc,player))choices.add(choice("camp_submit",watch?"watch_done":CampLifeService.personal(r)?"personal_submit":"submit","camp_thanks",watch?"camp_watch":"camp_deliver"));
                choices.add(choice("camp_decline","decline","camp_declined","none"));
            }
            case "camp_donate" -> {
                var kind=CampLifeService.supply(offered,npc);
                if(kind==null){body.add(line(voice+".choose_item"));break;}
                body.add(line(voice+".donate"));
                for(int amount:new TreeSet<>(List.of(1,Math.min(8,offered.getCount()),Math.min(64,offered.getCount()))))if(amount>0)choices.add(choice("camp_give_"+amount,"give","camp_thanks","camp_donate"));
            }
            case "camp_transfer" -> {body.add(line(voice+".transfer"));if(pending)choices.add(choice("camp_confirm_transfer","confirm_transfer","root","camp_transfer"));choices.add(choice("camp_cancel_transfer","keep_home",root,"camp_cancel_transfer"));}
            case "camp_reverse_rite" -> {body.add(line(voice+".reverse_rite"));choices.add(choice("camp_confirm_reverse_rite","confirm_reverse_rite","root","reverse_rite"));}
            case "camp_thanks" -> body.add(line(voice+".thanks"));
            case "camp_watch_thanks" -> body.add(line(voice+".watch_thanks"));
            case "camp_watch_wait" -> body.add(line(voice+".watch_wait"));
            case "camp_declined" -> body.add(line(voice+".declined"));
            case "camp_changed" -> body.add(line(voice+".changed"));
            case "camp_meal" -> body.add(line(voice+".meal"));
            case "camp_outfit" -> body.add(line(voice+".outfit"));
            case "camp_outfit_changed" -> body.add(line("status.outfit_changed"));
            default -> body.add(line(voice+".greeting"));
        }
        choices.add(choice("camp_back","back",root,"none"));choices.add(choice("camp_close","later",node,"close"));
        if(voice.equals(residentVoice)){
            String tone=CreaturePersonality.has(npc,CreaturePersonality.Trait.PLAYFUL)?"playful":CreaturePersonality.has(npc,CreaturePersonality.Trait.CAUTIOUS)?"cautious":CreaturePersonality.has(npc,CreaturePersonality.Trait.POLITE)?"polite":"";
            for(int i=0;i<body.size();i++)for(String topic:List.of("greeting","request","thanks"))if(!tone.isEmpty()&&body.get(i).parts().get(0).text().equals(PREFIX+residentVoice+"."+topic+".0"))body.set(i,line(residentVoice+"."+tone+"_"+topic));
        }
        return new NpcDefinitions.Node(List.copyOf(body),List.copyOf(choices));
    }
    public static ListTag arguments(String key,String choice,ChangedEntity npc,ServerPlayer player,String requestId,ItemStack offered){
        List<Component> args=List.of();
        if(key.equals(PREFIX+"status.beds.0")){var beds=NaturalCampService.sites(npc).beds();long free=beds.stream().filter(p->PlayerOutpostRestGoal.free(npc,(net.minecraft.server.level.ServerLevel)npc.level(),p)).count();args=List.of(Component.literal(""+free),Component.literal(""+beds.size()));}
        if(key.endsWith("request.0")||key.equals(PREFIX+"choice.request")){
            String id=choice.startsWith("camp_req_")?choice.substring(9):requestId;var request=CampLifeService.request(npc,id);
            if(request!=null){String kind=request.getString("Kind");args=List.of(Component.literal(""+request.getInt("Count")),Component.translatable(kind.equals("WATCH")?PREFIX+"supply.watch":CampLifeService.Supply.valueOf(kind).label()));}
        }
        if(key.endsWith(".donate.0"))args=List.of(offered.getHoverName());
        if(key.equals(PREFIX+"choice.give"))args=List.of(Component.literal(choice.substring("camp_give_".length())),offered.getHoverName());
        if(key.endsWith(".transfer.0")){
            var old=CampLifeData.get(player.server).camps.get(CampMembershipService.membership(player).getString("HomeKey"));String home=old==null?"previous":switch(old.getString("Template")){case "forest_treehouse"->"weaver";case "hyena_hideout"->"matriarch";case "observatory"->"archivist";case "desert_refuge"->"pathfinder";default->"previous";};
            args=List.of(Component.translatable(PREFIX+"home."+home));
        }
        var result=new ListTag();for(var arg:args)result.add(StringTag.valueOf(Component.Serializer.toJson(arg)));return result;
    }
}
