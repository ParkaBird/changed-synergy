package net.parkabird.changedsynergy.item;

import java.util.List;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.data.AccessorySlotContext;
import net.ltxprogrammer.changed.data.AccessorySlotType;
import net.ltxprogrammer.changed.entity.variant.ClothingShape;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.ltxprogrammer.changed.init.ChangedSounds;
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

/** Burrow Shawl: faction neck accessory tailored for Manul, Gray Fox, female, and standard forms while leaving shirt and pants slots open. */
public final class BurrowShawlItem extends ClothingItem {
    public static final String DISPLAY_CUT_TAG = "DisplayCut";
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "changed_synergy", "textures/models/burrow_shawl.png");

    public BurrowShawlItem() {
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
        return CaveMinerOutfitItem.isTaur(wearer)
                || body.getEntityShape().torsoShape == ClothingShape.Torso.ANTHRO;
    }

    public static boolean closed(ItemStack stack) {
        return stack.getItem() instanceof BurrowShawlItem item
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
        return fits(wearer) && slot == ChangedAccessorySlots.NECK.get();
    }

    @Override
    public boolean shouldDisableSlot(AccessorySlotContext<?> context, AccessorySlotType other) {
        return false;
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
        lines.add(Component.translatable("item.changed_synergy.burrow_shawl.desc"));
    }
}
