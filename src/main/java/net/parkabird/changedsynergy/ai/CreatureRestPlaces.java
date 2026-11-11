package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Native seats first, soft carpets second, bare ground last. */
public final class CreatureRestPlaces {
    private CreatureRestPlaces() {}
    public static int rank(Level level, BlockPos pos) {
        var state=level.getBlockState(pos);
        if(state.is(BlockTags.WOOL_CARPETS))return 1;
        return PlayerOutpostRestGoal.isRestPoint(state)?0:2;
    }
    public static boolean carpetFree(ChangedEntity mob, ServerLevel level, BlockPos pos) {
        if(!level.hasChunkAt(pos) || !level.getBlockState(pos).is(BlockTags.WOOL_CARPETS))return false;
        var body=mob.getBoundingBox().move(new Vec3(pos.getX()+.5,pos.getY()+.0625,pos.getZ()+.5).subtract(mob.position()));
        return level.noCollision(mob,body) && level.getEntityCollisions(mob,body).isEmpty();
    }
    public static boolean sitOnGround(ChangedEntity mob, ServerLevel level, BlockPos pos) {
        boolean carpet=level.getBlockState(pos).is(BlockTags.WOOL_CARPETS);
        var floor=pos.below();
        var seat=SeatEntity.createFor(level,level.getBlockState(floor),floor,false,false,true);
        if(seat==null)return false;
        seat.setPos(pos.getX()+.5,pos.getY()+(carpet?.0625:0),pos.getZ()+.5);
        if(mob.startRiding(seat,true))return true;
        seat.discard();return false;
    }
}
