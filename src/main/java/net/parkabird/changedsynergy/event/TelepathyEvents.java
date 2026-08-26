package net.parkabird.changedsynergy.event;

import net.ltxprogrammer.changed.process.TransfurEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.TelepathyService;

/** Tracks the player's permanent familiarity with telepathic latex speech. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TelepathyEvents {
    private TelepathyEvents() {
    }

    @SubscribeEvent
    public static void onNewlyTransfurred(
            TransfurEvents.NewlyTransfurredEntityEvent event) {
        if (event.entity.getEntity() instanceof ServerPlayer player) {
            TelepathyService.recordCompletedTransfur(
                    player, event.entity.getTransfurVariantInstance());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TelepathyService.restoreAdvancement(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original
                && event.getEntity() instanceof ServerPlayer clone) {
            TelepathyService.copyPlayerData(original, clone);
        }
    }
}
