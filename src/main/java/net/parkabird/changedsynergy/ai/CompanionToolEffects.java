package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeMod;
import net.parkabird.changedsynergy.world.inventory.BondedInventoryService;

/** Tool effects shared by companion work, using the actual working stack. */
public final class CompanionToolEffects {
    private CompanionToolEffects() { }

    public static float toolMiningSpeed(ItemStack tool, BlockState state) {
        float speed = tool.getDestroySpeed(state);
        int efficiency = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, tool);
        if (speed > 1 && efficiency > 0) speed += efficiency * efficiency + 1;
        return speed;
    }

    public static float miningSpeed(LivingEntity worker, ItemStack tool, BlockState state) {
        float speed = toolMiningSpeed(tool, state);
        if (MobEffectUtil.hasDigSpeed(worker))
            speed *= 1 + (MobEffectUtil.getDigSpeedAmplification(worker) + 1) * .2F;
        var fatigue = worker.getEffect(MobEffects.DIG_SLOWDOWN);
        if (fatigue != null) speed *= switch (fatigue.getAmplifier()) {
            case 0 -> .3F;
            case 1 -> .09F;
            case 2 -> .0027F;
            default -> .00081F;
        };
        if (worker.isEyeInFluidType(ForgeMod.WATER_TYPE.get()) && !EnchantmentHelper.hasAquaAffinity(worker)) speed /= 5;
        if (!worker.onGround()) speed /= 5;
        return speed;
    }

    public static int fishingWaitTicks(ItemStack rod) {
        // Keep Changed's eight-second base cast. Lure saves two seconds per level.
        int lure = EnchantmentHelper.getFishingSpeedBonus(rod);
        return Math.max(20, 160 - 40 * lure);
    }

    public record WorkTool(Container inventory, int slot, ItemStack stack) {
        public void damage(ChangedEntity worker) {
            if (inventory == null) return; // The NPC's own fallback tool is not a stored item.
            stack.hurtAndBreak(1, worker, broken -> broken.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            GatheringToolPresentation.updateInventoryItem(worker, inventory, slot, stack);
        }
    }

    private static Container inventory(ChangedEntity worker) {
        // An ordinary friend must not become a tame pet just to access its tools.
        return BondedInventoryService.inventory(worker, LatexSocialMemory.getPetOwner(worker));
    }

    public static WorkTool gatheringPickaxe(ChangedEntity worker, BlockState state) {
        return gatheringPickaxe(worker, inventory(worker), state);
    }

    public static WorkTool gatheringPickaxe(ChangedEntity worker, Container inventory, BlockState state) {
        int selected = -1;
        float best = -1;
        ItemStack tool = ItemStack.EMPTY;
        for (int slot = 0; slot < Math.min(24, inventory.getContainerSize()); slot++) {
            ItemStack candidate = GatheringToolPresentation.inventoryItem(worker, inventory, slot);
            if (candidate.getItem() instanceof PickaxeItem && candidate.isCorrectToolForDrops(state)) {
                float speed = toolMiningSpeed(candidate, state);
                if (speed > best) { selected = slot; best = speed; tool = candidate; }
            }
        }
        return selected < 0 ? new WorkTool(null, -1, new ItemStack(Items.IRON_PICKAXE))
                : new WorkTool(inventory, selected, tool);
    }

    public static WorkTool gatheringRod(ChangedEntity worker) {
        Container inventory = inventory(worker);
        int selected = -1, best = -1;
        ItemStack tool = ItemStack.EMPTY;
        for (int slot = 0; slot < Math.min(24, inventory.getContainerSize()); slot++) {
            ItemStack candidate = GatheringToolPresentation.inventoryItem(worker, inventory, slot);
            if (candidate.getItem() instanceof FishingRodItem) {
                int quality = EnchantmentHelper.getFishingSpeedBonus(candidate) * 10
                        + EnchantmentHelper.getFishingLuckBonus(candidate);
                if (quality > best) { selected = slot; best = quality; tool = candidate; }
            }
        }
        return selected < 0 ? new WorkTool(null, -1, new ItemStack(Items.FISHING_ROD))
                : new WorkTool(inventory, selected, tool);
    }
}
