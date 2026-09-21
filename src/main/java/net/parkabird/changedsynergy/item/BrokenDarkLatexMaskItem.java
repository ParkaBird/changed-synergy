package net.parkabird.changedsynergy.item;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.parkabird.changedsynergy.ai.BondedRevivalService;

public final class BrokenDarkLatexMaskItem extends Item {
    public BrokenDarkLatexMaskItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        String bondName = BondedRevivalService.bondName(stack);
        if (!bondName.isBlank()) {
            tooltip.add(Component.translatable(
                    "tooltip.changed_synergy.revival_mask.bond", bondName)
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable(
                "tooltip.changed_synergy.broken_dark_latex_mask.repair")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
