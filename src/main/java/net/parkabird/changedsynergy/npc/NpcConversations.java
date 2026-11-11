package net.parkabird.changedsynergy.npc;

import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.network.*;

/** One server-owned conversation per speaker; clients only submit visible choice IDs. */
public final class NpcConversations {
    private static final Map<UUID,Session> sessions=new HashMap<>();
    private static final class Session {
        final UUID nonce=UUID.randomUUID();final ChangedEntity npc;final ServerPlayer player;final NpcDefinitions.Profile profile;
        String node="root";int line,revision;long lastAction;
        String request="",pendingMode="",pendingNext="",pendingHome="";net.minecraft.world.item.ItemStack offered=net.minecraft.world.item.ItemStack.EMPTY;
        CompoundTag state=new CompoundTag();
        Session(ChangedEntity npc,ServerPlayer player,NpcDefinitions.Profile profile){this.npc=npc;this.player=player;this.profile=profile;lastAction=npc.level().getGameTime();}
    }
    public static boolean holds(ChangedEntity npc){return sessions.values().stream().anyMatch(s->s.npc==npc);}
    public static boolean talking(ServerPlayer player){return sessions.containsKey(player.getUUID());}
    public static ServerPlayer listener(ChangedEntity npc){return sessions.values().stream().filter(s->s.npc==npc).map(s->s.player).findFirst().orElse(null);}
    public static boolean canOpen(ChangedEntity npc){var profile=StoryNpcService.profile(npc);return profile!=null?profile.dialogue():CampLifeService.mainCamp(npc);}
    private static NpcDefinitions.Node node(Session s){
        if(s.node.startsWith("camp_"))return CampDialogue.node(s.npc,s.player,s.node,s.request,s.offered,!s.pendingMode.isEmpty());
        if(!StoryNpcService.isNpc(s.npc)&&(!CampLifeService.atHome(s.npc)||!net.parkabird.changedsynergy.dialogue.NpcDialogue.canUnderstand(s.npc,s.player)))return CampDialogue.node(s.npc,s.player,"camp_resident",s.request,s.offered,false);
        if(s.profile==null)return CampDialogue.node(s.npc,s.player,"camp_resident",s.request,s.offered,false);
        var original=s.profile.nodes().get(s.node);if(original==null)return s.profile.nodes().get("root");
        if(!CampLifeService.mainCamp(s.npc)||!(Set.of("root","return","membership").contains(s.node)||s.node.equals(CampDialogue.residentRoot(s.npc,s.player))))return original;
        var choices=new ArrayList<>(original.choices());choices.add(CampDialogue.entry());
        if(s.node.equals("membership")&&CampMembershipService.member(s.npc,s.player))choices.add(CampDialogue.formEntry());
        return new NpcDefinitions.Node(original.lines(),List.copyOf(choices));
    }
    public static void open(ChangedEntity npc,ServerPlayer player) {
        if(!canOpen(npc))return;
        var profile=StoryNpcService.profile(npc);boolean ordinary=profile==null;if(ordinary)profile=CampLifeService.profile(npc);if(profile==null&&!CampLifeService.mainCamp(npc))return;
        if(holds(npc)){player.displayClientMessage(Component.translatable("npc.changed_synergy.busy"),true);return;}
        var session=new Session(npc,player,profile);if(!valid(session))return;
        if(ordinary)session.node=CampDialogue.residentRoot(npc,player);
        var record=StoryNpcData.get(player.server).records.get(StoryNpcService.key(npc));
        if(!ordinary&&record!=null&&record.getCompound("Progress").getCompound(player.getUUID().toString()).getBoolean("root"))session.node="return";
        close(player);player.closeContainer();sessions.put(player.getUUID(),session);
        CampLifeService.register(npc);CampLifeService.rememberStores(npc);CampApproachGoal.dismiss(player);
        CreaturePersonality.rememberEncounter(npc,player);CreaturePeerInteractionService.cancel(npc);npc.getNavigation().stop();send(session);
    }
    private static boolean valid(Session s) {
        var body=TrueTransfurCompat.interactionBody(s.player);
        return s.npc.isAlive()&&!s.npc.isRemoved()&&s.player.isAlive()&&!s.player.isSpectator()
                &&s.npc.level()==body.level()&&s.npc.distanceToSqr(body)<=64&&s.npc.hasLineOfSight(body)
                &&s.npc.getTarget()==null&&!s.npc.isSleeping()&&!TakeoverService.active(s.player)
                &&!s.player.isSleeping()
                &&!(s.player.getLastHurtByMob()!=null&&s.player.tickCount-s.player.getLastHurtByMobTimestamp()<100)
                &&!(s.player.getLastHurtMob()!=null&&s.player.tickCount-s.player.getLastHurtMobTimestamp()<100)
                &&net.ltxprogrammer.changed.ability.GrabEntityAbility.getGrabber(s.player)==null
                &&net.ltxprogrammer.changed.ability.GrabEntityAbility.getGrabberSafe(s.npc).isEmpty()
                &&!CreatureGrabService.isGrabberBusy(s.npc);
    }
    public static void action(ServerPlayer player,UUID nonce,int revision,String choice) {
        var s=sessions.get(player.getUUID());if(s==null||!s.nonce.equals(nonce))return;
        if(choice.equals("cancel")){close(player);return;}
        if(s.revision!=revision)return;
        if(!valid(s)){close(player);return;}
        var node=node(s);s.lastAction=s.npc.level().getGameTime();
        if(choice.equals("advance")) {
            if(s.line<visiblePassages(s,node).size()-1)s.line++;
            else return;
        }else {
            if(s.line<visiblePassages(s,node).size()-1)return;
            var selected=node.choices().stream().filter(c->c.id().equals(choice)&&available(c,s)).findFirst().orElse(null);if(selected==null)return;
            if(selected.effect().equals("close")){complete(s);close(player);return;}
            if(Set.of("join","reversible","permanent").contains(selected.effect())){
                String home=CampMembershipService.membership(player).getString("HomeKey");
                if(!home.isEmpty()&&!home.equals(StoryNpcService.homeKey(s.npc))){
                    if(!CampMembershipService.canJoin(s.npc,player))return;
                    s.pendingMode=selected.effect();s.pendingNext=selected.next();s.pendingHome=home;s.node="camp_transfer";s.line=0;send(s);return;
                }
            }
            boolean success=switch(selected.effect()) {
                case "join","reversible","permanent" -> CampMembershipService.join(s.npc,player,selected.effect());
                case "leave" -> {CampMembershipService.leave(player);yield true;}
                case "camp_donate" -> CampLifeService.deliver(s.npc,player,s.offered,Integer.parseInt(selected.id().substring("camp_give_".length())),"");
                case "camp_deliver" -> {var r=CampLifeService.request(s.npc,s.request);yield r!=null&&CampLifeService.deliver(s.npc,player,s.offered,r.getInt("Count"),s.request);}
                case "camp_watch" -> CampLifeService.finishWatch(s.npc,player,s.request);
                case "camp_meal" -> CampApproachGoal.shareMeal(s.npc,player);
                case "camp_outfit" -> CampLifeService.claimOutfit(s.npc,player);
                case "camp_transfer" -> !s.pendingMode.isEmpty()&&s.pendingHome.equals(CampMembershipService.membership(player).getString("HomeKey"))&&CampMembershipService.join(s.npc,player,s.pendingMode);
                case "camp_cancel_transfer" -> {s.pendingMode="";s.pendingNext="";s.pendingHome="";yield true;}
                default -> true;
            };
            if(!success){s.node=selected.effect().equals("camp_watch")?"camp_watch_wait":selected.effect().equals("camp_outfit")?"camp_outfit_changed":selected.effect().startsWith("camp_")?"camp_changed":"root";s.line=0;send(s);return;}
            complete(s);
            s.node=selected.effect().equals("camp_transfer")?s.pendingNext:selected.effect().equals("camp_watch")?"camp_watch_thanks":selected.next();s.line=0;
            if(selected.effect().equals("camp_transfer")){s.pendingMode="";s.pendingNext="";s.pendingHome="";}
            if(selected.id().startsWith("camp_req_"))s.request=selected.id().substring(9);
            if(s.node.equals("camp_donate")||s.node.equals("camp_request"))s.offered=player.getMainHandItem().copy();
        }
        send(s);
    }
    private static List<List<NpcDefinitions.Line>> visiblePassages(Session s,NpcDefinitions.Node node) {
        boolean understand=TelepathyService.enabled()&&(ProcessTransfur.isPlayerTransfurred(s.player)||TelepathyService.canUnderstand(s.player));
        var passages=new ArrayList<List<NpcDefinitions.Line>>();
        for(var line:node.lines()) {
            if(!available(line.condition(),s)||line.channel().equals("mental")&&!understand)continue;
            // A thought belongs beside the preceding spoken line, not behind another click.
            if(passages.isEmpty()||!line.channel().equals("mental"))passages.add(new ArrayList<>());
            passages.get(passages.size()-1).add(line);
        }
        return passages;
    }
    private static boolean available(String condition,Session s) {
        return switch(condition) {
            case "chief" -> CampDialogue.chief(s.npc);
            case "can_join" -> CampMembershipService.canJoin(s.npc,s.player);
            case "cannot_join" -> !CampMembershipService.canJoin(s.npc,s.player);
            case "member" -> CampMembershipService.member(s.npc,s.player);
            case "can_reversible" -> CampMembershipService.canJoin(s.npc,s.player)&&NpcRites.canCopy(s.npc,s.player);
            case "can_permanent" -> CampMembershipService.canJoin(s.npc,s.player)&&NpcRites.canCopy(s.npc,s.player)&&NpcRites.supportsPermanent();
            case "understands" -> TelepathyService.enabled()&&(ProcessTransfur.isPlayerTransfurred(s.player)||TelepathyService.canUnderstand(s.player));
            case "stranger" -> !CampMembershipService.member(s.npc,s.player);
            case "human" -> !ProcessTransfur.isPlayerTransfurred(s.player)&&TrueTransfurCompat.interactionBody(s.player)==s.player;
            case "transformed" -> ProcessTransfur.isPlayerTransfurred(s.player)||TrueTransfurCompat.interactionBody(s.player)!=s.player;
            case "day" -> s.npc.level().isDay();
            case "night_human" -> !s.npc.level().isDay()&&available("human",s);
            case "night_transformed" -> !s.npc.level().isDay()&&available("transformed",s);
            case "human_join" -> available("human",s)&&CampMembershipService.canJoin(s.npc,s.player);
            case "transformed_join" -> available("transformed",s)&&CampMembershipService.canJoin(s.npc,s.player);
            default -> true;
        };
    }
    private static void send(Session s) {
        var node=node(s);var lines=visiblePassages(s,node);
        if(lines.isEmpty()){close(s.player);return;}
        s.line=Math.min(s.line,lines.size()-1);
        var state=new CompoundTag();state.putUUID("Session",s.nonce);state.putInt("Revision",++s.revision);state.putInt("Entity",s.npc.getId());
        state.putString("Name",Component.Serializer.toJson(s.npc.getDisplayName()));state.putString("Title",StoryNpcService.isNpc(s.npc)?"npc."+s.profile.id().replace(':','.')+".title":s.npc.getType().getDescriptionId());
        state.putString("Profile",s.profile==null?"":s.profile.id());state.putString("Node",s.node);
        state.putBoolean("Chief",CampDialogue.chief(s.npc)||CampLifeService.mainCamp(s.npc));state.putInt("Trust",CampMembershipService.score(s.npc,s.player));
        boolean organic=LatexSocialMemory.isOrganic(s.npc)||!s.npc.getType().is(net.ltxprogrammer.changed.init.ChangedTags.EntityTypes.LATEX);
        state.putBoolean("Organic",organic);
        if(s.npc.getSelfVariant()!=null){var colors=s.npc.getSelfVariant().getColors();state.putInt("Background",colors.getFirst().toInt());state.putInt("Foreground",colors.getSecond().toInt());}
        state.putBoolean("Last",s.line==lines.size()-1);state.putInt("Line",s.line);state.putInt("Lines",lines.size());
        var parts=new ListTag();
        for(var line:lines.get(s.line))for(int i=0;i<line.parts().size();i++) {
            var p=line.parts().get(i);var part=new CompoundTag();
            part.putString("Text",p.text());part.putString("Channel",line.channel());part.putBoolean("Break",i==0&&!parts.isEmpty());
            part.putBoolean("Beat",p.beat());
            part.putString("Effect",p.effect());part.putInt("Pause",p.pause());part.putString("Emotion",p.emotion());part.putFloat("Speed",p.speed());part.putFloat("Intensity",p.intensity());parts.add(part);
            part.put("Arguments",CampDialogue.arguments(p.text(),"",s.npc,s.player,s.request,s.offered));
        }
        state.put("Parts",parts);
        var choices=new ListTag();if(s.line==lines.size()-1)for(var c:node.choices())if(available(c,s)){var choice=new CompoundTag();choice.putString("Id",c.id());choice.putString("Text",c.text());choice.put("Arguments",CampDialogue.arguments(c.text(),c.id(),s.npc,s.player,s.request,s.offered));choices.add(choice);}state.put("Choices",choices);
        ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->s.player),new StoryNpcStatePacket(state));
        s.state=state.copy();
    }
    public static CompoundTag state(ServerPlayer player){var session=sessions.get(player.getUUID());return session==null?new CompoundTag():session.state.copy();}
    private static void complete(Session s) {
        var store=StoryNpcData.get(s.player.server);var record=store.records.get(StoryNpcService.key(s.npc));
        if(record==null)return;
        var progress=record.getCompound("Progress");var seen=progress.getCompound(s.player.getUUID().toString());
        seen.putBoolean(s.node,true);progress.put(s.player.getUUID().toString(),seen);record.put("Progress",progress);store.setDirty();
    }
    private static boolean available(NpcDefinitions.Choice choice,Session s) {
        if(!available(choice.condition(),s))return false;
        var record=StoryNpcData.get(s.player.server).records.get(StoryNpcService.key(s.npc));
        var seen=record==null?new CompoundTag():record.getCompound("Progress").getCompound(s.player.getUUID().toString());
        return choice.requires().stream().allMatch(seen::getBoolean);
    }
    public static void close(ServerPlayer player){var old=sessions.remove(player.getUUID());if(old!=null)ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new StoryNpcStatePacket(new CompoundTag()));}
    public static void tick(MinecraftServer server) {
        if(server.overworld().getGameTime()%10!=0)return;
        CampApproachGoal.reap(server);
        for(var s:new ArrayList<>(sessions.values()))if(server.getPlayerList().getPlayer(s.player.getUUID())!=s.player||!valid(s)
                ||(s.profile==null?!CampLifeService.mainCamp(s.npc):NpcDefinitions.get(s.profile.id())!=s.profile))close(s.player);
    }
    public static void clear(){sessions.clear();CampApproachGoal.clear();}
}
