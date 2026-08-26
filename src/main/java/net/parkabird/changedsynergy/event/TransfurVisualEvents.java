package net.parkabird.changedsynergy.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.process.TransfurEvents;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.TransfurVisualPacket;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Tracks which form most recently contributed to a player's partial transfur. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TransfurVisualEvents {
    private static final String SAVED_COLOR = "ChangedSynergyLegacyTransfurColor";
    private static final String SAVED_ORGANIC = "ChangedSynergyLegacyTransfurOrganic";
    private static final Map<UUID, Candidate> CANDIDATES = new HashMap<>();
    private static final Map<UUID, VisualStyle> ACTIVE_STYLES = new HashMap<>();
    private static final Map<UUID, Float> LAST_PROGRESS = new HashMap<>();

    private TransfurVisualEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAssimilationDecision(TransfurEvents.LatexAssimilationDecisionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        rememberCandidate(player, event.getSourceEntity(), event.getTransfurVariant());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onNonLatexAssimilationDecision(TransfurEvents.NonLatexAssimilationDecisionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        rememberCandidate(player, event.getSourceEntity(), event.getTransfurVariant());
    }

    private static void rememberCandidate(
            ServerPlayer player,
            LivingEntity source,
            TransfurVariant<?> variant) {
        double distanceSqr = source == null ? 0.0 : source.distanceToSqr(player);
        long now = player.level().getGameTime();
        int color = variant.getColors().getFirst().toInt();
        boolean organic = source instanceof ChangedEntity changed
                && LatexSocialMemory.isOrganic(changed);
        Candidate current = CANDIDATES.get(player.getUUID());

        // Keep the nearest recent decision. AI target checks can ask for a
        // decision from far away, while the decision that actually lands is
        // necessarily close to the player.
        if (current == null || now - current.tick > 4L || distanceSqr <= current.distanceSqr) {
            CANDIDATES.put(player.getUUID(), new Candidate(
                    color, organic, now, distanceSqr));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        UUID id = player.getUUID();
        float progress = ProcessTransfur.getPlayerTransfurProgress(player);
        float previous = LAST_PROGRESS.getOrDefault(id, 0.0F);
        LAST_PROGRESS.put(id, progress);

        if (ProcessTransfur.isPlayerTransfurred(player) || progress <= 0.0001F) {
            clear(player);
            return;
        }

        Candidate candidate = CANDIDATES.get(id);
        boolean freshCandidate = candidate != null && player.level().getGameTime() - candidate.tick <= 5L;
        if ((progress > previous + 0.0001F || !ACTIVE_STYLES.containsKey(id)) && freshCandidate) {
            activate(player, candidate.color, candidate.organic);
            CANDIDATES.remove(id);
        } else if (!ACTIVE_STYLES.containsKey(id)
                && player.getPersistentData().contains(SAVED_COLOR, Tag.TAG_INT)) {
            activate(
                    player,
                    player.getPersistentData().getInt(SAVED_COLOR),
                    player.getPersistentData().getBoolean(SAVED_ORGANIC));
        } else if (candidate != null && !freshCandidate) {
            CANDIDATES.remove(id);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer clone)
                || !(event.getOriginal() instanceof ServerPlayer original)) {
            return;
        }

        UUID id = clone.getUUID();
        CANDIDATES.remove(id);
        ACTIVE_STYLES.remove(id);
        LAST_PROGRESS.remove(id);
        if (original.getPersistentData().contains(SAVED_COLOR, Tag.TAG_INT)) {
            clone.getPersistentData().putInt(SAVED_COLOR, original.getPersistentData().getInt(SAVED_COLOR));
            clone.getPersistentData().putBoolean(
                    SAVED_ORGANIC, original.getPersistentData().getBoolean(SAVED_ORGANIC));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        float progress = ProcessTransfur.getPlayerTransfurProgress(player);
        if (progress > 0.0001F && !ProcessTransfur.isPlayerTransfurred(player)
                && player.getPersistentData().contains(SAVED_COLOR, Tag.TAG_INT)) {
            VisualStyle style = savedStyle(player);
            ACTIVE_STYLES.put(player.getUUID(), style);
            broadcast(player, style);
        } else {
            clear(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer
                && event.getTarget() instanceof ServerPlayer target) {
            VisualStyle style = ACTIVE_STYLES.get(target.getUUID());
            if (style != null) {
                sendTo(observer, target, style);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer observer)) {
            return;
        }

        float progress = ProcessTransfur.getPlayerTransfurProgress(observer);
        if (progress > 0.0001F && !ProcessTransfur.isPlayerTransfurred(observer)
                && observer.getPersistentData().contains(SAVED_COLOR, Tag.TAG_INT)) {
            ACTIVE_STYLES.put(observer.getUUID(), savedStyle(observer));
        }

        for (ServerPlayer target : observer.server.getPlayerList().getPlayers()) {
            VisualStyle style = ACTIVE_STYLES.get(target.getUUID());
            if (style != null) {
                sendTo(observer, target, style);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        CANDIDATES.remove(id);
        ACTIVE_STYLES.remove(id);
        LAST_PROGRESS.remove(id);
    }

    private static void activate(ServerPlayer player, int color, boolean organic) {
        UUID id = player.getUUID();
        VisualStyle style = new VisualStyle(color, organic);
        VisualStyle previous = ACTIVE_STYLES.put(id, style);
        player.getPersistentData().putInt(SAVED_COLOR, color);
        player.getPersistentData().putBoolean(SAVED_ORGANIC, organic);
        if (!style.equals(previous)) {
            broadcast(player, style);
        }
    }

    private static void clear(ServerPlayer player) {
        UUID id = player.getUUID();
        CANDIDATES.remove(id);
        player.getPersistentData().remove(SAVED_COLOR);
        player.getPersistentData().remove(SAVED_ORGANIC);
        if (ACTIVE_STYLES.remove(id) != null) {
            ChangedSynergyNetwork.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    new TransfurVisualPacket(player.getId(), TransfurVisualPacket.CLEAR, false));
        }
    }

    private static VisualStyle savedStyle(ServerPlayer player) {
        return new VisualStyle(
                player.getPersistentData().getInt(SAVED_COLOR),
                player.getPersistentData().getBoolean(SAVED_ORGANIC));
    }

    private static void broadcast(ServerPlayer player, VisualStyle style) {
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new TransfurVisualPacket(player.getId(), style.color, style.organic));
    }

    private static void sendTo(
            ServerPlayer observer,
            ServerPlayer target,
            VisualStyle style) {
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> observer),
                new TransfurVisualPacket(target.getId(), style.color, style.organic));
    }

    private record Candidate(int color, boolean organic, long tick, double distanceSqr) {
    }

    private record VisualStyle(int color, boolean organic) {
    }
}
