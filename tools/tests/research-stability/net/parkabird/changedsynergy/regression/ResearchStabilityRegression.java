package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.world.ResearchLabStructure;

/** Actual placement, survival and post-generation drop checks, in a disposable world. */
@Mod.EventBusSubscriber(modid="changed_synergy", value=Dist.DEDICATED_SERVER)
public final class ResearchStabilityRegression {
    private static final String[] NAMES=Boolean.getBoolean("changed_synergy.stabilityDesertCamps")
            ? new String[]{"oasis","muddy_campsite"} : Boolean.getBoolean("changed_synergy.stabilityObservatory")
            ? new String[]{"observatory"} : Boolean.getBoolean("changed_synergy.stabilityHyenas")
            ? new String[]{"hyena_hideout","hyena_camp_tropical","hyena_camp_open","hyena_camp_snowy"}
            : new String[]{"red_lab_tower","green_lab","white_lab_tower","water_tower_changed"};
    private static MinecraftServer server;
    private static ServerLevel level;
    private static ServerPlayer observer;
    private static ResearchLabStructure.Piece piece;
    private static int index, ticks, layouts, problems;
    private static final Map<BlockPos,BlockState> initial=new HashMap<>();
    private static final Map<UUID,CompoundTag> hangings=new HashMap<>();
    private static final List<ChunkPos> forced=new ArrayList<>();

