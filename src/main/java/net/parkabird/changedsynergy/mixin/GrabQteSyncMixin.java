package net.parkabird.changedsynergy.mixin;

import javax.annotation.Nullable;
import net.ltxprogrammer.changed.ability.AbstractAbility;
import net.ltxprogrammer.changed.ability.AbstractAbilityInstance;
import net.ltxprogrammer.changed.ability.AbstractAbilityInstance.KeyReference;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.parkabird.changedsynergy.ai.CreatureSocialProfile;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.ai.ReleasePlacementService;
import net.parkabird.changedsynergy.ai.GrabEscapeStunService;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.HypnosisProfile;
import net.parkabird.changedsynergy.ai.PatAnimationService;
import net.parkabird.changedsynergy.ai.SharedRestGoal;
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
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    @Unique
    @Nullable
    private ReleasePlacementService.PendingRelease changedSynergy$pendingRelease;

    protected GrabQteSyncMixin(AbstractAbility<?> ability, IAbstractChangedEntity entity) {
        super(ability, entity);
    }

    @Redirect(method = "tickIdle", at = @At(value = "INVOKE",
            target = "Lnet/ltxprogrammer/changed/entity/variant/TransfurVariantInstance;syncEntityPosRotWithEntity(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)V"),
            remap = false)
    private void changedSynergy$allowRestCarryLook(LivingEntity held, LivingEntity carrier) {
        boolean restCarry = entity.getEntity() instanceof ChangedEntity creature
                && grabbedEntity == held && !suited
                && (entity.getLevel().isClientSide()
                        ? FriendlySocialHugState.isLocked(creature.getId(), held.getId())
                        : SharedRestGoal.isCarryTarget(creature, held));
        if (restCarry) {
            held.setDeltaMovement(carrier.getDeltaMovement());
            held.setPos(carrier.getX(), carrier.getY(), carrier.getZ());
        } else {
            TransfurVariantInstance.syncEntityPosRotWithEntity(held, carrier);
        }
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

    /** Scripted safety holds keep their lock; social play hugs allow QTE. */
    @Inject(
            method = "handleEscape",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void changedSynergy$suppressFriendlyHugEscape(CallbackInfo callback) {
        if (grabbedEntity != null && entity.getEntity() instanceof ChangedEntity mob
                && (entity.getLevel().isClientSide()
                        ? FriendlySocialHugState.isLocked(mob.getId(), grabbedEntity.getId())
                        : SharedRestGoal.isCarryTarget(mob, grabbedEntity))) {
            grabStrength = 1.0F;
            ticksUnpressed = 0;
            currentEscapeKey = lastEscapeKey = null;
            callback.cancel();
            return;
        }
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
        if (friendlyHug
                && !(entity.getEntity() instanceof ChangedEntity mob
                        && grabbedEntity != null
                        && !entity.getLevel().isClientSide()
                        && LatexSocialMemory.isFriendlySocialHugTarget(mob, grabbedEntity))
                && (entity.getLevel().isClientSide()
                        ? grabbedEntity == null || !FriendlySocialHugState.isActive(
                                entity.getEntity().getId(), grabbedEntity.getId())
                        : true)) {
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
        // A grabber must never grab itself or another entity that already
        // holds it; either case creates a cyclic grab state.
        if (target == null
                || target == entity.getEntity()
                || target == grabbedEntity
                || target.getVehicle() == entity.getEntity()
                || target instanceof ChangedEntity other
                        && BondedSuitService.ability(other) != null
                        && BondedSuitService.ability(other).grabbedEntity
                                == entity.getEntity()) {
            callback.setReturnValue(false);
            return;
        }
        if (!entity.getLevel().isClientSide()
                && GrabEscapeStunService.isEscapeProtected(target)) {
            callback.setReturnValue(false);
            return;
        }
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
        ReleasePlacementService.PendingRelease release = changedSynergy$pendingRelease;
        changedSynergy$pendingRelease = null;
        if (release != null && grabbedEntity == null) {
            release.finish();
        }
    }

    @Inject(method = "releaseEntity", at = @At("HEAD"), remap = false)
    private void changedSynergy$stunGrabberAfterEscape(
            boolean applyDebuffs,
            CallbackInfo callback) {
        changedSynergy$pendingRelease = ReleasePlacementService.capture(entity.getEntity(), grabbedEntity);
        if (!entity.getLevel().isClientSide()
                && entity.getEntity() instanceof ChangedEntity friendlyMob
                && grabbedEntity instanceof ServerPlayer friendlyPlayer
                && LatexSocialMemory.isFriendlySocialHugTarget(friendlyMob, friendlyPlayer)
                && grabStrength <= 0.0F) {
            PatAnimationService.scheduleFixed(friendlyMob, friendlyPlayer, 20, 4);
        }
        if (!entity.getLevel().isClientSide()
                && ChangedSynergyGameRules.enabled(
                        entity.getLevel(),
                        ChangedSynergyGameRules.GRAB_QTE_ENHANCEMENTS)
                && applyDebuffs
                && !suited
                && grabStrength <= 0.0F
                && grabbedEntity instanceof ServerPlayer player
                && entity.getEntity() instanceof Mob mob) {
            if (mob instanceof ChangedEntity changed
                    && LatexSocialMemory.isFriendlySocialHugTarget(changed, player)) {
                return;
            }
            GrabEscapeStunService.stun(mob, player);
        }
    }

    @ModifyVariable(method = "releaseEntity", at = @At("HEAD"), argsOnly = true,
            ordinal = 0, remap = false)
    private boolean changedSynergy$noFriendlyEscapeDebuffs(boolean applyDebuffs) {
        return applyDebuffs && !(entity.getEntity() instanceof ChangedEntity mob
                && grabbedEntity != null
                && LatexSocialMemory.isFriendlySocialHugTarget(mob, grabbedEntity));
    }

    @Unique
    private static int ordinal(@Nullable KeyReference key) {
        return key == null ? -1 : key.ordinal();
    }
}
