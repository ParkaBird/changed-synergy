package net.parkabird.changedsynergy.npc;

import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;

/** Real supplies and bounded requests; no virtual currency or offline upkeep. */
public final class CampLifeService {
    public enum Supply {
        FOOD(4), WOOL(4), LEATHER(4), PAPER(8), LIGHT(8);
        public final int amount;public final TagKey<Item> tag;
        Supply(int amount){this.amount=amount;tag=TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("changed_synergy","camp_"+name().toLowerCase(Locale.ROOT)));}
        public String label(){return "camp.changed_synergy.supply."+name().toLowerCase(Locale.ROOT);}
        public boolean accepts(ItemStack stack,ChangedEntity resident){
            if(stack.isEmpty()||!stack.is(tag)||stack.hasCustomHoverName()||StoryNpcService.bound(stack))return false;
            if(this!=FOOD)return true;
            var food=net.parkabird.changedsynergy.entity.SpottedHyenaDiet.foodProperties(stack,resident);
            return food!=null&&food.getNutrition()>0&&food.getEffects().stream().noneMatch(p->p.getFirst()!=null&&!p.getFirst().getEffect().isBeneficial());
        }
    }
    public record Stock(String home,Container inventory) {}
    public record Taken(String home,String item) {}
    private CampLifeService(){}
    public static String template(ChangedEntity npc){return npc.getPersistentData().getCompound(StructureResidentGoal.DATA).getString("Template");}
    public static boolean mainCamp(ChangedEntity npc){return NaturalCampService.member(npc)&&npc.getPersistentData().getCompound(StructureResidentGoal.DATA).getBoolean("Resolved")&&Set.of("forest_treehouse","hyena_hideout","observatory","desert_refuge").contains(template(npc));}
    public static String voice(ChangedEntity npc){return switch(template(npc)){case "forest_treehouse"->"weaver";case "hyena_hideout"->"matriarch";case "observatory"->"archivist";case "desert_refuge"->"pathfinder";default->"resident";};}
    public static NpcDefinitions.Profile profile(ChangedEntity npc){return mainCamp(npc)?NpcDefinitions.all().stream().filter(p->p.chief()&&p.dialogue()&&p.template().equals(template(npc))).findFirst().orElse(null):null;}
    public static CompoundTag register(ChangedEntity npc){
        if(!NaturalCampService.publicReserveOnly(npc)||!(npc.level() instanceof ServerLevel level))return new CompoundTag();
        var store=CampLifeData.get(level.getServer());String key=StoryNpcService.homeKey(npc);var tag=store.camps.get(key);
        if(tag==null){tag=new CompoundTag();var resident=npc.getPersistentData().getCompound(StructureResidentGoal.DATA);for(String field:List.of("MinX","MinY","MinZ","MaxX","MaxY","MaxZ"))tag.putDouble(field,resident.getDouble(field));tag.putString("Template",template(npc));tag.putString("Key",key);var fixed=StoryNpcData.get(level.getServer()).records.get(StoryNpcService.key(npc));tag.putString("Dimension",fixed==null?level.dimension().location().toString():fixed.getString("Dimension"));tag.put("Requests",new ListTag());store.camps.put(key,tag);store.setDirty();}
        var resident=npc.getPersistentData().getCompound(StructureResidentGoal.DATA);String dimension=resident.getString("Dimension");
        tag.putBoolean("RoutinesOnly",!mainCamp(npc));
        if(Set.of("desert_refuge","oasis","muddy_campsite","hyena_camp_tropical","hyena_camp_open","hyena_camp_snowy").contains(template(npc))&&tag.getInt("StorageLayout")<2){
            var positions=NaturalCampService.authoredPublicStores(npc);
            if(!positions.isEmpty()){tag.putLongArray("PublicStores",positions.stream().mapToLong(BlockPos::asLong).toArray());tag.remove("Stores");tag.putInt("StorageLayout",2);store.setDirty();}
        }
        if(!dimension.isEmpty()&&!dimension.equals(tag.getString("Dimension"))){tag.putString("Dimension",dimension);store.setDirty();}
        return tag;
    }
    public static boolean atHome(ChangedEntity npc){if(!mainCamp(npc))return false;var camp=register(npc);return camp.getString("Dimension").equals(npc.level().dimension().location().toString())&&NaturalCampService.bounds(npc).contains(npc.position());}
    public static List<ChangedEntity> residents(ChangedEntity npc){return npc.level().getEntitiesOfClass(ChangedEntity.class,NaturalCampService.bounds(npc),m->m.isAlive()&&mainCamp(m)&&StoryNpcService.homeKey(m).equals(StoryNpcService.homeKey(npc)));}
    public static List<Stock> stores(ChangedEntity npc){
        if(!(npc.level() instanceof ServerLevel level)||!atHome(npc))return List.of();
        var result=new ArrayList<Stock>();var seen=Collections.newSetFromMap(new IdentityHashMap<Container,Boolean>());var positions=new HashSet<Long>();
        for(var pos:NaturalCampService.sites(npc).storage()){
            if(!CampMembershipService.usableStorage(npc,pos))continue;
            var state=level.getBlockState(pos);long canonical=pos.asLong();if(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock&&state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE)canonical=Math.min(canonical,pos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state)).asLong());if(!positions.add(canonical))continue;
            var container=PlayerOutpostData.container(level,pos);if(container==null)continue;
            if(result.stream().anyMatch(s->sameContainer(s.inventory(),container)))continue;
            if(seen.add(container))result.add(new Stock(StoryNpcService.homeKey(npc),container));
        }
        return result;
    }
    private static boolean sameContainer(Container a,Container b){return a==b||a instanceof CompoundContainer left&&left.contains(b)||b instanceof CompoundContainer right&&right.contains(a);}
    public static int count(ChangedEntity npc,Supply supply){int count=0;for(var store:stores(npc))for(int i=0;i<store.inventory().getContainerSize();i++){var item=store.inventory().getItem(i);if(supply.accepts(item,npc))count+=item.getCount();}return count;}
    public static int population(ChangedEntity npc){return Math.max(1,residents(npc).size());}
    public static String foodState(ChangedEntity npc){int food=count(npc,Supply.FOOD),people=population(npc);return food<people*2?"low":food>=people*6?"plenty":"enough";}
    public static boolean safe(ChangedEntity npc){return residents(npc).stream().noneMatch(m->m.getTarget()!=null&&m.getTarget().isAlive()&&!(m.getTarget() instanceof net.minecraft.world.entity.animal.Animal));}
    public static long day(ChangedEntity npc){return npc.level().getServer().overworld().getGameTime()/24000;}
    public static boolean personal(CompoundTag request){return request.getString("Scope").equals("PERSONAL");}
    private static boolean ownsRequest(ChangedEntity npc,CompoundTag request){return request.hasUUID("Owner")&&request.getUUID("Owner").equals(npc.getUUID())||!request.getString("OwnerKey").isEmpty()&&request.getString("OwnerKey").equals(StoryNpcService.key(npc));}
    private static Container belongings(ChangedEntity npc){return net.parkabird.changedsynergy.world.inventory.BondedInventoryService.inventory(npc,LatexSocialMemory.getPetOwner(npc));}
    public static ListTag requests(ChangedEntity npc){
        var camp=register(npc);var requests=camp.getList("Requests",10);
        if(camp.isEmpty()||!atHome(npc))return requests;
        // Pending requests never expire; only completed slots can be refreshed on a later active day.
        if(!camp.contains("RequestDay")||camp.getLong("RequestDay")!=day(npc)){
            requests.removeIf(t->((CompoundTag)t).getBoolean("Done"));camp.putLong("RequestDay",day(npc));
            var wanted=new ArrayList<String>();if(foodState(npc).equals("low"))wanted.add("FOOD");
            wanted.add(switch(template(npc)){case "forest_treehouse"->"WOOL";case "hyena_hideout"->"LEATHER";case "desert_refuge"->"LIGHT";default->"PAPER";});
            wanted.add("WATCH");
            for(String kind:wanted){if(requests.stream().map(CompoundTag.class::cast).filter(r->!personal(r)).count()>=3)break;if(requests.stream().map(CompoundTag.class::cast).anyMatch(r->!personal(r)&&r.getString("Kind").equals(kind)))continue;
                if(!kind.equals("WATCH")&&count(npc,Supply.valueOf(kind))>=Supply.valueOf(kind).amount*2)continue;
                var request=new CompoundTag();request.putString("Id",UUID.randomUUID().toString());request.putString("Kind",kind);request.putString("Scope","PUBLIC");request.putInt("Count",kind.equals("WATCH")?1:Supply.valueOf(kind).amount);requests.add(request);
            }
            camp.put("Requests",requests);CampLifeData.get(npc.level().getServer()).setDirty();
        }
        for(var resident:residents(npc)){
            if(StoryNpcService.isNpc(resident)||requests.stream().map(CompoundTag.class::cast).anyMatch(r->personal(r)&&ownsRequest(resident,r)))continue;
            var inventory=belongings(resident);int food=0;for(int slot=0;slot<Math.min(24,inventory.getContainerSize());slot++)if(Supply.FOOD.accepts(inventory.getItem(slot),resident))food+=inventory.getItem(slot).getCount();
            if(food>=2)continue;
            var request=new CompoundTag();request.putString("Id",UUID.randomUUID().toString());request.putString("Kind","FOOD");request.putString("Scope","PERSONAL");request.putUUID("Owner",resident.getUUID());request.putInt("Count",2-food);requests.add(request);camp.put("Requests",requests);CampLifeData.get(npc.level().getServer()).setDirty();
        }
        return requests;
    }
    public static List<CompoundTag> visibleRequests(ChangedEntity npc){return requests(npc).stream().map(CompoundTag.class::cast).filter(r->!r.getBoolean("Done")&&(CampDialogue.chief(npc)?!personal(r):personal(r)&&ownsRequest(npc,r))).toList();}
    public static CompoundTag request(ChangedEntity npc,String id){return requests(npc).stream().map(CompoundTag.class::cast).filter(t->t.getString("Id").equals(id)&&!t.getBoolean("Done")).findFirst().orElse(null);}
    public static CompoundTag forResident(ChangedEntity npc){return visibleRequests(npc).stream().filter(CampLifeService::personal).findFirst().orElse(null);}
    public static Supply supply(ItemStack stack,ChangedEntity npc){return Arrays.stream(Supply.values()).filter(s->s.accepts(stack,npc)).findFirst().orElse(null);}
    public static boolean canHelp(ChangedEntity npc,ServerPlayer player){var body=TrueTransfurCompat.interactionBody(player);return atHome(npc)&&body.level()==npc.level()&&NaturalCampService.bounds(npc).contains(body.position())&&CampMembershipService.score(npc,player)>=0&&!LatexSocialMemory.isProvoked(npc,player)&&!LatexSocialMemory.hasRelationshipBetrayal(npc,player);}
    private static CompoundTag account(String home,ServerPlayer player){var data=CampMembershipService.membership(player);var all=data.getCompound("Contributions");var account=all.getCompound(home);all.put(home,account);data.put("Contributions",all);return account;}
    private static CompoundTag account(ChangedEntity npc,ServerPlayer player){return account(StoryNpcService.homeKey(npc),player);}
    public static int reward(String home,ServerPlayer player,int amount){
        if(home==null||home.isEmpty()||net.parkabird.changedsynergy.init.ChangedSynergyGameRules.noCivilizationMode(player.level()))return 0;
        var data=CampMembershipService.membership(player);
        if(data.getCompound("LocalTrust").getInt(home)<0)return 0;
        var record=account(home,player);long day=player.serverLevel().getDayTime()/24000L;
        if(!record.contains("Day")||record.getLong("Day")!=day){record.putLong("Day",day);record.putInt("Earned",0);}
        int granted=Math.max(0,Math.min(amount,8-record.getInt("Earned")));
        if(granted<=0)return 0;
        record.putInt("Earned",record.getInt("Earned")+granted);
        CampMembershipService.adjust(home,player,granted);record.putLong("LastHelp",player.level().getGameTime());CampMembershipData.get(player.server).setDirty();return granted;
    }
    public static int reward(ChangedEntity npc,ServerPlayer player,int amount){
        if(!canHelp(npc,player))return 0;
        return reward(StoryNpcService.homeKey(npc),player,amount);
    }
    private static int returned(ChangedEntity npc,ServerPlayer player,ItemStack stack,int amount){var debt=account(npc,player).getCompound("Withdrawn");String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();int used=Math.min(amount,debt.getInt(id));debt.putInt(id,Math.max(0,debt.getInt(id)-used));account(npc,player).put("Withdrawn",debt);return amount-used;}
    public static boolean deliver(ChangedEntity npc,ServerPlayer player,ItemStack expected,int amount,String requestId){
        if(!canHelp(npc,player)||amount<1||amount>64)return false;var held=player.getMainHandItem();
        if(!ItemStack.isSameItemSameTags(held,expected)||held.getCount()<amount)return false;
        var kind=supply(held,npc);if(kind==null)return false;var request=requestId.isEmpty()?null:request(npc,requestId);
        if(!requestId.isEmpty()&&(request==null||!request.getString("Kind").equals(kind.name())||request.getInt("Count")!=amount))return false;
        if(request!=null&&!personal(request)&&!CampDialogue.chief(npc))return false;
        if(request!=null&&personal(request)&&(!ownsRequest(npc,request)||StoryNpcService.isNpc(npc)))return false;
        if(request==null&&count(npc,kind)>=Math.max(kind.amount*2,population(npc)*2))return false;
        var gift=held.copyWithCount(amount);Container destination;
        if(request!=null&&personal(request)){destination=belongings(npc);if(!PlayerOutpostGoal.acceptsAll(destination,gift))return false;}
        else{var reserve=stores(npc).stream().filter(s->PlayerOutpostGoal.acceptsAll(s.inventory(),gift)).findFirst().orElse(null);if(reserve==null)return false;destination=reserve.inventory();}
        var remaining=PlayerOutpostGoal.insert(destination,gift);int moved=amount-remaining.getCount();
        if(moved<=0)return false;held.shrink(moved);player.getInventory().setChanged();player.containerMenu.broadcastChanges();
        int fresh=returned(npc,player,expected,moved);
        // A capability that changes its mind after simulation must never duplicate player items.
        if(!remaining.isEmpty()){if(request!=null){request.putInt("Count",remaining.getCount());CampLifeData.get(player.server).setDirty();}CampMembershipData.get(player.server).setDirty();return false;}
        if(fresh>0)reward(npc,player,request==null?1:2);
        if(request!=null){request.putBoolean("Done",true);request.putUUID("CompletedBy",player.getUUID());rewardOwner(npc,player,request);CampLifeData.get(player.server).setDirty();}
        account(npc,player).putLong("LastHelp",npc.level().getGameTime());CampMembershipData.get(player.server).setDirty();return true;
    }
    public static boolean canClaimOutfit(ChangedEntity npc,ServerPlayer player){return CampDialogue.chief(npc)&&canHelp(npc,player)&&CampMembershipService.protectedMember(npc,player)&&CampMembershipService.score(npc,player)>=40&&!CampMembershipService.membership(player).getCompound("OutfitClaims").getBoolean(StoryNpcService.homeKey(npc));}
    public static boolean claimOutfit(ChangedEntity npc,ServerPlayer player){
        if(!canClaimOutfit(npc,player))return false;var profile=StoryNpcService.profile(npc);if(profile.equipment().isEmpty())return false;
        var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(profile.equipment().get(0)));var outfit=new ItemStack(item);if(outfit.isEmpty()||!player.addItem(outfit))return false;
        var data=CampMembershipService.membership(player);var claims=data.getCompound("OutfitClaims");claims.putBoolean(StoryNpcService.homeKey(npc),true);data.put("OutfitClaims",claims);CampMembershipData.get(player.server).setDirty();return true;
    }
    public static boolean defended(ChangedEntity resident,ServerPlayer player){
        if(!mainCamp(resident))return false;if(!canHelp(resident,player))return true;
        reward(resident,player,2);for(var value:requests(resident)){var request=(CompoundTag)value;if(!request.getBoolean("Done")&&request.getString("Kind").equals("WATCH")){var evidence=request.getCompound("Evidence");evidence.putBoolean(player.getUUID().toString(),true);request.put("Evidence",evidence);}}
        CampLifeData.get(player.server).setDirty();return true;
    }
    public static boolean finishWatch(ChangedEntity npc,ServerPlayer player,String id){var r=request(npc,id);if(!canHelp(npc,player)||!safe(npc)||r==null||!r.getString("Kind").equals("WATCH")||!r.getCompound("Evidence").getBoolean(player.getUUID().toString()))return false;r.putBoolean("Done",true);r.putUUID("CompletedBy",player.getUUID());rewardOwner(npc,player,r);CampLifeData.get(player.server).setDirty();return true;}
    private static void rewardOwner(ChangedEntity npc,ServerPlayer player,CompoundTag request){
        var fixed=StoryNpcData.get(player.server).records.get(request.getString("OwnerKey"));var owner=fixed==null?request.hasUUID("Owner")?((ServerLevel)npc.level()).getEntity(request.getUUID("Owner")):null:StoryNpcService.actor(player.server,fixed);
        CreaturePersonality.rewardCare(owner instanceof ChangedEntity resident&&resident.isAlive()&&StoryNpcService.homeKey(resident).equals(StoryNpcService.homeKey(npc))?resident:npc,player,2);
    }
    public static Map<Taken,Integer> snapshot(AbstractContainerMenu menu,ServerPlayer player){
        var result=new HashMap<Taken,Integer>();var known=new IdentityHashMap<Object,String>();
        var level=TrueTransfurCompat.interactionBody(player).level() instanceof ServerLevel bodyLevel?bodyLevel:player.serverLevel();
        for(var slot:menu.slots){if(slot.container==player.getInventory())continue;
            var handler=slot instanceof net.minecraftforge.items.SlotItemHandler itemSlot?itemSlot.getItemHandler():null;
            String home=known.computeIfAbsent(handler==null?slot.container:handler,c->containerHome(level,slot.container,handler));if(home.isEmpty()||slot.getItem().isEmpty())continue;
            result.merge(new Taken(home,BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString()),slot.getItem().getCount(),Integer::sum);
        }return result;
    }
    private static String containerHome(ServerLevel level,Container container,net.minecraftforge.items.IItemHandler handler){
        if(handler==null&&!(container instanceof BlockEntity)&&!(container instanceof CompoundContainer))return "";
        for(var camp:CampLifeData.get(level.getServer()).camps.values()){
            if(!camp.getString("Dimension").equals(level.dimension().location().toString()))continue;
            if(container instanceof BlockEntity be){var p=be.getBlockPos();if(inside(camp,p)&&NaturalCampService.publicStorage(level,p)&&!be.saveWithFullMetadata().contains("LootTable"))return camp.getString("Key");}
            if(container instanceof CompoundContainer pair){for(long packed:camp.getLongArray("Stores")){var p=BlockPos.of(packed);if(NaturalCampService.publicStorage(level,p)&&level.getBlockEntity(p) instanceof Container part&&pair.contains(part))return camp.getString("Key");}}
            if(handler!=null){for(long packed:camp.getLongArray("Stores")){var p=BlockPos.of(packed);if(!NaturalCampService.publicStorage(level,p))continue;var be=level.getBlockEntity(p);if(be!=null&&be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).filter(h->h==handler).isPresent())return camp.getString("Key");}}
        }return "";
    }
    public static boolean inside(CompoundTag camp,BlockPos pos){return new AABB(camp.getDouble("MinX"),camp.getDouble("MinY"),camp.getDouble("MinZ"),camp.getDouble("MaxX"),camp.getDouble("MaxY"),camp.getDouble("MaxZ")).contains(Vec3.atCenterOf(pos));}
    public static boolean canSleep(ServerPlayer player,BlockPos pos){
        for(var camp:CampLifeData.get(player.server).camps.values())if(!camp.getBoolean("RoutinesOnly")&&camp.getString("Dimension").equals(TrueTransfurCompat.interactionBody(player).level().dimension().location().toString())&&inside(camp,pos)){
            var data=CampMembershipService.membership(player);String home=camp.getString("Key");int trust=data.getCompound("LocalTrust").getInt(home);
            if(home.equals(data.getString("HomeKey")))return !data.getBoolean("Suspended")&&trust>CampMembershipService.DISTRUSTED;
            return trust>=40;
        }return true;
    }
    public static void withdrawn(ServerPlayer player,Map<Taken,Integer> before,Map<Taken,Integer> after){
        if(before.isEmpty()&&after.isEmpty())return;
        var data=CampMembershipService.membership(player);var all=data.getCompound("Contributions");
        var keys=new HashSet<>(before.keySet());keys.addAll(after.keySet());
        int totalRewarded = 0;
        for(var key:keys){
            int amount=before.getOrDefault(key,0)-after.getOrDefault(key,0);
            if(amount==0)continue;
            var account=all.getCompound(key.home());
            var debt=account.getCompound("Withdrawn");
            if(amount>0){
                debt.putInt(key.item(),(int)Math.max(0,Math.min(Integer.MAX_VALUE,(long)debt.getInt(key.item())+amount)));
            }else{
                int deposit=-amount;
                int owed=debt.getInt(key.item());
                int paid=Math.min(deposit,owed);
                debt.putInt(key.item(),owed-paid);
                int fresh=deposit-paid;
                if(fresh>0){
                    var item=BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(key.item()));
                    if(item!=null&&item.isEdible()){
                        totalRewarded+=reward(key.home(),player,fresh);
                    }
                }
            }
            account.put("Withdrawn",debt);all.put(key.home(),account);
        }
        data.put("Contributions",all);CampMembershipData.get(player.server).setDirty();
        if(totalRewarded>0){
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.changed_synergy.camp.trust_deposit",totalRewarded),true);
        }
    }
    public static void rememberStores(ChangedEntity npc){
        var camp=register(npc);if(camp.isEmpty()||!camp.getString("Dimension").equals(npc.level().dimension().location().toString()))return;
        long[] positions=NaturalCampService.sites(npc).storage().stream().filter(p->NaturalCampService.publicStorage((ServerLevel)npc.level(),p)).mapToLong(BlockPos::asLong).toArray();
        var known=new LinkedHashSet<Long>();for(long p:camp.getLongArray("PublicStores"))known.add(p);for(long p:positions)known.add(p);
        long[] permanent=known.stream().mapToLong(Long::longValue).toArray();
        if(!Arrays.equals(positions,camp.getLongArray("Stores"))||!Arrays.equals(permanent,camp.getLongArray("PublicStores"))){camp.putLongArray("Stores",positions);camp.putLongArray("PublicStores",permanent);CampLifeData.get(npc.level().getServer()).setDirty();}
    }
    public static void opened(ServerLevel level,BlockPos pos){
        if(!NaturalCampService.publicStorage(level,pos))return;
        for(var camp:CampLifeData.get(level.getServer()).camps.values())if(camp.getString("Dimension").equals(level.dimension().location().toString())&&inside(camp,pos)){
            long packed=pos.asLong();if(Arrays.stream(camp.getLongArray("Stores")).noneMatch(p->p==packed)){var old=camp.getLongArray("Stores");var updated=Arrays.copyOf(old,old.length+1);updated[old.length]=packed;camp.putLongArray("Stores",updated);CampLifeData.get(level.getServer()).setDirty();}
        }
    }
}
