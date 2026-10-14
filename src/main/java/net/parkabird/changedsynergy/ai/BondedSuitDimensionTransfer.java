package net.parkabird.changedsynergy.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Carries a bonded suit through the player's dimension change. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class BondedSuitDimensionTransfer {
    private record PendingSuit(UUID pet, ResourceKey<Level> destination, long expires) {}
    private static final Map<UUID, PendingSuit> PENDING = new HashMap<>();

    private BondedSuitDimensionTransfer() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ChangedEntity pet = BondedSuitService.getFriendlySuitPet(player);
        if (pet != null && LatexSocialMemory.isPetOwner(pet, player)) {
            PENDING.put(player.getUUID(), new PendingSuit(pet.getUUID(),
                    event.getDimension(), player.level().getGameTime() + 400L));
        }
    }

    @SubscribeEvent
    public static void afterTravel(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PendingSuit pending = PENDING.get(player.getUUID());
        if (pending == null || player.level().dimension() != pending.destination()) return;
        player.server.execute(() -> {
            if (restore(player, pending.pet())) PENDING.remove(player.getUUID(), pending);
        });
    }

    @SubscribeEvent
    public static void retryAfterTravel(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 20 != 0) return;
        PendingSuit pending = PENDING.get(player.getUUID());
        if (pending == null) return;
        if (player.level().getGameTime() >= pending.expires()
                || player.level().dimension() == pending.destination()
                        && restore(player, pending.pet())) {
            PENDING.remove(player.getUUID(), pending);
        }
    }

    public static void cancelRestoration(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && PENDING.remove(player.getUUID()) != null
                && BondedSuitService.getFriendlySuitPet(player) == null) {
            BondedSuitService.clearStaleOwnerStateAfterDeath(player);
        }
    }

    private static boolean restore(ServerPlayer player, UUID petId) {
        if (!player.isAlive()) return true;
        ChangedEntity pet = null;
        for (ServerLevel level : player.server.getAllLevels()) {
            Entity found = level.getEntity(petId);
            if (found instanceof ChangedEntity changed) {
                pet = changed;
                break;
            }
        }
        if (pet == null) return false;
        if (!pet.isAlive() || !LatexSocialMemory.isPetOwner(pet, player))
            return true;
        var variant = ProcessTransfur.getPlayerTransfurVariant(player);
        if (variant != null && !variant.isTemporaryFromSuit()) return true;
        if (pet.level() != player.level()) {
            var grab = BondedSuitService.ability(pet);
            if (grab != null && grab.grabbedEntity == player) {
                grab.releaseEntity(false);
            }
            pet = BondedTeleportSafety.moveNearPlayer(pet, player).orElse(null);
        }
        if (pet == null) return false;
        if (BondedSuitService.isSuitingOwner(pet, player)
                && ProcessTransfur.getPlayerTransfurVariant(player) != null
                && ProcessTransfur.getPlayerTransfurVariant(player)
                        .isTemporaryFromSuit()) return true;
        var grab = BondedSuitService.ability(pet);
        if (grab != null && grab.grabbedEntity == player) {
            grab.releaseEntity(false);
        }
        BondedSuitService.clearStaleOwnerStateAfterDeath(player);
        return BondedSuitService.suitOwner(pet, player,
                BondedSuitService.SuitReason.MANUAL);
    }
}
