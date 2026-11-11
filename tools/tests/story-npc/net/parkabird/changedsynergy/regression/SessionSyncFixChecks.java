package net.parkabird.changedsynergy.regression;

import java.util.*;
import com.mojang.authlib.GameProfile;
import net.ltxprogrammer.changed.ability.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.*;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.phys.*;
import net.parkabird.changedsynergy.ability.HypnosisToggle;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.npc.*;
import net.parkabird.changedsynergy.origin.*;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostMenu;

final class SessionSyncFixChecks {
    private static int checks;
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    static void run(MinecraftServer server,List<ChangedEntity> chiefs)throws Exception {
        var level=server.overworld();
        for(var chief:chiefs)if(CampDialogue.chief(chief))ownership(chief);
        var chief=chiefs.stream().filter(CampDialogue::chief).findFirst().orElseThrow();
        var p=player(server,chief.position());
        try{provocation(chief,p,chiefs);counting(level,p);desert(server,p);hypnosis(p);}finally{NpcConversations.close(p);p.discard();}
        System.out.println("SESSION_SYNC_FIXES_PASS: "+checks+" ownership, legacy rotations, provocation, warnings, double chest, desert dialogue and hypnosis input checks");
    }
    private static ServerPlayer player(MinecraftServer server,Vec3 pos)throws Exception {
        var p=new ServerPlayer(server,server.overworld(),new GameProfile(UUID.randomUUID(),"SyncFix"));
        p.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),p);
        var id=new NativeOriginData.Identity();id.state="human";NativeOriginData.get(server).players.put(p.getUUID(),id);
        var f=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.players.PlayerList.class,"f_11197_");f.setAccessible(true);
        @SuppressWarnings("unchecked")var map=(Map<UUID,ServerPlayer>)f.get(server.getPlayerList());map.put(p.getUUID(),p);server.overworld().addNewPlayer(p);p.setPos(pos);return p;
    }
    private static void ownership(ChangedEntity npc){
        var level=(ServerLevel)npc.level();var data=npc.getPersistentData().getCompound(StructureResidentGoal.DATA);
        for(var pos:NaturalCampService.sites(npc).storage()){
            var be=level.getBlockEntity(pos);if(!be.saveWithFullMetadata().contains("LootTable"))continue;
            check(NaturalCampService.ownsStorage(npc,pos),"authored loot belongs to camp before opening");
            PlayerOutpostData.container(level,pos).getItem(0);
            check(NaturalCampService.ownsStorage(npc,pos),"opening loot cannot erase ownership");
            // Reproduce an already-opened pre-fix camp, without either placement or BE markers.
            data.remove("PlacementOrigin");data.remove("PlacementRotation");be.getPersistentData().remove("ChangedSynergyStructureStorage");
            var state=level.getBlockState(pos);
            if(state.getBlock() instanceof ChestBlock&&state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE){
                var mate=pos.relative(ChestBlock.getConnectedDirection(state));level.getBlockEntity(mate).getPersistentData().remove("ChangedSynergyStructureStorage");
            }
            check(NaturalCampService.ownsStorage(npc,pos),"old unpacked camp retains ownership: "+data.getString("Template")+pos);
            if(!CampMembershipService.privateStorage(npc,pos))check(data.contains("PlacementOrigin"),"old unpacked public loot recovers its exact transform: "+data.getString("Template")+pos+data);
            var saved=be.saveWithFullMetadata();be.load(saved);
            check(NaturalCampService.ownsStorage(npc,pos),"ownership survives container NBT reload");
        }
        var box=NaturalCampService.bounds(npc);boolean tested=false;
        for(int x=(int)box.minX;x<box.maxX&&!tested;x++)for(int z=(int)box.minZ;z<box.maxZ&&!tested;z++){
            var pos=new BlockPos(x,(int)box.maxY-1,z);if(!level.isEmptyBlock(pos))continue;
            level.setBlock(pos,Blocks.CHEST.defaultBlockState(),2);
            check(!NaturalCampService.ownsStorage(npc,pos),"arbitrary player chest is not claimed by the structure");level.removeBlock(pos,false);tested=true;
        }check(tested,"player chest control exercised");
    }
    private static void provocation(ChangedEntity chief,ServerPlayer p,List<ChangedEntity> chiefs){
        p.setPos(chief.position());CampMembershipService.refreshChiefPeace(chief,p);
        check(CampMembershipService.hasChiefPeace(chief,p),"peace active before offence");
        check(LatexSocialMemory.markProvoked(chief,p),"chief first offence terminates without recursion");
        check(LatexSocialMemory.isProvoked(chief,p)&&!CampMembershipService.hasChiefPeace(chief,p),"chief provocation revokes peace");
        check(!LatexSocialMemory.markProvoked(chief,p),"repeated offence is idempotent");
        var resident=CampLifeService.residents(chief).stream().filter(n->!StoryNpcService.isNpc(n)).findFirst().orElseThrow();
        check(LatexSocialMemory.markProvoked(resident,p),"ordinary resident can notify chief without recursion");
        var other=chiefs.stream().filter(c->c!=chief&&CampDialogue.chief(c)).findFirst().orElseThrow();var oldPos=other.position();var oldTag=other.getPersistentData().getCompound(StoryNpcService.DATA).copy();
        try{
            other.setPos(chief.position());other.getPersistentData().getCompound(StoryNpcService.DATA).putString("Key",StoryNpcService.profile(other).id()+"@"+StoryNpcService.homeKey(chief));
            CampMembershipService.breakChiefPeace(resident,p);
            check(LatexSocialMemory.isProvoked(other,p),"multiple chiefs in one home notify each other without recursion");
        }finally{other.setPos(oldPos);other.getPersistentData().put(StoryNpcService.DATA,oldTag);}
        for(String suffix:List.of("camp_private_storage","camp_private_storage_repeat")){
            chief.getPersistentData().remove("SynergyContextLineAt");
            net.parkabird.changedsynergy.dialogue.NpcDialogue.context(chief,p,suffix);
            check(chief.getPersistentData().getLong("SynergyContextLineAt")>levelTime(chief),"fixed chief gives functional private-storage warning: "+suffix);
        }
        chief.getPersistentData().remove("SynergyContextLineAt");
        net.parkabird.changedsynergy.dialogue.NpcDialogue.context(chief,p,"camp_arrival");
        check(chief.getPersistentData().getLong("SynergyContextLineAt")==0,"random fixed-NPC chatter stays suppressed");
    }
    private static long levelTime(ChangedEntity n){return n.level().getGameTime();}
    private static void counting(ServerLevel level,ServerPlayer p)throws Exception {
        var left=new BlockPos(8,100,8);var right=left.east();level.getChunk(left);
        level.setBlock(left,Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.NORTH).setValue(ChestBlock.TYPE,ChestType.LEFT),2);
        level.setBlock(right,Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.NORTH).setValue(ChestBlock.TYPE,ChestType.RIGHT),2);
        ((ChestBlockEntity)level.getBlockEntity(left)).setItem(0,new ItemStack(Items.DIAMOND,7));
        var ctor=PlayerOutpostData.Outpost.class.getDeclaredConstructor(UUID.class,String.class,BlockPos.class);ctor.setAccessible(true);
        var outpost=ctor.newInstance(p.getUUID(),level.dimension().location().toString(),left);outpost.storage=left;outpost.storages.add(left);outpost.storages.add(right);
        var counter=PlayerOutpostMenu.class.getDeclaredMethod("countStock",ServerLevel.class,PlayerOutpostData.Outpost.class);counter.setAccessible(true);
        check((int)counter.invoke(null,level,outpost)==7,"both linked halves count once");outpost.storages.remove(right);
        check((int)counter.invoke(null,level,outpost)==7,"single registered half sees full chest");
        var single=left.north(3);level.setBlock(single,Blocks.CHEST.defaultBlockState(),2);((ChestBlockEntity)level.getBlockEntity(single)).setItem(0,new ItemStack(Items.IRON_INGOT,3));outpost.storages.add(single);
        check((int)counter.invoke(null,level,outpost)==10,"separate chest is still included");
    }
    private static void activate(ServerLevel level,BlockPos pos)throws Exception {
        level.getChunk(pos);level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
        var f=Arrays.stream(ServerLevel.class.getDeclaredFields()).filter(x->net.minecraft.world.level.entity.PersistentEntitySectionManager.class.isAssignableFrom(x.getType())).findFirst().orElseThrow();f.setAccessible(true);
        var m=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)f.get(level);m.updateChunkStatus(new net.minecraft.world.level.ChunkPos(pos),FullChunkStatus.ENTITY_TICKING);m.tick();
    }
    private static void desert(MinecraftServer server,ServerPlayer p)throws Exception {
        var level=server.overworld();int i=0;
        for(var rotation:Rotation.values()){
            var template=server.getStructureManager().getOrCreate(ResourceLocation.parse("changed_synergy:desert_refuge"));
            var origin=new BlockPos(4000+i++*96,100,4000);var settings=new StructurePlaceSettings().setRotation(rotation);var box=template.getBoundingBox(settings,origin);
            for(int x=box.minX()>>4;x<=box.maxX()>>4;x++)for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++)activate(level,new BlockPos(x*16,100,z*16));
            check(template.placeInWorld(level,origin,origin,settings,net.minecraft.util.RandomSource.create(17),2),"desert placed");
            var residents=level.getEntitiesOfClass(ChangedEntity.class,AABB.of(box),m->NaturalCampService.member(m)&&!StoryNpcService.isNpc(m));check(residents.size()==5,"all five ordinary desert residents preserved");
            for(var npc:residents){
                check(CampLifeService.mainCamp(npc)&&CampLifeService.profile(npc)!=null&&CampLifeService.profile(npc).id().equals("changed_synergy:desert_chief")&&!StoryNpcService.isNpc(npc),"ordinary residents share the authored community profile without becoming fixed Chiefs");
                var stores=CampLifeService.stores(npc);check(stores.size()==1&&stores.get(0).inventory().getContainerSize()==54&&stores.get(0).inventory().isEmpty(),"desert public reserve is empty connected double chest");
            }
            ownership(residents.get(0));
            var npc=residents.get(0);npc.setNoAi(true);p.setPos(npc.position());
            ProcessTransfur.setPlayerTransfurVariant(p,npc.getSelfVariant(),null,1,false);
            NpcConversations.open(npc,p);check(!NpcConversations.state(p).isEmpty(),"ordinary resident conversation opens");
            NpcConversations.tick(server);check(NpcConversations.talking(p),"ordinary resident conversation remains valid");
            var state=NpcConversations.state(p);NpcConversations.action(p,state.getUUID("Session"),state.getInt("Revision"),"permanent");
            check(!CampMembershipService.member(npc,p)&&!CampMembershipService.canJoin(npc,p),"ordinary resident cannot recruit or accept forged ritual action");
            NpcConversations.close(p);ProcessTransfur.removePlayerTransfurVariant(p);
        }
    }
    private static final class CountingHypnosis extends HypnosisAbility {
        int starts,ticks,stops;
        @Override public void startUsing(IAbstractChangedEntity e){starts++;}
        @Override public void tick(IAbstractChangedEntity e){ticks++;}
        @Override public void stopUsing(IAbstractChangedEntity e){stops++;}
    }
    private static final class CountingHold extends SimpleAbility {
        int starts,stops;
        @Override public AbstractAbility.UseType getUseType(IAbstractChangedEntity e){return AbstractAbility.UseType.HOLD;}
        @Override public boolean canUse(IAbstractChangedEntity e){return true;}
        @Override public void startUsing(IAbstractChangedEntity e){starts++;}
        @Override public void stopUsing(IAbstractChangedEntity e){stops++;}
    }
    private static void input(AbstractAbility.Controller c,boolean down){AbstractAbility.UseType.HOLD.check(down,c.exchangeKeyState(down),true,c);}
    private static void hypnosis(ServerPlayer p)throws Exception {
        p.setPos(5000,100,5000);ProcessTransfur.setPlayerTransfurVariant(p,ChangedTransfurVariants.LATEX_HYPNO_CAT.get(),null,1,false);
        var ability=new CountingHypnosis();var instance=ability.makeInstance(IAbstractChangedEntity.forPlayer(p));var c=instance.getController();var toggle=(HypnosisToggle)c;
        input(c,true);check(ability.starts==1&&c.getProgressActive()==1&&toggle.changedSynergy$hypnosisBrightness()==1,"first press activates with full held brightness");
        input(c,true);input(c,false);check(toggle.changedSynergy$isLatched()&&c.getProgressActive()==1&&toggle.changedSynergy$hypnosisBrightness()==.65F,"release keeps active with lower brightness");
        for(int i=0;i<100;i++)input(c,false);
        check(ability.ticks==102&&ability.stops==0&&c.getHoldTicks()==102,"latched input runs native continuous ticks after release");
        input(c,true);check(!toggle.changedSynergy$isLatched()&&ability.stops==1&&c.getProgressActive()==0,"second press deactivates exactly once");
        input(c,true);input(c,false);check(ability.starts==1&&ability.stops==1,"held second press and release cannot restart");
        input(c,true);input(c,false);toggle.changedSynergy$clearHypnosis();check(ability.starts==2&&ability.stops==2,"explicit lifecycle cleanup stops and clears latch");
        var mob=ChangedEntities.LATEX_HYPNO_CAT.get().create(p.level());var npcAbility=new CountingHypnosis();var nc=npcAbility.makeInstance(IAbstractChangedEntity.forEntity(mob)).getController();
        input(nc,true);input(nc,false);check(npcAbility.starts==1&&npcAbility.stops==1&&!((HypnosisToggle)nc).changedSynergy$isLatched(),"NPC hypnosis retains native hold controls");
        var otherAbility=new CountingHold();var oc=otherAbility.makeInstance(IAbstractChangedEntity.forPlayer(p)).getController();input(oc,true);input(oc,false);
        check(otherAbility.starts==1&&otherAbility.stops==1&&!((HypnosisToggle)oc).changedSynergy$isLatched(),"other held abilities retain native controls");
        var variant=ProcessTransfur.getPlayerTransfurVariant(p);variant.setSelectedAbility(ChangedAbilities.HYPNOSIS.get());var real=variant.getSelectedAbility().getController();
        var tick=net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance.class.getDeclaredMethod("tickAbilities");tick.setAccessible(true);
        variant.abilityKey.queueKeyState(true);tick.invoke(variant);variant.abilityKey.queueKeyState(false);tick.invoke(variant);
        check(((HypnosisToggle)real).changedSynergy$isLatched(),"real variant tick consumes native physical press and release");
        variant.setSelectedAbility(ChangedAbilities.TOGGLE_NIGHT_VISION.get());tick.invoke(variant);
        check(!((HypnosisToggle)real).changedSynergy$isLatched(),"deselecting hypnosis clears stale input");
        if(net.parkabird.changedsynergy.compat.ChangedAddonCompat.isLoaded()){
            @SuppressWarnings("unchecked")var enabled=(net.minecraftforge.common.ForgeConfigSpec.ConfigValue<Boolean>)Class.forName("net.foxyas.changedaddon.configuration.ChangedAddonServerConfiguration").getField("ALLOW_SECOND_ABILITY_USE").get(null);
            enabled.set(true);
            var api=Class.forName("net.foxyas.changedaddon.variant.TransfurVariantInstanceExtensor");api.getMethod("setSecondSelectedAbility",AbstractAbility.class).invoke(variant,ChangedAbilities.HYPNOSIS.get());
            var key=(net.ltxprogrammer.changed.util.KeyStateTracker)api.getMethod("getSecondAbilityKey").invoke(variant);
            key.queueKeyState(true);tick.invoke(variant);key.queueKeyState(false);tick.invoke(variant);tick.invoke(variant);
            check(((HypnosisToggle)real).changedSynergy$isLatched(),"Addon secondary ability continues after physical release");
            key.queueKeyState(true);tick.invoke(variant);key.queueKeyState(false);tick.invoke(variant);
            check(!((HypnosisToggle)real).changedSynergy$isLatched(),"Addon secondary ability switches off on next press");
            api.getMethod("setSecondSelectedAbility",AbstractAbility.class).invoke(variant,ChangedAbilities.TOGGLE_NIGHT_VISION.get());
        }
        variant.setSelectedAbility(ChangedAbilities.HYPNOSIS.get());input(real,true);input(real,false);ProcessTransfur.removePlayerTransfurVariant(p);
        check(!((HypnosisToggle)real).changedSynergy$isLatched(),"form removal clears old controller state");
    }
}
