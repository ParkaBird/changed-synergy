package net.parkabird.changedsynergy.mixin;

import javax.annotation.Nullable;
import net.ltxprogrammer.changed.ability.AbstractAbility;
import net.ltxprogrammer.changed.ability.AbstractAbilityInstance;
import net.ltxprogrammer.changed.ability.AbstractAbilityInstance.KeyReference;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.process.ProcessTransfur;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
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
    @Nullable
    public RandomSource escapeKeyRandom;

    @Shadow(remap = false)
    public int ticksUnpressed;

    @Shadow(remap = false)
    public float grabStrength;

    @Shadow(remap = false)
    public boolean suited;

    @Unique private boolean changedSynergy$harmlessRelease;
    @Unique private int changedSynergy$friendlyHoldTicks;
    @Unique private int changedSynergy$lastFriendlyHoldTick = Integer.MIN_VALUE;
    @Unique private LivingEntity changedSynergy$friendlyPassenger;

    @Unique
    private boolean changedSynergy$harmlessArmHold() {
        if (suited || grabbedEntity == null || !(entity.getEntity() instanceof ChangedEntity mob)
                || mob.getUnderlyingPlayer() != null
                || net.parkabird.changedsynergy.compat.TrueTransfurCompat.isInstinctBody(mob)) return false;
        return entity.getLevel().isClientSide()
                ? FriendlySocialHugState.isActive(mob.getId(), grabbedEntity.getId())
                        || net.parkabird.changedsynergy.compat.ChangedAddonCompat.isSafeGrab(this)
                : LatexSocialMemory.isFriendlyArmHoldTarget(mob, grabbedEntity)
                        || net.parkabird.changedsynergy.compat.ChangedAddonCompat.isSafeGrab(this);
    }

    /** A safe social hold must expire even if Addon's grab-AI flag is disabled mid-hug. */
    @Inject(method = "tickIdle", at = @At("HEAD"), cancellable = true)
    private void changedSynergy$limitFriendlyHold(CallbackInfo callback) {
        if (entity.getLevel().isClientSide()) return;
        if (!changedSynergy$harmlessArmHold()) {
            changedSynergy$friendlyPassenger = null;
            changedSynergy$friendlyHoldTicks = 0;
            return;
        }
        if (changedSynergy$friendlyPassenger != grabbedEntity) {
            changedSynergy$friendlyPassenger = grabbedEntity;
            changedSynergy$friendlyHoldTicks = 0;
        }
        if (changedSynergy$lastFriendlyHoldTick != entity.getEntity().tickCount) {
            changedSynergy$lastFriendlyHoldTick = entity.getEntity().tickCount;
            changedSynergy$friendlyHoldTicks++;
        }
        var mob = (ChangedEntity)entity.getEntity();
        boolean timedHug = LatexSocialMemory.isFriendlySocialHugTarget(mob, grabbedEntity);
        boolean expired = timedHug && !LatexSocialMemory.isFriendlySocialHugActive(mob, grabbedEntity);
        boolean ordinaryHug = !LatexSocialMemory.isFriendlyArmHoldTarget(mob, grabbedEntity) || timedHug;
        if (expired || ordinaryHug && changedSynergy$friendlyHoldTicks >= 200) {
            ((GrabEntityAbilityInstance)(Object)this).releaseEntity(false);
            callback.cancel();
        }
    }

    @Unique
    @Nullable
    private KeyReference changedSynergy$lastSentCurrent;

    @Unique
    @Nullable
    private KeyReference changedSynergy$lastSentPrevious;

    @Unique
    @Nullable
    private ReleasePlacementService.PendingRelease changedSynergy$pendingRelease;

    @Unique
    @Nullable
    private ServerPlayer changedSynergy$releasedSuitOwner;

    @Unique
    @Nullable
    private ChangedEntity changedSynergy$nextSuitCarrier;

    @Unique
    private boolean changedSynergy$friendlySuitReleased;

    protected GrabQteSyncMixin(AbstractAbility<?> ability, IAbstractChangedEntity entity) {
        super(ability, entity);
    }

    /** Native holds need the same safety rules even when Addon's safe-mode is absent. */
    @Inject(method = "tickIdle", at = @At("HEAD"), remap = false)
    private void changedSynergy$clearFriendlyHoldInputs(CallbackInfo callback) {
        if (changedSynergy$isScriptedFriendlyHold()) {
            var hold = (GrabEntityAbilityInstance)(Object)this;
            hold.attackDown = hold.useDown = false;
        }
    }

    @Inject(method = "makeAssimilationDecision", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void changedSynergy$protectFriendlyHold(
            CallbackInfoReturnable<LatexAssimilationDecision<?>> callback) {
        if (changedSynergy$isScriptedFriendlyHold()) callback.setReturnValue(null);
    }

    @Inject(method = "isGrabbedInvalid", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void changedSynergy$allowSafeTransformedPassenger(
            CallbackInfoReturnable<Boolean> callback) {
        // Only lift Changed's permanent-form restriction; death, removal,
        // dimension changes and spectators must still end the hold.
        if (grabbedEntity instanceof Player player && ProcessTransfur.isPlayerTransfurred(player)
                && changedSynergy$isScriptedFriendlyHold()
                && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && entity.getEntity().isAlive() && !entity.getEntity().isRemoved()
                && player.level() == entity.getLevel()) callback.setReturnValue(false);
    }

    @Unique
    private boolean changedSynergy$isScriptedFriendlyHold() {
        if (grabbedEntity == null || !(entity.getEntity() instanceof ChangedEntity mob)
                || mob.getUnderlyingPlayer() != null) return false;
        if (entity.getLevel().isClientSide()) {
            return FriendlySocialHugState.isActive(mob.getId(), grabbedEntity.getId());
        }
        return LatexSocialMemory.isFriendlyArmHoldActive(mob, grabbedEntity)
                || grabbedEntity instanceof ServerPlayer player
                        && LatexSocialMemory.isFriendlySuitActive(mob, player);
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

    @Inject(method = "stealGrabFrom", at = @At("HEAD"), remap = false)
    private void changedSynergy$transferEscapeGenerator(
            GrabEntityAbilityInstance previous, CallbackInfo callback) {
        // Changed transfers the keys and queued input, but omits their generator.
        // Copy before releaseEntity clears it on the previous grabber.
        if (previous != (Object)this) escapeKeyRandom = previous.escapeKeyRandom;
    }

    @Inject(method = "handleEscape", at = @At("HEAD"), remap = false)
    private void changedSynergy$repairIncompleteEscapeState(CallbackInfo callback) {
        if (currentEscapeKey != null && escapeKeyRandom == null) {
            // The server reinitializes and announces the native seed; clients
            // wait for it rather than advancing an uninitialized sequence.
            currentEscapeKey = lastEscapeKey = null;
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
                || target.getVehicle() == entity.getEntity()
                || target instanceof ChangedEntity other
                        && BondedSuitService.ability(other) != null
                        && BondedSuitService.ability(other).grabbedEntity
                                == entity.getEntity()) {
            callback.setReturnValue(false);
            return;
        }
        // Changed calls grabEntity on the current target to unwrap a suit into
        // an arm hold. This is not a new grab or a cycle; its next ability hold
        // releases the target. TakeoverGrabMixin still guards locked takeovers.
        if (target == grabbedEntity) return;
        if (target instanceof net.minecraft.world.entity.player.Player
                && net.parkabird.changedsynergy.compat.FirearmCompat.ridingSuperbVehicle(target) != null) {
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
        ServerPlayer releasedOwner = changedSynergy$releasedSuitOwner;
        changedSynergy$releasedSuitOwner = null;
        boolean harmless = changedSynergy$harmlessRelease;
        changedSynergy$harmlessRelease = false;
        if (grabbedEntity == null && harmless) {
            changedSynergy$friendlyPassenger = null;
            changedSynergy$friendlyHoldTicks = 0;
            if (entity.getEntity() instanceof ChangedEntity mob) {
                if (releasedOwner != null) {
                    // Preserve friendly markers until native debuffs have been decided.
                    LatexSocialMemory.blockGrabAgainst(mob, releasedOwner, 60);
                    ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> releasedOwner),
                            new net.parkabird.changedsynergy.network.FriendlySocialHugSyncPacket(mob.getId(), releasedOwner.getId(), false, 0));
                    net.ltxprogrammer.changed.Changed.PACKET_HANDLER.send(PacketDistributor.TRACKING_ENTITY.with(() -> mob),
                            new net.ltxprogrammer.changed.network.packet.GrabEntityPacket(mob, releasedOwner,
                                    net.ltxprogrammer.changed.network.packet.GrabEntityPacket.GrabType.RELEASE));
                }
                LatexSocialMemory.endFriendlySocialHug(mob);
                if (releasedOwner != null && SharedRestGoal.isCarryTarget(mob, releasedOwner)) SharedRestGoal.cancel(mob);
                net.parkabird.changedsynergy.compat.ChangedAddonCompat.configureFriendlyGrab(this, false);
            }
        }
        ChangedEntity nextCarrier = changedSynergy$nextSuitCarrier;
        changedSynergy$nextSuitCarrier = null;
        boolean friendlySuit = changedSynergy$friendlySuitReleased;
        changedSynergy$friendlySuitReleased = false;
        if (releasedOwner != null && grabbedEntity == null
                && entity.getEntity() instanceof ChangedEntity creature) {
            var nextAbility = nextCarrier == null ? null : BondedSuitService.ability(nextCarrier);
            if (nextAbility != null && nextAbility.grabbedEntity == releasedOwner
                    && releasedOwner instanceof net.ltxprogrammer.changed.entity.LivingEntityDataExtension extension) {
                extension.setGrabbedBy(nextCarrier);
            }
            if (friendlySuit) BondedSuitService.onNativeSuitReleased(creature, releasedOwner);
        }
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
        changedSynergy$harmlessRelease = changedSynergy$harmlessArmHold();
        changedSynergy$releasedSuitOwner = !entity.getLevel().isClientSide()
                && entity.getEntity() instanceof ChangedEntity
                && grabbedEntity instanceof ServerPlayer owner
                ? owner : null;
        changedSynergy$friendlySuitReleased = changedSynergy$releasedSuitOwner != null
                && entity.getEntity() instanceof ChangedEntity creature
                && (LatexSocialMemory.isFriendlySuitActive(creature, changedSynergy$releasedSuitOwner)
                        || BondedSuitService.isNativeOwnerSuit(creature, changedSynergy$releasedSuitOwner));
        changedSynergy$nextSuitCarrier = changedSynergy$releasedSuitOwner
                instanceof net.ltxprogrammer.changed.entity.LivingEntityDataExtension extension
                && extension.getGrabbedBy() instanceof ChangedEntity carrier
                && carrier != entity.getEntity() ? carrier : null;
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
            if (changedSynergy$harmlessArmHold()) {
                return;
            }
            GrabEscapeStunService.stun(mob, player);
        }
    }

    @ModifyVariable(method = "releaseEntity", at = @At("HEAD"), argsOnly = true,
            ordinal = 0, remap = false)
    private boolean changedSynergy$noFriendlyEscapeDebuffs(boolean applyDebuffs) {
        return applyDebuffs && !changedSynergy$harmlessArmHold();
    }

    @Unique
    private static int ordinal(@Nullable KeyReference key) {
        return key == null ? -1 : key.ordinal();
    }
}
