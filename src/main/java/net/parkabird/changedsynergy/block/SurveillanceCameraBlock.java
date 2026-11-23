package net.parkabird.changedsynergy.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.parkabird.changedsynergy.init.ChangedSynergyBlockEntities;

/** Wall-mounted CRISSCROSS surveillance probe: static JSON bracket + rotating BlockEntity gimbal head. */
public final class SurveillanceCameraBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final VoxelShape SHAPE_NORTH = Block.box(3.0D, 3.0D, 2.0D, 13.0D, 16.0D, 16.0D);
    private static final VoxelShape SHAPE_SOUTH = Block.box(3.0D, 3.0D, 0.0D, 13.0D, 16.0D, 14.0D);
    private static final VoxelShape SHAPE_EAST = Block.box(0.0D, 3.0D, 3.0D, 14.0D, 16.0D, 13.0D);
    private static final VoxelShape SHAPE_WEST = Block.box(2.0D, 3.0D, 3.0D, 16.0D, 16.0D, 13.0D);

    public SurveillanceCameraBlock() {
        super(Properties.of().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer != null && level.getBlockEntity(pos) instanceof SurveillanceCameraBlockEntity camera) {
            camera.initializeDefaultOrientation(state, placer.getEyePosition());
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SurveillanceCameraBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ChangedSynergyBlockEntities.SURVEILLANCE_CAMERA.get()) return null;
        return level.isClientSide
                ? (lvl, p, st, be) -> ((SurveillanceCameraBlockEntity) be).clientTick(lvl, p, st)
                : (lvl, p, st, be) -> ((SurveillanceCameraBlockEntity) be).serverTick(lvl, p, st);
    }
}