    @SuppressWarnings("unchecked")
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        server=event.getServer();level=server.overworld();
        try {
            level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false,server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,server);
            observer=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"LabStability"));
            observer.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),observer);
            observer.setGameMode(GameType.SPECTATOR);
            var map=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(PlayerList.class,"f_11197_");map.setAccessible(true);
            ((Map<UUID,ServerPlayer>)map.get(server.getPlayerList())).put(observer.getUUID(),observer);
            var list=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(PlayerList.class,"f_11196_");list.setAccessible(true);
            ((List<ServerPlayer>)list.get(server.getPlayerList())).add(observer);level.addNewPlayer(observer);
            prepare();
        } catch(Throwable failure) { fail(failure); }
    }

    private static void prepare() throws Exception {
        int rotationCount=Boolean.getBoolean("changed_synergy.stabilityAllRotations")?4:1;
        String name=NAMES[index/rotationCount];Rotation rotation=Rotation.values()[index%rotationCount];
        BlockPos anchor=new BlockPos(4000+index*160,100,4000);
        ResourceLocation id=new ResourceLocation("changed_synergy",name);
        CompoundTag raw;
        try(var stream=server.getResourceManager().open(new ResourceLocation("changed_synergy","structures/"+name+".nbt"))) {raw=NbtIo.readCompressed(stream);}
        piece=new ResearchLabStructure.Piece(server.getStructureManager(),id,anchor,rotation,name.equals("water_tower_changed"));
        var box=piece.getBoundingBox();
        for(int x=(box.minX()>>4)-1;x<=(box.maxX()>>4)+1;x++)for(int z=(box.minZ()>>4)-1;z<=(box.maxZ()>>4)+1;z++) {
            level.getChunk(x,z);level.setChunkForced(x,z,true);forced.add(new ChunkPos(x,z));
        }
        if(name.equals("water_tower_changed")) {
            // Surround the aquatic template with the ocean water omitted from its exterior padding.
            for(int x=box.minX()-1;x<=box.maxX()+1;x++)for(int z=box.minZ()-1;z<=box.maxZ()+1;z++)for(int y=99;y<=126;y++)
                level.setBlock(new BlockPos(x,y,z),(x==box.minX()-1||x==box.maxX()+1||z==box.minZ()-1||z==box.maxZ()+1||y==99?Blocks.STONE:Blocks.WATER).defaultBlockState(),2);
        }
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++)for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) {
            var chunk=new ChunkPos(x,z);
            piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(4),
                    new BoundingBox(chunk.getMinBlockX(),level.getMinBuildHeight(),chunk.getMinBlockZ(),chunk.getMaxBlockX(),level.getMaxBuildHeight()-1,chunk.getMaxBlockZ()),chunk,anchor);
        }
        observer.teleportTo(level,(box.minX()+box.maxX())/2.0,100,(box.minZ()+box.maxZ())/2.0,Set.of(),0,0);
        initial.clear();hangings.clear();ticks=0;
        var air=Blocks.AIR.defaultBlockState();
        var palette=raw.getList("palette",Tag.TAG_COMPOUND);
        for(var value:raw.getList("blocks",Tag.TAG_COMPOUND)) {
            var b=(CompoundTag)value;var p=b.getList("pos",Tag.TAG_INT);
            var local=new BlockPos(p.getInt(0),p.getInt(1),p.getInt(2));
            var world=net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(local,net.minecraft.world.level.block.Mirror.NONE,rotation,BlockPos.ZERO).offset(anchor);
            var state=level.getBlockState(world);
            var expected=palette.getCompound(b.getInt("state")).getString("Name");
            boolean wetSponge=expected.equals("minecraft:sponge")&&state.is(Blocks.WET_SPONGE);
            if(!wetSponge&&!BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(expected)
                    &&!expected.equals("minecraft:air")&&!expected.equals("minecraft:water")&&!expected.equals("changed:fresh_air"))
                problem("PLACEMENT_MISMATCH",world,expected+" -> "+state);
            if(!state.isAir()) initial.put(world.immutable(),state);
            if(!state.isAir()&&!state.canSurvive(level,world)) problem("UNSUPPORTED",world,state.toString());
        }
        System.out.println("STABILITY_LAYOUT_BEGIN "+name+" "+rotation);
    }

    private static void captureHangings() {
        for(var entity:level.getEntities(null,AABB.of(piece.getBoundingBox()).inflate(2))) {
            if(entity instanceof Mob mob) {mob.setNoAi(true);mob.setInvulnerable(true);}
            if(entity instanceof HangingEntity hanging) {
                hangings.put(hanging.getUUID(),hanging.saveWithoutId(new CompoundTag()));
                if(!hanging.survives())problem("HANGING_UNSUPPORTED",hanging.getPos(),hanging.saveWithoutId(new CompoundTag()).toString());
            }
        }
        System.out.println("STABILITY_CAPTURE index="+index+" hangings="+hangings.size());
    }

    private static void problem(String kind,BlockPos world,String detail) {
        problems++;
        var local=world.subtract(piece.templatePosition());
        System.out.println("STABILITY_"+kind+" index="+index+" local="+local+" "+detail);
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(piece==null||event.phase!=TickEvent.Phase.END)return;
        try {
            ticks++;
            if(ticks==5)captureHangings();
            if(ticks<140)return;
            var area=AABB.of(piece.getBoundingBox()).inflate(2);
            for(var entry:initial.entrySet()) {
                var now=level.getBlockState(entry.getKey());
                if(now.getBlock()!=entry.getValue().getBlock()&&!entry.getValue().is(Blocks.WATER))
                    problem("CHANGED_BLOCK",entry.getKey(),entry.getValue()+" -> "+now);
            }
            for(var entry:hangings.entrySet())if(level.getEntity(entry.getKey())==null) {
                var n=entry.getValue();problem("LOST_HANGING",new BlockPos(n.getInt("TileX"),n.getInt("TileY"),n.getInt("TileZ")),n.toString());
            }
            for(var entity:level.getEntities(null,area)) {
                if(entity instanceof ItemEntity item)problem("DROP",item.blockPosition(),item.getItem().toString());
                if(entity instanceof net.minecraft.world.entity.item.FallingBlockEntity fall)problem("FALLING",fall.blockPosition(),fall.getBlockState().toString());
            }
            System.out.println("STABILITY_LAYOUT_END index="+index+" ticks="+ticks+" observerTicks="+observer.tickCount+" problems="+problems);
            for(var entity:level.getEntities(null,area))if(entity!=observer)entity.discard();
            for(var chunk:forced)level.setChunkForced(chunk.x,chunk.z,false);forced.clear();
            layouts++;index++;
            int total=(Boolean.getBoolean("changed_synergy.stabilityRedOnly")?1:NAMES.length)*(Boolean.getBoolean("changed_synergy.stabilityAllRotations")?4:1);
            if(index<total)prepare();
            else {
                if(problems>0&&!Boolean.getBoolean("changed_synergy.stabilityDiagnostic"))throw new AssertionError(problems+" unstable contents");
                System.out.println("RESEARCH_STABILITY_REGRESSION_PASS: layouts="+layouts+" problems="+problems+" realTicksPerLayout=140");
                finish();
            }
        } catch(Throwable failure) {fail(failure);}
    }
    private static void fail(Throwable failure) {System.err.println("RESEARCH_STABILITY_REGRESSION_FAIL");failure.printStackTrace();finish();}
    private static void finish() {piece=null;if(observer!=null)server.getPlayerList().remove(observer);server.halt(false);}
}
