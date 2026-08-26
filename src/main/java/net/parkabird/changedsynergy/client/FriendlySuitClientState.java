package net.parkabird.changedsynergy.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.LivingEntityDataExtension;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Retries and maintains pet-suit state after packet/entity initialization races. */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class FriendlySuitClientState {
    private static final Map<Integer, SuitState> ACTIVE_SUITS = new HashMap<>();

    private FriendlySuitClientState() {
    }

    public static void receive(int grabberId, int ownerId, boolean active) {
        if (active) {
            SuitState state = new SuitState(grabberId, ownerId, clientGameTime());
            ACTIVE_SUITS.put(grabberId, state);
            apply(state, true);
        } else {
            SuitState state = ACTIVE_SUITS.remove(grabberId);
            apply(state != null
                    ? state
                    : new SuitState(grabberId, ownerId, clientGameTime()), false);
        }
    }

    public static boolean isOwnerSuited(int ownerId) {
        return ACTIVE_SUITS.values().stream()
                .anyMatch(state -> state.ownerId == ownerId);
    }

    public static ChangedEntity getSuitingCreature(int ownerId) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        return ACTIVE_SUITS.values().stream()
                .filter(state -> state.ownerId == ownerId)
                .map(state -> level.getEntity(state.grabberId))
                .filter(ChangedEntity.class::isInstance)
                .map(ChangedEntity.class::cast)
                .findFirst()
                .orElse(null);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().level == null) {
            return;
        }
        long now = clientGameTime();
        Iterator<SuitState> iterator = ACTIVE_SUITS.values().iterator();
        while (iterator.hasNext()) {
            SuitState state = iterator.next();
            if (now - state.lastSeenTick > 60L) {
                apply(state, false);
                iterator.remove();
            } else {
                apply(state, true);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE_SUITS.clear();
    }

    private static boolean apply(SuitState state, boolean active) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }

        Entity ownerEntity = level.getEntity(state.ownerId);
        if (!(ownerEntity instanceof LivingEntity owner)) {
            return false;
        }
        Entity sourceEntity = level.getEntity(state.grabberId);
        if (!active && !(sourceEntity instanceof ChangedEntity)) {
            clearGrabbedReference(owner, state.grabberId);
            return true;
        }
        if (!(sourceEntity instanceof ChangedEntity grabber)) {
            return false;
        }

        GrabEntityAbilityInstance ability = IAbstractChangedEntity.forEntity(grabber)
                .getAbilityInstanceSafe(ChangedAbilities.GRAB_ENTITY_ABILITY.get())
                .orElse(null);
        if (ability == null) {
            return false;
        }

        if (!active) {
            if (ability.grabbedEntity == owner) {
                ability.releaseEntity(false);
            }
            clearGrabbedReference(owner, state.grabberId);
            grabber.setInvisible(false);
            return true;
        }

        if (ability.grabbedEntity != owner) {
            ability.suitEntity(owner);
        }
        ability.grabbedEntity = owner;
        ability.grabbedHasControl = true;
        ability.suited = true;
        ability.grabStrengthO = 1.0F;
        ability.grabStrength = 1.0F;
        ability.suitTransitionO = GrabEntityAbilityInstance.SUIT_TRANSITION_MAX;
        ability.suitTransition = GrabEntityAbilityInstance.SUIT_TRANSITION_MAX;
        ability.attackDown = false;
        ability.useDown = false;
        if (owner instanceof LivingEntityDataExtension extension) {
            extension.setGrabbedBy(grabber);
        }
        owner.setInvisible(false);
        grabber.setInvisible(true);
        TransfurVariantInstance.syncEntityPosRotWithEntity(grabber, owner);
        return true;
    }

    private static void clearGrabbedReference(LivingEntity owner, int grabberId) {
        if (owner instanceof LivingEntityDataExtension extension) {
            LivingEntity grabbedBy = extension.getGrabbedBy();
            if (grabbedBy != null && grabbedBy.getId() == grabberId) {
                extension.setGrabbedBy(null);
            }
        }
        owner.setInvisible(false);
    }

    private static long clientGameTime() {
        return Minecraft.getInstance().level == null
                ? 0L : Minecraft.getInstance().level.getGameTime();
    }

    private record SuitState(int grabberId, int ownerId, long lastSeenTick) {
    }
}
