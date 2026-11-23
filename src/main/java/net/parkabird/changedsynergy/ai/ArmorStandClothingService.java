package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.data.AccessorySlotContext;
import net.ltxprogrammer.changed.data.AccessorySlotType;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.ltxprogrammer.changed.item.ClothingItem;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.item.BurrowShawlItem;
import net.parkabird.changedsynergy.item.CaveMinerOutfitItem;
import net.parkabird.changedsynergy.item.EagleOwlAviatorItem;
import net.parkabird.changedsynergy.item.FieldCoatItem;

import javax.annotation.Nullable;
import java.util.List;

@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArmorStandClothingService {
    public static final String DISPLAY_ONLY_TAG = "ChangedSynergyDisplayOnly";

    private ArmorStandClothingService() {}

    public static boolean isDisplayOnly(@Nullable Entity entity) {
        return entity instanceof ArmorStand && entity.getPersistentData().getBoolean(DISPLAY_ONLY_TAG);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getTarget() instanceof ArmorStand stand)) return;
        if (isDisplayOnly(stand)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        handleStandInteraction(event, stand);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (isDisplayOnly(event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(AttackEntityEvent event) {
        if (isDisplayOnly(event.getTarget()) && !event.getEntity().isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (isDisplayOnly(event.getEntity())) {
            event.getDrops().clear();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        Level level = event.getLevel();
        if (level.isClientSide || !(entity instanceof ArmorStand stand)) return;
        if (stand.getRemovalReason() != Entity.RemovalReason.KILLED) return;

        AccessorySlots.getForEntity(stand).ifPresent(slots -> {
            if (isDisplayOnly(stand)) {
                clearAllSlots(slots);
            } else {
                dropAndClearSlots(level, stand, slots);
            }
        });
    }

    private static void handleStandInteraction(PlayerInteractEvent.EntityInteractSpecific event, ArmorStand stand) {
        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        ItemStack held = player.getItemInHand(hand);
        AccessorySlots slots = AccessorySlots.getForEntity(stand).orElse(null);
        if (slots == null) return;

        if (!held.isEmpty()) {
            if (!(held.getItem() instanceof ClothingItem clothing)) {
                return;
            }
            if (player.isSpectator()) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.PASS);
                return;
            }
            ItemStack single = held.copy();
            single.setCount(1);
            AccessorySlotType targetSlot = findTargetSlot(stand, slots, clothing, single);
            if (targetSlot == null) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                return;
            }
            if (!player.level().isClientSide) {
                ItemStack previous = slots.getItem(targetSlot).orElse(ItemStack.EMPTY).copy();
                slots.setItem(targetSlot, single);
                AccessorySlots.equipEventAndSound(stand, single);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                    if (!previous.isEmpty()) {
                        if (held.isEmpty()) {
                            player.setItemInHand(hand, previous);
                        } else if (!player.getInventory().add(previous)) {
                            player.drop(previous, false);
                        }
                    }
                }
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
            return;
        }

        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) return;

        if (player.isShiftKeyDown()) {
            AccessorySlotType toggleSlot = findToggleableSlot(slots);
            if (toggleSlot == null) return;
            if (!player.level().isClientSide) {
                AccessorySlots.onInteractAccessory(stand, toggleSlot);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
            return;
        }

        double clickY = event.getLocalPos().y;
        if (clickY >= 1.55 && !stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        if (clickY < 0.85 && !stand.getItemBySlot(EquipmentSlot.FEET).isEmpty()) return;
        if (clickY >= 0.85 && clickY < 1.55 && !stand.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) return;

        AccessorySlotType occupied = findRemovableSlot(slots);
        if (occupied == null) return;

        if (!player.level().isClientSide) {
            ItemStack removed = slots.getItem(occupied).orElse(ItemStack.EMPTY).copy();
            if (!removed.isEmpty()) {
                slots.setItem(occupied, ItemStack.EMPTY);
                player.setItemInHand(hand, removed);
                SoundEvent sound = removed.getItem() instanceof ClothingItem c && c.getEquipSound(removed) != null
                        ? c.getEquipSound(removed)
                        : SoundEvents.ARMOR_EQUIP_GENERIC;
                stand.playSound(sound, 1.0F, 1.0F);
            }
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
    }

    @Nullable
    private static AccessorySlotType findTargetSlot(ArmorStand stand, AccessorySlots slots, ClothingItem clothing, ItemStack single) {
        List<AccessorySlotType> ordered = slots.getOrderedSlots();
        for (AccessorySlotType slot : ordered) {
            if (slots.getItem(slot).orElse(ItemStack.EMPTY).isEmpty()
                    && AccessorySlots.isSlotAvailable(stand, slot)
                    && clothing.allowedInSlot(single, stand, slot)) {
                return slot;
            }
        }
        for (AccessorySlotType slot : ordered) {
            ItemStack existing = slots.getItem(slot).orElse(ItemStack.EMPTY);
            if (existing.isEmpty()) continue;
            slots.setItem(slot, ItemStack.EMPTY);
            boolean ok = AccessorySlots.isSlotAvailable(stand, slot)
                    && clothing.allowedInSlot(single, stand, slot);
            slots.setItem(slot, existing);
            if (ok) {
                return slot;
            }
        }
        return null;
    }

    @Nullable
    private static AccessorySlotType findToggleableSlot(AccessorySlots slots) {
        for (AccessorySlotType slot : slots.getOrderedSlots()) {
            ItemStack stack = slots.getItem(slot).orElse(ItemStack.EMPTY);
            if (stack.getItem() instanceof FieldCoatItem
                    || stack.getItem() instanceof BurrowShawlItem
                    || stack.getItem() instanceof CaveMinerOutfitItem
                    || stack.getItem() instanceof EagleOwlAviatorItem) {
                return slot;
            }
        }
        return null;
    }

    @Nullable
    private static AccessorySlotType findRemovableSlot(AccessorySlots slots) {
        AccessorySlotType body = ChangedAccessorySlots.BODY.get();
        if (slots.hasSlot(body) && slots.getItem(body).filter(s -> !s.isEmpty()).isPresent()) {
            return body;
        }
        AccessorySlotType fullBody = ChangedAccessorySlots.FULL_BODY.get();
        if (slots.hasSlot(fullBody) && slots.getItem(fullBody).filter(s -> !s.isEmpty()).isPresent()) {
            return fullBody;
        }
        for (AccessorySlotType slot : slots.getOrderedSlots()) {
            if (slots.getItem(slot).filter(s -> !s.isEmpty()).isPresent()) {
                return slot;
            }
        }
        return null;
    }

    private static void clearAllSlots(AccessorySlots slots) {
        for (AccessorySlotType slot : slots.getOrderedSlots()) {
            if (!slots.getItem(slot).orElse(ItemStack.EMPTY).isEmpty()) {
                slots.setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private static void dropAndClearSlots(Level level, ArmorStand stand, AccessorySlots slots) {
        for (AccessorySlotType slot : slots.getOrderedSlots()) {
            ItemStack stack = slots.getItem(slot).orElse(ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                Block.popResource(level, stand.blockPosition(), stack.copy());
                slots.setItem(slot, ItemStack.EMPTY);
            }
        }
    }
}
