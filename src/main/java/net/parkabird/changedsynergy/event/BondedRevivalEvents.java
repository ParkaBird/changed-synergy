package net.parkabird.changedsynergy.event;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.TransfurEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.BondedRevivalService;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.init.ChangedSynergyItems;

/** Event edges for the item-facing phases of bonded revival. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BondedRevivalEvents {
    private BondedRevivalEvents() {
    }

    /** The provisional body is transactional until its owner releases the wrap. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void protectProvisionalBody(LivingDeathEvent event) {
        if (event.getEntity() instanceof ChangedEntity creature
                && BondedRevivalService.isProvisional(creature)) {
            event.setCanceled(true);
            creature.setHealth(creature.getMaxHealth());
        }
    }

    /** Runs after cancellable death decisions, while entity-side bonds still exist. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBondedCreatureDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ChangedEntity creature) {
            BondedRevivalService.dropBrokenMask(creature);
        }
    }

    @SubscribeEvent
    public static void onItemEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ChangedEntity creature
                && !event.getLevel().isClientSide()
                && creature.getPersistentData().getBoolean("ChangedSynergyRevivalGrabAbility")) {
            BondedSuitService.ensureRevivalGrabAbility(creature);
        }
        if (event.getEntity() instanceof ItemEntity item
                && (BondedRevivalService.isRevivalMask(item.getItem())
                        || BondedRevivalService.isRevivalSample(item.getItem())
                        || BondedRevivalService.isRevivalVessel(item.getItem()))) {
            ItemStack normalized = BondedRevivalService.upgradeLegacyRepairedMask(item.getItem());
            if (normalized != item.getItem()) {
                item.setItem(normalized);
            }
            BondedRevivalService.normalizeDisplayName(normalized);
            item.setGlowingTag(true);
            if (item.getItem().is(ChangedSynergyItems.BROKEN_DARK_LATEX_MASK.get())
                    || BondedRevivalService.isRevivalSample(item.getItem())) {
                item.setUnlimitedLifetime();
            }
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getTarget() instanceof ChangedEntity creature
                && creature.getPersistentData().getBoolean("ChangedSynergyRevivalGrabAbility")) {
            net.parkabird.changedsynergy.network.ChangedSynergyNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new net.parkabird.changedsynergy.network.RevivalGrabAbilityPacket(creature.getId()));
        }
    }

    @SubscribeEvent
    public static void onMaskPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItem().getItem();
        if (!(stack.is(ChangedSynergyItems.BROKEN_DARK_LATEX_MASK.get())
                || BondedRevivalService.isRevivalSample(stack))
                || !BondedRevivalService.hasRevivalToken(stack)) {
            return;
        }
        if (BondedRevivalService.belongsTo(stack, player)) {
            if (BondedRevivalService.markOwnerPickup(stack)) {
                player.sendSystemMessage(Component.translatable(
                        BondedRevivalService.isRevivalSample(stack)
                                ? "message.changed_synergy.revival.sample_picked_up"
                                : "message.changed_synergy.revival.mask_picked_up",
                        BondedRevivalService.bondName(stack)));
            }
        } else {
            player.sendSystemMessage(Component.translatable(
                    "message.changed_synergy.revival.mask_silent",
                    BondedRevivalService.bondName(stack)));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 20 == 0) {
            for (int slot = 0;
                    slot < player.getInventory().getContainerSize();
                    slot++) {
                ItemStack current = player.getInventory().getItem(slot);
                ItemStack normalized = BondedRevivalService.upgradeLegacyRepairedMask(current);
                if (normalized != current) {
                    player.getInventory().setItem(slot, normalized);
                }
                BondedRevivalService.normalizeDisplayName(normalized);
            }
        }
        ItemStack worn = player.getItemBySlot(
                net.minecraft.world.entity.EquipmentSlot.HEAD);
        ItemStack normalizedWorn = BondedRevivalService.upgradeLegacyRepairedMask(worn);
        if (normalizedWorn != worn) {
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, normalizedWorn);
            worn = normalizedWorn;
        }
        if (!BondedRevivalService.isRepairedMask(worn)
                || !BondedRevivalService.hasRevivalToken(worn)) {
            return;
        }
        String name = BondedRevivalService.bondName(worn);
        if (!BondedRevivalService.belongsTo(worn, player)) {
            player.setItemSlot(
                    net.minecraft.world.entity.EquipmentSlot.HEAD,
                    ItemStack.EMPTY);
            if (!player.getInventory().add(worn)) {
                player.drop(worn, false);
            }
            player.sendSystemMessage(Component.translatable(
                    "message.changed_synergy.revival.wrong_owner", name));
            return;
        }
        if (BondedRevivalService.markWorn(worn)) {
            player.sendSystemMessage(Component.translatable(
                    "message.changed_synergy.revival.mask_worn", name));
        }
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && BondedRevivalService.isRepairedMask(event.getCrafting())
                && BondedRevivalService.hasRevivalToken(event.getCrafting())) {
            BondedRevivalService.markRepaired(event.getCrafting(), player);
        }
    }

    @SubscribeEvent
    public static void onRepairedMaskTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (BondedRevivalService.isRevivalSample(stack)
                || BondedRevivalService.isRevivalVessel(stack)) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.changed_synergy.revival_mask.bond",
                    BondedRevivalService.bondName(stack)).withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.translatable(
                    BondedRevivalService.isRevivalSample(stack)
                            ? "tooltip.changed_synergy.revival_sample.infuser"
                            : "tooltip.changed_synergy.revival_vessel.use")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (!BondedRevivalService.isRepairedMask(stack)
                || !BondedRevivalService.hasRevivalToken(stack)) {
            return;
        }
        String name = BondedRevivalService.bondName(stack);
        event.getToolTip().add(Component.translatable(
                "tooltip.changed_synergy.revival_mask.bond", name)
                .withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(Component.translatable(
                "tooltip.changed_synergy.repaired_dark_latex_mask.use")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @SubscribeEvent
    public static void onNewlyTransfurred(
            TransfurEvents.NewlyTransfurredEntityEvent event) {
        if (event.entity.getEntity() instanceof ServerPlayer player) {
            BondedRevivalService.onMaskTransfurCompleted(player);
            BondedRevivalService.onVesselTransfurCompleted(player);
        }
    }
}
