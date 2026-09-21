package net.parkabird.changedsynergy.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.CreatureCacheGuardService;

@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CreatureCacheGuardEvents {
    private CreatureCacheGuardEvents() {
    }

    @SubscribeEvent
    public static void onCacheAccess(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getLevel() instanceof ServerLevel level
                && event.getHand() == InteractionHand.MAIN_HAND
                && level.getBlockEntity(event.getPos()) instanceof Container) {
            if (!CreatureCacheGuardService.handleCacheAccess(
                    level, player, event.getPos())) {
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCacheBroken(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && event.getLevel() instanceof ServerLevel level
                && level.getBlockEntity(event.getPos()) instanceof Container) {
            CreatureCacheGuardService.handleCacheDestroyed(
                    level, player, event.getPos());
        }
    }
}
