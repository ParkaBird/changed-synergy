package net.parkabird.changedsynergy.event;

import net.ltxprogrammer.changed.block.DroppedOrange;
import net.ltxprogrammer.changed.init.ChangedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.CreatureSettlementService;
import net.parkabird.changedsynergy.world.OrangeLeafRegrowthData;

/** Player harvesting follows the existing creature harvest and regrowth rules. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerOrangeHarvestEvents {
    private static final ResourceLocation FRUITING_LEAVES =
            ResourceLocation.fromNamespaceAndPath("changed", "orange_tree_leaves");
    private static final ResourceLocation ORANGE =
            ResourceLocation.fromNamespaceAndPath("changed", "orange");

    private PlayerOrangeHarvestEvents() {
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack held = event.getEntity().getMainHandItem();
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        Block leaves = ForgeRegistries.BLOCKS.getValue(FRUITING_LEAVES);
        boolean harvestLeaves = held.is(Items.SHEARS) && leaves != null && state.is(leaves);
        boolean takePile = held.isEmpty() && state.is(ChangedBlocks.DROPPED_ORANGE.get())
                && state.hasProperty(DroppedOrange.ORANGES);
        if (!harvestLeaves && !takePile) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Item orange = ForgeRegistries.ITEMS.getValue(ORANGE);
        if (orange == null) {
            return;
        }
        if (harvestLeaves) {
            BlockState replacement = CreatureSettlementService.localLeaves(level, pos);
            if (!level.setBlock(pos, replacement, Block.UPDATE_ALL)) {
                return;
            }
            OrangeLeafRegrowthData.schedule(level, pos, state, replacement);
            held.hurtAndBreak(1, player,
                    owner -> owner.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        } else {
            int count = state.getValue(DroppedOrange.ORANGES);
            if (count > 1) {
                level.setBlock(pos, state.setValue(DroppedOrange.ORANGES, count - 1), Block.UPDATE_ALL);
            } else {
                level.removeBlock(pos, false);
            }
        }
        ItemStack fruit = new ItemStack(orange);
        if (harvestLeaves) {
            Block.popResource(level, pos, fruit);
        } else if (!player.addItem(fruit)) {
            player.drop(fruit, false);
        }
    }
}
