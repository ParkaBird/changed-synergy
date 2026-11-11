package net.parkabird.changedsynergy.event;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.CreatureCacheGuardService;
import net.parkabird.changedsynergy.ai.CreatureCommunityData;
import net.parkabird.changedsynergy.advancement.SynergyAdvancements;

@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CreatureCacheGuardEvents {
    private record PendingAccess(ServerLevel level, net.minecraft.core.BlockPos pos, long tick) {}
    private static final java.util.Map<ServerPlayer, PendingAccess> PENDING = new java.util.WeakHashMap<>();
    private CreatureCacheGuardEvents() {
    }

    @SubscribeEvent
    public static void onCreatureLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof ChangedEntity creature) {
            CreatureCacheGuardService.forget(creature);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CreatureCacheGuardService.clearAssignments();
        PENDING.clear();
    }

    @SubscribeEvent
    public static void onCacheOpened(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var pending = PENDING.remove(player);
        if (pending != null && pending.level() == player.serverLevel()
                && pending.tick() == pending.level().getGameTime()
                && opensStorage(event.getContainer(), pending.level(), pending.pos())) {
            CreatureCacheGuardService.handleCacheAccess(pending.level(), player, pending.pos());
        }
        var checked = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Container, Boolean>());
        for (var slot : event.getContainer().slots) {
            if (checked.add(slot.container) && CreatureCommunityData.isCacheContainer(player.serverLevel(), slot.container)) {
                SynergyAdvancements.grant(player, SynergyAdvancements.MYSTERIOUS_FORAGING_SPOT);
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCacheAccess(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getLevel() instanceof ServerLevel level
                && event.getHand() == InteractionHand.MAIN_HAND
                && event.getUseBlock() != net.minecraftforge.eventbus.api.Event.Result.DENY
                && net.parkabird.changedsynergy.ai.PlayerOutpostData.container(level,event.getPos()) != null) {
            PENDING.put(player, new PendingAccess(level, event.getPos().immutable(), level.getGameTime()));
            net.parkabird.changedsynergy.npc.CampLifeService.opened(level,event.getPos());
        }
    }

    private static boolean opensStorage(net.minecraft.world.inventory.AbstractContainerMenu menu,
                                        ServerLevel level, net.minecraft.core.BlockPos pos) {
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof Container container) {
            return menu.slots.stream().anyMatch(slot -> slot.container == container
                    || slot.container instanceof net.minecraft.world.CompoundContainer pair && pair.contains(container));
        }
        return blockEntity != null && blockEntity.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER)
                .map(handler -> menu.slots.stream().anyMatch(slot -> slot instanceof net.minecraftforge.items.SlotItemHandler itemSlot
                        && itemSlot.getItemHandler() == handler)).orElse(false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCacheBroken(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && event.getLevel() instanceof ServerLevel level
                && net.parkabird.changedsynergy.ai.PlayerOutpostData.container(level,event.getPos()) != null) {
            CreatureCacheGuardService.handleCacheDestroyed(
                    level, player, event.getPos());
        }
    }
}
