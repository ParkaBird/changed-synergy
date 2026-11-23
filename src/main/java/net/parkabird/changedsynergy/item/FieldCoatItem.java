package net.parkabird.changedsynergy.item;

import java.util.List;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.data.AccessorySlotContext;
import net.ltxprogrammer.changed.data.AccessorySlotType;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.entity.variant.ClothingShape;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.ltxprogrammer.changed.init.ChangedSounds;
import net.ltxprogrammer.changed.init.ChangedTags;
import net.ltxprogrammer.changed.item.ClothingItem;
import net.ltxprogrammer.changed.item.ClothingState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.StateDefinition;
import net.parkabird.changedsynergy.ai.CreatureSocialProfile;

/** Field Coat: adult-only research parka tailored for bipedal, female, and Grizzly forms, with upper-only fit on legless, tail-legged, and taur bodies. */
public final class FieldCoatItem extends ClothingItem {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "changed_synergy", "textures/models/field_coat.png");

    public FieldCoatItem() {
        super();
        this.registerDefaultState(this.stateDefinition.any().setValue(CLOSED, false));
    }

    public static boolean fits(@Nullable LivingEntity wearer) {
        if (wearer == null || wearer.isBaby()) return false;
        var standShape = HyenaTrailclothItem.standShape(wearer);
        if (standShape != null) {
            return standShape.torsoShape == ClothingShape.Torso.ANTHRO;
        }
        var body = HyenaTrailclothItem.body(wearer);
        if (body == null) return wearer instanceof Player;
        if (body.isBaby() || EagleOwlAviatorItem.juvenile(wearer) || CreatureSocialProfile.isJuvenile(body)) {
            return false;
        }
        if (body.getType().is(ChangedTags.EntityTypes.ARMLESS)) {
            return false;
        }
        return CaveMinerOutfitItem.isTaur(wearer)
                || body.getEntityShape().torsoShape == ClothingShape.Torso.ANTHRO;
    }

    public static boolean hasLowerSection(@Nullable LivingEntity wearer) {
        return HyenaTrailclothItem.hasLowerSection(wearer) && !CaveMinerOutfitItem.isTaur(wearer);
    }

    public static final String DISPLAY_CUT_TAG = "DisplayCut";

    public static boolean isGrizzlyDisplay(ItemStack stack) {
        return stack.hasTag() && "grizzly".equals(stack.getTag().getString(DISPLAY_CUT_TAG));
    }

    public static boolean closed(ItemStack stack) {
        return stack.getItem() instanceof FieldCoatItem item
                && Boolean.TRUE.equals(item.getClothingState(stack).getValue(CLOSED));
    }

    @Override
    protected void createClothingStateDefinition(StateDefinition.Builder<ClothingItem, ClothingState> builder) {
        super.createClothingStateDefinition(builder);
        builder.add(CLOSED);
    }

    @Override
    public void accessoryInteract(AccessorySlotContext<?> context) {
        super.accessoryInteract(context);
        ClothingState next = getClothingState(context.stack()).cycle(CLOSED);
        setClothingState(context.stack(), next);
        SoundEvent sound = getEquipSound(context.stack());
        if (sound != null) {
            context.wearer().playSound(sound, 1.0F, 1.0F);
        }
    }

    @Override
    public boolean allowedInSlot(ItemStack stack, LivingEntity wearer, AccessorySlotType slot) {
        if (!fits(wearer)) return false;
        if (slot != ChangedAccessorySlots.BODY.get() && slot != ChangedAccessorySlots.FULL_BODY.get()) {
            return false;
        }
        return AccessorySlots.getForEntity(wearer).map(slots -> slots.getSlotTypes().filter(type -> type != slot)
                .noneMatch(type -> slots.getItem(type).filter(item -> item.getItem() == this).isPresent())
                && (!hasLowerSection(wearer) || !slots.hasSlot(ChangedAccessorySlots.LEGS.get())
                        || AccessorySlots.isSlotAvailable(wearer, ChangedAccessorySlots.LEGS.get())))
                .orElse(false);
    }

    @Override
    public boolean shouldDisableSlot(AccessorySlotContext<?> context, AccessorySlotType other) {
        return other == ChangedAccessorySlots.LEGS.get() && hasLowerSection(context.wearer());
    }

    @Override
    public SoundEvent getEquipSound(ItemStack stack) {
        return ChangedSounds.COAT_EQUIP.get();
    }

    @Override
    public SoundEvent getBreakSound(ItemStack stack) {
        return ChangedSounds.COAT_BREAK.get();
    }

    @Override
    public ResourceLocation getTexture(ItemStack stack, Entity entity) {
        return TEXTURE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flags) {
        if (level != null && level.isClientSide()) addInteractInstructions(lines::add);
        super.appendHoverText(stack, level, lines, flags);
        lines.add(Component.translatable("item.changed_synergy.field_coat.desc"));
    }
}
