package net.parkabird.changedsynergy.block;

import java.util.List;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.init.ChangedItems;
import net.ltxprogrammer.changed.item.DarkLatexMask;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.init.ChangedSynergyBlocks;

/**
 * Flat-placed Dark Latex Mask prop bound directly to Changed's {@link DarkLatexMask} item instances.
 * Preserves the placed mask stack's variant/revival NBT and reacts to empty-hand use by equipping
 * directly onto human players or picking up into the hand of transfurred players.
 */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DarkLatexMaskBlock extends Block implements EntityBlock {
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;
    public static final VoxelShape SHAPE_NS = Block.box(4.0D, 0.0D, 4.5D, 12.0D, 3.0D, 11.5D);
    public static final VoxelShape SHAPE_EW = Block.box(4.5D, 0.0D, 4.0D, 11.5D, 3.0D, 12.0D);
    public static final VoxelShape SHAPE_DIAG = Block.box(3.5D, 0.0D, 3.5D, 12.5D, 3.0D, 12.5D);

    public DarkLatexMaskBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(0.35F)
                .sound(SoundType.STONE)
                .noOcclusion()
                .pushReaction(PushReaction.DESTROY));
        registerDefaultState(stateDefinition.any().setValue(ROTATION, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(ROTATION, RotationSegment.convertToSegment(context.getRotation()));
    }

    public static VoxelShape shapeForRotation(int rotation) {
        int mod = Math.floorMod(rotation, 8);
        if (mod == 0) {
            return SHAPE_NS;
        }
        if (mod == 4) {
            return SHAPE_EW;
        }
        return SHAPE_DIAG;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForRotation(state.getValue(ROTATION));
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ROTATION, rotation.rotate(state.getValue(ROTATION), 16));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(ROTATION, mirror.mirror(state.getValue(ROTATION), 16));
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    public BlockState updateShape(
            BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DarkLatexMaskBlockEntity(pos, state);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
        return storedStack(level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof DarkLatexMaskBlockEntity maskEntity) {
            return List.of(maskEntity.getMaskStack());
        }
        return List.of(new ItemStack(ChangedItems.DARK_LATEX_MASK.get()));
    }

    @Override
    public InteractionResult use(
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || !player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack maskStack = storedStack(level, pos);
        if (shouldEquipOnInteract(player, maskStack)) {
            player.setItemSlot(EquipmentSlot.HEAD, maskStack);
            level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 1.0F, 1.0F);
            level.removeBlock(pos, false);
            level.gameEvent(player, GameEvent.EQUIP, pos);
            return InteractionResult.CONSUME;
        }

        player.setItemInHand(hand, maskStack);
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                0.2F, ((level.random.nextFloat() - level.random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
        level.removeBlock(pos, false);
        level.gameEvent(player, GameEvent.BLOCK_DESTROY, pos);
        return InteractionResult.CONSUME;
    }

    private static ItemStack storedStack(BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof DarkLatexMaskBlockEntity maskEntity) {
            return maskEntity.getMaskStack();
        }
        return new ItemStack(ChangedItems.DARK_LATEX_MASK.get());
    }

    private static boolean shouldEquipOnInteract(Player player, ItemStack maskStack) {
        if (ProcessTransfur.isPlayerTransfurred(player)) {
            return false;
        }
        if (!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            return false;
        }
        return !(maskStack.getItem() instanceof DarkLatexMask maskItem)
                || maskItem.allowedToWear(maskStack, player, EquipmentSlot.HEAD);
    }

    public static boolean isPlaceableMask(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof DarkLatexMask;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()) {
            return;
        }
        Player player = event.getEntity();
        if (!player.isShiftKeyDown() || player.isSpectator()) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (!isPlaceableMask(held)) {
            return;
        }

        Level level = event.getLevel();
        BlockPlaceContext placeContext = new BlockPlaceContext(player, event.getHand(), held, event.getHitVec());
        BlockPos placePos = placeContext.getClickedPos();
        if (!placeContext.canPlace()
                || !level.mayInteract(player, placePos)
                || !player.mayUseItemAt(placePos, placeContext.getClickedFace(), held)) {
            return;
        }

        DarkLatexMaskBlock block = ChangedSynergyBlocks.DARK_LATEX_MASK.get();
        BlockState placedState = block.getStateForPlacement(placeContext);
        if (!placedState.canSurvive(level, placePos)
                || !level.isUnobstructed(placedState, placePos, CollisionContext.of(player))) {
            return;
        }

        if (!level.isClientSide) {
            ItemStack placedStack = held.copyWithCount(1);
            if (!level.setBlock(placePos, placedState, Block.UPDATE_ALL)) {
                return;
            }
            if (level.getBlockEntity(placePos) instanceof DarkLatexMaskBlockEntity maskEntity) {
                maskEntity.setMaskStack(placedStack);
            }
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            SoundType soundType = placedState.getSoundType(level, placePos, player);
            level.playSound(null, placePos, soundType.getPlaceSound(), SoundSource.BLOCKS,
                    (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
            level.gameEvent(GameEvent.BLOCK_PLACE, placePos, GameEvent.Context.of(player, placedState));
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }
}
