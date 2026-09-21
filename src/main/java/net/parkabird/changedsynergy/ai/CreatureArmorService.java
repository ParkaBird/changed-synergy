package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.variant.ClothingShape;
import net.ltxprogrammer.changed.item.ExtendedItemProperties;
import net.ltxprogrammer.changed.world.enchantments.FormFittingEnchantment;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.parkabird.changedsynergy.event.NpcDispositionEvents;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/** Shared checks and direct interactions for creature equipment. */
public final class CreatureArmorService {
    private CreatureArmorService() {
    }

    public static boolean canWear(ChangedEntity creature, ItemStack original, EquipmentSlot slot) {
        if (original.isEmpty()) {
            return true;
        }
        ItemStack fitted = FormFittingEnchantment.getFormFitted(creature, original, slot);
        if (fitted.getItem() instanceof ExtendedItemProperties properties) {
            if (!properties.allowedInSlot(fitted, creature, slot)) {
                return false;
            }
        } else {
            var shape = creature.getEntityShape();
            boolean compatible = switch (slot) {
                case HEAD -> shape.getHeadShape() == ClothingShape.Head.ANTHRO;
                case CHEST -> shape.getTorsoShape() == ClothingShape.Torso.ANTHRO;
                case LEGS -> shape.getLegsShape() == ClothingShape.Legs.BIPEDAL;
                case FEET -> shape.getFeetShape() == ClothingShape.Feet.BIPEDAL;
                default -> true;
            };
            if (!compatible) {
                return false;
            }
        }
        return creature.isItemAllowedInSlot(fitted, slot);
    }

    /**
     * Includes ordinary and modded melee weapons which advertise positive
     * main-hand attack damage. Bows, crossbows and shields remain outside this
     * interaction even when another mod adds attributes to them.
     */
    public static boolean isMeleeWeapon(ItemStack stack) {
        if (stack.isEmpty()
                || stack.getItem() instanceof ProjectileWeaponItem
                || stack.getItem() instanceof ShieldItem) {
            return false;
        }
        if (stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof TridentItem) {
            return true;
        }
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE)
                .stream()
                .anyMatch(modifier -> modifier.getAmount() > 0.0D);
    }

    /** Consumes shift+equipment interactions, including refusals, before native handlers. */
    public static boolean handleInteraction(PlayerInteractEvent.EntityInteract event) {
        ItemStack offered = event.getItemStack();
        ArmorItem armor = offered.getItem() instanceof ArmorItem item ? item : null;
        boolean weapon = armor == null && isMeleeWeapon(offered);
        boolean retrieveWeapon = offered.isEmpty()
                && event.getTarget() instanceof ChangedEntity candidate
                && isMeleeWeapon(candidate.getMainHandItem())
                && !(candidate.getMainHandItem().hasTag()
                        && candidate.getMainHandItem().getTag().getBoolean(
                                "ChangedSynergyTemporaryGatheringTool"));
        if (!(event.getTarget() instanceof ChangedEntity creature)
                || !event.getEntity().isShiftKeyDown()
                || event.getEntity().isSpectator()
                || armor == null && !weapon && !retrieveWeapon
                || TakeoverService.carrying(creature)
                || creature.getUnderlyingPlayer() != null
                || !CreatureSocialProfile.allowsPersonalRelationship(creature)) {
            return false;
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return true;
        }
        boolean owner = LatexSocialMemory.isPetOwner(creature, player);
        if (!ChangedSynergyGameRules.enabled(player.level(), owner
                        ? ChangedSynergyGameRules.BOND_SYSTEM
                        : ChangedSynergyGameRules.FRIENDSHIP_SYSTEM)
                || !owner && !CreaturePersonality.hasTrustedRelationship(creature, player)
                || NpcDispositionEvents.hasHostileDisposition(creature, player)) {
            tell(player, weapon || retrieveWeapon, "requires_friend");
            return true;
        }
        var grab = BondedSuitService.ability(creature);
        if (!creature.isAlive() || creature.isRemoved()
                || creature.level() != player.level() || player.distanceToSqr(creature) > 64.0D
                || creature.getTarget() != null || creature.noPhysics
                || creature.isPassenger() || creature.isVehicle()
                || grab != null && grab.grabbedEntity != null) {
            tell(player, weapon || retrieveWeapon, "busy");
            return true;
        }
        if (weapon || retrieveWeapon) {
            // A provisioner's displayed work tool is only a temporary copy.
            // Restore the real main hand before deciding whether it is free.
            GatheringToolPresentation.restore(creature);
        }
        if (retrieveWeapon) {
            ItemStack removed = creature.getMainHandItem();
            if (!isMeleeWeapon(removed)) return true;
            creature.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            if (!player.getInventory().add(removed)) player.drop(removed, false);
            return true;
        }
        EquipmentSlot slot = weapon
                ? EquipmentSlot.MAINHAND : armor.getEquipmentSlot();
        if (!creature.getItemBySlot(slot).isEmpty()) {
            tell(player, weapon, "occupied");
            return true;
        }
        ItemStack held = player.getItemInHand(event.getHand());
        boolean valid = !held.isEmpty()
                && (weapon
                        ? isMeleeWeapon(held)
                                && creature.isItemAllowedInSlot(held, slot)
                        : held.getItem() == armor
                                && held.canEquip(slot, creature)
                                && canWear(creature, held, slot));
        if (!valid) {
            tell(player, weapon, "incompatible");
            return true;
        }
        // Take the item from the authoritative hand before equipping it. If another
        // handler changes the creature during this interaction, rollback the exact
        // extracted stack instead of ever creating a second copy.
        ItemStack equipped = player.getAbilities().instabuild
                ? held.copyWithCount(1) : held.split(1);
        if (!creature.getItemBySlot(slot).isEmpty()) {
            restore(player, event, equipped);
            tell(player, weapon, "occupied");
            return true;
        }
        try {
            creature.setItemSlot(slot, equipped);
        } catch (RuntimeException failure) {
            if (creature.getItemBySlot(slot).isEmpty()) restore(player, event, equipped);
            throw failure;
        }
        creature.setDropChance(slot, 2.0F);
        creature.setPersistenceRequired();
        // setItemSlot already invokes vanilla's equip sound and equipment synchronization.
        if (!weapon) {
            player.displayClientMessage(Component.translatable(
                    "message.changed_synergy.armor.equipped",
                    creature.getDisplayName(), equipped.getHoverName()), true);
        }
        return true;
    }

    private static void restore(ServerPlayer player, PlayerInteractEvent.EntityInteract event,
            ItemStack extracted) {
        if (player.getAbilities().instabuild || extracted.isEmpty()) return;
        ItemStack hand = player.getItemInHand(event.getHand());
        if (hand.isEmpty()) player.setItemInHand(event.getHand(), extracted);
        else if (ItemStack.isSameItemSameTags(hand, extracted)
                && hand.getCount() < hand.getMaxStackSize()) hand.grow(extracted.getCount());
        else if (!player.getInventory().add(extracted)) player.drop(extracted, false);
    }

    private static void tell(ServerPlayer player, boolean weapon, String reason) {
        player.displayClientMessage(Component.translatable(
                "message.changed_synergy." + (weapon ? "weapon" : "armor") + "." + reason),
                true);
    }
}
