package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.client.gui.AbilityRadialScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Public helpers used by Synergy screens without coupling them to the mixin. */
public final class RadialWheelAnimations {
    private static final long SWITCH_HANDOFF_TIMEOUT_NANOS = 3_000_000_000L;
    private static WheelKind pendingArrival = WheelKind.NONE;
    private static long pendingArrivalNanos;
    private static int pendingEntityId = -1;

    private RadialWheelAnimations() {
    }

    public static boolean requestClose(Object screen, Runnable completion) {
        return request(screen, completion, false);
    }

    public static boolean requestWheelSwitch(Object screen, Runnable completion) {
        WheelKind target = switchTarget(screen);
        return requestWheelSwitch(screen, completion, target);
    }

    /** Smoothly hands any social/ability wheel off to negotiation. */
    public static boolean requestNegotiationSwitch(
            Object screen,
            Runnable completion) {
        if (kindOf(screen) == WheelKind.NEGOTIATION) {
            return false;
        }
        return requestWheelSwitch(
                screen, completion, WheelKind.NEGOTIATION);
    }

    private static boolean requestWheelSwitch(
            Object screen,
            Runnable completion,
            WheelKind target) {
        if (target == WheelKind.NONE || hasPendingSwitch()) {
            return false;
        }
        pendingArrival = target;
        pendingArrivalNanos = System.nanoTime();
        // Absorption negotiation starts from the player's ability wheel but
        // opens a synthetic creature preview, so there is no shared entity id
        // to compare across that particular hand-off.
        pendingEntityId = target == WheelKind.NEGOTIATION
                        && screen instanceof AbilityRadialScreen
                ? -1 : entityId(screen);
        boolean accepted = request(screen, completion, true);
        if (!accepted) {
            // The fallback opens the target immediately, so keep the hand-off
            // token available for the target screen's first init pass.
            completion.run();
        }
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(
                        SoundEvents.UI_LOOM_SELECT_PATTERN,
                        0.9F));
        return true;
    }

    public static float layerAlpha(Object screen) {
        return screen instanceof RadialWheelAnimationAccess access
                ? access.changedSynergy$getLayerAlpha()
                : 1.0F;
    }

    public static float overallAlpha(Object screen) {
        return screen instanceof RadialWheelAnimationAccess access
                ? access.changedSynergy$getOverallAlpha()
                : 1.0F;
    }

    public static float horizontalOffset(Object screen) {
        return screen instanceof RadialWheelAnimationAccess access
                ? access.changedSynergy$getHorizontalOffset()
                : 0.0F;
    }

    /** Consumed once by the newly opened half of a paired wheel switch. */
    public static boolean consumeSwitchArrival(Object screen) {
        if (pendingArrival == WheelKind.NONE) {
            return false;
        }
        if (System.nanoTime() - pendingArrivalNanos
                > SWITCH_HANDOFF_TIMEOUT_NANOS) {
            pendingArrival = WheelKind.NONE;
            pendingEntityId = -1;
            return false;
        }
        if (kindOf(screen) != pendingArrival
                || pendingEntityId >= 0
                        && entityId(screen) != pendingEntityId) {
            return false;
        }
        pendingArrival = WheelKind.NONE;
        pendingEntityId = -1;
        return true;
    }

    private static boolean hasPendingSwitch() {
        if (pendingArrival == WheelKind.NONE) {
            return false;
        }
        if (System.nanoTime() - pendingArrivalNanos
                <= SWITCH_HANDOFF_TIMEOUT_NANOS) {
            return true;
        }
        pendingArrival = WheelKind.NONE;
        pendingEntityId = -1;
        return false;
    }

    private static boolean request(
            Object screen,
            Runnable completion,
            boolean wheelSwitch) {
        return screen instanceof RadialWheelAnimationAccess access
                && access.changedSynergy$startClosing(completion, wheelSwitch);
    }

    private static WheelKind switchTarget(Object screen) {
        if (screen instanceof AbilityRadialScreen) {
            return WheelKind.PLAYER_RELATIONSHIPS;
        }
        if (screen instanceof PlayerRelationshipScreen relationships
                && relationships.menu.isTransfurred()) {
            return WheelKind.ABILITY;
        }
        if (screen instanceof BondedLatexScreen) {
            return WheelKind.SOCIAL;
        }
        if (screen instanceof SocialInteractionScreen social
                && social.menu.isBondedMode()) {
            return WheelKind.FUNCTION;
        }
        return WheelKind.NONE;
    }

    private static WheelKind kindOf(Object screen) {
        if (screen instanceof AbilityRadialScreen) {
            return WheelKind.ABILITY;
        }
        if (screen instanceof PlayerRelationshipScreen) {
            return WheelKind.PLAYER_RELATIONSHIPS;
        }
        if (screen instanceof BondedLatexScreen) {
            return WheelKind.FUNCTION;
        }
        if (screen instanceof SocialInteractionScreen social
                && social.menu.isNegotiationMode()) {
            return WheelKind.NEGOTIATION;
        }
        if (screen instanceof SocialInteractionScreen social
                && social.menu.isBondedMode()) {
            return WheelKind.SOCIAL;
        }
        return WheelKind.NONE;
    }

    private static int entityId(Object screen) {
        if (screen instanceof AbilityRadialScreen ability) {
            return ability.menu.player.getId();
        }
        if (screen instanceof PlayerRelationshipScreen relationships) {
            return relationships.menu.getPlayer().getId();
        }
        if (screen instanceof BondedLatexScreen bonded
                && bonded.menu.getPet() != null) {
            return bonded.menu.getPet().getId();
        }
        if (screen instanceof SocialInteractionScreen social
                && social.menu.getCreature() != null) {
            return social.menu.getCreature().getId();
        }
        return -1;
    }

    private enum WheelKind {
        NONE,
        ABILITY,
        PLAYER_RELATIONSHIPS,
        FUNCTION,
        SOCIAL,
        NEGOTIATION
    }
}
