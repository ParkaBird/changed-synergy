package net.parkabird.changedsynergy.item;

import java.util.List;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.data.AccessorySlotContext;
import net.ltxprogrammer.changed.data.AccessorySlotType;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.Gender;
import net.ltxprogrammer.changed.entity.GenderedEntity;
import net.ltxprogrammer.changed.entity.variant.ClothingShape;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.ltxprogrammer.changed.init.ChangedTags;
import net.ltxprogrammer.changed.item.ShirtItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.parkabird.changedsynergy.ai.CreatureSocialProfile;

/** One native body accessory, with an optional lower section for bipedal wearers. */
public final class HyenaTrailclothItem extends ShirtItem {
    @Nullable public static ChangedEntity body(LivingEntity wearer) {
        return IAbstractChangedEntity.forEitherSafe(wearer).map(IAbstractChangedEntity::getChangedEntity).orElse(null);
    }

    @javax.annotation.Nullable
    public static net.ltxprogrammer.changed.entity.variant.EntityShape standShape(@javax.annotation.Nullable LivingEntity wearer) {
        if (wearer instanceof net.ltxprogrammer.changed.entity.decoration.AbstractArmorStand stand) {
            return stand.getEntityShape();
        }
        if (wearer instanceof net.minecraft.world.entity.decoration.ArmorStand) {
            return net.ltxprogrammer.changed.entity.variant.EntityShape.ANTHRO;
        }
        return null;
    }

    public static boolean isArmorStand(@javax.annotation.Nullable LivingEntity wearer) {
        return wearer instanceof net.minecraft.world.entity.decoration.ArmorStand;
    }

    public static boolean fits(LivingEntity wearer) {
        if (wearer.isBaby()) return false;
        var standShape = standShape(wearer);
        if (standShape != null) {
            return standShape.torsoShape == ClothingShape.Torso.ANTHRO;
        }
        var body = body(wearer);
        return body == null ? wearer instanceof Player
                : !body.isBaby() && !CreatureSocialProfile.isJuvenile(body)
                    && !body.getType().is(ChangedTags.EntityTypes.ARMLESS)
                    && body.getEntityShape().torsoShape == ClothingShape.Torso.ANTHRO;
    }

    public static boolean hasLowerSection(LivingEntity wearer) {
        var standShape = standShape(wearer);
        if (standShape != null) {
            return standShape.legsShape == ClothingShape.Legs.BIPEDAL;
        }
        var body = body(wearer);
        return body == null || body.getEntityShape().legsShape == ClothingShape.Legs.BIPEDAL;
    }

    public static boolean female(LivingEntity wearer) {
        return body(wearer) instanceof GenderedEntity gendered && gendered.getGender() == Gender.FEMALE;
    }

    /** Keep the legacy feminine cut for Changed and addon females. */
    public static boolean synergyFemale(LivingEntity wearer) {
        var body = body(wearer);
        var id = body == null ? null : net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(body.getType());
        return id != null && id.getNamespace().equals("changed_synergy") && female(wearer);
    }

    @Override public boolean allowedInSlot(ItemStack stack, LivingEntity wearer, AccessorySlotType slot) {
        return (slot == ChangedAccessorySlots.BODY.get() || slot == ChangedAccessorySlots.FULL_BODY.get()) && fits(wearer)
                && AccessorySlots.getForEntity(wearer).map(slots->slots.getItem(slot==ChangedAccessorySlots.BODY.get()?ChangedAccessorySlots.FULL_BODY.get():ChangedAccessorySlots.BODY.get())
                    .map(other->other.getItem()!=this).orElse(true)).orElse(false)
                && (!hasLowerSection(wearer) || AccessorySlots.getForEntity(wearer)
                    .map(slots -> !slots.hasSlot(ChangedAccessorySlots.LEGS.get())
                        || AccessorySlots.isSlotAvailable(wearer, ChangedAccessorySlots.LEGS.get())).orElse(false));
    }

    @Override public boolean shouldDisableSlot(AccessorySlotContext<?> context, AccessorySlotType other) {
        return other == ChangedAccessorySlots.LEGS.get() && hasLowerSection(context.wearer());
    }

    @Override public ResourceLocation getTexture(ItemStack stack, Entity entity) {
        boolean female = entity instanceof LivingEntity wearer && female(wearer);
        boolean hyena=entity instanceof LivingEntity wearer && body(wearer) instanceof net.parkabird.changedsynergy.entity.LatexSpottedHyena;
        if (female && hyena) return ResourceLocation.parse("changed_synergy:textures/models/hyena_trailcloth_synergy_female.png");
        return ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/models/hyena_trailcloth_" + (female ? "female" : "male") + (hyena?"":"_other") + ".png");
    }

    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flags) {
        super.appendHoverText(stack, level, lines, flags);
        lines.add(Component.translatable("item.changed_synergy.hyena_trailcloth_outfit.desc").withStyle(ChatFormatting.GRAY));
    }
}
