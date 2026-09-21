package net.parkabird.changedsynergy.event;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.advancement.SynergyAdvancements;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.TakeoverService;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;

/** Awards and state changes only after a real night skip, never after merely entering a bed. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class WrappedSleepEvents {
    private static final Map<UUID, SleepContext> SLEEPING = new HashMap<>();
    private static final Set<UUID> COMPLETED = new HashSet<>();

    private WrappedSleepEvents() {}

    private record SleepContext(@Nullable UUID wrappingCreature, boolean takeover, boolean organic) {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player)) return;
        if (!player.isSleeping()) {
            if (!COMPLETED.contains(player.getUUID())) SLEEPING.remove(player.getUUID());
            return;
        }
        SLEEPING.computeIfAbsent(player.getUUID(), ignored -> capture(player));
    }

    private static SleepContext capture(ServerPlayer player) {
        ChangedEntity wrapping = BondedSuitService.getWrappingPet(player);
        boolean organic = ProcessTransfur.getPlayerTransfurVariantSafe(player)
                .map(instance -> LatexSocialMemory.isOrganic(instance.getChangedEntity()))
                .orElse(false);
        return new SleepContext(wrapping == null ? null : wrapping.getUUID(),
                TakeoverService.active(player), organic);
    }

    @SubscribeEvent
    public static void onNightSkipped(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        for (ServerPlayer player : level.players()) {
            if (player.isSleeping() && SLEEPING.containsKey(player.getUUID()))
                COMPLETED.add(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onWake(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !COMPLETED.remove(player.getUUID())) return;
        SleepContext context = SLEEPING.remove(player.getUUID());
        if (context == null) return;

        boolean wrapped = context.wrappingCreature() != null || context.takeover();
        if (wrapped) SynergyAdvancements.grant(player, SynergyAdvancements.WRAPPED_SLEEP);

        ChangedEntity wrapping = BondedSuitService.getWrappingPet(player);
        if (context.wrappingCreature() != null && wrapping != null
                && context.wrappingCreature().equals(wrapping.getUUID()))
            NpcDialogue.trigger(wrapping, player, Cue.BOND_WRAP_SLEEP);
        if (context.takeover()) TakeoverService.onCompletedBedSleep(player);

        if (context.organic() && InvoluntaryTransfurNegotiation.releaseOrganicAfterSleep(player))
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.changed_synergy.organic_sleep_reverted"), false);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SLEEPING.remove(event.getEntity().getUUID());
        COMPLETED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SLEEPING.clear();
        COMPLETED.clear();
    }
}
