package net.parkabird.changedsynergy.mixin;

import net.foxyas.changedaddon.entity.api.IGrabberEntity;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;

/** Keeps Addon's universal damage-drop entry from breaking an evacuation hold. */
@Pseudo
@Mixin(
        targets = "net.foxyas.changedaddon.entity.api.IGrabberEntity",
        remap = false)
public interface OrganicEvacuationDropGuardMixin {
    /**
     * Preserves Addon's normal damage-release behavior, except while an
     * organic companion is actively evacuating its bonded player.
     *
     * @author ParkaBird
     * @reason Mixin 0.8.5 cannot inject into an interface default method.
     */
    @Overwrite(remap = false)
    default void mayDropGrabbedEntity(DamageSource source, float amount) {
        IGrabberEntity grabber = (IGrabberEntity)(Object)this;
        GrabEntityAbilityInstance ability = grabber.getGrabAbilityInstance();
        if (grabber.asMob() instanceof ChangedEntity mob
                && ability != null
                && ability.grabbedEntity instanceof ServerPlayer owner
                && (LatexSocialMemory.isOrganicEvacuationActive(mob, owner)
                        || InvoluntaryTransfurNegotiation
                                .isReleaseHoldTarget(mob, owner))) {
            return;
        }

        if (!grabber.asMob().level().isClientSide
                && ability != null) {
            LivingEntity grabbed = ability.grabbedEntity;
            if (grabbed != null) {
                ability.releaseEntity(false);
                Changed.PACKET_HANDLER.send(
                        PacketDistributor.TRACKING_ENTITY.with(
                                grabber::asMob),
                        new GrabEntityPacket(
                                grabber.asMob(),
                                grabbed,
                                GrabEntityPacket.GrabType.RELEASE));
                grabber.setGrabCooldown(120);
            }
        }
    }
}
