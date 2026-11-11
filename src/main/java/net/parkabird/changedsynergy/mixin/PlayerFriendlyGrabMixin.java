package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.util.UniversalDist;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.PlayerHugAccess;
import net.parkabird.changedsynergy.ai.PlayerHugService;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.PlayerHugActionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shares native escape/hold physics; friendly input can never become assimilation or a suit. */
@Mixin(value = GrabEntityAbilityInstance.class, remap = false, priority = 1200)
public abstract class PlayerFriendlyGrabMixin implements PlayerHugAccess {
    @Unique private boolean changedSynergy$playerHug;
    @Unique private boolean changedSynergy$uprightCarry;
    @Unique private long changedSynergy$lastHugRequest = -1000;
    @Unique private String changedSynergy$heldForm;
    @Unique private Player changedSynergy$heldPlayer;
    @Unique private java.util.UUID changedSynergy$heldOwner;
    @Unique private int changedSynergy$friendlyTick = Integer.MIN_VALUE;
    @Unique private LivingEntity changedSynergy$tickedTarget;
    @Override public boolean changedSynergy$isPlayerHug() { return changedSynergy$playerHug; }
    @Override public void changedSynergy$setPlayerHug(boolean value) { changedSynergy$playerHug = value; }
    @Override public boolean changedSynergy$isUprightCarry() { return changedSynergy$uprightCarry; }
    @Override public void changedSynergy$setUprightCarry(boolean value) { changedSynergy$uprightCarry = value; }
    @Unique private GrabEntityAbilityInstance changedSynergy$self() { return (GrabEntityAbilityInstance)(Object)this; }
    @Unique private boolean changedSynergy$local() {
        var grab = changedSynergy$self(); var local = UniversalDist.getLocalPlayer();
        var state = PlayerHugService.clientState(grab.entity.getEntity().getUUID());
        return local != null && (local == grab.entity.getEntity() || state != null && state.owner().equals(local.getUUID()));
    }

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void changedSynergy$noSharkGrab(CallbackInfoReturnable<Boolean> callback) {
        var grab = changedSynergy$self();
        if (PlayerHugService.unsupported(grab.entity.getChangedEntity())
                && (grab.entity.getEntity() instanceof Player || PlayerHugService.owner(grab.entity.getEntity()) != null)) callback.setReturnValue(false);
    }
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void changedSynergy$friendlyControls(CallbackInfo callback) {
        var grab = changedSynergy$self();
        if (!changedSynergy$playerHug && !PlayerHugService.mode(grab)) return;
        callback.cancel();
        var clientState = grab.entity.getLevel().isClientSide() ? PlayerHugService.clientState(grab) : null;
        if (grab.grabbedEntity != null || clientState != null && clientState.target() != null) {
            if (grab.getController().getHoldTicks() >= 40) {
                grab.getController().resetHoldTicks();
                if (grab.entity.getLevel().isClientSide() && changedSynergy$local())
                    ChangedSynergyNetwork.CHANNEL.sendToServer(new PlayerHugActionPacket(PlayerHugActionPacket.RELEASE, -1));
                else if (!grab.entity.getLevel().isClientSide()) PlayerHugService.release(grab);
            }
        } else if (grab.entity.getLevel().isClientSide() && changedSynergy$local()
                && grab.entity.getLevel().getGameTime() - changedSynergy$lastHugRequest >= 7) {
            var target=net.parkabird.changedsynergy.ai.EagleOwlTraits.airborne(grab.entity.getEntity())
                    ?net.parkabird.changedsynergy.ai.EagleOwlTraits.contactTarget(grab)
                    :UniversalDist.getLocalHitResult() instanceof EntityHitResult hit&&hit.getEntity() instanceof LivingEntity living?living:null;
            if(target!=null&&PlayerHugService.validTarget(grab,target)){
                changedSynergy$lastHugRequest = grab.entity.getLevel().getGameTime();
                ChangedSynergyNetwork.CHANNEL.sendToServer(new PlayerHugActionPacket(PlayerHugActionPacket.GRAB, target.getId()));
            }
        }
    }
    @Inject(method = "grabEntity", at = @At("HEAD"), cancellable = true)
    private void changedSynergy$validateFriendlyTarget(LivingEntity target, CallbackInfoReturnable<Boolean> callback) {
        var grab = changedSynergy$self();
        if ((grab.entity.getEntity() instanceof Player || PlayerHugService.owner(grab.entity.getEntity()) != null)
                && PlayerHugService.unsupported(grab.entity.getChangedEntity())) { callback.setReturnValue(false); return; }
        if (changedSynergy$playerHug && grab.grabbedEntity == target) { callback.setReturnValue(true); return; }
        if (changedSynergy$playerHug || !grab.entity.getLevel().isClientSide()
                && PlayerHugService.mode(grab) && !PlayerHugService.validTarget(grab, target)) callback.setReturnValue(false);
    }
    @Inject(method = "grabEntity", at = @At("RETURN"))
    private void changedSynergy$markFriendly(LivingEntity target, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue()) {
            PlayerHugService.began(changedSynergy$self());
            changedSynergy$heldForm = PlayerHugService.form(target);
            var owner = PlayerHugService.escapePlayer(changedSynergy$self());
            changedSynergy$heldOwner = owner == null ? null : owner.getUUID();
        }
    }
    @Inject(method = "tickIdle", at = @At("HEAD"), cancellable = true)
    private void changedSynergy$keepFriendly(CallbackInfo callback) {
        var grab = changedSynergy$self();
        if (!changedSynergy$playerHug) return;
        if (!grab.entity.getLevel().isClientSide() && (!ChangedSynergyConfig.COMMON.playerFriendlyHugs.get()
                || PlayerHugService.owner(grab.entity.getEntity()) == null
                || PlayerHugService.escapePlayer(grab) != null && !ChangedSynergyConfig.COMMON.playerFriendlyHugPlayers.get()
                || changedSynergy$heldOwner != null && (PlayerHugService.escapePlayer(grab) == null
                    || !changedSynergy$heldOwner.equals(PlayerHugService.escapePlayer(grab).getUUID()))
                || grab.grabbedEntity != null && !PlayerHugService.form(grab.grabbedEntity).equals(changedSynergy$heldForm))) {
            PlayerHugService.release(grab); callback.cancel(); return;
        }
        // Addon and the independent TT body may both request the same idle update.
        int tick = grab.entity.getEntity().tickCount;
        if (changedSynergy$friendlyTick == tick && changedSynergy$tickedTarget == grab.grabbedEntity && grab.grabStrength > 0) {
            callback.cancel(); return;
        }
        changedSynergy$friendlyTick = tick; changedSynergy$tickedTarget = grab.grabbedEntity;
        grab.attackDown = grab.useDown = grab.suited = grab.grabbedHasControl = false;
        grab.suitTransition = grab.suitTransitionO = 0;
        PlayerHugService.linkEscapePlayer(grab);
    }
    @Redirect(method = "handleEscape", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Lnet/ltxprogrammer/changed/ability/GrabEntityAbilityInstance;grabbedEntity:Lnet/minecraft/world/entity/LivingEntity;"))
    private LivingEntity changedSynergy$playerBodyEscape(GrabEntityAbilityInstance grab) {
        var owner = PlayerHugService.escapePlayer(grab);
        return changedSynergy$playerHug && owner != null ? owner : grab.grabbedEntity;
    }

    @Redirect(method = "releaseEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/network/simple/SimpleChannel;sendToServer(Ljava/lang/Object;)V"))
    private void changedSynergy$noFriendlyReleaseEcho(net.minecraftforge.network.simple.SimpleChannel channel, Object packet) {
        // Friendly manual requests use PlayerHugActionPacket; a replicated release is not a new request.
        if (!changedSynergy$playerHug) channel.sendToServer(packet);
    }

    @Redirect(method = "tickIdle", at = @At(value = "INVOKE",
            target = "Lnet/ltxprogrammer/changed/ability/GrabEntityAbilityInstance;releaseEntity(Z)V"))
    private void changedSynergy$serverOwnsNaturalRelease(GrabEntityAbilityInstance grab, boolean debuffs) {
        if (changedSynergy$playerHug && grab.entity.getLevel().isClientSide()
                && debuffs && grab.grabStrength <= 0) return;
        grab.releaseEntity(debuffs);
    }
    @Inject(method = "makeAssimilationDecision", at = @At("HEAD"), cancellable = true)
    private void changedSynergy$noFriendlyTransfur(CallbackInfoReturnable<LatexAssimilationDecision<?>> callback) {
        if (changedSynergy$playerHug || PlayerHugService.mode(changedSynergy$self())) callback.setReturnValue(null);
    }
    @Inject(method = "suitEntity", at = @At("HEAD"), cancellable = true)
    private void changedSynergy$noFriendlySuit(LivingEntity target, CallbackInfoReturnable<Boolean> callback) {
        if (changedSynergy$playerHug || PlayerHugService.mode(changedSynergy$self())) callback.setReturnValue(false);
    }
    @Inject(method = "stopUsing", at = @At("HEAD"), cancellable = true)
    private void changedSynergy$noControlTransfer(CallbackInfo callback) { if (changedSynergy$playerHug) callback.cancel(); }
    @Inject(method = "handleInstructions", at = @At("HEAD"), cancellable = true)
    private void changedSynergy$noHostileHints(net.minecraft.world.level.Level level, CallbackInfo callback) {
        if (changedSynergy$playerHug) callback.cancel();
    }
    @Inject(method = "isGrabbedInvalid", at = @At("HEAD"), cancellable = true)
    private void changedSynergy$allowFriendlyForms(CallbackInfoReturnable<Boolean> callback) {
        var grab = changedSynergy$self(); var target = grab.grabbedEntity; var carrier = grab.entity.getEntity();
        if (changedSynergy$playerHug) callback.setReturnValue(target == null || !target.isAlive() || target.isRemoved()
                || target.isSpectator() || !carrier.isAlive() || carrier.isRemoved() || target.level() != carrier.level());
    }
    @Inject(method = "applyReleaseDebuff", at = @At("HEAD"), cancellable = true)
    private void changedSynergy$harmlessExit(float scale, LivingEntity grabbed, CallbackInfo callback) {
        if (changedSynergy$playerHug) callback.cancel();
    }
    @Inject(method = "releaseEntity", at = @At("HEAD"))
    private void changedSynergy$unlinkPlayer(boolean debuffs, CallbackInfo callback) {
        if (!changedSynergy$playerHug) return;
        var held = changedSynergy$self().grabbedEntity;
        if (held != null && held.level().isClientSide() && !(held instanceof Player)) {
            // Discard tracking interpolation queued before the native hold mirrored the carrier's position.
            held.lerpTo(held.getX(), held.getY(), held.getZ(), held.getYRot(), held.getXRot(), 0, false);
        }
        changedSynergy$heldPlayer = PlayerHugService.escapePlayer(changedSynergy$self());
        PlayerHugService.unlinkEscapePlayer(changedSynergy$self());
    }
    @Inject(method = "releaseEntity", at = @At("RETURN"))
    private void changedSynergy$clearFriendly(boolean debuffs, CallbackInfo callback) {
        if (changedSynergy$playerHug) {
            PlayerHugService.ended(changedSynergy$self());
            if (changedSynergy$heldPlayer instanceof net.minecraft.server.level.ServerPlayer player) {
                var owner = PlayerHugService.owner(changedSynergy$self().entity.getEntity());
                if (owner != null) ChangedSynergyNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                        new net.parkabird.changedsynergy.network.PlayerHugStatePacket(changedSynergy$self().entity.getEntity().getUUID(), changedSynergy$self().entity.getEntity().getId(), owner.getUUID(), null, -1, null, PlayerHugService.mode(changedSynergy$self()), false));
            }
            changedSynergy$heldPlayer = null; changedSynergy$heldForm = null; changedSynergy$heldOwner = null;
        }
    }
    @Inject(method = "saveData", at = @At("TAIL"))
    private void changedSynergy$noOrphanedHug(CompoundTag tag, CallbackInfo callback) {
        if (changedSynergy$playerHug) {
            tag.remove("GrabbedEntity"); tag.putBoolean("Suited", false); tag.putBoolean("GrabbedHasControl", false);
            tag.putFloat("GrabStrength", 0); tag.putFloat("SuitTransition", 0); tag.putInt("TicksGrabbed", 0);
        }
    }
}
