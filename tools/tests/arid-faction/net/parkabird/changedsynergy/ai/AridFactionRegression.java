package net.parkabird.changedsynergy.ai;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.dialogue.LatexTerritory;
import net.parkabird.changedsynergy.init.ChangedSynergyEntities;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.npc.CampMembershipData;
import net.parkabird.changedsynergy.origin.NativeOriginCatalog;
import net.parkabird.changedsynergy.origin.NativeOriginData;
import net.parkabird.changedsynergy.origin.NativeOriginService;

@Mod.EventBusSubscriber(modid="changed_synergy", value=net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER)
public final class AridFactionRegression {
    private static int checks;
    private static void check(boolean valid, String why) {
        if (!valid) throw new AssertionError(why);
        checks++;
    }

    @SubscribeEvent public static void run(ServerStartedEvent event) {
        var server=event.getServer();
        try {
            ChangedSynergyConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
            server.overworld().getGameRules().getRule(ChangedSynergyGameRules.FACTION_REPUTATION).set(true,server);
            reputations();warrants();origins();world(server);communities(server);playerApi(server);
            System.out.println("ARID_FACTION_PASS: "+checks+" migration, persistence, identity, habitat and isolation checks");
        } catch (Throwable failure) {
            System.err.println("ARID_FACTION_FAIL");failure.printStackTrace();
        } finally { server.halt(false); }
    }

    private static void reputations() {
        for (Integer desert : new Integer[]{null,-100,-40,0,30,60,100})
            for (Integer badlands : new Integer[]{null,-100,-40,0,30,60,100})
                for (Integer arid : new Integer[]{null,-70,0,90}) {
                    var data=new CompoundTag();Integer expected=null;
                    if (desert!=null) {data.putInt("faction:light:desert",desert);expected=desert;}
                    if (badlands!=null) {data.putInt("faction:light:badlands",badlands);expected=expected==null?badlands:FactionDiplomacy.mergeAridStanding(expected,badlands);}
                    if (arid!=null) {data.putInt("faction:light:arid",arid);expected=expected==null?arid:FactionDiplomacy.mergeAridStanding(expected,arid);}
                    data.putInt("faction:light:forest",23);
                    FactionReputation.migrateAridStanding(data);
                    check(!data.contains("faction:light:desert")&&!data.contains("faction:light:badlands"),"old accounts retired");
                    check(expected==null?!data.contains("faction:light:arid"):data.getInt("faction:light:arid")==expected,"negative wins, otherwise maximum, including existing arid");
                    check(data.getInt("faction:light:forest")==23,"other account unchanged");
                    var after=data.copy();FactionReputation.migrateAridStanding(data);
                    check(data.equals(after),"reputation migration idempotent");
                }
        var data=new CompoundTag();data.putInt("faction:light:desert",1000);data.putInt("faction:light:badlands",-1000);
        data.putInt("interaction_remainder:faction:light:desert",80);data.putInt("interaction_remainder:faction:light:badlands",65);
        data.putLong("diplomacy_notice:faction:light:desert",200);data.putLong("diplomacy_notice:faction:light:badlands",450);
        FactionReputation.migrateAridStanding(data);
        check(data.getInt("faction:light:arid")==-100,"out-of-range old score bounded");
        check(data.getInt("interaction_remainder:faction:light:arid")==80,"fractional gains cannot be summed into free points");
        check(data.getLong("diplomacy_notice:faction:light:arid")==450,"notification cooldown retained");
    }

    private static void warrants() {
        var warrants=new CompoundTag();var desert=new CompoundTag();var badlands=new CompoundTag();
        desert.putString("Type","changed_synergy:latex_jerboa");desert.putLong("Next",1200);desert.putLong("Warning",500);
        badlands.putString("Type","changed:latex_snake");badlands.putLong("Next",1600);badlands.putLong("Warning",300);
        warrants.put("light:desert",desert);warrants.put("light:badlands",badlands);warrants.put("white",new CompoundTag());
        FactionPursuitService.migrateAridWarrants(warrants);
        check(warrants.size()==2,"one warrant replaces two; unrelated warrant remains");
        check(warrants.getCompound("light:arid").getLong("Next")==1600,"later pursuit cooldown wins");
        check(warrants.getCompound("light:arid").getLong("Warning")==500,"warning delay retained");
        check(warrants.getCompound("light:arid").getString("Type").equals("changed:latex_snake"),"cooldown owner's source retained");
        var after=warrants.copy();FactionPursuitService.migrateAridWarrants(warrants);check(after.equals(warrants),"warrant migration idempotent");
    }

