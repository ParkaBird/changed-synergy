package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.*;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.npc.*;
import net.parkabird.changedsynergy.origin.*;
import net.parkabird.changedsynergy.world.inventory.PlayerRelationshipMenu;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostCargoInventory;

final class CommunityStoreChecks {
    private static int checks;
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    static void run(MinecraftServer server,List<ChangedEntity> chiefs)throws Exception {
        for(var chief:chiefs)if(CampLifeService.mainCamp(chief))storage(server,chief);
        for(String profile:List.of("canopy_chief","hyena_chief","observatory_chief")){
            var chief=chiefs.stream().filter(n->StoryNpcService.profile(n).id().endsWith(":"+profile)).findFirst().orElseThrow();
            introductions(server,chief);
        }
        System.out.println("COMMUNITY_STORAGE_PASS: "+checks+" rotated double chests, loot isolation, real resident pickup/deposit, introductions and roster transport checks");
    }
    private static void storage(MinecraftServer server,ChangedEntity chief)throws Exception {
        var level=(ServerLevel)chief.level();
        var publicPositions=NaturalCampService.sites(chief).storage().stream().filter(p->NaturalCampService.publicStorage(level,p)).toList();
        check(publicPositions.size()==2,"exactly two community chest halves per rotated household");
        var a=publicPositions.get(0);var b=publicPositions.get(1);var state=level.getBlockState(a);
        check(state.getBlock()==Blocks.CHEST&&state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE&&a.relative(ChestBlock.getConnectedDirection(state)).equals(b),"the authored halves connect in the real game");
        var store=PlayerOutpostData.container(level,a);check(store instanceof CompoundContainer&&store.getContainerSize()==54,"community reserve is one real 54-slot chest");
        check(store.isEmpty(),"public reserve starts empty");
        check(CampLifeService.stores(chief).size()==1,"both halves are counted once");
        for(var p:publicPositions){
            check(level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP),"chest rests on solid authored ground");
            check(!level.getBlockState(p.above()).isRedstoneConductor(level,p.above()),"chest lid has clearance");
            check(CampLifeService.template(chief).equals("desert_refuge")?NaturalCampService.mealPoints(chief).size()==1:NaturalCampService.sites(chief).fires().stream().anyMatch(f->f.distSqr(p)<=25),"community has its authored reserve and gathering place");
            check(!level.getBlockEntity(p).saveWithFullMetadata().contains("LootTable"),"community reserve is not a loot chest");
        }
        var loot=NaturalCampService.sites(chief).storage().stream().filter(p->!publicPositions.contains(p)).findFirst().orElseThrow();
        var old=level.getBlockEntity(loot).saveWithFullMetadata();
        var lootStore=PlayerOutpostData.container(level,loot);
        try {
            check(!CampMembershipService.usableStorage(chief,loot),"unopened loot is never a resident supply source");
            CampLifeService.count(chief,CampLifeService.Supply.FOOD);
            check(level.getBlockEntity(loot).saveWithFullMetadata().equals(old),"supply scan leaves authored loot untouched");
            for(int i=0;i<lootStore.getContainerSize();i++)lootStore.setItem(i,new ItemStack(Items.BREAD,64));
            check(!CampMembershipService.usableStorage(chief,loot)&&CampLifeService.count(chief,CampLifeService.Supply.FOOD)==0,"even opened, food-filled loot remains outside public supplies");
            store.setItem(0,new ItemStack(Items.WRITTEN_BOOK));check(CampMembershipService.usableStorage(chief,a),"an explicitly public chest does not become private when a member leaves a book");store.clearContent();
            var oldPos=chief.position();boolean noAi=chief.isNoAi(),ground=chief.onGround();boolean stagger=net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.staggerBackgroundAi.get();
            var cargo=PlayerOutpostCargoInventory.inventory(chief);var saved=new ArrayList<ItemStack>();for(int i=0;i<cargo.getContainerSize();i++)saved.add(cargo.getItem(i).copy());cargo.clearContent();
            try {
                chief.setNoAi(false);chief.setPos(Vec3.atBottomCenterOf(a.relative(state.getValue(ChestBlock.FACING))));chief.setOnGround(true);
                net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.staggerBackgroundAi.set(false);
                net.parkabird.changedsynergy.performance.SynergyPerformanceTracker.onServerTickStart(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.START,()->true,server));
                var remainder=PlayerOutpostWorkGoal.acceptCargo(chief,new ItemStack(Items.IRON_NUGGET,5));check(remainder.isEmpty(),"resident holds actual activity proceeds");
                var goal=new NaturalCampGoal(chief);check(goal.canUse(),"resident finds its community reserve for deposit");goal.start();goal.tick();goal.stop();
                check(store.countItem(Items.IRON_NUGGET)==5&&PlayerOutpostCargoInventory.inventory(chief).isEmpty()&&lootStore.countItem(Items.IRON_NUGGET)==0,"real resident deposit reaches public chest, never nearby loot");
                store.clearContent();for(int i=0;i<store.getContainerSize();i++)store.setItem(i,new ItemStack(Items.DIRT,64));
                PlayerOutpostWorkGoal.acceptCargo(chief,new ItemStack(Items.IRON_NUGGET,3));
                var blocked=new NaturalCampGoal(chief);check(!blocked.canUse()&&PlayerOutpostCargoInventory.inventory(chief).countItem(Items.IRON_NUGGET)==3,"full public storage retains cargo instead of spilling into loot");
            }finally{chief.setPos(oldPos);chief.setNoAi(noAi);chief.setOnGround(ground);net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.staggerBackgroundAi.set(stagger);store.clearContent();cargo.clearContent();for(int i=0;i<saved.size();i++)cargo.setItem(i,saved.get(i));}
        }finally{level.getBlockEntity(loot).load(old);}
    }
    private static void introductions(MinecraftServer server,ChangedEntity chief)throws Exception {
        var level=(ServerLevel)chief.level();var p=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"CampIntroduction"));p.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),p);
        var identity=new NativeOriginData.Identity();identity.state="human";NativeOriginData.get(server).players.put(p.getUUID(),identity);
        var field=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.players.PlayerList.class,"f_11197_");field.setAccessible(true);
        @SuppressWarnings("unchecked")var players=(Map<UUID,ServerPlayer>)field.get(server.getPlayerList());players.put(p.getUUID(),p);level.addNewPlayer(p);p.setPos(chief.position());
        var residents=level.getEntitiesOfClass(ChangedEntity.class,NaturalCampService.activityBounds(chief),r->CampLifeService.mainCamp(r)&&StoryNpcService.homeKey(r).equals(StoryNpcService.homeKey(chief)));
        try {
            FactionReputation.set(chief,p,0);var before=new HashMap<UUID,Integer>();for(var r:residents)before.put(r.getUUID(),CreaturePersonality.familiarity(r,p));
            check(CampMembershipService.join(chief,p,"join"),"formal home admission succeeds");
            check(CampMembershipService.score(chief,p)==0&&FactionReputation.score(chief,p)==0,"introductions grant neither repeated encounter reputation nor extra admission trust");
            var contacts=PlayerRelationshipSettings.contacts(p);
            for(var r:residents){check(CreaturePersonality.hasEstablishedRelationship(r,p)&&CreaturePersonality.familiarity(r,p)==before.get(r.getUUID())+1,"every authored resident gains exactly one affection and a remembered relationship");
                check(contacts.stream().anyMatch(c->c.uuid().equals(r.getUUID())&&c.campmate()&&!c.bonded()),"resident appears as a campmate without becoming bonded");}
            var contact=contacts.stream().filter(c->c.uuid().equals(chief.getUUID())).findFirst().orElseThrow();
            var buffer=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try{var write=PlayerRelationshipMenu.class.getDeclaredMethod("writeContact",FriendlyByteBuf.class,PlayerRelationshipSettings.Contact.class);var read=PlayerRelationshipMenu.class.getDeclaredMethod("readContact",FriendlyByteBuf.class);write.setAccessible(true);read.setAccessible(true);write.invoke(null,buffer,contact);var decoded=(PlayerRelationshipSettings.Contact)read.invoke(null,buffer);check(decoded.equals(contact)&&buffer.readableBytes()==0,"campmate badge survives actual menu wire encoding");}finally{buffer.release();}
            CampMembershipService.leave(p);check(PlayerRelationshipSettings.contacts(p).stream().filter(c->before.containsKey(c.uuid())).noneMatch(PlayerRelationshipSettings.Contact::campmate),"leaving removes the campmate badge while preserving contacts");
            check(CampMembershipService.join(chief,p,"join"),"returning resident can rejoin");
            for(var r:residents)check(CreaturePersonality.familiarity(r,p)==before.get(r.getUUID())+1,"rejoining cannot farm introduction affection");
            var ordinary=residents.stream().filter(r->!StoryNpcService.isNpc(r)).findFirst().orElseThrow();var box=NaturalCampService.bounds(ordinary);
            p.setPos(box.maxX+5,box.minY+1,(box.minZ+box.maxZ)/2);check(StructureResidentGoal.mayFollow(ordinary,p),"ordinary residents can still accompany a nearby campmate");
            p.setPos(box.maxX+7,box.minY+1,(box.minZ+box.maxZ)/2);check(!StructureResidentGoal.mayFollow(ordinary,p),"a friend cannot lead the resident beyond the reduced home margin");
            check(!NaturalCampService.activityBounds(ordinary).contains(p.position())&&NaturalCampService.activityBounds(ordinary).contains(new Vec3(box.maxX+5,box.minY+1,(box.minZ+box.maxZ)/2)),"reduced boundary retains nearby gathering room");
        }finally{players.remove(p.getUUID());p.discard();}
    }
}
