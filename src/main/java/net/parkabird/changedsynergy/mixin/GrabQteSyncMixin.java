package net.parkabird.changedsynergy.mixin;

import javax.annotation.Nullable;
import net.ltxprogrammer.changed.ability.AbstractAbility;
import net.ltxprogrammer.changed.ability.AbstractAbilityInstance;
import net.ltxprogrammer.changed.ability.AbstractAbilityInstance.KeyReference;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.parkabird.changedsynergy.ai.CreatureSocialProfile;
import net.parkabird.changedsynergy.ai.GrabEscapeStunService;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.HypnosisProfile;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.FriendlySocialHugState;
import net.parkabird.changedsynergy.network.GrabQteSyncPacket;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps the prompt rendered by the client aligned with the key checked by the server. */
@Mixin(value = GrabEntityAbilityInstance.class, remap = false)
public abstract class GrabQteSyncMixin extends AbstractAbilityInstance {
    @Shadow(remap = false)
    @Nullable
    public LivingEntity grabbedEntity;

    @Shadow(remap = false)
    @Nullable
    public KeyReference currentEscapeKey;

    @Shadow(remap = false)
    @Nullable
    public KeyReference lastEscapeKey;

    @Shadow(remap = false)
    public int ticksUnpressed;

    @Shadow(remap = false)
    public float grabStrength;

    @Shadow(remap = false)
    public boolean suited;

    @Unique
    @Nullable
    private KeyReference changedSynergy$lastSentCurrent;

    @Unique
    @Nullable
    private KeyReference changedSynergy$lastSentPrevious;

    protected GrabQteSyncMixin(AbstractAbility<?> ability, IAbstractChangedEntity entity) {
        super(ability, entity);
    }

    @Inject(method = "handleEscape", at = @At("TAIL"), remap = false)
    private void changedSynergy$syncExpectedEscapeKey(CallbackInfo callback) {
        if (entity.getLevel().isClientSide()
                || !ChangedSynergyGameRules.enabled(
                        entity.getLevel(),
                        ChangedSynergyGameRules.GRAB_QTE_ENHANCEMENTS)
                || !(grabbedEntity instanceof ServerPlayer player)
                || currentEscapeKey == changedSynergy$lastSentCurrent
                        && lastEscapeKey == changedSynergy$lastSentPrevious) {
            return;
        }
        changedSynergy$lastSentCurrent = currentEscapeKey;
        changedSynergy$lastSentPrevious = lastEscapeKey;
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new GrabQteSyncPacket(
                        entity.getId(),
                        player.getId(),
                        ordinal(currentEscapeKey),
                        ordinal(lastEscapeKey),
                        ticksUnpressed));
    }

    /** Friendly social-wheel hugs are timed by the server and have no escape QTE. */
    @Inject(
            method = "handleEscape",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void changedSynergy$suppressFriendlyHugEscape(CallbackInfo callback) {
        boolean friendlyHug =
                entity.getLevel().isClientSide()
                        ? grabbedEntity != null
                                && FriendlySocialHugState.isActive(
                                        entity.getEntity().getId(),
                                        grabbedEntity.getId())
                        : entity.getEntity() instanceof ChangedEntity mob
                                && grabbedEntity != null
                                && LatexSocialMemory.isFriendlyArmHoldTarget(
                                        mob, grabbedEntity);
        if (friendlyHug) {
            grabStrength = 1.0F;
            ticksUnpressed = 0;
            currentEscapeKey = null;
            lastEscapeKey = null;
            callback.cancel();
        }
    }

    @Inject(method = "grabEntity", at = @At("RETURN"), remap = false)
    private void changedSynergy$beginQteSession(
            LivingEntity target,
            CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue()) {
            changedSynergy$lastSentCurrent = null;
            changedSynergy$lastSentPrevious = null;
        }
    }

    /** Final authority: protected players never enter the arm-grab state at all. */
    @Inject(
            method = "grabEntity",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void changedSynergy$rejectProtectedGrab(
            LivingEntity target,
            CallbackInfoReturnable<Boolean> callback) {
        if (!entity.getLevel().isClientSide()
                && entity.getEntity() instanceof ChangedEntity mob
                && CreatureSocialProfile.isGrabMechanicExcluded(mob)) {
            callback.setReturnValue(false);
            return;
        }
        if (!entity.getLevel().isClientSide()
                && entity.getEntity() instanceof ChangedEntity mob
                && GrabEscapeStunService.isStunned(mob)) {
            callback.setReturnValue(false);
            return;
        }
        if (!entity.getLevel().isClientSide()
                && entity.getEntity() instanceof ChangedEntity mob
                && CreatureSocialProfile.allowsSynergySystems(mob)
                && HypnosisProfile.isHypnoticCreature(mob)
                && mob.getTarget() == target) {
            callback.setReturnValue(false);
            return;
        }
        if (!entity.getLevel().isClientSide()
                && entity.getEntity() instanceof ChangedEntity mob
                && target instanceof ServerPlayer player
                && LatexSocialMemory.isSocialLatex(mob)
                && CreatureSocialProfile.allowsSynergySystems(mob)) {
            if (!LatexSocialMemory.isFriendlyArmHoldActive(mob, player)
                    && (!LatexSocialMemory.mayInitiateHostileGrab(mob, player)
                            || !LatexSocialMemory.passHostileGrabAttemptRoll(mob, player))) {
                callback.setReturnValue(false);
            }
        }
    }

    @Inject(method = "releaseEntity", at = @At("TAIL"), remap = false)
    private void changedSynergy$endQteSession(
            boolean applyDebuffs,
            CallbackInfo callback) {
        changedSynergy$lastSentCurrent = null;
        changedSynergy$lastSentPrevious = null;
    }

    @Inject(method = "releaseEntity", at = @At("HEAD"), remap = false)
    private void changedSynergy$stunGrabberAfterEscape(
            boolean applyDebuffs,
            CallbackInfo callback) {
        if (!entity.getLevel().isClientSide()
                && ChangedSynergyGameRules.enabled(
                        entity.getLevel(),
                        ChangedSynergyGameRules.GRAB_QTE_ENHANCEMENTS)
                && applyDebuffs
                && !suited
                && grabStrength <= 0.0F
                && grabbedEntity instanceof ServerPlayer player
                && entity.getEntity() instanceof Mob mob) {
            GrabEscapeStunService.stun(mob, player);
        }
    }

    @Unique
    private static int ordinal(@Nullable KeyReference key) {
        return key == null ? -1 : key.ordinal();
    }
}
