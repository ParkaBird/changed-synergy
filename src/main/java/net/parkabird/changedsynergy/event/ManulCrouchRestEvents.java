package net.parkabird.changedsynergy.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ability.ManulCrouchRestAbility;

/** Breaks crouch-rest on hits and keeps rooted players from sliding after travel. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class ManulCrouchRestEvents {
    private ManulCrouchRestEvents() {}

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        interrupt(event.getEntity());
    }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (event.getAmount() > 0.0F) {
            interrupt(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        ManulCrouchRestAbility.Instance instance = ManulCrouchRestAbility.instanceOf(player);
        if (instance != null && instance.isResting()) {
            player.setDeltaMovement(new Vec3(0.0D, Math.min(player.getDeltaMovement().y, 0.0D), 0.0D));
            player.hasImpulse = true;
            player.hurtMarked = true;
            player.setSprinting(false);
            player.setJumping(false);
        }
    }

    private static void interrupt(net.minecraft.world.entity.LivingEntity living) {
        ManulCrouchRestAbility.Instance instance = ManulCrouchRestAbility.instanceOf(living);
        if (instance != null && (instance.isArmed() || instance.wrapAmount() > 0.0F)) {
            instance.interrupt();
        }
    }
}
