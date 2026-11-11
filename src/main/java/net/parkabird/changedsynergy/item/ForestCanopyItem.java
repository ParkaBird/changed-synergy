package net.parkabird.changedsynergy.item;

import java.util.List;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.data.*;
import net.ltxprogrammer.changed.entity.variant.ClothingShape;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.ltxprogrammer.changed.item.ShirtItem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Native clothing: adult upper/lower sections, or only the circlet for juveniles. */
public final class ForestCanopyItem extends ShirtItem {
    public static boolean fits(LivingEntity wearer) {
        var body = HyenaTrailclothItem.body(wearer);
        return body == null ? wearer instanceof Player : EagleOwlAviatorItem.juvenile(wearer)
                || body instanceof net.ltxprogrammer.changed.entity.beast.MilkPudding
                || body.getEntityShape().torsoShape == ClothingShape.Torso.ANTHRO;
    }

    @Override public boolean allowedInSlot(ItemStack stack, LivingEntity wearer, AccessorySlotType slot) {
        if (!fits(wearer)) return false;
        boolean young = EagleOwlAviatorItem.juvenile(wearer);
        if (young ? slot != ChangedAccessorySlots.HEAD.get() && slot != ChangedAccessorySlots.FACE.get()
                : slot != ChangedAccessorySlots.BODY.get() && slot != ChangedAccessorySlots.FULL_BODY.get()) return false;
        return AccessorySlots.getForEntity(wearer).map(slots -> slots.getSlotTypes().filter(type -> type != slot)
                .noneMatch(type -> slots.getItem(type).filter(item -> item.getItem() == this).isPresent())
                && (slot == ChangedAccessorySlots.HEAD.get() || !slots.hasSlot(ChangedAccessorySlots.HEAD.get())
                        || AccessorySlots.isSlotAvailable(wearer, ChangedAccessorySlots.HEAD.get()))
                && (young || !HyenaTrailclothItem.hasLowerSection(wearer) || !slots.hasSlot(ChangedAccessorySlots.LEGS.get())
                        || AccessorySlots.isSlotAvailable(wearer, ChangedAccessorySlots.LEGS.get()))).orElse(false);
    }

    @Override public boolean shouldDisableSlot(AccessorySlotContext<?> context, AccessorySlotType other) {
        return other == ChangedAccessorySlots.HEAD.get() && context.slotType() != ChangedAccessorySlots.HEAD.get()
                || !EagleOwlAviatorItem.juvenile(context.wearer()) && other == ChangedAccessorySlots.LEGS.get()
                    && HyenaTrailclothItem.hasLowerSection(context.wearer());
    }

    @Override public ResourceLocation getTexture(ItemStack stack, Entity entity) {
        return ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/models/forest_canopy_outfit.png");
    }

    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flags) {
        super.appendHoverText(stack, level, lines, flags);
        lines.add(Component.translatable("item.changed_synergy.forest_canopy_outfit.desc"));
    }
}
