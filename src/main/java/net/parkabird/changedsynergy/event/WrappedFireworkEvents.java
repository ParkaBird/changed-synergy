package net.parkabird.changedsynergy.event;

import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** A suit carrier shares the flying host's position, but is not a vanilla passenger. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class WrappedFireworkEvents {
    private WrappedFireworkEvents() {}

    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof FireworkRocketEntity rocket)
                || !(rocket.getOwner() instanceof LivingEntity host) || !host.isFallFlying()
                || !(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        var grabber = GrabEntityAbility.getGrabber(host);
        if (grabber == null || grabber.getEntity() != hit.getEntity()) return;
        var hold = grabber.getAbilityInstanceSafe(ChangedAbilities.GRAB_ENTITY_ABILITY.get()).orElse(null);
        if (hold != null && hold.suited && hold.grabbedEntity == host && hold.grabbedHasControl)
            event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
    }
}
