package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.entity.beast.LatexCrow;
import net.minecraft.core.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.parkabird.changedsynergy.block.PerchBlock;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="changed_synergy")
public final class PerchSleepService {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void joined(net.minecraftforge.event.entity.EntityJoinLevelEvent event){
        // Old nearby-perch sleeps had no camp reservation to restore on reload.
        if(!event.getLevel().isClientSide()&&event.getEntity() instanceof net.ltxprogrammer.changed.entity.ChangedEntity mob
                &&mob.getUnderlyingPlayer()==null&&perch(mob)!=null)mob.stopSleeping();
    }
    private PerchSleepService(){}
    public static boolean bird(LivingEntity entity){
        if(entity instanceof LatexCrow)return true;
        if(EagleOwlTraits.body(entity)!=null)return true;
        return IAbstractChangedEntity.forEitherSafe(entity).map(IAbstractChangedEntity::getChangedEntity).filter(LatexCrow.class::isInstance).isPresent();
    }
    public static java.util.List<BlockPos> places(Level level,BlockPos pos){
        var state=level.getBlockState(pos);
        if(!(state.getBlock() instanceof PerchBlock block)||!block.complete(level,pos,state))return java.util.List.of();
        return block.vertical()?java.util.List.of(block.sleepPos(state,pos).immutable()):java.util.List.of(pos.immutable(),block.other(pos,state));
    }
    /** Palette checks skip whole sections; no block entity or full-volume camp scan is needed. */
    public static void addBeds(net.minecraft.world.level.chunk.LevelChunk chunk,AABB bounds,java.util.Collection<BlockPos> beds){
        var sections=chunk.getSections();int baseX=chunk.getPos().getMinBlockX(),baseZ=chunk.getPos().getMinBlockZ();
        for(int i=0;i<sections.length;i++){
            var section=sections[i];int baseY=chunk.getMinBuildHeight()+i*16;
            if(baseY+16<=bounds.minY||baseY>=bounds.maxY||section.hasOnlyAir()||!section.maybeHas(s->s.getBlock() instanceof PerchBlock))continue;
            for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=0;y<16;y++){
                var state=section.getBlockState(x,y,z);if(!(state.getBlock() instanceof PerchBlock block))continue;
                var pos=new BlockPos(baseX+x,baseY+y,baseZ+z);
                if(bounds.contains(Vec3.atCenterOf(pos))&&block.sleepPos(state,pos).equals(pos)&&block.complete(chunk.getLevel(),pos,state))beds.add(pos);
            }
        }
    }
    public static BlockPos perch(LivingEntity entity){
        var pos=entity.getSleepingPos().orElse(null);
        return pos!=null&&entity.level().hasChunkAt(pos)&&entity.level().getBlockState(pos).getBlock() instanceof PerchBlock?pos:null;
    }
    public static boolean free(Level level,BlockPos pos,LivingEntity sleeper){
        if(!level.hasChunkAt(pos)||!level.dimensionType().bedWorks())return false;
        var state=level.getBlockState(pos);
        if(!(state.getBlock() instanceof PerchBlock block)||!block.complete(level,pos,state)||!block.sleepPos(state,pos).equals(pos))return false;
        if(state.getValue(PerchBlock.OCCUPIED))return false;
        return level.getEntitiesOfClass(LivingEntity.class,new AABB(pos).inflate(3),other->other!=sleeper&&other.isAlive()&&other.getSleepingPos().filter(pos::equals).isPresent()).isEmpty();
    }
    public static Vec3 position(Level level,BlockPos pos){
        var state=level.getBlockState(pos);var block=(PerchBlock)state.getBlock();
        var direction=state.getValue(PerchBlock.FACING).getClockWise();
        double edge=block.vertical()?0:state.getValue(PerchBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER?-.125:.125;
        return new Vec3(pos.getX()+.5+direction.getStepX()*edge,pos.getY()+(block.vertical()?1.75:.9375),pos.getZ()+.5+direction.getStepZ()*edge);
    }
}
