package net.parkabird.changedsynergy.ai;

import java.util.EnumMap;
import java.util.Map;
import net.ltxprogrammer.changed.entity.ChangedEntity;
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
        boolean keepExistingArmor = hasArmor(absorber);
        Map<EquipmentSlot, ItemStack> inheritedEquipment =
                new EnumMap<>(EquipmentSlot.class);
        for (Map.Entry<EquipmentSlot, ItemStack> entry
                : snapshot.equipment.entrySet()) {
            EquipmentSlot slot = entry.getKey();
            ItemStack inherited = entry.getValue().copy();
            if (inherited.isEmpty()
                    || slot.getType() == EquipmentSlot.Type.ARMOR && keepExistingArmor
                    || !fits(absorber, inherited, slot)) {
                continue;
            }
            victim.setItemSlot(slot, ItemStack.EMPTY);
            inheritedEquipment.put(slot, inherited);
        }
        if (inheritedEquipment.isEmpty()) {
            return;
        }
        SafeEntityMutationQueue.queue(
                absorber,
                "absorbed_equipment_" + victim.getUUID(),
                () -> equipInherited(absorber, inheritedEquipment));
    }

    private static void equipInherited(
            Mob absorber,
            Map<EquipmentSlot, ItemStack> inheritedEquipment) {
        // Recheck at execution time: a player or another queued transfer may
        // have equipped armor since the absorption event captured its loot.
        boolean keepExistingArmor = hasArmor(absorber);
        for (Map.Entry<EquipmentSlot, ItemStack> entry
                : inheritedEquipment.entrySet()) {
            EquipmentSlot slot = entry.getKey();
            ItemStack inherited = entry.getValue();
            ItemStack current = absorber.getItemBySlot(slot);
            if (!absorber.isAlive()
                    || slot.getType() == EquipmentSlot.Type.ARMOR && keepExistingArmor
                    || !fits(absorber, inherited, slot)) {
                absorber.spawnAtLocation(inherited);
                continue;
            }
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

    private static boolean hasArmor(Mob creature) {
        for (EquipmentSlot slot : SLOTS) {
            if (slot.getType() == EquipmentSlot.Type.ARMOR
                    && !creature.getItemBySlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean fits(Mob creature, ItemStack stack, EquipmentSlot slot) {
        return stack.canEquip(slot, creature)
                && (!(creature instanceof ChangedEntity changed)
                        || slot.getType() != EquipmentSlot.Type.ARMOR
                        || CreatureArmorService.canWear(changed, stack, slot));
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
