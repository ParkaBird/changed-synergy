package net.parkabird.changedsynergy.ai;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/** Shared player familiarity is keyed by two real UUIDs, never by a replacement Instinct body. */
public final class PlayerFriendshipData extends SavedData {
    public static final class Pair {
        public final UUID first, second;
        public boolean friends;
        public int familiarity, allowance;
        public long careAt, giftAt;
        public String firstName="", secondName="";
        Pair(UUID first, UUID second) { this.first=first; this.second=second; }
        public UUID other(UUID id) { return first.equals(id)?second:first; }
        public String name(UUID id) { return first.equals(id)?firstName:secondName; }
    }
    private final Map<String,Pair> pairs=new HashMap<>();
    private final Map<UUID,Set<String>> byPlayer=new HashMap<>();
    public static PlayerFriendshipData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PlayerFriendshipData::load,PlayerFriendshipData::new,"changed_synergy_player_friends");
    }
    private static String key(UUID a,UUID b) { return a.compareTo(b)<0?a+":"+b:b+":"+a; }
    private void add(Pair pair) {
        String key=key(pair.first,pair.second); pairs.put(key,pair);
        byPlayer.computeIfAbsent(pair.first,id->new HashSet<>()).add(key);
        byPlayer.computeIfAbsent(pair.second,id->new HashSet<>()).add(key);
    }
    public Pair find(UUID a,UUID b) { return pairs.get(key(a,b)); }
    public void rememberName(UUID player,String name) {
        for(String key:byPlayer.getOrDefault(player,Set.of())) {
            Pair pair=pairs.get(key);
            if(name.equals(pair.name(player)))continue;
            if(pair.first.equals(player))pair.firstName=name;else pair.secondName=name;
            setDirty();
        }
    }
    public List<Pair> contacts(UUID player) {
        return byPlayer.getOrDefault(player,Set.of()).stream().map(pairs::get).filter(pair->pair.friends).toList();
    }
    public static boolean friends(ServerPlayer a,ServerPlayer b) {
        if(a==null||b==null||a.server!=b.server||!ChangedSynergyGameRules.enabled(a.level(),ChangedSynergyGameRules.FRIENDSHIP_SYSTEM))return false;
        Pair pair=get(a.server).find(a.getUUID(),b.getUUID()); return pair!=null&&pair.friends;
    }
    public static boolean eligible(ServerPlayer a,ServerPlayer b) {
        return a!=b&&a.server==b.server&&ChangedSynergyGameRules.enabled(a.level(),ChangedSynergyGameRules.FRIENDSHIP_SYSTEM)
                &&(NativeRoleplayService.active(a)||NativeRoleplayService.active(b));
    }
    private Pair ensure(ServerPlayer a,ServerPlayer b) {
        Pair pair=find(a.getUUID(),b.getUUID());
        if(pair==null) { UUID first=a.getUUID().compareTo(b.getUUID())<0?a.getUUID():b.getUUID();
            pair=new Pair(first,first.equals(a.getUUID())?b.getUUID():a.getUUID());
            pair.allowance=ChangedSynergyConfig.COMMON.careAffectionAllowance.get(); add(pair); }
        pair.firstName=NativeRoleplayService.name(pair.first.equals(a.getUUID())?a:b).getString();
        pair.secondName=NativeRoleplayService.name(pair.second.equals(a.getUUID())?a:b).getString();
        setDirty();
        return pair;
    }
    public boolean establish(ServerPlayer a,ServerPlayer b) {
        if(!eligible(a,b))return false; Pair pair=ensure(a,b); pair.friends=true; setDirty(); return true;
    }
    public void remove(UUID a,UUID b) { Pair pair=find(a,b); if(pair!=null&&pair.friends) {pair.friends=false;setDirty();} }
    public int care(ServerPlayer a,ServerPlayer b,boolean gift) {
        if(!eligible(a,b))return 0;
        Pair pair=ensure(a,b); long now=a.server.overworld().getGameTime(); var cfg=ChangedSynergyConfig.COMMON;
        if(cfg.balancedRelationships.get() && now<(gift?pair.giftAt:pair.careAt))return 0;
        int gain=Math.max(0,Math.min(2,60-pair.familiarity));
        if(!gift&&cfg.balancedRelationships.get())gain=Math.min(gain,pair.allowance);
        if(gift) { pair.giftAt=now+cfg.giftCooldownSeconds.get()*20L; pair.allowance=cfg.careAffectionAllowance.get(); }
        else if(gain>0) { pair.careAt=now+cfg.careAffectionCooldownSeconds.get()*20L; pair.allowance-=gain; }
        pair.familiarity+=gain; setDirty(); return gain;
    }
    public static PlayerFriendshipData load(CompoundTag root) {
        var data=new PlayerFriendshipData();
        for(Tag value:root.getList("Pairs",Tag.TAG_COMPOUND)) {
            CompoundTag tag=(CompoundTag)value; if(!tag.hasUUID("First")||!tag.hasUUID("Second"))continue;
            UUID a=tag.getUUID("First"),b=tag.getUUID("Second"); if(a.equals(b))continue;
            Pair pair=new Pair(a,b); pair.friends=tag.getBoolean("Friends"); pair.familiarity=Math.max(0,Math.min(60,tag.getInt("Familiarity")));
            pair.allowance=Math.max(0,Math.min(60,tag.getInt("Allowance"))); pair.careAt=tag.getLong("CareAt");pair.giftAt=tag.getLong("GiftAt");
            pair.firstName=tag.getString("FirstName");pair.secondName=tag.getString("SecondName");data.add(pair);
        } return data;
    }
    @Override public CompoundTag save(CompoundTag root) {
        ListTag list=new ListTag(); for(Pair pair:pairs.values()) {
            CompoundTag tag=new CompoundTag();tag.putUUID("First",pair.first);tag.putUUID("Second",pair.second);tag.putBoolean("Friends",pair.friends);
            tag.putInt("Familiarity",pair.familiarity);tag.putInt("Allowance",pair.allowance);tag.putLong("CareAt",pair.careAt);tag.putLong("GiftAt",pair.giftAt);
            tag.putString("FirstName",pair.firstName);tag.putString("SecondName",pair.secondName);list.add(tag);
        } root.put("Pairs",list);return root;
    }
}
