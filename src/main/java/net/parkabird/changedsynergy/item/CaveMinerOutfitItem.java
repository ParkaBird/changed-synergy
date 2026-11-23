package net.parkabird.changedsynergy.item;

import java.util.List;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.data.AccessorySlotContext;
import net.ltxprogrammer.changed.data.AccessorySlotType;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.entity.variant.ClothingShape;
import net.ltxprogrammer.changed.entity.variant.EntityShape;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.ltxprogrammer.changed.item.ClothingItem;
import net.ltxprogrammer.changed.item.ClothingState;
import net.ltxprogrammer.changed.item.ShirtItem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.parkabird.changedsynergy.entity.CaveMinerLightTracker;

/** Cave Miner outfit: headlamp-only on juveniles, tailored across bipeds, females, multi-arms, legless, and taurs, with a right-click moving light. */
public final class CaveMinerOutfitItem extends ShirtItem {
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "changed_synergy", "textures/models/cave_miner_outfit.png");

    public CaveMinerOutfitItem() {
        super();
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
    }

    public static boolean fits(LivingEntity wearer) {
        if (wearer.isBaby()) return false;
        var standShape = HyenaTrailclothItem.standShape(wearer);
        if (standShape != null) {
            return standShape == EntityShape.TAUR || standShape.torsoShape == ClothingShape.Torso.ANTHRO;
        }
        var body = HyenaTrailclothItem.body(wearer);
        return body == null ? wearer instanceof Player : EagleOwlAviatorItem.juvenile(wearer)
                || body instanceof net.ltxprogrammer.changed.entity.beast.MilkPudding
                || isTaur(wearer)
                || body.getEntityShape().torsoShape == ClothingShape.Torso.ANTHRO;
    }

    public static boolean isTaur(@Nullable LivingEntity wearer) {
        if (HyenaTrailclothItem.standShape(wearer) == EntityShape.TAUR) return true;
        var body = wearer == null ? null : HyenaTrailclothItem.body(wearer);
        return body != null && (body instanceof net.ltxprogrammer.changed.entity.beast.LatexTaur<?>
                || body.getEntityShape() == EntityShape.TAUR);
    }

    public static boolean lit(ItemStack stack) {
        return stack.getItem() instanceof CaveMinerOutfitItem item
                && Boolean.TRUE.equals(item.getClothingState(stack).getValue(LIT));
    }

    @Override
    protected void createClothingStateDefinition(StateDefinition.Builder<ClothingItem, ClothingState> builder) {
        super.createClothingStateDefinition(builder);
        builder.add(LIT);
    }

    @Override
    public void accessoryInteract(AccessorySlotContext<?> context) {
        super.accessoryInteract(context);
        ClothingState next = getClothingState(context.stack()).cycle(LIT);
        setClothingState(context.stack(), next);
        boolean active = Boolean.TRUE.equals(next.getValue(LIT));
        if (!context.wearer().level().isClientSide()) {
            context.wearer().level().playSound(null, context.wearer().blockPosition(),
                    active ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
                    SoundSource.PLAYERS, 0.45F, active ? 1.35F : 0.95F);
            CaveMinerLightTracker.syncNow(context.wearer(), active);
        }
    }

    @Override
    public boolean allowedInSlot(ItemStack stack, LivingEntity wearer, AccessorySlotType slot) {
        if (!fits(wearer)) return false;
        boolean young = EagleOwlAviatorItem.juvenile(wearer);
        if (young ? slot != ChangedAccessorySlots.HEAD.get() && slot != ChangedAccessorySlots.FACE.get()
                : slot != ChangedAccessorySlots.BODY.get() && slot != ChangedAccessorySlots.FULL_BODY.get()) {
            return false;
        }
        return AccessorySlots.getForEntity(wearer).map(slots -> slots.getSlotTypes().filter(type -> type != slot)
                .noneMatch(type -> slots.getItem(type).filter(item -> item.getItem() == this).isPresent())
                && (young || !slots.hasSlot(ChangedAccessorySlots.HEAD.get())
                        || AccessorySlots.isSlotAvailable(wearer, ChangedAccessorySlots.HEAD.get()))
                && (young || !HyenaTrailclothItem.hasLowerSection(wearer)
                        || !slots.hasSlot(ChangedAccessorySlots.LEGS.get())
                        || AccessorySlots.isSlotAvailable(wearer, ChangedAccessorySlots.LEGS.get())))
                .orElse(false);
    }

    @Override
    public boolean shouldDisableSlot(AccessorySlotContext<?> context, AccessorySlotType other) {
        if (EagleOwlAviatorItem.juvenile(context.wearer())) return false;
        return other == ChangedAccessorySlots.HEAD.get()
                || other == ChangedAccessorySlots.LEGS.get() && HyenaTrailclothItem.hasLowerSection(context.wearer());
    }

    @Override
    public ResourceLocation getTexture(ItemStack stack, Entity entity) {
        return TEXTURE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flags) {
        super.appendHoverText(stack, level, lines, flags);
        if (level != null && level.isClientSide()) addInteractInstructions(lines::add);
        lines.add(Component.translatable("item.changed_synergy.cave_miner_outfit.desc"));
    }
}
