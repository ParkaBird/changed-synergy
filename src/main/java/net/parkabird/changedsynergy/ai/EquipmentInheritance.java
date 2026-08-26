package net.parkabird.changedsynergy.ai;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/** Moves useful equipment from an absorbed mob onto its absorber. */
public final class EquipmentInheritance {
    private static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
        EquipmentSlot.MAINHAND,
        EquipmentSlot.OFFHAND
    };

    private EquipmentInheritance() {
    }

    public static EquipmentSnapshot capture(LivingEntity victim) {
        Map<EquipmentSlot, ItemStack> equipment =
                new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = victim.getItemBySlot(slot);
            if (!stack.isEmpty() && usefulIn(slot, stack)) {
                equipment.put(slot, stack.copy());
            }
        }
        return new EquipmentSnapshot(victim, equipment);
    }

    public static void transfer(
            EquipmentSnapshot snapshot,
            Mob absorber) {
        LivingEntity victim = snapshot.victim;
        for (Map.Entry<EquipmentSlot, ItemStack> entry
                : snapshot.equipment.entrySet()) {
            EquipmentSlot slot = entry.getKey();
            ItemStack inherited = entry.getValue().copy();
            if (inherited.isEmpty()
                    || !inherited.canEquip(slot, absorber)) {
                continue;
            }
            victim.setItemSlot(slot, ItemStack.EMPTY);
            ItemStack current = absorber.getItemBySlot(slot);
            if (current.isEmpty()
                    || equipmentScore(inherited, slot)
                            > equipmentScore(current, slot)) {
                if (!current.isEmpty()) {
                    absorber.spawnAtLocation(current.copy());
                }
                absorber.setItemSlot(slot, inherited);
            } else {
                absorber.spawnAtLocation(inherited);
            }
        }
    }

    private static boolean usefulIn(
            EquipmentSlot slot,
            ItemStack stack) {
        if (slot.getType() == EquipmentSlot.Type.ARMOR) {
            return true;
        }
        return stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof TridentItem
                || slot == EquipmentSlot.OFFHAND
                        && stack.getItem() instanceof ShieldItem;
    }

    private static double equipmentScore(
            ItemStack stack,
            EquipmentSlot slot) {
        double score = stack.getAttributeModifiers(slot)
                .get(Attributes.ATTACK_DAMAGE)
                .stream()
                .mapToDouble(modifier -> modifier.getAmount())
                .sum();
        if (stack.getItem() instanceof ArmorItem armor) {
            score += armor.getDefense() * 2.0D
                    + armor.getToughness();
        }
        score += stack.getEnchantmentTags().size() * 0.25D;
        return score;
    }

    public record EquipmentSnapshot(
            LivingEntity victim,
            Map<EquipmentSlot, ItemStack> equipment) {
    }
}
