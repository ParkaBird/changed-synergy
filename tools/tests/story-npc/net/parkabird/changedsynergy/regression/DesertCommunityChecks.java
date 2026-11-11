package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.*;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.npc.*;
import net.parkabird.changedsynergy.init.*;

final class DesertCommunityChecks {
    private static int checks,index;private static MinecraftServer server;private static ServerLevel level;
    private static ResourceLocation id(String p){return ResourceLocation.parse("changed_synergy:"+p);}
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    static void run(MinecraftServer s)throws Exception{
        server=s;level=s.overworld();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,s);net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.staggerBackgroundAi.set(false);
        for(var rotation:Rotation.values()){
            var residents=place("desert_refuge",rotation);var chief=residents.stream().filter(StoryNpcService::isNpc).findFirst().orElseThrow();
            check(chief.getBasicPlayerInfo().getEyeStyle()==net.ltxprogrammer.changed.entity.EyeStyle.TALL
                    &&chief.getBasicPlayerInfo().getLeftIrisColor().toInt()==0x70B7A5
                    &&chief.getBasicPlayerInfo().getScleraColor().toInt()==0xFFFFFF,"Pathfinder has sage turquoise irises and white sclera");
            check(residents.size()==6&&NpcConversations.canOpen(chief),"six residents and an enabled Chief");
            check(CampLifeService.voice(chief).equals("pathfinder")&&NaturalCampService.sites(chief).fires().isEmpty()&&NaturalCampService.mealPoints(chief).size()==1,"refuge gathers in the common room without a campfire");
            var roles=new HashSet<String>();for(var m:residents)if(m!=chief){roles.add(m.getPersistentData().getCompound(StructureResidentGoal.DATA).getString("DialogueRole"));check(!StoryNpcService.isNpc(m),"residents retain ordinary mortal identity");}
            check(roles.equals(Set.of("dorm","study","garden","lab","corridor")),"original residents acquire distinct dialogue roles in every rotation");
            if(rotation==Rotation.NONE){
                var origin=BlockPos.of(chief.getPersistentData().getCompound(StructureResidentGoal.DATA).getLong("PlacementOrigin"));
                var old=origin.offset(2,7,16);var other=old.south();
                for(var pos:List.of(old,other)){var flags=level.getBlockEntity(pos).getPersistentData();flags.remove("ChangedSynergyPrivate");flags.putBoolean(NaturalCampService.PUBLIC_STORAGE,true);}
                var oldInventory=PlayerOutpostData.container(level,old);oldInventory.setItem(0,new ItemStack(Items.DIAMOND,3));oldInventory.setChanged();
                var garden=origin.offset(8,14,15);var gardenInventory=PlayerOutpostData.container(level,garden);gardenInventory.setItem(0,new ItemStack(Items.EMERALD,2));gardenInventory.setChanged();
                for(var pos:List.of(garden,garden.south()))level.getBlockEntity(pos).getPersistentData().remove(NaturalCampService.PUBLIC_STORAGE);
                var record=CampLifeService.register(chief);record.remove("StorageLayout");record.putLongArray("PublicStores",new long[]{old.asLong(),other.asLong()});
                for(var pos:List.of(old,other,garden,garden.south()))NaturalCampService.ownsStorage(chief,pos);CampLifeService.rememberStores(chief);
                check(CampMembershipService.privateStorage(chief,old)&&!NaturalCampService.publicStorage(level,old)&&!CampMembershipService.usableStorage(chief,old),"old reserve becomes private and cannot be re-marked by stale saved positions");
                check(NaturalCampService.publicStorage(level,garden)&&CampLifeService.stores(chief).size()==1,"garden double chest replaces previous reserve in existing camp data");
                check(oldInventory.countItem(Items.DIAMOND)==3&&gardenInventory.countItem(Items.EMERALD)==2,"storage migration preserves both existing inventories");oldInventory.clearContent();gardenInventory.clearContent();
            }
            var p=player(chief);check(CampMembershipService.join(chief,p,"join"),"new household accepts a human without changing form");
            check(CampMembershipService.member(chief,p)&&!ProcessTransfur.isPlayerTransfurred(p),"formal local membership preserves human origin");
            var reserve=CampLifeService.stores(chief).get(0).inventory();reserve.setItem(0,new ItemStack(Items.BREAD,32));reserve.setChanged();
            check(CampApproachGoal.shareMeal(chief,p),"shared-meal action succeeds in the campfire-free refuge");check(!CampApproachGoal.shareMeal(chief,p),"meal invitation cannot be spammed");
            if(rotation==Rotation.NONE){
                p.setPos(chief.getX(),chief.getY(),chief.getZ());NpcConversations.open(chief,p);var state=NpcConversations.state(p);check(state.getString("Node").equals("root"),"authored Chief introduction opens");
                check(!choices(state).contains("records"),"old records are hidden before introduction and home topics");
                choose(p,"about");choose(p,"back");choose(p,"home");choose(p,"back");check(choices(NpcConversations.state(p)).contains("records"),"third background topic unlocks after both prerequisites");
                choose(p,"camp_life");check(NpcConversations.state(p).getList("Parts",10).stream().anyMatch(t->((CompoundTag)t).getString("Text").contains("pathfinder.greeting")),"Chief uses refuge-specific everyday voice");NpcConversations.close(p);
                ProcessTransfur.setPlayerTransfurVariant(p,ChangedSynergyTransfurVariants.LATEX_JERBOA.get(),null,1,false);
                for(var resident:residents)if(resident!=chief){
                    p.setPos(resident.getX(),resident.getY(),resident.getZ());NpcConversations.open(resident,p);
                    check(NpcConversations.state(p).getString("Node").equals("resident_"+resident.getPersistentData().getCompound(StructureResidentGoal.DATA).getString("DialogueRole")),"understood ordinary resident opens its authored situation");
                    choose(p,"camp_life");check(NpcConversations.state(p).getList("Parts",10).stream().anyMatch(t->((CompoundTag)t).getString("Text").contains("desert_resident")),"ordinary camp actions use desert-adapted wording");NpcConversations.close(p);
                }
                ProcessTransfur.removePlayerTransfurVariant(p);
                var worker=chief;
                var meal=new NaturalCampGoal(worker);var select=NaturalCampGoal.class.getDeclaredMethod("selectMeal",ServerLevel.class,AABB.class);select.setAccessible(true);boolean route=(boolean)select.invoke(meal,level,NaturalCampService.bounds(worker));
                if(!route){
                    var origin=BlockPos.of(worker.getPersistentData().getCompound(StructureResidentGoal.DATA).getLong("PlacementOrigin"));
                    System.out.println("DESERT_NAV_DEBUG: step="+worker.maxUpStep()+" follow="+worker.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)+" pass="+worker.getNavigation().getNodeEvaluator().canPassDoors()+" open="+worker.getNavigation().getNodeEvaluator().canOpenDoors());
                    for(var local:List.of(new BlockPos(28,20,18),new BlockPos(27,21,18),new BlockPos(26,21,18),new BlockPos(25,21,18),new BlockPos(18,21,30),new BlockPos(18,14,30))){var pos=origin.offset(local);var path=worker.getNavigation().createPath(pos,1);var st=level.getBlockState(pos);System.out.println("DESERT_WAYPOINT: "+local+" state="+st+" type="+st.getBlockPathType(level,pos,worker)+" path="+(path==null?"null":path.canReach()+" end="+path.getEndNode()+" count="+path.getNodeCount()));}
                    var food=NaturalCampGoal.class.getDeclaredMethod("foodSlot",Container.class);food.setAccessible(true);System.out.println("DESERT_MEAL_DEBUG: type="+worker.getType()+" position="+worker.position()+" size="+worker.getBbWidth()+","+worker.getBbHeight()+" ground="+worker.onGround()+" reserve_food_slot="+food.invoke(meal,reserve)+" stores="+NaturalCampService.sites(worker).storage().stream().filter(p0->CampMembershipService.usableStorage(worker,p0)).toList());
                    for(var anchor:NaturalCampService.mealPoints(worker))for(var p0:BlockPos.betweenClosed(anchor.offset(-5,-1,-5),anchor.offset(5,1,5)))if(PlayerOutpostRestGoal.isRestPoint(level.getBlockState(p0))){var path=worker.getNavigation().createPath(p0,1);System.out.println("DESERT_SEAT_PATH: "+p0+" free="+PlayerOutpostRestGoal.free(worker,level,p0)+" path="+(path==null?"null":path.canReach()+" end="+path.getEndNode()+" count="+path.getNodeCount()));}
                }
                check(route,"resident can find a real route from office to common-room seat with food available");
            }
            CampMembershipService.leave(p);p.discard();
        }
        for(var rotation:Rotation.values())for(String name:List.of("oasis","muddy_campsite")){
            var residents=place(name,rotation);check(residents.size()==(name.equals("oasis")?2:4),"small camp residents exactly match authored bed capacity");
            if(name.equals("muddy_campsite"))check(residents.stream().map(m->net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).toString()).collect(java.util.stream.Collectors.toSet()).equals(Set.of("changed:latex_red_dragon","changed:latex_fennec_fox","changed_synergy:latex_jerboa","changed:green_lizard")),"muddy roster has one of each requested species");
            for(var mob:residents){check(!StoryNpcService.isNpc(mob)&&!CampLifeService.mainCamp(mob)&&!NpcConversations.canOpen(mob),"small camps have no Chief or separate admission");check(level.noCollision(mob),"inhabitant is clear of structure blocks");
                var sites=NaturalCampService.sites(mob);check(sites.beds().size()==(name.equals("oasis")?2:4),"all authored beds recognized");
                check(sites.storage().stream().filter(p->NaturalCampService.publicStorage(level,p)).count()==1,"one independent public reserve");
                for(var pos:sites.storage())if(level.getBlockState(pos).is(Blocks.BARREL)){
                    var barrel=(net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity)level.getBlockEntity(pos);
                    check(CampMembershipService.usableStorage(mob,pos),"authored barrel is shared storage with its initial loot retained");barrel.unpackLootTable(null);
                    check(CampMembershipService.privateStorage(mob,pos),"resident supplies remain private to players");
                    check(CampMembershipService.usableStorage(mob,pos),"shared barrel stays usable after loot initialization");
                }
            }
            var first=residents.get(0);var reserve=NaturalCampService.sites(first).storage().stream().filter(p->NaturalCampService.publicStorage(level,p)).findFirst().orElseThrow();
            var record=CampLifeService.register(first);check(record.getBoolean("RoutinesOnly")&&record.getLongArray("PublicStores").length==1,"small-camp reserve repair is remembered without membership rules");
            var state=level.getBlockState(reserve);level.setBlock(reserve,Blocks.AIR.defaultBlockState(),3);level.setBlock(reserve,state,3);check(NaturalCampService.publicStorage(level,reserve),"replacement chest at original reserve position is recognized");
            check(NaturalCampService.mealPoints(first).size()==1,"small camp retains its campfire gathering place");
        }
        for(String name:List.of("hyena_camp_tropical","hyena_camp_open","hyena_camp_snowy","oasis","muddy_campsite")){
            var residents=place(name,Rotation.NONE);level.setDayTime(14000);level.updateSkyBrightness();
            var supplies=NaturalCampService.sites(residents.get(0)).storage();
            check(supplies.size()==1&&CampMembershipService.privateStorage(residents.get(0),supplies.get(0))&&CampMembershipService.usableStorage(residents.get(0),supplies.get(0)),"all five small camps retain private, resident-usable stores");
            outfitRolls(residents.get(0));
            var selected=new HashSet<BlockPos>();var goals=new ArrayList<NaturalCampSleepGoal>();
            for(var mob:residents){
                var goal=new NaturalCampSleepGoal(mob);boolean usable=goal.canUse();
                if(!usable){System.out.println("BED_DEBUG "+name+" type="+mob.getType()+" pos="+mob.position()+" sleepTime="+PlayerOutpostRestGoal.sleepTime(mob));for(var p0:NaturalCampService.sites(mob).beds()){var path=NaturalCampService.path(mob,p0,1);System.out.println("BED_ROUTE "+p0+" free="+PlayerOutpostRestGoal.free(mob,level,p0)+" reachable="+(path!=null&&path.canReach()));}}
                check(usable,"each "+name+" "+mob.getType()+" resident can reach a free bed from its authored position");goal.start();
                var field=NaturalCampSleepGoal.class.getDeclaredField("bed");field.setAccessible(true);var bed=(BlockPos)field.get(goal);
                check(bed!=null&&selected.add(bed),"each "+name+" resident reserves a distinct bed");goals.add(goal);
            }
            check(selected.size()==NaturalCampService.sites(residents.get(0)).beds().size(),"all "+name+" beds are allocated exactly once");goals.forEach(NaturalCampSleepGoal::stop);
        }
        var researcher=place("green_lab",Rotation.NONE).stream().filter(StoryNpcService::isNpc).findFirst().orElseThrow();
        check(!NaturalCampService.member(researcher)&&!CampLifeService.mainCamp(researcher),"researcher does not become a recruiting community");
        check(researcher.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal() instanceof NaturalCampSleepGoal)&&researcher.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof NaturalCampGoal),"researcher gains sleep but no harvesting or hunting job");
        var beds=NaturalCampService.sites(researcher).beds();check(beds.size()==1&&NaturalCampService.sites(researcher).storage().isEmpty(),"researcher recognizes only its attic bed and no loot stores");
        var bed=beds.get(0);var bedPath=NaturalCampService.path(researcher,bed,1);check(bedPath!=null&&bedPath.canReach(),"researcher can reach attic bed from authored home");level.setDayTime(14000);level.updateSkyBrightness();researcher.setPos(bed.getX()+.5,bed.getY(),bed.getZ()+.5);var sleep=new NaturalCampSleepGoal(researcher);check(sleep.canUse(),"researcher can select attic bed at night");sleep.start();sleep.tick();check(researcher.isSleeping(),"researcher uses native sleeping pose");level.setDayTime(1000);level.updateSkyBrightness();check(!sleep.canContinueToUse(),"researcher wakes in daytime");sleep.stop();
        var chair=NaturalCampService.restBounds(researcher);BlockPos seat=null;
        for(var pos:BlockPos.betweenClosed(BlockPos.containing(chair.minX,chair.minY,chair.minZ),BlockPos.containing(chair.maxX-1,chair.maxY-1,chair.maxZ-1)))if(level.getBlockState(pos).getBlock() instanceof net.ltxprogrammer.changed.block.OfficeChair&&level.getBlockState(pos).getValue(net.ltxprogrammer.changed.block.OfficeChair.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER){seat=pos.immutable();break;}
        check(seat!=null,"attic chair remains available");researcher.setPos(seat.getX()+.5,seat.getY(),seat.getZ()+.5);
        var own=net.parkabird.changedsynergy.world.inventory.BondedInventoryService.inventory(researcher,null);own.setItem(0,new ItemStack(Items.BREAD,3));own.setChanged();
        var comfort=new CreatureComfortGoal(researcher);var field=CreatureComfortGoal.class.getDeclaredField("target");field.setAccessible(true);field.set(comfort,seat);var begin=CreatureComfortGoal.class.getDeclaredMethod("beginRest");begin.setAccessible(true);begin.invoke(comfort);
        check(researcher.isPassenger()&&CompanionAutoEatGoal.active(researcher)&&net.parkabird.changedsynergy.world.inventory.BondedInventoryService.inventory(researcher,null).getItem(0).getCount()==2,"seated researcher eats one item from its own inventory");comfort.stop();
        var returned=net.parkabird.changedsynergy.world.inventory.BondedInventoryService.inventory(researcher,null);int bread=0;for(int i=0;i<returned.getContainerSize();i++)if(returned.getItem(i).is(Items.BREAD))bread+=returned.getItem(i).getCount();
        check(!CompanionAutoEatGoal.active(researcher)&&bread==3,"interrupted seated meal returns the single uneaten item without duplication");
        maps();System.out.println("DESERT_COMMUNITY_SERVER_PASS: "+checks+" rotations, household dialogue, admission, meals, independent camps, loot isolation, repair and researcher rest checks");
    }
    private static void outfitRolls(ChangedEntity source){
        var template=source.getPersistentData().getCompound(StructureResidentGoal.DATA);var seeds=new Random(76543);int dressed=0;
        String garment=template.getString("Template").startsWith("hyena")?"changed_synergy:hyena_trailcloth_outfit":"changed_synergy:desert_nomad_outfit";
        for(int i=0;i<128;i++){
            var mob=(ChangedEntity)source.getType().create(level);var data=template.copy();data.putString("Outfit",garment);data.putFloat("OutfitChance",.25F);data.remove("OutfitRolled");data.remove("OutfitApplied");
            mob.getPersistentData().put(StructureResidentGoal.DATA,data);mob.getRandom().setSeed(seeds.nextLong());StructureResidentGoal.install(mob);
            boolean wears=net.ltxprogrammer.changed.data.AccessorySlots.isWearing(mob,s->s.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(garment))));
            check(data.getBoolean("OutfitRolled")&&wears==!data.getString("Outfit").isEmpty(),"single outfit roll and native equipment agree");if(wears)dressed++;
            StructureResidentGoal.install(mob);check(wears==net.ltxprogrammer.changed.data.AccessorySlots.isWearing(mob,s->s.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(garment)))),"installing twice never rerolls outfit");mob.discard();
        }
        check(dressed>=20&&dressed<=44,"fixed-seed 25% clothing distribution: "+dressed+"/128");
    }
    private static void maps(){
        var gson=net.minecraft.world.level.storage.loot.Deserializers.createFunctionSerializer().create();var fn=net.parkabird.changedsynergy.world.NearbyObservatoryMapFunction.create("desert_refuge").build();
        var json=gson.toJson(fn,net.minecraft.world.level.storage.loot.functions.LootItemFunction.class);check(json.contains("desert_refuge"),"destination survives loot serialization");
        var restored=gson.fromJson(json,net.minecraft.world.level.storage.loot.functions.LootItemFunction.class);var params=new LootParams.Builder(server.getLevel(net.minecraft.world.level.Level.NETHER)).withParameter(LootContextParams.ORIGIN,Vec3.ZERO).create(LootContextParamSets.CHEST);
        check(restored.apply(new ItemStack(Items.MAP),new LootContext.Builder(params).create(null)).isEmpty(),"missing destination produces no blank map");
        for(String name:List.of("chests/desert_camp_oasis","chests/desert_camp_muddy"))check(server.getLootData().getLootTable(id(name)).getPool("changed_synergy_desert_refuge_map")!=null,"camp explorer pool registered");
        check(server.getLootData().getLootTable(ResourceLocation.parse("minecraft:chests/desert_pyramid")).getPool("changed_synergy_desert_refuge_map")!=null,"desert pyramid explorer pool registered");
        check(server.getLootData().getLootTable(ResourceLocation.parse("minecraft:chests/village/village_cartographer")).getPool("changed_synergy_desert_refuge_map")==null,"nested supply table cannot roll another refuge map");
    }
    private static List<ChangedEntity> place(String name,Rotation rotation)throws Exception{
        var template=server.getStructureManager().getOrCreate(id(name));var origin=new BlockPos(10000+index++*100,100,10000);var settings=new StructurePlaceSettings().setRotation(rotation);var box=template.getBoundingBox(settings,origin);
        var field=Arrays.stream(ServerLevel.class.getDeclaredFields()).filter(f->net.minecraft.world.level.entity.PersistentEntitySectionManager.class.isAssignableFrom(f.getType())).findFirst().orElseThrow();field.setAccessible(true);var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)field.get(level);
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++)for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++){var p=new BlockPos(x*16,100,z*16);level.getChunk(p);level.setChunkForced(x,z,true);manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(x,z),net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING);manager.tick();}
        check(template.placeInWorld(level,origin,origin,settings,net.minecraft.util.RandomSource.create(64),2),"template placement: "+name);
        var result=level.getEntitiesOfClass(ChangedEntity.class,AABB.of(box),m->!m.getPersistentData().getCompound(StructureResidentGoal.DATA).isEmpty());result.forEach(m->m.setOnGround(true));return result;
    }
    private static ServerPlayer player(ChangedEntity mob){var p=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"DesertCommunity"));p.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),p);p.setPos(mob.getX(),mob.getY(),mob.getZ());var identity=new net.parkabird.changedsynergy.origin.NativeOriginData.Identity();identity.state="human";net.parkabird.changedsynergy.origin.NativeOriginData.get(server).players.put(p.getUUID(),identity);return p;}
    private static Set<String> choices(CompoundTag state){var result=new HashSet<String>();for(var t:state.getList("Choices",10))result.add(((CompoundTag)t).getString("Id"));return result;}
    private static void choose(ServerPlayer p,String id){
        for(int i=0;i<50;i++){var state=NpcConversations.state(p);if(state.getBoolean("Last")){check(choices(state).contains(id),"visible dialogue option "+id);NpcConversations.action(p,state.getUUID("Session"),state.getInt("Revision"),id);return;}NpcConversations.action(p,state.getUUID("Session"),state.getInt("Revision"),"advance");}
        throw new AssertionError("Dialogue never completed");
    }
}
