package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.event.CreatureCacheGuardEvents;

final class CacheWitnessChecks {
    private static int checks;
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    static void run(MinecraftServer server)throws Exception {
        sight(server); natural(server); doubleChest(server); moddedStorage(server); appearance(server);
        System.out.println("CACHE_WITNESS_PASS: "+checks+" visibility, distance, real opening, camp defence, return, independent warnings and varied persistent eyes checks");
    }
    private static ServerPlayer player(ServerLevel level) {
        var player=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"WitnessTest"));
        player.connection=new ServerGamePacketListenerImpl(level.getServer(),new Connection(PacketFlow.SERVERBOUND),player);
        return player;
    }
    private static void open(ServerPlayer player,BlockPos pos) {
        player.closeContainer();
        var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
        CreatureCacheGuardEvents.onCacheAccess(new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,hit));
        var state=player.serverLevel().getBlockState(pos);
        state.getBlock().use(state,player.serverLevel(),pos,player,InteractionHand.MAIN_HAND,hit);
    }
    private static void sight(MinecraftServer server) {
        var level=server.overworld();var pos=new BlockPos(40,90,40);level.getChunk(pos);
        level.setBlock(pos,Blocks.BARREL.defaultBlockState(),3);
        var guard=ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);guard.setPos(40.5,90,35.5);
        level.addFreshEntity(guard);CreatureLifeMemory.setRole(guard,CreatureLifeMemory.GroupRole.GUARD);CreatureCommunityData.markCache(guard,pos);
        var player=player(level);player.setPos(40.5,90,43.5);FactionReputation.set(guard,player,0);
        try {
            for(var block:BlockPos.betweenClosed(new BlockPos(39,90,39),new BlockPos(41,93,39)))level.setBlock(block,Blocks.STONE.defaultBlockState(),3);
            check(!guard.hasLineOfSight(player),"wall really blocks native sight");
            open(player,pos);check(FactionReputation.score(guard,player)==0&&CreatureCacheGuardService.assignment(guard).isEmpty(),"wild outpost does not see through walls");
            for(var block:BlockPos.betweenClosed(new BlockPos(39,90,39),new BlockPos(41,93,39)))level.removeBlock(block,false);
            guard.setPos(player.getX()+17,90,player.getZ());
            open(player,pos);check(FactionReputation.score(guard,player)==0,"17 blocks away does not witness");
            guard.setPos(40.5,90,35.5);guard.startSleeping(guard.blockPosition());
            open(player,pos);check(FactionReputation.score(guard,player)==0,"sleepers do not witness");guard.stopSleeping();
            player.closeContainer();
            var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
            CreatureCacheGuardEvents.onCacheAccess(new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,hit));
            check(FactionReputation.score(guard,player)==0,"right click without a menu is not an offence");
            var unrelated=pos.east(2);level.setBlock(unrelated,Blocks.BARREL.defaultBlockState(),3);
            var other=level.getBlockState(unrelated);other.getBlock().use(other,level,unrelated,player,InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(unrelated),Direction.UP,unrelated,false));
            check(FactionReputation.score(guard,player)==0,"an unrelated menu cannot consume a pending cache click");
            check(guard.hasLineOfSight(player),"unobstructed control fixture");
            open(player,pos);check(FactionReputation.score(guard,player)==-2&&CreatureCacheGuardService.isDefendingAgainst(guard,player),"visible outpost intrusion keeps its original guard response");
            level.removeBlock(unrelated,false);
        }finally{player.closeContainer();CreatureCacheGuardService.forget(guard);guard.discard();player.discard();level.removeBlock(pos,false);}
    }
    private static ChangedEntity resident(ServerLevel level,BlockPos cache) {
        for(var floor:BlockPos.betweenClosed(cache.offset(-10,-1,-10),cache.offset(10,-1,10)))level.setBlock(floor,Blocks.STONE.defaultBlockState(),2);
        level.setBlock(cache,Blocks.BARREL.defaultBlockState(),3);
        var mob=net.parkabird.changedsynergy.init.ChangedSynergyEntities.LATEX_SPOTTED_HYENA_MALE.get().create(level);
        mob.setPos(cache.getX()+.5,cache.getY(),cache.getZ()-5.5);
        var data=new CompoundTag();data.putString("Template","hyena_hideout");data.putBoolean("Camp",true);data.putBoolean("Resolved",true);
        data.putDouble("MinX",cache.getX()-10);data.putDouble("MinY",cache.getY()-1);data.putDouble("MinZ",cache.getZ()-10);
        data.putDouble("MaxX",cache.getX()+11);data.putDouble("MaxY",cache.getY()+8);data.putDouble("MaxZ",cache.getZ()+11);
        data.putDouble("HomeX",mob.getX());data.putDouble("HomeY",mob.getY());data.putDouble("HomeZ",mob.getZ());
        mob.getPersistentData().put(StructureResidentGoal.DATA,data);level.addFreshEntity(mob);CreatureLifeMemory.setRole(mob,CreatureLifeMemory.GroupRole.SCOUT);return mob;
    }
    private static void natural(MinecraftServer server)throws Exception {
        var level=server.overworld();var cache=new BlockPos(80,90,80);var other=new BlockPos(160,90,80);
        level.getChunk(cache);level.getChunk(other);var first=resident(level,cache);var second=resident(level,other);var player=player(level);
        var field=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.players.PlayerList.class,"f_11197_");field.setAccessible(true);
        @SuppressWarnings("unchecked")var players=(Map<UUID,ServerPlayer>)field.get(server.getPlayerList());players.put(player.getUUID(),player);level.addNewPlayer(player);
        try {
            player.setPos(cache.getX()+.5,90,cache.getZ()+2.5);FactionReputation.set(first,player,50);
            open(player,cache);open(player,cache);
            check(CreatureCacheGuardService.assignment(first).isEmpty(),"respected camp visitors get the existing two warnings");
            open(player,cache);
            check(CreatureCacheGuardService.isDefendingAgainst(first,player)&&first.getTarget()==player,"natural residents actually defend after the final warning");
            check(CreatureLifeMemory.role(first)==CreatureLifeMemory.GroupRole.SCOUT&&CreatureCommunityData.snapshot(first).isEmpty(),"defence creates no wild-outpost role or community");
            double before=first.distanceToSqr(player);boolean moved=false;
            for(int tick=0;tick<60&&!moved;tick++) {
                server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);
                CreatureCacheGuardService.tick(first);first.tick();
                moved=first.distanceToSqr(player)<before-4;
            }
            check(moved,"native combat AI actually pursues the intruder");
            player.setPos(cache.getX()+25,90,cache.getZ());CreatureCacheGuardService.tick(first);
            check(!CreatureCacheGuardService.isDefendingAgainst(first,player)&&first.getTarget()==null,"leaving ends camp pursuit");
            var goal=new CacheGuardReturnGoal(first);check(goal.canUse(),"non-guard camp resident can return to its post");
            goal.start();var post=CreatureCacheGuardService.assignment(first).orElseThrow().post();first.setPos(Vec3.atBottomCenterOf(post));goal.tick();
            check(CreatureCacheGuardService.assignment(first).isEmpty(),"return retires the temporary camp assignment");
            player.setPos(other.getX()+.5,90,other.getZ()+2.5);FactionReputation.set(second,player,50);open(player,other);
            check(CreatureCacheGuardService.assignment(second).isEmpty(),"warnings in a different camp start independently");
        }finally{player.closeContainer();players.remove(player.getUUID());player.discard();for(var mob:List.of(first,second)){CreatureCacheGuardService.forget(mob);mob.discard();}}
    }
    private static void doubleChest(MinecraftServer server) {
        var level=server.overworld();var pos=new BlockPos(220,90,80);var guard=resident(level,pos);
        var left=Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.NORTH).setValue(ChestBlock.TYPE,ChestType.LEFT);
        var other=pos.relative(ChestBlock.getConnectedDirection(left));level.setBlock(pos,left,18);level.setBlock(other,left.setValue(ChestBlock.TYPE,ChestType.RIGHT),18);
        var player=player(level);player.setPos(pos.getX()+.5,90,pos.getZ()+2.5);FactionReputation.set(guard,player,0);
        try{open(player,other);check(FactionReputation.score(guard,player)==-2&&CreatureCacheGuardService.isDefendingAgainst(guard,player),"actual opening of the other chest half is witnessed once");}
        finally{player.closeContainer();CreatureCacheGuardService.forget(guard);guard.discard();player.discard();}
    }
    private static void appearance(MinecraftServer server)throws Exception {
        var styles=new HashSet<Integer>();var colors=new HashSet<Integer>();int count=0;
        for(var name:List.of("forest_treehouse","hyena_hideout","hyena_camp_tropical","hyena_camp_open","hyena_camp_snowy","red_lab_tower","green_lab","water_tower_changed")) {
            CompoundTag template;
            try(var input=server.getResourceManager().open(net.minecraft.resources.ResourceLocation.parse("changed_synergy:structures/"+name+".nbt"))){template=NbtIo.readCompressed(input);}
            for(var entry:template.getList("entities",10)) {
                var raw=((CompoundTag)entry).getCompound("nbt").copy();
                if(!raw.contains("LocalVariantInfo")&&!raw.getCompound("ForgeData").getBoolean("ChangedSynergyRandomStructureEyes"))continue;
                var entity=EntityType.loadEntityRecursive(raw,server.overworld(),e->e);if(!(entity instanceof ChangedEntity mob)){if(entity!=null)entity.discard();continue;}
                var old=mob.saveWithoutId(new CompoundTag()).getCompound("LocalVariantInfo");
                boolean random=raw.getCompound("ForgeData").getBoolean("ChangedSynergyRandomStructureEyes");
                mob.setPos(3000+count++*4,120,3000);server.overworld().getChunk(mob.blockPosition());server.overworld().addFreshEntity(mob);
                var saved=mob.saveWithoutId(new CompoundTag());var current=saved.getCompound("LocalVariantInfo");
                if(random){styles.add(current.getInt("eyeStyle"));colors.add(current.getInt("irisLeft"));check(!mob.getPersistentData().contains("ChangedSynergyRandomStructureEyes"),"randomization flag consumed once");}
                else check(current.equals(old),"authored custom eyes are preserved");
                check(current.getInt("hair")==old.getInt("hair")&&current.getFloat("scale")==old.getFloat("scale"),"eye initialization preserves hair and body scale");
                mob.discard();var clone=(ChangedEntity)mob.getType().create(server.overworld());clone.load(saved);server.overworld().addFreshEntity(clone);
                check(clone.saveWithoutId(new CompoundTag()).getCompound("LocalVariantInfo").equals(current),"randomized eyes survive reload without rerolling");clone.discard();
            }
        }
        check(styles.size()>1&&colors.size()>1,"structure populations have varied eye styles and colors");
    }
    private static void moddedStorage(MinecraftServer server) {
        if(!net.minecraftforge.fml.ModList.get().isLoaded("sophisticatedstorage"))return;
        var level=server.overworld();var pos=new BlockPos(260,90,80);var guard=resident(level,pos);
        var block=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(net.minecraft.resources.ResourceLocation.parse("sophisticatedstorage:barrel"));
        check(block!=null,"Sophisticated Storage block exists");level.setBlock(pos,block.defaultBlockState(),3);
        var player=player(level);player.setPos(pos.getX()+.5,90,pos.getZ()+2.5);FactionReputation.set(guard,player,0);
        try {
            check(PlayerOutpostData.container(level,pos)!=null,"capability storage remains recognized as camp storage");
            open(player,pos);check(player.containerMenu!=player.inventoryMenu,"actual Sophisticated Storage menu opens");
            check(FactionReputation.score(guard,player)==-2&&CreatureCacheGuardService.isDefendingAgainst(guard,player),"modded storage opening retains witnessed camp reactions");
        }finally{player.closeContainer();CreatureCacheGuardService.forget(guard);guard.discard();player.discard();}
    }
}
