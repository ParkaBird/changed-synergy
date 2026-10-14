package net.parkabird.changedsynergy.event;

import net.ltxprogrammer.changed.block.CannedSoup;
import net.ltxprogrammer.changed.block.DroppedOrange;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.CreatureSettlementService;
import net.parkabird.changedsynergy.world.OrangeLeafRegrowthData;

/** Hand collection of placed food piles and shearing that follows existing leaf-regrowth rules. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerOrangeHarvestEvents {
    private static final ResourceLocation FRUITING_LEAVES =
            ResourceLocation.fromNamespaceAndPath("changed", "orange_tree_leaves");
    private static final ResourceLocation ORANGE =
            ResourceLocation.fromNamespaceAndPath("changed", "orange");

    private PlayerOrangeHarvestEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack held = event.getEntity().getMainHandItem();
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        Block leaves = ForgeRegistries.BLOCKS.getValue(FRUITING_LEAVES);
        boolean harvestLeaves = held.is(Items.SHEARS) && leaves != null && state.is(leaves);
        IntegerProperty pileCount = held.isEmpty() ? pileCount(state) : null;
        boolean takePile = pileCount != null;
        if (!harvestLeaves && !takePile) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack collected;
        if (harvestLeaves) {
            Item orange = ForgeRegistries.ITEMS.getValue(ORANGE);
            if (orange == null) return;
            collected = new ItemStack(orange);
            BlockState replacement = CreatureSettlementService.localLeaves(level, pos);
            if (!level.setBlock(pos, replacement, Block.UPDATE_ALL)) {
                return;
            }
            OrangeLeafRegrowthData.schedule(level, pos, state, replacement);
            level.playSound(null, pos, SoundEvents.SHEEP_SHEAR,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
            held.hurtAndBreak(1, player,
                    owner -> owner.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        } else {
            collected = state.getBlock().getCloneItemStack(state, event.getHitVec(), level, pos, player)
                    .copyWithCount(1);
            if (collected.isEmpty()) return;
            int count = state.getValue(pileCount);
            if (!(count > 1
                    ? level.setBlock(pos, state.setValue(pileCount, count - 1), Block.UPDATE_ALL)
                    : level.removeBlock(pos, false))) return;
        }
        if (harvestLeaves) {
            Block.popResource(level, pos, collected);
        } else {
            if (!player.addItem(collected)) player.drop(collected, false);
            level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP,
                    SoundSource.PLAYERS, 0.2F, 1.0F);
        }
    }

    private static IntegerProperty pileCount(BlockState state) {
        if (state.getBlock() instanceof DroppedOrange) return DroppedOrange.ORANGES;
        if (state.getBlock() instanceof CannedSoup) return CannedSoup.CANS;
        // Match Addon's shared stackable-can type without linking an optional mod on servers without it.
        for (Class<?> type = state.getBlock().getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals("net.foxyas.changedaddon.block.StackableCanBlock")) {
                var count = state.getBlock().getStateDefinition().getProperty("cans");
                return count instanceof IntegerProperty integer ? integer : null;
            }
        }
        return null;
    }
}
