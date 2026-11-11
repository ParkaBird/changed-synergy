package net.parkabird.changedsynergy.npc;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;

/** One home household; trust and admission rewards belong to each individual camp. */
public final class CampMembershipService {
    public static final int DISTRUSTED=-15, HOSTILE=-40;
    public static CompoundTag membership(ServerPlayer player) {
        var store=CampMembershipData.get(player.server);var data=store.player(player.getUUID());
        if(data.getInt("MembershipVersion")<3) {
            String home=data.getString("HomeKey"),faction=data.getString("Faction");
            if(!home.isEmpty()&&!faction.isEmpty()) {
                var trust=data.getCompound("LocalTrust");if(!trust.contains(home))trust.putInt(home,0);data.put("LocalTrust",trust);
                if(data.getList("PermanentRewards",Tag.TAG_STRING).contains(StringTag.valueOf(faction))) {
                    var rewards=data.getCompound("AdmissionRewards");rewards.putInt(home,Math.max(8,rewards.getInt(home)));data.put("AdmissionRewards",rewards);
                }
            }
            data.remove("Faction");data.remove("PermanentRewards");data.putInt("MembershipVersion",3);store.setDirty();
        }
        return data;
    }
    public static int score(ChangedEntity npc,ServerPlayer player) {if(net.parkabird.changedsynergy.init.ChangedSynergyGameRules.noCivilizationMode(player.level()))return 0;return membership(player).getCompound("LocalTrust").getInt(StoryNpcService.homeKey(npc));}
    public static FactionReputation.Standing standing(ChangedEntity npc,ServerPlayer player){int value=score(npc,player);return value<=HOSTILE?FactionReputation.Standing.HOSTILE:value<=DISTRUSTED?FactionReputation.Standing.DISTRUSTED:value>=70?FactionReputation.Standing.ALLIED:value>=40?FactionReputation.Standing.RESPECTED:value>=20?FactionReputation.Standing.RECOGNIZED:FactionReputation.Standing.NEUTRAL;}
    public static boolean canJoin(ChangedEntity npc,ServerPlayer player) {
        var profile=StoryNpcService.profile(npc);
        return profile!=null&&profile.chief()&&profile.dialogue()&&CampLifeService.atHome(npc)&&score(npc,player)>=0
                &&npc.getTarget()==null&&!LatexSocialMemory.isProvoked(npc,player)&&!LatexSocialMemory.hasRelationshipBetrayal(npc,player);
    }
    public static boolean join(ChangedEntity npc,ServerPlayer player,String mode) {
        if(!canJoin(npc,player)||!java.util.Set.of("join","reversible","permanent").contains(mode))return false;
        int reward=mode.equals("permanent")?8:mode.equals("reversible")?5:
                ProcessTransfur.isPlayerTransfurred(player)||TrueTransfurCompat.interactionBody(player)!=player?2:0;
        if(!mode.equals("join")&&!NpcRites.apply(npc,player,mode.equals("permanent")))return false;
        var store=CampMembershipData.get(player.server);var data=membership(player);String home=StoryNpcService.homeKey(npc);
        data.putString("HomeKey",home);data.putString("ChiefKey",StoryNpcService.key(npc));data.putBoolean("Suspended",false);
        data.putString("Dimension",npc.level().dimension().location().toString());data.put("Bounds",boundsTag(NaturalCampService.bounds(npc)));
        var rewards=data.getCompound("AdmissionRewards");int previous=rewards.getInt(home);
        if(reward>previous){rewards.putInt(home,reward);data.put("AdmissionRewards",rewards);adjust(npc,player,reward-previous);}
        for(var resident:npc.level().getEntitiesOfClass(ChangedEntity.class,NaturalCampService.activityBounds(npc),
                r->r.isAlive()&&CampLifeService.mainCamp(r)&&StoryNpcService.homeKey(r).equals(home)))CreaturePersonality.introduceCampMember(resident,player);
        store.setDirty();return true;
    }
    public static void introduceLoadedResident(ChangedEntity resident) {
        if(!CampLifeService.mainCamp(resident))return;
        for(var player:resident.level().getServer().getPlayerList().getPlayers())if(protectedMember(resident,player))CreaturePersonality.introduceCampMember(resident,player);
    }
    /** Local actions affect this household; diplomacy and unrelated camps cannot alter membership. */
    public static void adjust(ChangedEntity npc,ServerPlayer player,int amount) {
        if(NaturalCampService.member(npc))adjust(StoryNpcService.homeKey(npc),player,amount);
    }
    public static void adjust(String home,ServerPlayer player,int amount) {
        if(amount==0||home==null||home.isEmpty()||net.parkabird.changedsynergy.init.ChangedSynergyGameRules.noCivilizationMode(player.level()))return;
        var data=membership(player);var trust=data.getCompound("LocalTrust");
        int updated=Math.max(-100,Math.min(100,trust.getInt(home)+amount));trust.putInt(home,updated);data.put("LocalTrust",trust);
        if(home.equals(data.getString("HomeKey"))) {
            if(updated<=HOSTILE)leave(player);
            else if(updated<=DISTRUSTED)data.putBoolean("Suspended",true);
            else if(updated>=0)data.putBoolean("Suspended",false);
        }
        CampMembershipData.get(player.server).setDirty();
    }
    private static CompoundTag boundsTag(AABB bounds){var tag=new CompoundTag();tag.putDouble("MinX",bounds.minX);tag.putDouble("MinY",bounds.minY);tag.putDouble("MinZ",bounds.minZ);tag.putDouble("MaxX",bounds.maxX);tag.putDouble("MaxY",bounds.maxY);tag.putDouble("MaxZ",bounds.maxZ);return tag;}
    public static final String CHIEF_PEACE = "ChangedSynergyChiefPeace";
    public static final double CHIEF_PEACE_RADIUS_SQR = 100.0D;
    public static final long CHIEF_PEACE_DURATION = 300L;
    public static boolean member(ChangedEntity npc,ServerPlayer player){if(!NaturalCampService.member(npc))return false;var home=membership(player).getString("HomeKey");return !home.isEmpty()&&home.equals(StoryNpcService.homeKey(npc));}
    public static boolean protectedMember(ChangedEntity npc,ServerPlayer player){return member(npc,player)&&!membership(player).getBoolean("Suspended")
            &&!LatexSocialMemory.isProvoked(npc,player)&&!LatexSocialMemory.hasRelationshipBetrayal(npc,player);}
    public static boolean hasChiefPeace(ChangedEntity npc, ServerPlayer player) {
        if (!NaturalCampService.member(npc) || LatexSocialMemory.isProvoked(npc, player) || LatexSocialMemory.hasRelationshipBetrayal(npc, player)) {
            return false;
        }
        var data = player.getPersistentData().getCompound(CHIEF_PEACE);
        String home = StoryNpcService.homeKey(npc);
        return data.getLong(home) > player.level().getGameTime();
    }
    public static void refreshChiefPeace(ChangedEntity chief, ServerPlayer player) {
        if (!CampDialogue.chief(chief) || !chief.isAlive() || LatexSocialMemory.isProvoked(chief, player)) return;
        String home = StoryNpcService.homeKey(chief);
        var root = player.getPersistentData();
        if (!root.contains(CHIEF_PEACE, Tag.TAG_COMPOUND)) root.put(CHIEF_PEACE, new CompoundTag());
        root.getCompound(CHIEF_PEACE).putLong(home, player.level().getGameTime() + CHIEF_PEACE_DURATION);
    }
    public static void breakChiefPeace(ChangedEntity npc, ServerPlayer player) {
        if (!NaturalCampService.member(npc) || player == null) return;
        String home = StoryNpcService.homeKey(npc);
        var root = player.getPersistentData();
        if (root.contains(CHIEF_PEACE, Tag.TAG_COMPOUND)) {
            root.getCompound(CHIEF_PEACE).remove(home);
        }
        for (var chief : npc.level().getEntitiesOfClass(ChangedEntity.class, npc.getBoundingBox().inflate(32),
                c -> c.isAlive() && CampDialogue.chief(c) && StoryNpcService.homeKey(c).equals(home))) {
            LatexSocialMemory.markProvoked(chief, player);
        }
    }
    public static boolean sharedStorage(ChangedEntity npc,ServerPlayer player,BlockPos pos) {
        return protectedMember(npc,player)&&(!CampLifeService.mainCamp(npc)||CampLifeService.atHome(npc)&&usableStorage(npc,pos))&&NaturalCampService.ownsStorage(npc,pos)&&!privateStorage(npc,pos);
    }
    public static boolean privateStorage(ChangedEntity npc,BlockPos pos) {
        if(!npc.level().hasChunkAt(pos))return true;
        var be=npc.level().getBlockEntity(pos);if(be==null)return false;
        var container=PlayerOutpostData.container((net.minecraft.server.level.ServerLevel)npc.level(),pos);if(container==null)return true;
        if(privateContents(be))return true;
        var state=npc.level().getBlockState(pos);
        if(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                &&state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE){
            var mate=pos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state));if(!npc.level().hasChunkAt(mate))return true;
            var other=npc.level().getBlockEntity(mate);
            return other!=null&&privateContents(other);
        }
        return false;
    }
    public static boolean usableStorage(ChangedEntity npc,BlockPos pos) {
        if(!npc.level().hasChunkAt(pos))return false;
        if(NaturalCampService.publicReserveOnly(npc)&&!NaturalCampService.publicStorage((net.minecraft.server.level.ServerLevel)npc.level(),pos))return false;
        if(CampLifeService.mainCamp(npc)&&!CampLifeService.register(npc).getString("Dimension").equals(npc.level().dimension().location().toString()))return false;
        if(privateStorage(npc,pos)&&!(NaturalCampService.smallCamp(npc)&&NaturalCampService.ownsStorage(npc,pos)&&NaturalCampService.publicStorage((net.minecraft.server.level.ServerLevel)npc.level(),pos)))return false;
        var be=npc.level().getBlockEntity(pos);
        if(be==null||be.saveWithFullMetadata().contains("LootTable"))return false;
        var state=npc.level().getBlockState(pos);
        if(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock&&state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE){
            var mate=npc.level().getBlockEntity(pos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state)));
            if(mate==null||mate.saveWithFullMetadata().contains("LootTable"))return false;
        }
        return true;
    }
    private static boolean privateContents(net.minecraft.world.level.block.entity.BlockEntity be) {
        return be.getPersistentData().getBoolean("ChangedSynergyPrivate");
    }
    public static boolean readableStory(ChangedEntity npc,BlockPos pos) {
        if(!npc.level().hasChunkAt(pos))return false;
        var be=npc.level().getBlockEntity(pos);if(be==null)return false;
        if(be instanceof net.minecraft.world.level.block.entity.LecternBlockEntity)return true;
        if(privateStorage(npc,pos))return false;
        // Inspect authored text without unpacking any loot table.
        return !be.getPersistentData().getBoolean(NaturalCampService.PUBLIC_STORAGE)&&containsStory(be.saveWithFullMetadata());
    }
    private static boolean containsStory(Tag value) {
        if(value instanceof CompoundTag tag) {
            String id=tag.getString("id");if(tag.contains("Count")&&(id.contains("written_book")||id.contains("writable_book")||id.contains("note")||id.contains("floppy")||tag.getCompound("tag").contains("Password")))return true;
            for(String key:tag.getAllKeys())if(containsStory(tag.get(key)))return true;
        }else if(value instanceof ListTag list)for(var child:list)if(containsStory(child))return true;
        return false;
    }
    public static void leave(ServerPlayer player){if(NpcRites.reversible(player))NpcRites.revertReversibleForm(player);var data=membership(player);for(String field:java.util.List.of("HomeKey","ChiefKey","Bounds","Dimension","Suspended"))data.remove(field);CampMembershipData.get(player.server).setDirty();}
}