    private static void origins() {
        for (String region:List.of("desert","badlands")) {
            var record=new CompoundTag();var id=UUID.randomUUID();record.putUUID("Id",id);
            record.putString("State","native");record.putString("Faction","light");record.putString("Region",region);
            record.putString("Form","changed_synergy:form_latex_jerboa");record.putString("Current","changed_synergy:form_latex_jerboa");
            record.putLong("Home",new BlockPos(20,90,30).asLong());record.putString("RoleName","Kept name");record.putBoolean("GlobalChat",true);
            var list=new ListTag();list.add(record);var root=new CompoundTag();root.put("Players",list);
            var data=NativeOriginData.load(root);var identity=data.players.get(id);
            check(data.isDirty()&&identity.region.equals("arid"),"old native region migrated and scheduled for save");
            check(NativeOriginService.account(identity).equals("light:arid"),"native faction matching uses merged account");
            check(identity.state.equals("native")&&identity.home.equals(new BlockPos(20,90,30))&&identity.roleName.equals("Kept name")&&identity.globalChat,"body identity, home, name and preferences retained");
            var saved=data.save(new CompoundTag());var reloaded=NativeOriginData.load(saved);
            check(reloaded.players.get(id).region.equals("arid")&&!reloaded.isDirty(),"new identity saves and reloads without another migration");
        }
    }

    private static void world(MinecraftServer server) throws Exception {
        var level=server.overworld();var biomes=level.registryAccess().registryOrThrow(Registries.BIOME);
        int index=0;
        for (var key:List.of(Biomes.DESERT,Biomes.BADLANDS,Biomes.WOODED_BADLANDS,Biomes.ERODED_BADLANDS)) {
            var biome=biomes.getHolderOrThrow(key);var id=key.location();
            check(LatexTerritory.populationRegion(biome,id,false).equals("arid"),"territory political label merged: "+id);
            check(LightFactionGroup.regionForBiome(level,id).equals("arid"),"biome-based legacy affiliations canonical: "+id);
            String habitat=key.equals(Biomes.DESERT)?"desert":"badlands";
            check(LatexTerritory.habitatRegion(biome,id,false).equals(habitat),"physical habitat remains distinct");
            var pos=new BlockPos(56+index++*96,80,56);
            level.getChunkAt(pos).fillBiomesFromNoise((x,y,z,sampler)->biome,level.getChunkSource().randomState().sampler());
            var mob=ChangedSynergyEntities.LATEX_JERBOA.get().create(level);mob.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            mob.getPersistentData().putString("ChangedSynergyLightReputationGroup",habitat);
            check(FactionReputation.groupId(mob).equals("light:arid"),"old entity affiliation migrates");
            check(mob.getPersistentData().getString("ChangedSynergyLightReputationGroup").equals("arid"),"entity persists canonical affiliation");
            check(CreatureSettlementService.isDesertCommunity(mob)==habitat.equals("desert"),"desert daily activities retained");
            check(CreatureSettlementService.isBadlandsCommunity(mob)==habitat.equals("badlands"),"badlands minecart activities retained");
            var choose=CreatureSettlementService.class.getDeclaredMethod("blueprintFor",net.ltxprogrammer.changed.entity.ChangedEntity.class,BlockPos.class);
            choose.setAccessible(true);
            var blueprint=(java.util.Optional<?>)choose.invoke(null,mob,pos);
            check(blueprint.isPresent()&&((net.parkabird.changedsynergy.world.CreatureSettlementBlueprints.Blueprint)blueprint.get())
                    .id().getPath().equals(habitat+"_cache"),"the correct habitat-specific blueprint is selected");
            var unloaded=new BlockPos(1000000,80,1000000);
            var resolve=CreatureSettlementService.class.getDeclaredMethod("settlementRegion",net.ltxprogrammer.changed.entity.ChangedEntity.class,BlockPos.class);
            resolve.setAccessible(true);
            check(!level.hasChunkAt(unloaded),"fixture distant cache is unloaded");
            check(resolve.invoke(null,mob,unloaded).equals("arid")&&!level.hasChunkAt(unloaded),"layout fallback cannot force-load a distant cache");
            mob.discard();
        }
        var type=ChangedSynergyEntities.LATEX_JERBOA.get();
        check(LightFactionGroup.nativeRegions(level.registryAccess(),type).equals(List.of("arid")),"species tag and origin affiliations merged");
        var entry=NativeOriginCatalog.find(level.registryAccess(),type.create(level).getSelfVariant().getFormId());
        check(entry!=null&&entry.region().equals("arid"),"native origin catalog exposes merged region");
    }

