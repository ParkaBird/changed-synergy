package net.parkabird.changedsynergy.block;

import net.minecraft.core.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

/** A two-block bird bed: one place on a stand, independent places at both rail ends. */
public final class PerchBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF=BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty OCCUPIED=BedBlock.OCCUPIED;
    private final boolean vertical;
    public PerchBlock(boolean vertical){super(Properties.of().strength(2).sound(SoundType.WOOD).noOcclusion());this.vertical=vertical;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(HALF,DoubleBlockHalf.LOWER).setValue(OCCUPIED,false));}
    public boolean vertical(){return vertical;}
    public BlockPos sleepPos(BlockState state,BlockPos pos){return vertical&&state.getValue(HALF)==DoubleBlockHalf.UPPER?pos.below():pos;}
    public boolean complete(LevelReader level,BlockPos pos,BlockState state){var next=other(pos,state);if(!level.hasChunkAt(next))return false;var mate=level.getBlockState(next);return mate.is(this)&&mate.getValue(FACING)==state.getValue(FACING)&&mate.getValue(HALF)!=state.getValue(HALF);}
    @Override public boolean isBed(BlockState state,BlockGetter level,BlockPos pos,net.minecraft.world.entity.Entity sleeper){return sleeper==null||sleeper instanceof LivingEntity living&&net.parkabird.changedsynergy.ai.PerchSleepService.bird(living);}
    @Override public void setBedOccupied(BlockState state,Level level,BlockPos pos,LivingEntity sleeper,boolean occupied){
        level.setBlock(pos,state.setValue(OCCUPIED,occupied),Block.UPDATE_CLIENTS);
        if(vertical&&complete(level,pos,state)){var mate=other(pos,state);level.setBlock(mate,level.getBlockState(mate).setValue(OCCUPIED,occupied),Block.UPDATE_CLIENTS);}
    }
    @Override public net.minecraft.world.InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){
        if(!net.parkabird.changedsynergy.ai.PerchSleepService.bird(player))return net.minecraft.world.InteractionResult.PASS;
        if(level.isClientSide)return net.minecraft.world.InteractionResult.SUCCESS;
        if(!level.dimensionType().bedWorks())return net.minecraft.world.InteractionResult.CONSUME;
        var selected=sleepPos(state,pos);
        if(!net.parkabird.changedsynergy.ai.PerchSleepService.free(level,selected,player)){
            if(!vertical&&net.parkabird.changedsynergy.ai.PerchSleepService.free(level,other(pos,state),player))selected=other(pos,state);
            else{player.displayClientMessage(net.minecraft.network.chat.Component.translatable("block.minecraft.bed.occupied"),true);return net.minecraft.world.InteractionResult.CONSUME;}
        }
        player.startSleepInBed(selected).ifLeft(problem->{if(problem.getMessage()!=null)player.displayClientMessage(problem.getMessage(),true);});
        return net.minecraft.world.InteractionResult.CONSUME;
    }
    public BlockPos other(BlockPos pos,BlockState state){var direction=vertical?Direction.UP:state.getValue(FACING).getClockWise();return pos.relative(state.getValue(HALF)==DoubleBlockHalf.LOWER?direction:direction.getOpposite());}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,HALF,OCCUPIED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){
        var state=defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());var second=other(c.getClickedPos(),state);
        return second.getY()<c.getLevel().getMaxBuildHeight()&&c.getLevel().getWorldBorder().isWithinBounds(second)
                &&c.getLevel().getBlockState(second).canBeReplaced(c)&&state.canSurvive(c.getLevel(),c.getClickedPos())
                &&(vertical||c.getLevel().getBlockState(second.below()).isFaceSturdy(c.getLevel(),second.below(),Direction.UP))?state:null;
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity entity,ItemStack stack){level.setBlock(other(pos,state),state.setValue(HALF,DoubleBlockHalf.UPPER),Block.UPDATE_ALL);}
    @Override public boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){
        if(state.getValue(HALF)==DoubleBlockHalf.UPPER){var companion=level.getBlockState(other(pos,state));return companion.is(this)&&companion.getValue(HALF)==DoubleBlockHalf.LOWER;}
        return level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP);
    }
    @Override public BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos next){
        if(next.equals(other(pos,state))&&(!neighbor.is(this)||neighbor.getValue(HALF)==state.getValue(HALF)||neighbor.getValue(FACING)!=state.getValue(FACING)))return Blocks.AIR.defaultBlockState();
        if(direction==Direction.DOWN&&state.getValue(HALF)==DoubleBlockHalf.LOWER&&!state.canSurvive(level,pos))return Blocks.AIR.defaultBlockState();
        return super.updateShape(state,direction,neighbor,level,pos,next);
    }
    @Override public void playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        if(!level.isClientSide&&complete(level,pos,state)){
            var second=other(pos,state);var mate=level.getBlockState(second);
            if(!player.isCreative()&&mate.getValue(HALF)==DoubleBlockHalf.LOWER)
                Block.dropResources(mate,level,second,level.getBlockEntity(second),player,player.getMainHandItem());
            // Skip shape recursion: the paired half must not drop again while the player mines this half.
            level.setBlock(second,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS);
        }
        super.playerWillDestroy(level,pos,state,player);
    }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        boolean lower=state.getValue(HALF)==DoubleBlockHalf.LOWER;
        // Sleep anchors sit on the visible beam; a taller collider starts the sleeper inside it.
        if(vertical)return lower?Shapes.or(box(3,0,3,13,3,13),box(6,3,6,10,16,10)):Shapes.or(box(6,0,6,10,10,10),state.getValue(FACING).getAxis()==Direction.Axis.Z?box(0,8,6,16,12,10):box(6,8,0,10,12,16));
        return state.getValue(FACING).getAxis()==Direction.Axis.Z?box(0,0,3,16,15,13):box(3,0,0,13,15,16);
    }
    @Override public boolean isPathfindable(BlockState state,BlockGetter level,BlockPos pos,net.minecraft.world.level.pathfinder.PathComputationType type){return false;}
}
