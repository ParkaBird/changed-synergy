package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.level.BlockEvent;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.npc.*;
import net.parkabird.changedsynergy.origin.*;
import net.parkabird.changedsynergy.world.inventory.*;

final class CampRepairBondChecks {
    private static int checks;
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    static void run(MinecraftServer server,List<ChangedEntity> chiefs)throws Exception {
        for(var chief:chiefs)if(CampLifeService.mainCamp(chief))repair(server,chief);
        for(String id:List.of("canopy_chief","hyena_chief","observatory_chief")){
            var chief=chiefs.stream().filter(n->StoryNpcService.profile(n).id().endsWith(":"+id)).findFirst().orElseThrow();
            bed(server,chief);relationships(server,chief,id);
        }
        System.out.println("CAMP_REPAIR_BOND_PASS: "+checks+" original-position repairs, player beds, double identities, separate hand-ins and clothing grants");
    }
    private static void placed(ServerLevel level,BlockPos pos,BlockState state,ChangedEntity actor){
        var snapshot=BlockSnapshot.create(level.dimension(),level,pos);level.setBlock(pos,state,2);
        MinecraftForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot,level.getBlockState(pos.below()),net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level)));
    }
    private static void repair(MinecraftServer server,ChangedEntity chief){
        var level=(ServerLevel)chief.level();CampLifeService.rememberStores(chief);var record=CampLifeService.register(chief);
        var positions=Arrays.stream(record.getLongArray("PublicStores")).mapToObj(BlockPos::of).toList();check(positions.size()==2,"permanent reserve address records both halves");
        var states=new LinkedHashMap<BlockPos,BlockState>();for(var p:positions)states.put(p,level.getBlockState(p));
        for(var p:positions){NaturalCampService.broken(new BlockEvent.BreakEvent(level,p,level.getBlockState(p),net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level)));level.setBlock(p,Blocks.AIR.defaultBlockState(),2);}
        CampLifeService.rememberStores(chief);check(record.getLongArray("PublicStores").length==2&&CampLifeService.stores(chief).isEmpty(),"destroying both halves preserves the address without inventing a container");
        check(CampLifeData.load(CampLifeData.get(server).save(new CompoundTag())).camps.get(record.getString("Key")).getLongArray("PublicStores").length==2,"reserve address survives saved-data serialization while the chest is absent");
        for(var entry:states.entrySet())placed(level,entry.getKey(),entry.getValue(),chief);
        check(positions.stream().allMatch(p->NaturalCampService.publicStorage(level,p)),"plain replacement chests regain public identity at the original positions");
        check(CampLifeService.stores(chief).size()==1&&CampLifeService.stores(chief).get(0).inventory().getContainerSize()==54,"replacement halves reconnect as one usable large reserve: "+states+" sites="+NaturalCampService.sites(chief).storage()+" stores="+CampLifeService.stores(chief));
        if(CampLifeService.template(chief).equals("hyena_hideout"))check(NaturalCampService.sites(chief).beds().stream().filter(p->level.getBlockState(p).is(Blocks.ORANGE_BED)).count()>=7,"six real rest-area beds join the Matriarch's existing orange bed");
        StoryNpcService.leave(new net.minecraftforge.event.entity.EntityLeaveLevelEvent(chief,level));
        check(StoryNpcData.get(server).records.get(StoryNpcService.key(chief)).getLong("Due")==0,"tracking loss without a removal reason never schedules a duplicate fixed NPC");
    }
    private static void bed(MinecraftServer server,ChangedEntity chief)throws Exception {
        var level=(ServerLevel)chief.level();var box=NaturalCampService.bounds(chief);var foot=BlockPos.containing(box.minX+3,box.minY+2,box.minZ+3);var head=foot.south();
        var saved=new LinkedHashMap<BlockPos,BlockState>();var tags=new HashMap<BlockPos,CompoundTag>();
        for(var value:BlockPos.betweenClosed(foot.offset(-1,-1,-1),foot.offset(2,3,2))){var p=value.immutable();saved.put(p,level.getBlockState(p));var be=level.getBlockEntity(p);if(be!=null)tags.put(p,be.saveWithFullMetadata());}
        var resident=(ChangedEntity)chief.getType().create(level);var data=chief.getPersistentData().getCompound(StructureResidentGoal.DATA).copy();data.putBoolean("Character",false);
        data.putDouble("MinX",foot.getX()-1);data.putDouble("MinY",foot.getY()-1);data.putDouble("MinZ",foot.getZ()-1);data.putDouble("MaxX",foot.getX()+3);data.putDouble("MaxY",foot.getY()+4);data.putDouble("MaxZ",foot.getZ()+3);
        resident.getPersistentData().put(StructureResidentGoal.DATA,data);resident.setPos(Vec3.atBottomCenterOf(foot));resident.setOnGround(true);
        boolean stagger=net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.staggerBackgroundAi.get();long time=level.getDayTime();
        try {
            for(var p:saved.keySet())level.setBlock(p,p.getY()==foot.getY()-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            level.addFreshEntity(resident);resident.setNoAi(false);net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.staggerBackgroundAi.set(false);
            level.setDayTime(resident instanceof net.parkabird.changedsynergy.entity.LatexEagleOwl?6000:18000);level.updateSkyBrightness();
            for(var block:List.of(Blocks.BLUE_BED,Blocks.RED_BED)){
                placed(level,foot,block.defaultBlockState().setValue(BedBlock.PART,BedPart.FOOT).setValue(BedBlock.FACING,Direction.SOUTH),resident);
                placed(level,head,block.defaultBlockState().setValue(BedBlock.PART,BedPart.HEAD).setValue(BedBlock.FACING,Direction.SOUTH),resident);
                check(NaturalCampService.sites(resident).beds().contains(head),"a player-placed or replaced bed is discovered without structure metadata");
                net.parkabird.changedsynergy.performance.SynergyPerformanceTracker.onServerTickStart(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.START,()->true,server));
                var goal=new NaturalCampSleepGoal(resident);check(goal.canUse(),"natural-camp sleep selects a newly placed suitable bed");goal.start();goal.tick();check(resident.getSleepingPos().filter(head::equals).isPresent(),"resident actually sleeps in the player bed");goal.stop();if(resident.isSleeping())resident.stopSleeping();
                NaturalCampService.broken(new BlockEvent.BreakEvent(level,head,level.getBlockState(head),net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level)));level.setBlock(head,Blocks.AIR.defaultBlockState(),2);level.setBlock(foot,Blocks.AIR.defaultBlockState(),2);resident.setPos(Vec3.atBottomCenterOf(foot));resident.setOnGround(true);
            }
        }finally{resident.discard();net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.staggerBackgroundAi.set(stagger);level.setDayTime(time);level.updateSkyBrightness();saved.forEach((p,s)->level.setBlock(p,s,2));tags.forEach((p,t)->{if(level.getBlockEntity(p)!=null)level.getBlockEntity(p).load(t);});}
    }
    private static ServerPlayer player(MinecraftServer server,ChangedEntity chief)throws Exception {
        var p=new ServerPlayer(server,(ServerLevel)chief.level(),new GameProfile(UUID.randomUUID(),"CampCoexist"));p.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),p);p.tickCount=200;
        var id=new NativeOriginData.Identity();id.state="human";NativeOriginData.get(server).players.put(p.getUUID(),id);
        var f=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.players.PlayerList.class,"f_11197_");f.setAccessible(true);@SuppressWarnings("unchecked")var map=(Map<UUID,ServerPlayer>)f.get(server.getPlayerList());map.put(p.getUUID(),p);((ServerLevel)chief.level()).addNewPlayer(p);p.setPos(chief.position().add(1,0,0));return p;
    }
    private static void finish(ServerPlayer p){for(int i=0;i<100;i++){var s=NpcConversations.state(p);if(s.isEmpty()||s.getBoolean("Last"))return;NpcConversations.action(p,s.getUUID("Session"),s.getInt("Revision"),"advance");}throw new AssertionError("dialogue cannot finish");}
    private static void choose(ServerPlayer p,String id){finish(p);var s=NpcConversations.state(p);check(!s.isEmpty(),"dialogue session exists before "+id);NpcConversations.action(p,s.getUUID("Session"),s.getInt("Revision"),id);}
    private static void open(ChangedEntity npc,ServerPlayer p){
        for(double y:List.of(0D,.5D,1D))for(var offset:List.of(new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,0,1),new Vec3(0,0,-1))){p.setPos(npc.position().add(offset).add(0,y,0));NpcConversations.open(npc,p);if(!NpcConversations.state(p).isEmpty())return;}
        throw new AssertionError("no unobstructed conversation position for "+StoryNpcService.key(npc));
    }
    private static void relationships(MinecraftServer server,ChangedEntity chief,String profile)throws Exception {
        var level=(ServerLevel)chief.level();var p=player(server,chief);var resident=CampLifeService.residents(chief).stream().filter(r->!StoryNpcService.isNpc(r)).findFirst().orElseThrow();var position=resident.position();var ownTag=resident.saveWithoutId(new CompoundTag());
        try {
            check(CampMembershipService.join(chief,p,"join"),"membership can precede a bond");check(BondTokenService.canCreateBond(resident,p),"ordinary camp resident is bond-eligible");
            LatexSocialMemory.addBond(resident,p);check(LatexSocialMemory.isBonded(resident,p)&&CampMembershipService.member(resident,p)&&CampLifeService.mainCamp(resident),"bond retains the resident's authored home identity");
            var contact=PlayerRelationshipSettings.contacts(p).stream().filter(c->c.uuid().equals(resident.getUUID())).findFirst().orElseThrow();check(contact.bonded()&&contact.campmate(),"real roster data carries both identities at once");
            var menu=new SocialInteractionMenu(22,p.getInventory(),resident,true,false);check(menu.isBondedMode()&&menu.getProgression().getBoolean("CampDialogue"),"bond wheel retains the camp dialogue replacement flag");
            LatexSocialMemory.setFollowingOwner(resident,false);check(!NaturalCampService.followingOwner(resident),"wandering bond can yield to camp routines");
            ProcessTransfur.setPlayerTransfurVariant(p,resident.getSelfVariant(),null,1,false);p.setPos(position.add(1,0,0));
            var personal=CampLifeService.visibleRequests(resident).stream().filter(CampLifeService::personal).findFirst().orElseThrow();String id=personal.getString("Id");int amount=personal.getInt("Count");
            check(CampLifeService.visibleRequests(chief).stream().noneMatch(CampLifeService::personal),"chief lists only public requests");
            var reserves=CampLifeService.stores(chief);var reserve=reserves.get(0).inventory();for(int i=0;i<reserve.getContainerSize();i++)reserve.setItem(i,new ItemStack(Items.DIRT,64));
            var belongings=BondedInventoryService.inventory(resident,p);int before=belongings.countItem(Items.BREAD);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BREAD,amount));
            open(resident,p);choose(p,"camp_requests");choose(p,"camp_req_"+id);CampLifeChecks.capture(profile+"-personal-request",p);
            check(!CampLifeService.deliver(chief,p,p.getMainHandItem().copy(),amount,id),"chief cannot intercept another resident's personal hand-in");
            choose(p,"camp_submit");check(p.getMainHandItem().isEmpty()&&BondedInventoryService.inventory(resident,p).countItem(Items.BREAD)==before+amount&&reserve.countItem(Items.BREAD)==0,"personal hand-in reaches the resident even when the public reserve is full");NpcConversations.close(p);
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BREAD,amount));check(!CampLifeService.deliver(resident,p,p.getMainHandItem().copy(),amount,id),"personal request completion is idempotent");reserve.clearContent();
            var home=StoryNpcService.homeKey(resident);var data=resident.getPersistentData().getCompound(StructureResidentGoal.DATA);String dimension=data.getString("Dimension");data.putString("Dimension","minecraft:the_nether");check(!NaturalCampService.inHomeDimension(resident)&&StoryNpcService.homeKey(resident).startsWith("minecraft:the_nether:"),"home identity uses its saved dimension rather than current body position");data.putString("Dimension",dimension);check(StoryNpcService.homeKey(resident).equals(home),"restoring dimension restores the exact stable home key");
            ProcessTransfur.removePlayerTransfurVariant(p);p.setPos(chief.position().add(1,0,0));CampMembershipService.adjust(chief,p,39-CampMembershipService.score(chief,p));check(!CampLifeService.canClaimOutfit(chief,p),"clothing is locked below respected trust");CampMembershipService.adjust(chief,p,1);
            p.getInventory().clearContent();for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.DIRT,64));check(!CampLifeService.claimOutfit(chief,p)&&CampLifeService.canClaimOutfit(chief,p),"a full player inventory spends no clothing entitlement");p.getInventory().clearContent();
            open(chief,p);choose(p,"camp_life");check(NpcConversations.state(p).getList("Choices",10).stream().map(CompoundTag.class::cast).anyMatch(c->c.getString("Id").equals("camp_outfit")),"chief exposes clothing reward at respected trust");choose(p,"camp_outfit");CampLifeChecks.capture(profile+"-outfit",p);NpcConversations.close(p);
            var outfit=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(StoryNpcService.profile(chief).equipment().get(0)));check(p.getInventory().countItem(outfit)==1&&!CampLifeService.claimOutfit(chief,p),"one real camp outfit is granted without repeat claims");
            CampMembershipService.leave(p);check(LatexSocialMemory.isBonded(resident,p)&&PlayerRelationshipSettings.contacts(p).stream().filter(c->c.uuid().equals(resident.getUUID())).allMatch(c->c.bonded()&&!c.campmate()),"leaving the camp does not cancel its independent bond");check(CampMembershipService.join(chief,p,"join")&&!CampLifeService.canClaimOutfit(chief,p),"rejoining preserves the outfit claim");
        }finally{NpcConversations.close(p);LatexSocialMemory.unregisterBond(resident,p);resident.load(ownTag);resident.setPos(position);p.discard();}
    }
}
