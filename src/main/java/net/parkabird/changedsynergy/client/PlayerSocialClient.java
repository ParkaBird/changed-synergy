package net.parkabird.changedsynergy.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.network.PlayerBodyPacket;

/** UUID-keyed identity avoids entity-id reuse after a control-mode switch. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, value = Dist.CLIENT)
public final class PlayerSocialClient {
    private static final Map<UUID, PlayerBodyPacket> BODIES = new HashMap<>();
    private static final Map<UUID, UUID> OWNERS = new HashMap<>();
    private static final Map<UUID, ChangedEntity> VISIBLE_BODIES = new HashMap<>();
    private static ClientLevel level;

    private PlayerSocialClient() {}

    public static void receive(PlayerBodyPacket packet) {
        refreshLevel();
        if (level != null) {
            UUID previous = OWNERS.put(packet.owner(), packet.body());
            if (previous != null && !previous.equals(packet.body())) {
                BODIES.remove(previous);
                VISIBLE_BODIES.remove(previous);
            }
            BODIES.put(packet.body(), packet);
            // Resolve once when identity arrives, never scan entities from a render call.
            if (!VISIBLE_BODIES.containsKey(packet.body())) {
                for (Entity entity : level.entitiesForRendering()) {
                    if (entity instanceof ChangedEntity creature && entity.getUUID().equals(packet.body())) {
                        VISIBLE_BODIES.put(packet.body(), creature);
                        break;
                    }
                }
            }
        }
    }

    /** A transformed technical player can otherwise render a second, named model. */
    public static boolean isHiddenTechnicalPlayer(Entity entity) {
        refreshLevel();
        Player player = entity instanceof Player direct ? direct
                : entity instanceof ChangedEntity form ? form.getUnderlyingPlayer() : null;
        return player != null && entity.level() == level && visibleBody(player) != player;
    }

    public static net.minecraft.world.entity.LivingEntity visibleBody(Player player) {
        refreshLevel();
        if (level == null || player.level() != level) return player;
        UUID id = OWNERS.get(player.getUUID());
        Entity body = VISIBLE_BODIES.get(id);
        return body instanceof ChangedEntity creature && body.isAlive() && !body.isRemoved() ? creature : player;
    }

    public static boolean isPlayerBody(Entity entity) {
        refreshLevel();
        return entity.level() == level && BODIES.containsKey(entity.getUUID());
    }
    public static boolean belongsTo(Entity entity,UUID owner) {
        refreshLevel();var body=BODIES.get(entity.getUUID());return entity.level()==level&&body!=null&&body.owner().equals(owner);
    }

    private static void refreshLevel() {
        ClientLevel current = Minecraft.getInstance().level;
        if (level != current) {
            BODIES.clear();
            OWNERS.clear();
            VISIBLE_BODIES.clear();
            level = current;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        consumeInteraction(event, event.getTarget());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractAt(PlayerInteractEvent.EntityInteractSpecific event) {
        consumeInteraction(event, event.getTarget());
    }

    private static void consumeInteraction(PlayerInteractEvent event, Entity target) {
        if (!event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND
                || event.getEntity().isSpectator()) return;
        if (target instanceof Player && (event.getItemStack().isEmpty() || event.getEntity().isShiftKeyDown())
                || target instanceof ChangedEntity && isPlayerBody(target)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide || !(event.getEntity() instanceof ChangedEntity body)) return;
        refreshLevel();
        if (event.getLevel() == level && BODIES.containsKey(body.getUUID())) {
            VISIBLE_BODIES.put(body.getUUID(), body);
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide && event.getLevel() == level) {
            UUID id = event.getEntity().getUUID();
            VISIBLE_BODIES.remove(id, event.getEntity());
            PlayerBodyPacket body = BODIES.remove(id);
            if (body != null) OWNERS.remove(body.owner(), id);
        }
    }
}
