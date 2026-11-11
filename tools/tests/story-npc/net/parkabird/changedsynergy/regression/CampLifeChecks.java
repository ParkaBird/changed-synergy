package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.npc.*;

final class CampLifeChecks {
    private static int checks;private static final Map<String,String> samples=new LinkedHashMap<>();
    static void run(MinecraftServer server,List<ChangedEntity> npcs)throws Exception {
        for(String profile:List.of("canopy_chief","hyena_chief","observatory_chief")){
            var npc=npcs.stream().filter(n->StoryNpcService.profile(n).id().endsWith(":"+profile)).findFirst().orElseThrow();
            verify(server,npc,profile);
        }
        var saved=CampLifeData.get(server).save(new CompoundTag());var reloaded=CampLifeData.load(saved);
        check(reloaded.camps.size()==CampLifeData.get(server).camps.size(),"all structure instances survive household serialization");
        check(reloaded.camps.values().stream().anyMatch(c->!c.getList("Requests",10).isEmpty()),"requests survive save/load");
        var json=new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(samples);
        java.nio.file.Files.writeString(java.nio.file.Path.of("camp-dialogue-samples.json"),json);
        System.out.println("CAMP_LIFE_PASS: "+checks+" local isolation, atomic supplies, shared requests, real menu transfers, consent and resident-language checks");
    }
    private static ServerPlayer player(MinecraftServer server,ChangedEntity npc,String name)throws Exception {
        var p=new ServerPlayer(server,(ServerLevel)npc.level(),new GameProfile(UUID.randomUUID(),name));p.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),p);
        var identity=new net.parkabird.changedsynergy.origin.NativeOriginData.Identity();identity.state="human";net.parkabird.changedsynergy.origin.NativeOriginData.get(server).players.put(p.getUUID(),identity);
        var field=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.players.PlayerList.class,"f_11197_");
        @SuppressWarnings("unchecked") var map=(Map<UUID,ServerPlayer>)field.get(server.getPlayerList());map.put(p.getUUID(),p);((ServerLevel)npc.level()).addNewPlayer(p);
        for(int[] off:List.of(new int[]{1,0},new int[]{-1,0},new int[]{0,1},new int[]{0,-1},new int[]{1,1},new int[]{-1,-1})) {p.setPos(npc.getX()+off[0],npc.getY(),npc.getZ()+off[1]);if(npc.level().noCollision(p)&&npc.hasLineOfSight(p))return p;}
        throw new AssertionError("no conversation position");
    }
    private static void finish(ServerPlayer p){for(int i=0;i<100;i++){var s=NpcConversations.state(p);if(s.isEmpty()||s.getBoolean("Last"))return;NpcConversations.action(p,s.getUUID("Session"),s.getInt("Revision"),"advance");}throw new AssertionError("nonterminating passage");}
    private static void choose(ServerPlayer p,String id){finish(p);var s=NpcConversations.state(p);NpcConversations.action(p,s.getUUID("Session"),s.getInt("Revision"),id);}
    static void capture(String name,ServerPlayer p){samples.put(name,NpcConversations.state(p).toString());}
    private static void verify(MinecraftServer server,ChangedEntity npc,String profile)throws Exception {
        var level=(ServerLevel)npc.level();var p=player(server,npc,"CampMember");var second=player(server,npc,"CampNeighbor");
        var floor=p.blockPosition().below();var old=level.getBlockState(floor);var oldBe=level.getBlockEntity(floor);var oldTag=oldBe==null?null:oldBe.saveWithFullMetadata();
        var privacy=new HashMap<BlockEntity,CompoundTag>();
        try {
            check(CampLifeService.mainCamp(npc),"chief's real structure is a main camp");
            for(var pos:NaturalCampService.sites(npc).storage()){var be=level.getBlockEntity(pos);if(be!=null){privacy.put(be,be.getPersistentData().copy());be.getPersistentData().putBoolean("ChangedSynergyPrivate",true);}}
            level.setBlock(floor,Blocks.CHEST.defaultBlockState(),3);level.getGameTime();server.getWorldData().overworldData().setGameTime(level.getGameTime()+201);
            var chest=(ChestBlockEntity)level.getBlockEntity(floor);chest.getPersistentData().putBoolean(NaturalCampService.PUBLIC_STORAGE,true);
            CampLifeService.opened(level,floor);CampLifeService.rememberStores(npc);
            check(CampLifeService.stores(npc).stream().anyMatch(s->s.inventory()==chest),"new public store is discovered");
            FactionReputation.set(npc,p,-80);check(CampMembershipService.canJoin(npc,p),"regional hostility does not prevent camp admission");
            check(CampMembershipService.join(npc,p,"join"),"ordinary human admission");
            check(!FactionReputation.isHostile(npc,p),"camp reactions use local trust");
            FactionReputation.adjustFromInteraction(npc,p,2);check(CampMembershipService.score(npc,p)==2&&FactionReputation.score(npc,p)==-80,"social interaction has one local effect and no regional effect");
            FactionReputation.adjust(npc,p,-2);check(CampMembershipService.score(npc,p)==0&&FactionReputation.score(npc,p)==-80,"offence changes local trust once");
            var factionRule=level.getGameRules().getRule(net.parkabird.changedsynergy.init.ChangedSynergyGameRules.FACTION_REPUTATION);boolean enabled=factionRule.get();factionRule.set(false,server);
            try{FactionReputation.adjust(npc,p,-2);check(CampMembershipService.score(npc,p)==-2,"local offences work with regional reputation disabled");CampMembershipService.adjust(npc,p,2);}finally{factionRule.set(enabled,server);}
            check(CampMembershipService.sharedStorage(npc,p,floor),"member shares public storage");
            check(CampLifeService.canSleep(p,floor)&&!CampLifeService.canSleep(second,floor),"home members have sleeping rights; unfamiliar visitors ask first");
            CampMembershipService.adjust(npc,p,-15);check(!CampLifeService.canSleep(p,floor),"suspended member also loses shared sleeping rights");CampMembershipService.adjust(npc,p,15);
            chest.getPersistentData().putBoolean("ChangedSynergyPrivate",true);check(!CampMembershipService.sharedStorage(npc,p,floor)&&CampLifeService.stores(npc).stream().noneMatch(s->s.inventory()==chest),"private containers excluded for residents and members");
            boolean noAi=npc.isNoAi();npc.setNoAi(false);int privacyTrust=CampMembershipService.score(npc,p);
            try {
                check(npc.hasLineOfSight(p),"private-container witness actually sees the member");
                CreatureCacheGuardService.handleCacheAccess(level,p,floor);CreatureCacheGuardService.handleCacheAccess(level,p,floor);
                check(CampMembershipService.score(npc,p)==privacyTrust&&npc.getTarget()==null,"first private-container visits warn without attacking or charging trust");
                CreatureCacheGuardService.handleCacheAccess(level,p,floor);
                check(CampMembershipService.score(npc,p)==privacyTrust-2&&npc.getTarget()==null&&!LatexSocialMemory.isProvoked(npc,p),"repeated private searching loses local trust without starting combat");
                CreatureCacheGuardService.handleCacheAccess(level,p,floor);check(CampMembershipService.score(npc,p)==privacyTrust-2,"private-search penalty is cooldown limited");
                chest.getPersistentData().remove("ChangedSynergyPrivate");chest.getPersistentData().remove(NaturalCampService.PUBLIC_STORAGE);chest.setItem(0,new ItemStack(Items.WRITTEN_BOOK));
                check(CampMembershipService.readableStory(npc,floor)&&!CampMembershipService.privateStorage(npc,floor),"authored reading material does not create private-storage restrictions");
                CreatureCacheGuardService.handleCacheAccess(level,p,floor);check(CampMembershipService.score(npc,p)==privacyTrust-2&&npc.getTarget()==null,"reading story material neither warns nor attacks nor costs trust");
            }finally{npc.setNoAi(noAi);CampMembershipService.adjust(npc,p,privacyTrust-CampMembershipService.score(npc,p));chest.clearContent();chest.getPersistentData().remove("ChangedSynergyPrivate");chest.getPersistentData().putBoolean(NaturalCampService.PUBLIC_STORAGE,true);}
            chest.setLootTable(net.minecraft.resources.ResourceLocation.parse("minecraft:chests/simple_dungeon"),1);
            CampLifeService.count(npc,CampLifeService.Supply.FOOD);check(chest.saveWithFullMetadata().contains("LootTable"),"supply scans never unpack authored loot");chest.setLootTable(null,0);
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BREAD,16));var offer=p.getMainHandItem().copy();
            check(CampLifeService.deliver(npc,p,offer,4,""),"real food donation");
            check(p.getMainHandItem().getCount()==12&&CampLifeService.count(npc,CampLifeService.Supply.FOOD)==4&&CampMembershipService.score(npc,p)==1,"exact item movement and modest trust reward");
            check(!CampLifeService.deliver(npc,p,new ItemStack(Items.CARROT,12),4,"")&&p.getMainHandItem().getCount()==12,"changed held item cannot be charged");
            NpcConversations.open(npc,p);choose(p,"camp_life");capture(profile+"-life",p);choose(p,"camp_status");capture(profile+"-status",p);choose(p,"camp_back");choose(p,"camp_life");choose(p,"camp_donate");capture(profile+"-donate",p);NpcConversations.close(p);
            var menu=ChestMenu.threeRows(7,p.getInventory(),chest);p.containerMenu=menu;
            p.getInventory().clearContent();menu.clicked(0,0,ClickType.PICKUP,p);menu.clicked(0,0,ClickType.PICKUP,p);
            check(CampMembershipService.membership(p).getCompound("Contributions").getCompound(StoryNpcService.homeKey(npc)).getCompound("Withdrawn").getInt("minecraft:bread")==0,"rearranging a public stack does not leave withdrawal debt");
            menu.clicked(0,0,ClickType.QUICK_MOVE,p);
            var debt=CampMembershipService.membership(p).getCompound("Contributions").getCompound(StoryNpcService.homeKey(npc)).getCompound("Withdrawn");
            check(debt.getInt("minecraft:bread")==4,"native shift-click records actual public withdrawals");
            p.containerMenu=p.inventoryMenu;
            int slot=-1;for(int i=0;i<36;i++)if(p.getInventory().getItem(i).is(Items.BREAD)){slot=i;break;}check(slot>=0,"withdrawn bread remains in player inventory");
            var bread=p.getInventory().removeItemNoUpdate(slot);p.setItemInHand(InteractionHand.MAIN_HAND,bread);int trust=CampMembershipService.score(npc,p);
            check(CampLifeService.deliver(npc,p,bread.copy(),4,"")&&CampMembershipService.score(npc,p)==trust,"returning borrowed supplies grants no donation reward");
            var supply=profile.equals("canopy_chief")?CampLifeService.Supply.WOOL:profile.equals("hyena_chief")?CampLifeService.Supply.LEATHER:CampLifeService.Supply.PAPER;
            var item=profile.equals("canopy_chief")?Items.WHITE_WOOL:profile.equals("hyena_chief")?Items.LEATHER:Items.PAPER;
            var request=CampLifeService.requests(npc).stream().map(CompoundTag.class::cast).filter(r->r.getString("Kind").equals(supply.name())).findFirst().orElseThrow();String id=request.getString("Id");int amount=request.getInt("Count");
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item,amount));second.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item,amount));
            NpcConversations.open(npc,p);choose(p,"camp_life");choose(p,"camp_requests");capture(profile+"-requests",p);choose(p,"camp_req_"+id);capture(profile+"-request",p);
            check(CampLifeService.deliver(npc,second,second.getMainHandItem().copy(),amount,id),"another player completes the shared request");
            int held=p.getMainHandItem().getCount();choose(p,"camp_submit");check(p.getMainHandItem().getCount()==held&&!request.getString("CompletedBy").equals(p.getUUID().toString()),"stale dialogue submission cannot take goods or duplicate completion");NpcConversations.close(p);
            check(!CampLifeService.deliver(npc,p,p.getMainHandItem().copy(),amount,id),"direct duplicate completion is also rejected");
            for(int i=0;i<chest.getContainerSize();i++)chest.setItem(i,new ItemStack(Items.DIRT,64));
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BREAD,4));check(!CampLifeService.deliver(npc,p,p.getMainHandItem().copy(),4,"")&&p.getMainHandItem().getCount()==4,"full storage takes no items");chest.clearContent();
            int granted=CampLifeService.reward(npc,p,100);check(granted==7&&CampLifeService.reward(npc,p,1)==0,"new contribution rewards stop at eight per player/camp/day");
            long beforeDay=level.getGameTime()/24000;server.getWorldData().overworldData().setGameTime((beforeDay+1)*24000);check(CampLifeService.reward(npc,p,1)==1,"next active day renews contribution allowance");
            var soil=floor.east();var crop=soil.above();var soilState=level.getBlockState(soil);var cropState=level.getBlockState(crop);
            var soilTag=level.getBlockEntity(soil)==null?null:level.getBlockEntity(soil).saveWithFullMetadata();var cropTag=level.getBlockEntity(crop)==null?null:level.getBlockEntity(crop).saveWithFullMetadata();
            try{
                level.setBlock(soil,Blocks.FARMLAND.defaultBlockState(),3);var wheat=(CropBlock)Blocks.WHEAT;var mature=wheat.getStateForAge(7);level.setBlock(crop,mature,3);
                int before=CampMembershipService.score(npc,p);net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.BlockEvent.BreakEvent(level,crop,mature,p));
                check(CampMembershipService.score(npc,p)==before,"harvesting without replanting earns no tending credit");
                var snapshot=net.minecraftforge.common.util.BlockSnapshot.create(level.dimension(),level,crop);level.setBlock(crop,wheat.getStateForAge(0),3);
                var planted=new net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent(snapshot,level.getBlockState(soil),p);net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(planted);
                check(CampMembershipService.score(npc,p)==before+1,"witnessed harvest and real crop placement earn one local point");
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent(snapshot,level.getBlockState(soil),p));check(CampMembershipService.score(npc,p)==before+1,"duplicate placement event cannot repeat the reward");
            }finally{level.setBlock(soil,soilState,3);level.setBlock(crop,cropState,3);if(soilTag!=null&&level.getBlockEntity(soil)!=null)level.getBlockEntity(soil).load(soilTag);if(cropTag!=null&&level.getBlockEntity(crop)!=null)level.getBlockEntity(crop).load(cropTag);}
            var oldHome=CampMembershipService.membership(p).getString("HomeKey");CampMembershipService.membership(p).putString("HomeKey","another-household");
            NpcConversations.open(npc,p);choose(p,"join_menu");choose(p,"join_as_is");
            check(NpcConversations.state(p).getString("Node").equals("camp_transfer")&&CampMembershipService.membership(p).getString("HomeKey").equals("another-household"),"moving homes waits for explicit confirmation");capture(profile+"-transfer",p);
            choose(p,"camp_cancel_transfer");check(CampMembershipService.membership(p).getString("HomeKey").equals("another-household"),"declining a move keeps original home");NpcConversations.close(p);CampMembershipService.membership(p).putString("HomeKey",oldHome);
            var resident=(ChangedEntity)npc.getType().create(level);var data=npc.getPersistentData().getCompound(StructureResidentGoal.DATA).copy();data.putBoolean("Character",false);resident.getPersistentData().put(StructureResidentGoal.DATA,data);resident.setPos(p.position());level.addFreshEntity(resident);resident.setNoAi(true);
            try {check(!StoryNpcService.isNpc(resident)&&NpcConversations.canOpen(resident),"ordinary resident receives conversations without fixed-NPC identity");
                boolean understood=net.parkabird.changedsynergy.dialogue.NpcDialogue.canUnderstand(resident,p);NpcConversations.open(resident,p);capture(profile+"-resident",p);check(!NpcConversations.state(p).isEmpty(),"human can respond through gestures without learning telepathy");
                if(!understood)check(NpcConversations.state(p).getList("Choices",10).stream().map(CompoundTag.class::cast).noneMatch(c->c.getString("Id").equals("camp_submit")),"unknown language does not expose request submission");
                NpcConversations.close(p);
                var pending=CampLifeService.requests(npc).stream().map(CompoundTag.class::cast).filter(r->!r.getBoolean("Done")&&CampLifeService.personal(r)).findFirst().orElseThrow();pending.putUUID("Owner",resident.getUUID());
                resident.setNoAi(false);level.setDayTime(profile.equals("observatory_chief")?18000:6000);level.updateSkyBrightness();
                var approach=new CampApproachGoal(resident);
                net.parkabird.changedsynergy.performance.SynergyPerformanceTracker.onServerTickStart(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.START,()->true,server));
                int phase=net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.staggerBackgroundAi.get()?Math.floorMod(resident.getId(),100):0;
                resident.tickCount=(phase+1)%100;check(!approach.canUse(),"unscheduled invitation scans yield to the AI budget");resident.tickCount=phase;
                check(approach.canUse(),"idle member can receive a resident invitation");approach.start();approach.tick();
                check(NpcConversations.state(p).isEmpty(),"invitation never opens a screen automatically");
                check(CampLifeService.register(resident).getLong("NextInvite")>=level.getGameTime()+6000&&resident.getPersistentData().getLong("SynergyCampInviteDay")==CampLifeService.day(resident),"camp and resident invitation cooldowns persist");
                var threat=net.minecraft.world.entity.EntityType.ZOMBIE.create(level);threat.setTarget(resident);resident.setTarget(threat);check(!approach.canContinueToUse(),"combat immediately preempts the approach");resident.setTarget(null);approach.stop();threat.discard();resident.setNoAi(true);
                chest.setItem(0,new ItemStack(Items.BREAD,16));
                var fires=NaturalCampService.sites(npc).fires();
                if(!fires.isEmpty()){
                    var fire=fires.get(0);var state=level.getBlockState(fire);level.setBlock(fire,state.setValue(CampfireBlock.LIT,true).setValue(CampfireBlock.WATERLOGGED,false),2);
                    try {npc.getPersistentData().putLong("ChangedSynergyNaturalCampNextMeal",level.getGameTime()+6000);
                        check(CampApproachGoal.shareMeal(npc,p)&&!npc.getPersistentData().contains("ChangedSynergyNaturalCampNextMeal"),"member invitation schedules the existing real-food meal routine");
                        check(CampLifeService.count(npc,CampLifeService.Supply.FOOD)==16&&!CampApproachGoal.shareMeal(npc,p),"meal invitation creates no food and cannot reset its cooldown");
                    }finally{level.setBlock(fire,state,2);}
                }
                data.putString("Template","hyena_camp_tropical");check(!CampLifeService.mainCamp(resident)&&!NpcConversations.canOpen(resident),"small camps do not gain recruitment or full household conversations");
            } finally {resident.discard();}
            var account=CampMembershipService.membership(p);account.putInt("MembershipVersion",2);var local=account.getCompound("LocalTrust");local.putInt(oldHome,35);account.put("LocalTrust",local);var rewards=account.getCompound("AdmissionRewards");rewards.putInt(oldHome,35);account.put("AdmissionRewards",rewards);
            check(CampMembershipService.score(npc,p)==35&&account.getCompound("AdmissionRewards").getInt(oldHome)==35,"current local trust and previously paid bonuses are never reduced on migration");
        } finally {
            NpcConversations.close(p);NpcConversations.close(second);p.containerMenu=p.inventoryMenu;p.discard();second.discard();
            level.setBlock(floor,old,3);if(oldTag!=null&&level.getBlockEntity(floor)!=null)level.getBlockEntity(floor).load(oldTag);
            privacy.forEach((be,tag)->{if(tag.contains("ChangedSynergyPrivate"))be.getPersistentData().putBoolean("ChangedSynergyPrivate",tag.getBoolean("ChangedSynergyPrivate"));else be.getPersistentData().remove("ChangedSynergyPrivate");});
        }
    }
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
}
