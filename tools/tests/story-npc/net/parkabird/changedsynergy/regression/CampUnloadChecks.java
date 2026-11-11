package net.parkabird.changedsynergy.regression;

import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/** Exercises vanilla's actual section iterator during a sleeping camp's visibility change. */
final class CampUnloadChecks {
    static void run(MinecraftServer server)throws Exception {
        var level=server.overworld();
        var field=Arrays.stream(ServerLevel.class.getDeclaredFields()).filter(f->net.minecraft.world.level.entity.PersistentEntitySectionManager.class.isAssignableFrom(f.getType())).findFirst().orElseThrow();field.setAccessible(true);
        var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<?>)field.get(level);
        var head=new BlockPos(4095,95,4096);var foot=head.west();var chunk=new net.minecraft.world.level.ChunkPos(head);
        for(int x=chunk.x-1;x<=chunk.x+1;x++)for(int z=chunk.z-1;z<=chunk.z+1;z++){level.getChunk(x,z);manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(x,z),FullChunkStatus.ENTITY_TICKING);}manager.tick();
        for(var p:BlockPos.betweenClosed(head.offset(-3,-1,-3),head.offset(3,2,3)))level.setBlock(p,p.getY()==94?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        level.setBlock(foot,Blocks.ORANGE_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST).setValue(BedBlock.PART,BedPart.FOOT),2);
        level.setBlock(head,Blocks.ORANGE_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST).setValue(BedBlock.PART,BedPart.HEAD),2);
        var type=net.parkabird.changedsynergy.init.ChangedSynergyEntities.LATEX_SILK_MOTH.get();
        var sleeper=type.create(level);var witness=type.create(level);var third=type.create(level);sleeper.setNoAi(true);witness.setNoAi(true);third.setNoAi(true);
        sleeper.setPos(Vec3.atBottomCenterOf(foot));witness.setPos(Vec3.atBottomCenterOf(head.offset(-2,0,2)));
        third.setPos(Vec3.atBottomCenterOf(head.offset(-3,0,2)));
        level.addFreshEntity(sleeper);level.addFreshEntity(witness);level.addFreshEntity(third);sleeper.startSleeping(head);
        sleeper.getPersistentData().putLong("ChangedSynergyNaturalCampBed",head.asLong());
        Vec3 position=sleeper.position();
        manager.updateChunkStatus(chunk,FullChunkStatus.INACCESSIBLE);
        if(!sleeper.isSleeping()||!sleeper.position().equals(position))throw new AssertionError("tracking loss must not wake or reposition a sleeping resident inside the section iterator: "+position+" -> "+sleeper.position());
        manager.updateChunkStatus(chunk,FullChunkStatus.ENTITY_TICKING);
        var saved=sleeper.saveWithoutId(new CompoundTag());sleeper.discard();witness.discard();third.discard();
        var reloaded=type.create(level);reloaded.load(saved);level.addFreshEntity(reloaded);
        if(reloaded.isSleeping()||reloaded.getPersistentData().contains("ChangedSynergyNaturalCampBed")||level.getBlockState(head).getValue(BedBlock.OCCUPIED))throw new AssertionError("safe rejoin clears stale sleep and bed occupancy");
        reloaded.discard();
        System.out.println("CAMP_UNLOAD_PASS: real section visibility transition, passive sleeping unload and safe saved-body rejoin");
    }
}
