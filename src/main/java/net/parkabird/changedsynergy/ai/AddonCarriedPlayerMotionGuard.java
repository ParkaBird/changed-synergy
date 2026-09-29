package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;

/** Keeps Addon's autonomous movement attacks from moving a carrier with a player inside. */
public final class AddonCarriedPlayerMotionGuard {
    private AddonCarriedPlayerMotionGuard() {}

    public static boolean isCarryingPlayer(ChangedEntity mob) {
        if (TakeoverService.carrying(mob)) return true;
        GrabEntityAbilityInstance nativeGrab = BondedSuitService.ability(mob);
        if (nativeGrab != null && nativeGrab.grabbedEntity instanceof Player) return true;
        GrabEntityAbilityInstance addonGrab = ChangedAddonCompat.grabAbility(mob);
        return addonGrab != null && addonGrab.grabbedEntity instanceof Player;
    }
}
