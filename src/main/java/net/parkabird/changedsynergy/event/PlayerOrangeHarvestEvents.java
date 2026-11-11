package net.parkabird.changedsynergy.event;

import net.ltxprogrammer.changed.block.CannedSoup;
import net.ltxprogrammer.changed.block.DroppedOrange;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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

/** Hand collection of placed food piles and shearing or latex bare-hand harvesting that follows existing leaf-regrowth rules. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerOrangeHarvestEvents {
    private static final ResourceLocation FRUITING_LEAVES =
            ResourceLocation.fromNamespaceAndPath("changed", "orange_tree_leaves");
    private static final ResourceLocation ORANGE =
            ResourceLocation.fromNamespaceAndPath("changed", "orange");

    private static Block fruitingLeaves;
    private static Item orangeItem;

    private PlayerOrangeHarvestEvents() {
    }

    private static Block fruitingLeaves() {
        if (fruitingLeaves == null) {
            fruitingLeaves = ForgeRegistries.BLOCKS.getValue(FRUITING_LEAVES);
        }
        return fruitingLeaves;
    }

    private static Item orangeItem() {
        if (orangeItem == null) {
            orangeItem = ForgeRegistries.ITEMS.getValue(ORANGE);
        }
        return orangeItem;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().isSpectator() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack held = event.getEntity().getMainHandItem();
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        Block leaves = fruitingLeaves();
        boolean isLatex = isLatexPlayer(event.getEntity());
        boolean harvestLeaves = leaves != null && state.is(leaves)
                && (held.is(Items.SHEARS) || (isLatex && held.isEmpty()));
        IntegerProperty pileCount = held.isEmpty() ? pileCount(state) : null;
        boolean takePile = pileCount != null;
        if (!harvestLeaves && !takePile) {
            return;
        }

        // Provide immediate client-side feedback (instant hand swing and local sound) without waiting for server round-trip.
        if (event.getLevel().isClientSide()) {
            event.getEntity().swing(event.getHand());
            if (harvestLeaves) {
                event.getLevel().playSound(event.getEntity(), pos,
                        held.is(Items.SHEARS) ? SoundEvents.SHEEP_SHEAR : SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        player.swing(event.getHand(), false);
        ItemStack collected;
        if (harvestLeaves) {
            Item orange = orangeItem();
            if (orange == null) return;
            collected = new ItemStack(orange);
            BlockState replacement = CreatureSettlementService.localLeaves(level, pos);
            if (!level.setBlock(pos, replacement, Block.UPDATE_ALL)) {
                return;
            }
            OrangeLeafRegrowthData.schedule(level, pos, state, replacement);
            if (held.is(Items.SHEARS)) {
                level.playSound(player, pos, SoundEvents.SHEEP_SHEAR,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
                held.hurtAndBreak(1, player,
                        owner -> owner.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            } else {
                level.playSound(player, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
            }
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
            if (held.isEmpty() && player.addItem(collected)) {
                level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP,
                        SoundSource.PLAYERS, 0.2F, 1.0F);
            } else {
                Block.popResource(level, pos, collected);
            }
        } else {
            if (player.addItem(collected)) {
                level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP,
                        SoundSource.PLAYERS, 0.2F, 1.0F);
            } else {
                player.drop(collected, false);
            }
        }
    }

    private static boolean isLatexPlayer(Player player) {
        return ProcessTransfur.isPlayerLatex(player) || ProcessTransfur.isPlayerTransfurred(player);
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