    private static void communities(MinecraftServer server) throws Exception {
        var root=new CompoundTag();var list=new ListTag();
        for (int i=0;i<2;i++) {
            var record=new CompoundTag();record.putUUID("Id",UUID.randomUUID());record.putString("Faction","light");record.putString("Dimension","minecraft:overworld");
            record.putString("Group",i==0?"light:desert":"light:badlands");record.putLong("Center",new BlockPos(0,80,i*200).asLong());record.putLong("Cache",new BlockPos(0,80,i*200).asLong());
            record.putInt("FoodDelivered",7+i);list.add(record);
        }
        root.put("Records",list);
        var load=CreatureCommunityData.class.getDeclaredMethod("load",CompoundTag.class);load.setAccessible(true);
        var data=(CreatureCommunityData)load.invoke(null,root);
        var migrate=CreatureCommunityData.class.getDeclaredMethod("migrateLegacyCommunities",net.minecraft.server.level.ServerLevel.class,String.class);migrate.setAccessible(true);
        migrate.invoke(data,server.overworld(),"minecraft:overworld");
        var saved=data.save(new CompoundTag()).getList("Records",10);
        check(saved.size()==2,"separate physical outposts must not merge inventories");
        for (int i=0;i<2;i++)check(saved.getCompound(i).getString("Group").equals("light:arid")&&saved.getCompound(i).getInt("FoodDelivered")==7+i,"outpost affiliation updates without merging stock");
    }

    private static void playerApi(MinecraftServer server) {
        var player=new ServerPlayer(server,server.overworld(),new GameProfile(UUID.randomUUID(),"AridCheck"));
        player.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),player);
        var membership=CampMembershipData.get(server).player(player.getUUID());var trust=new CompoundTag();trust.putInt("refuge:one",35);trust.putInt("refuge:two",-20);membership.put("LocalTrust",trust);
        var before=membership.copy();var data=new CompoundTag();data.putInt("faction:light:desert",30);data.putInt("faction:light:badlands",-40);
        PlayerPersistentData.get(player).put("ChangedSynergyReputation",data);
        check(FactionReputation.scoreForAccount(player,"light:desert")==-40,"legacy public score reader resolves to merged data");
        FactionReputation.damageNativeMember(player,"light:badlands",-5);
        check(data.getInt("faction:light:arid")==-45&&!data.contains("faction:light:badlands"),"native-member damage cannot recreate retired account");
        var mob=ChangedSynergyEntities.LATEX_JERBOA.get().create(server.overworld());
        mob.getPersistentData().putString("ChangedSynergyLightReputationGroup","badlands");
        var persisted=PlayerPersistentData.get(player);long expiry=server.overworld().getGameTime()+3000;
        for (String key:List.of("ChangedSynergyFactionGrace","ChangedSynergyFactionDamageLock","ChangedSynergyFactionAggression")) {
            var timers=new CompoundTag();timers.putLong("light:desert",expiry-100);timers.putLong("light:badlands",expiry);timers.putLong("white",50);
            persisted.put(key,timers);
            boolean active=switch(key) {
                case "ChangedSynergyFactionGrace"->FactionHostilityGrace.active(mob,player);
                case "ChangedSynergyFactionDamageLock"->FactionHostilityGrace.damageLocked(mob,player);
                default->FactionHostilityGrace.wasAggressor(mob,player);
            };
            check(active&&timers.getLong("light:arid")==expiry,"old grace, damage lock and aggression timers remain active: "+key);
            check(!timers.contains("light:desert")&&!timers.contains("light:badlands")&&timers.getLong("white")==50,"only merged timer keys change");
        }
        FactionHostilityGrace.clear(mob,player);
        check(persisted.getCompound("ChangedSynergyFactionGrace").getLong("light:arid")==expiry,"secondary damage lock still prevents early truce cancellation");
        FactionHostilityGrace.endFatalTakeoverProtection(player,mob);
        check(!persisted.getCompound("ChangedSynergyFactionGrace").contains("light:arid")&&!persisted.getCompound("ChangedSynergyFactionDamageLock").contains("light:arid"),"explicit fatal breakout clears migrated protection");
        check(CampMembershipData.get(server).player(player.getUUID()).equals(before),"local trust and community membership untouched");
        mob.discard();player.discard();
    }
}
