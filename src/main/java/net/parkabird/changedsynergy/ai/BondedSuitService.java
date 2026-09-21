package net.parkabird.changedsynergy.ai;

import java.util.UUID;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.LivingEntityDataExtension;
import net.ltxprogrammer.changed.entity.TamableLatexEntity;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.TransfurContext;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.init.ChangedGameRules;
import net.ltxprogrammer.changed.init.ChangedSounds;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket.GrabType;
import net.ltxprogrammer.changed.network.packet.SyncTransfurPacket;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.advancement.SynergyAdvancements;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.FriendlySuitSyncPacket;
import net.parkabird.changedsynergy.mixin.ChangedEntityAbilityInvoker;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.PacketDistributor;

/**
 * Friendly owner-suiting built on Changed's native pet suitEntity flow, plus
 * release, emergency healing, and deliberate re-assimilation controls.
 */
public final class BondedSuitService {
    private static final float EMERGENCY_TRIGGER_HEALTH = 0.30F;
    private static final float EMERGENCY_RELEASE_HEALTH = 0.80F;
    private static final int AQUATIC_RESCUE_AIR_THRESHOLD = 100;
    private static final long MINIMUM_EMERGENCY_SUIT_TICKS = 60L;
    private static final long MANUAL_RELEASE_COOLDOWN = 200L;
    private static final long RELEASE_REQUEST_DEBOUNCE_TICKS = 10L;
    private static final String DROWNING_SUIT = "ChangedSynergyDrowningSuit";
    private static final String TRANSFUR_SUIT = "ChangedSynergyTransfurSuit";
    private static final String TRANSFUR_RESCUE_OWNER =
            "ChangedSynergyTransfurRescueOwner";
    private static final String TRANSFUR_RESCUE_UNTIL =
            "ChangedSynergyTransfurRescueUntil";
    private static final long TRANSFUR_RESCUE_WINDOW = 240L;
    private static final float TRANSFUR_RESCUE_TRIGGER_RATIO = 0.75F;
    private static final float TRANSFUR_RECOVERY_PER_SECOND = 0.08F;
    private static final float TRANSFUR_RELEASE_RATIO = 0.10F;
    private static final long SUIT_TAKEOVER_REGRAB_BLOCK_TICKS = 100L;

    private BondedSuitService() {
    }

    public static boolean needsEmergencyRescue(ChangedEntity pet, ServerPlayer player) {
        return player.isAlive()
                && !player.isCreative()
                && !player.isSpectator()
                && (player.getHealth() <= player.getMaxHealth() * EMERGENCY_TRIGGER_HEALTH
                        || needsDrowningRescue(pet, player)
                        || isTransfurRescueRequested(pet, player));
    }

    /**
     * Warns the nearest available bond before its reverted owner reaches the
     * transfur threshold. Damage is cancelled only after the creature has
     * physically reached and suited the owner.
     */
    public static boolean protectOrRequestTransfurRescue(
            ServerPlayer owner,
            float incomingProgress) {
        if (incomingProgress <= 0.0F
                || !owner.isAlive()
                || owner.isCreative()
                || owner.isSpectator()) {
            return false;
        }

        ChangedEntity wrappingPet = getWrappingPet(owner);
        if (wrappingPet != null
                && (LatexSocialMemory.isPetOwner(wrappingPet, owner)
                        || isNativeOwnerSuit(wrappingPet, owner))) {
            return true;
        }
        if (ProcessTransfur.isPlayerTransfurred(owner)) {
            return false;
        }

        float currentProgress = ProcessTransfur.getPlayerTransfurProgress(owner);
        double tolerance = Math.max(
                0.0001D,
                ProcessTransfur.getEntityTransfurTolerance(owner));
        if (currentProgress + incomingProgress
                < tolerance * TRANSFUR_RESCUE_TRIGGER_RATIO) {
            return false;
        }

        for (ChangedEntity candidate : LatexSocialMemory.loadedBondedCreatures(owner)) {
            if (candidate.level() == owner.level()
                    && LatexSocialMemory.isPetOwner(candidate, owner)
                    && canStartSuit(candidate, owner)
                    && LatexSocialMemory.canStartEmergencyRescue(candidate)
                    && isTransfurRescueRequested(candidate, owner)) {
                // A rescue is already on its way, but it has not reached the
                // owner yet and therefore grants no remote immunity.
                return false;
            }
        }

        ChangedEntity rescuer = LatexSocialMemory.loadedBondedCreatures(owner)
                .stream()
                .filter(candidate -> candidate.level() == owner.level())
                .filter(candidate -> LatexSocialMemory.isPetOwner(candidate, owner))
                .filter(candidate -> canStartSuit(candidate, owner))
                .filter(LatexSocialMemory::canStartEmergencyRescue)
                .filter(candidate -> {
                    GrabEntityAbilityInstance ability = ability(candidate);
                    return ability != null && ability.grabbedEntity == null;
                })
                .min(java.util.Comparator.comparingDouble(
                        candidate -> candidate.distanceToSqr(owner)))
                .orElse(null);
        if (rescuer == null) {
            return false;
        }

        rescuer.getPersistentData().putUUID(
                TRANSFUR_RESCUE_OWNER, owner.getUUID());
        rescuer.getPersistentData().putLong(
                TRANSFUR_RESCUE_UNTIL,
                rescuer.level().getGameTime() + TRANSFUR_RESCUE_WINDOW);
        return false;
    }

    public static boolean isTransfurRescueRequested(
            ChangedEntity pet,
            ServerPlayer owner) {
        if (!pet.getPersistentData().hasUUID(TRANSFUR_RESCUE_OWNER)
                || !owner.getUUID().equals(
                        pet.getPersistentData().getUUID(TRANSFUR_RESCUE_OWNER))) {
            return false;
        }
        if (pet.getPersistentData().getLong(TRANSFUR_RESCUE_UNTIL)
                > pet.level().getGameTime()) {
            return true;
        }
        clearTransfurRescueRequest(pet);
        return false;
    }

    private static void clearTransfurRescueRequest(ChangedEntity pet) {
        pet.getPersistentData().remove(TRANSFUR_RESCUE_OWNER);
        pet.getPersistentData().remove(TRANSFUR_RESCUE_UNTIL);
    }

    /** Aquatic partners start swimming over before bubbles run out. */
    public static boolean needsDrowningRescue(ChangedEntity pet, ServerPlayer player) {
        if (!player.isUnderWater()) {
            return false;
        }
        return HunterFaction.isAquatic(pet)
                ? player.getAirSupply() <= AQUATIC_RESCUE_AIR_THRESHOLD
                : player.getAirSupply() <= 0;
    }

    /** A friendly suit may begin only for a reverted owner and never from an organic creature. */
    public static boolean canStartSuit(ChangedEntity pet, ServerPlayer owner) {
        return pet.isAlive()
                && !CreatureSocialProfile.isJuvenile(pet)
                && owner.isAlive()
                && !owner.isCreative()
                && !owner.isSpectator()
                && !LatexSocialMemory.isOrganic(pet)
                && !ProcessTransfur.isPlayerTransfurred(owner)
                && LatexSocialMemory.isPetOwner(pet, owner);
    }

    @Nullable
    public static GrabEntityAbilityInstance ability(ChangedEntity pet) {
        return IAbstractChangedEntity.forEntity(pet)
                .getAbilityInstanceSafe(ChangedAbilities.GRAB_ENTITY_ABILITY.get())
                .orElse(null);
    }

    /** Gives a reconstructed non-grabber the standard Changed suit ability. */
    public static void ensureRevivalGrabAbility(ChangedEntity pet) {
        if (ability(pet) != null) {
            return;
        }
        var instance = ChangedAbilities.GRAB_ENTITY_ABILITY.get()
                .makeInstance(IAbstractChangedEntity.forEntity(pet));
        ((ChangedEntityAbilityInvoker)pet).changedSynergy$registerAbility(
                ignored -> true, instance);
    }

    public static boolean isSuitingOwner(ChangedEntity pet, ServerPlayer owner) {
        return isWrappingOwner(pet, owner)
                && LatexSocialMemory.isFriendlySuitActive(pet, owner);
    }

    /** True for both Synergy-managed suits and Changed's native pet suit. */
    public static boolean isWrappingOwner(ChangedEntity pet, ServerPlayer owner) {
        GrabEntityAbilityInstance ability = ability(pet);
        return ability != null
                && ability.grabbedEntity == owner
                && ability.suited
                && ability.grabbedHasControl;
    }

    /** True for Changed/Addon native pet suits, whether or not Synergy started them. */
    public static boolean isNativeOwnerSuit(ChangedEntity pet, ServerPlayer owner) {
        return isWrappingOwner(pet, owner)
                && pet instanceof TamableLatexEntity nativePet
                && nativePet.getOwner() == owner;
    }

    @Nullable
    public static ChangedEntity getWrappingPet(ServerPlayer owner) {
        IAbstractChangedEntity grabber = GrabEntityAbility.getGrabber(owner);
        if (grabber == null || !(grabber.getEntity() instanceof ChangedEntity pet)) {
            return null;
        }
        return isWrappingOwner(pet, owner) ? pet : null;
    }

    @Nullable
    public static ChangedEntity getFriendlySuitPet(ServerPlayer owner) {
        ChangedEntity pet = getWrappingPet(owner);
        return pet != null && isSuitingOwner(pet, owner) ? pet : null;
    }

    /**
     * Ends an external hostile arm-grab before the bonded suit takes ownership
     * of the player. Changed normally transfers grabs inside {@code suitEntity},
     * but the old ability clears the player's grabbed-by reference after the new
     * one has claimed it. Sending RELEASE first also prevents the client from
     * retaining both movement locks at once.
     */
    private static boolean releaseConflictingGrabForSuit(
            ChangedEntity pet,
            ServerPlayer owner) {
        IAbstractChangedEntity previous = GrabEntityAbility.getGrabber(owner);
        if (previous == null) {
            return true;
        }
        LivingEntity previousGrabber = previous.getEntity();
        if (previousGrabber == pet) {
            return true;
        }
        GrabEntityAbilityInstance previousAbility = previous
                .getAbilityInstanceSafe(ChangedAbilities.GRAB_ENTITY_ABILITY.get())
                .orElse(null);
        if (previousAbility == null || previousAbility.grabbedEntity != owner) {
            if (owner instanceof LivingEntityDataExtension extension
                    && extension.getGrabbedBy() == previousGrabber) {
                extension.setGrabbedBy(null);
            }
            return true;
        }

        // Consensual holds and existing suits finish through their own state
        // machine. Only a hostile arm-grab may be pre-empted by this rescue.
        if (previousAbility.suited
                || previousGrabber instanceof ChangedEntity changedGrabber
                        && LatexSocialMemory.isFriendlyArmHoldTarget(
                                changedGrabber, owner)) {
            return false;
        }

        if (previousGrabber instanceof ChangedEntity changedGrabber) {
            LatexSocialMemory.blockGrabAgainst(
                    changedGrabber, owner, SUIT_TAKEOVER_REGRAB_BLOCK_TICKS);
            LatexSocialMemory.endSecondaryGrab(changedGrabber, owner);
        }
        previousAbility.attackDown = false;
        previousAbility.useDown = false;
        ChangedAddonCompat.configureFriendlyGrab(previousAbility, false);
        previousAbility.releaseEntity(false);
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(
                        () -> previousGrabber),
                new GrabEntityPacket(
                        previousGrabber, owner, GrabType.RELEASE));

        if (owner instanceof LivingEntityDataExtension extension
                && extension.getGrabbedBy() == previousGrabber) {
            extension.setGrabbedBy(null);
        }
        return GrabEntityAbility.getGrabber(owner) == null;
    }

    public static boolean suitOwner(
            ChangedEntity pet,
            ServerPlayer owner,
            SuitReason reason) {
        if (isSuitingOwner(pet, owner)) {
            if (reason == SuitReason.TRANSFUR) {
                LatexSocialMemory.beginFriendlySuit(pet, owner, true, false);
                pet.getPersistentData().putBoolean(TRANSFUR_SUIT, true);
                clearTransfurRescueRequest(pet);
            }
            return true;
        }
        if (!canStartSuit(pet, owner)) {
            return false;
        }

        GrabEntityAbilityInstance ability = ability(pet);
        if (ability == null) {
            return false;
        }
        if (!releaseConflictingGrabForSuit(pet, owner)) {
            return false;
        }
        // Addon's safe mode intentionally cancels suitEntity at its HEAD.  It is
        // enabled only after Changed has established the actual suit reference.
        ChangedAddonCompat.clearBondedFavor(pet);
        BondedPetSettings.clearFavor(pet);
        ChangedAddonCompat.configureFriendlyGrab(ability, false);
        if (!ability.suitEntity(owner)) {
            return false;
        }

        ability.grabbedHasControl = true;
        ability.suited = true;
        ability.attackDown = false;
        ability.useDown = false;
        if (owner instanceof LivingEntityDataExtension extension) {
            extension.setGrabbedBy(pet);
        }
        ChangedAddonCompat.configureFriendlyGrab(ability, true);
        boolean combat = reason == SuitReason.COMBAT;
        boolean emergency = reason != SuitReason.MANUAL;
        LatexSocialMemory.beginFriendlySuit(pet, owner, emergency, combat);
        if (emergency && !combat) {
            prepareProtectiveRelease(pet, owner);
        }
        if (reason == SuitReason.DROWNING) {
            pet.getPersistentData().putBoolean(DROWNING_SUIT, true);
            owner.setAirSupply(owner.getMaxAirSupply());
        } else {
            pet.getPersistentData().remove(DROWNING_SUIT);
        }
        if (reason == SuitReason.TRANSFUR) {
            pet.getPersistentData().putBoolean(TRANSFUR_SUIT, true);
        } else {
            pet.getPersistentData().remove(TRANSFUR_SUIT);
        }
        clearTransfurRescueRequest(pet);
        LatexSocialEvents.calmTowards(pet, owner);
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY.with(() -> pet),
                new GrabEntityPacket(pet, owner, GrabType.SUIT));
        ChangedAddonCompat.syncFriendlySuitControl(pet, owner, true);
        syncOwnerSuitState(pet, owner, true);
        ChangedSounds.broadcastSound(pet, ChangedSounds.LATEX_SUIT_ENTITY, 1.0F, 1.0F);

        Cue cue = switch (reason) {
            case DROWNING -> Cue.BOND_DROWNING_RESCUE;
            case TRANSFUR -> Cue.BOND_TRANSFUR_RESCUE;
            case EMERGENCY -> Cue.BOND_EMERGENCY_WRAP;
            case COMBAT -> Cue.BOND_COMBAT_WRAP;
            case MANUAL -> {
                float health = owner.getHealth()
                        / Math.max(1.0F, owner.getMaxHealth());
                if (health <= 0.45F) {
                    yield Cue.BOND_WRAP_REVERTED;
                }
                yield health <= 0.80F
                        ? Cue.BOND_WRAP_MANUAL_READY
                        : Cue.BOND_WRAP_MANUAL_CURIOUS;
            }
        };
        NpcDialogue.trigger(pet, owner, cue);
        if (emergency) {
            SynergyAdvancements.grant(
                    owner, SynergyAdvancements.FIRST_CONTACT);
            SynergyAdvancements.grant(
                    owner, SynergyAdvancements.BONDED_COMPANION);
            SynergyAdvancements.grant(
                    owner, SynergyAdvancements.PROTECTED_BY_SYNERGY);
        }
        return true;
    }

    /**
     * Uses Changed's real suit state for the final phase of mask reconstruction.
     * The player already has the mask-selected form, so that form is explicitly
     * made temporary and is removed when the owner releases the rebuilt body.
     */
    public static boolean beginRevivalSuit(
            ChangedEntity pet,
            ServerPlayer owner,
            UUID revivalToken) {
        GrabEntityAbilityInstance ability = ability(pet);
        TransfurVariantInstance<?> current =
                ProcessTransfur.getPlayerTransfurVariant(owner);
        if (!pet.isAlive()
                || !owner.isAlive()
                || owner.isCreative()
                || owner.isSpectator()
                || ability == null
                || current == null
                || !releaseConflictingGrabForSuit(pet, owner)) {
            return false;
        }

        ChangedAddonCompat.clearBondedFavor(pet);
        BondedPetSettings.clearFavor(pet);
        ChangedAddonCompat.configureFriendlyGrab(ability, false);
        if (!ability.suitEntity(owner)) {
            return false;
        }
        current.setTemporaryForSuit(true);
        ability.grabbedHasControl = true;
        ability.suited = true;
        ability.attackDown = false;
        ability.useDown = false;
        if (owner instanceof LivingEntityDataExtension extension) {
            extension.setGrabbedBy(pet);
        }
        ChangedAddonCompat.configureFriendlyGrab(ability, true);
        LatexSocialMemory.beginFriendlySuit(pet, owner, false, false);
        pet.getPersistentData().putUUID(
                BondedRevivalService.REVIVAL_ENTITY_TOKEN, revivalToken);
        pet.getPersistentData().remove(DROWNING_SUIT);
        pet.getPersistentData().remove(TRANSFUR_SUIT);
        clearTransfurRescueRequest(pet);
        LatexSocialEvents.calmTowards(pet, owner);
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY.with(() -> pet),
                new GrabEntityPacket(pet, owner, GrabType.SUIT));
        ChangedAddonCompat.syncFriendlySuitControl(pet, owner, true);
        syncOwnerSuitState(pet, owner, true);
        ChangedSounds.broadcastSound(
                pet, ChangedSounds.LATEX_SUIT_ENTITY, 1.0F, 1.0F);
        return true;
    }

    private static void prepareProtectiveRelease(
            ChangedEntity pet,
            ServerPlayer owner) {
        if (!ChangedSynergyConfig.COMMON.bondProtectiveReleaseRequests.get()
                || !ChangedSynergyGameRules.enabled(
                        pet.level(), ChangedSynergyGameRules.PERSONALITY_SYSTEM)) {
            LatexSocialMemory.configureFriendlySuitReleaseRequests(pet, owner, 1);
            return;
        }
        if (!LatexSocialMemory.claimFriendlySuitConcernRecord(pet, owner)) {
            return;
        }
        int concern = CreaturePersonality.rememberDangerRescue(pet, owner);
        LatexSocialMemory.configureFriendlySuitReleaseRequests(
                pet, owner, requiredReleaseRequests(pet, concern));
    }

    private static int requiredReleaseRequests(ChangedEntity pet, int concern) {
        return switch (CreaturePersonality.dominantTrait(pet)) {
            case SENSITIVE -> concern >= 5 ? 3 : concern >= 2 ? 2 : 1;
            case CAUTIOUS -> concern >= 5 ? 3 : concern >= 3 ? 2 : 1;
            case PROTECTIVE -> concern >= 3 ? 3 : 1;
            default -> 1;
        };
    }

    /**
     * Handles one radial-wheel release request. The wheel closes client-side
     * after every click; accepted progress remains on the creature for the next
     * time the player opens it.
     */
    public static boolean requestOwnerRelease(
            ChangedEntity pet,
            ServerPlayer owner) {
        if (!isSuitingOwner(pet, owner)) {
            return false;
        }
        if (BondedRevivalService.token(pet) != null
                || !LatexSocialMemory.isEmergencySuitActive(pet, owner)
                || LatexSocialMemory.isCombatSuitActive(pet, owner)
                || !ChangedSynergyConfig.COMMON.bondProtectiveReleaseRequests.get()
                || !ChangedSynergyGameRules.enabled(
                        pet.level(), ChangedSynergyGameRules.PERSONALITY_SYSTEM)
                || LatexSocialMemory.friendlySuitReleaseRequestsRequired(pet, owner) <= 1) {
            return releaseOwner(pet, owner, ReleaseReason.MANUAL);
        }
        if (!protectiveReleaseConditionsMet(pet, owner)) {
            NpcDialogue.trigger(pet, owner, Cue.BOND_SAFETY_RELEASE_NOT_READY);
            owner.displayClientMessage(Component.translatable(
                    "message.changed_synergy.bond_release_not_ready"), true);
            return false;
        }

        int accepted = LatexSocialMemory.acceptFriendlySuitReleaseRequest(
                pet, owner, RELEASE_REQUEST_DEBOUNCE_TICKS);
        if (accepted < 0) {
            return false;
        }
        int required = LatexSocialMemory.friendlySuitReleaseRequestsRequired(
                pet, owner);
        if (accepted >= required) {
            NpcDialogue.triggerPersonality(
                    pet, owner, Cue.BOND_SAFETY_RELEASE_ACCEPT);
            return releaseOwner(pet, owner, ReleaseReason.SAFETY_ACCEPTED);
        }
        NpcDialogue.triggerPersonality(
                pet,
                owner,
                accepted == 1
                        ? Cue.BOND_SAFETY_RELEASE_REFUSE_FIRST
                        : Cue.BOND_SAFETY_RELEASE_REFUSE_REPEAT);
        return false;
    }

    public static boolean releaseOwner(
            ChangedEntity pet,
            ServerPlayer owner,
            ReleaseReason reason) {
        GrabEntityAbilityInstance ability = ability(pet);
        if (ability == null || ability.grabbedEntity != owner) {
            LatexSocialMemory.endFriendlySuit(pet, owner);
            pet.getPersistentData().remove(DROWNING_SUIT);
            pet.getPersistentData().remove(TRANSFUR_SUIT);
            clearTransfurRescueRequest(pet);
            clearTemporarySuitVariant(owner);
            syncOwnerSuitState(pet, owner, false);
            return false;
        }

        TransfurVariantInstance<?> current = ProcessTransfur.getPlayerTransfurVariant(owner);
        boolean temporarySuit = current != null && current.isTemporaryFromSuit();
        if (temporarySuit && !owner.onGround()) {
            owner.displayClientMessage(Component.translatable(
                    "message.changed_synergy.suit.airborne_release_blocked"), true);
            return false;
        }
        boolean emergency = LatexSocialMemory.isEmergencySuitActive(pet, owner);
        boolean combat = LatexSocialMemory.isCombatSuitActive(pet, owner);
        boolean drowning = pet.getPersistentData().getBoolean(DROWNING_SUIT);
        boolean transfurRescue = pet.getPersistentData().getBoolean(TRANSFUR_SUIT);
        UUID revivalToken = BondedRevivalService.token(pet);
        pet.getPersistentData().remove(DROWNING_SUIT);
        pet.getPersistentData().remove(TRANSFUR_SUIT);
        clearTransfurRescueRequest(pet);
        ability.releaseEntity(false);
        ChangedAddonCompat.configureFriendlyGrab(ability, false);
        LatexSocialMemory.endFriendlySuit(pet, owner);
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY.with(() -> pet),
                new GrabEntityPacket(pet, owner, GrabType.RELEASE));
        ChangedAddonCompat.syncFriendlySuitControl(pet, owner, false);
        syncOwnerSuitState(pet, owner, false);
        if (temporarySuit) {
            ProcessTransfur.removePlayerTransfurVariant(owner);
        }
        ChangedSounds.broadcastSound(pet, ChangedSounds.LATEX_UNSUIT_ENTITY, 1.0F, 1.0F);

        if (revivalToken != null) {
            ProcessTransfur.setPlayerTransfurProgress(owner, 0.0F);
            suppressPostRevivalRescue(owner);
            BondedRevivalService.complete(pet, owner, revivalToken);
            return true;
        }

        if (reason == ReleaseReason.MANUAL
                && (combat || emergency
                        && owner.getHealth() < owner.getMaxHealth() * EMERGENCY_RELEASE_HEALTH)) {
            LatexSocialMemory.delayEmergencyRescue(pet, MANUAL_RELEASE_COOLDOWN);
        }

        Cue cue = reason == ReleaseReason.RECOVERED
                ? transfurRescue
                        ? Cue.BOND_TRANSFUR_RECOVERED
                        : drowning
                                ? Cue.BOND_DROWNING_RECOVERED
                                : Cue.BOND_EMERGENCY_RECOVERED
                : temporarySuit
                        ? Cue.BOND_RELEASE_REVERTED
                        : Cue.BOND_RELEASE_TRANSFURRED;
        if (reason != ReleaseReason.SAFETY_ACCEPTED) {
            NpcDialogue.trigger(pet, owner, cue);
        }
        return true;
    }

    /** Clears already queued rescue requests and gives the completed release time to settle. */
    private static void suppressPostRevivalRescue(ServerPlayer owner) {
        for (ChangedEntity bonded : LatexSocialMemory.loadedBondedCreatures(owner)) {
            clearTransfurRescueRequest(bonded);
            LatexSocialMemory.delayEmergencyRescue(
                    bonded, TRANSFUR_RESCUE_WINDOW);
        }
    }

    /**
     * Detaches a native Changed/Addon pet before its wrapping around the owner
     * variant is reversed. The actual pet entity is retained; only the grab and
     * suit references are cleared.
     */
    public static boolean separateNativeOwnerSuit(
            ChangedEntity pet,
            ServerPlayer owner) {
        if (!isNativeOwnerSuit(pet, owner)) {
            return false;
        }
        GrabEntityAbilityInstance ability = ability(pet);
        if (ability == null || ability.grabbedEntity != owner) {
            return false;
        }

        boolean bondedSuit = LatexSocialMemory.isFriendlySuitActive(pet, owner);
        ability.releaseEntity(false);
        ChangedAddonCompat.configureFriendlyGrab(ability, false);
        LatexSocialMemory.endFriendlySuit(pet, owner);
        pet.getPersistentData().remove(DROWNING_SUIT);
        pet.getPersistentData().remove(TRANSFUR_SUIT);
        clearTransfurRescueRequest(pet);
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY.with(() -> pet),
                new GrabEntityPacket(pet, owner, GrabType.RELEASE));
        ChangedAddonCompat.syncFriendlySuitControl(pet, owner, false);
        syncOwnerSuitState(pet, owner, false);
        ChangedSounds.broadcastSound(pet, ChangedSounds.LATEX_UNSUIT_ENTITY, 1.0F, 1.0F);
        LatexSocialEvents.calmTowards(pet, owner);
        if (bondedSuit) {
            NpcDialogue.trigger(pet, owner, Cue.BOND_RELEASE_REVERTED);
        }
        return true;
    }

    /** Covers direct form removal paths that do not post an untransfur event. */
    public static void tickNativeOwnerSuit(ServerPlayer owner) {
        ChangedEntity pet = getWrappingPet(owner);
        if (pet == null || !isNativeOwnerSuit(pet, owner)) {
            return;
        }
        TransfurVariantInstance<?> current = ProcessTransfur.getPlayerTransfurVariant(owner);
        if (current == null || !current.isTemporaryFromSuit()) {
            separateNativeOwnerSuit(pet, owner);
        }
    }

    /** Clears the native suit reference without producing a normal release cue. */
    public static void breakFriendlySuitOnDeath(ChangedEntity pet, ServerPlayer owner) {
        clearFriendlySuitReference(pet, owner, false);
    }

    /**
     * Removes any player-side wrapping state that survived the death event.
     *
     * <p>The normal release path starts from the wrapping creature. Death and
     * player cloning can invalidate that reference first, however, while
     * Changed's temporary suit variant is still attached to the player. Keep
     * this cleanup independent and idempotent so it is safe to call from the
     * death, clone, and respawn phases.</p>
     */
    public static void clearStaleOwnerStateAfterDeath(ServerPlayer owner) {
        TransfurVariantInstance<?> current =
                ProcessTransfur.getPlayerTransfurVariant(owner);
        if (current == null || !current.isTemporaryFromSuit()) {
            return;
        }
        if (owner instanceof LivingEntityDataExtension extension) {
            extension.setGrabbedBy(null);
        }
        owner.setInvisible(false);
        ProcessTransfur.removePlayerTransfurVariant(owner);
    }

    /** Releases an owner before a voluntary bond ending, without a duplicate cue. */
    public static void releaseForBondEnd(ChangedEntity pet, ServerPlayer owner) {
        clearFriendlySuitReference(pet, owner, true);
    }

    private static void clearFriendlySuitReference(
            ChangedEntity pet,
            ServerPlayer owner,
            boolean playReleaseSound) {
        GrabEntityAbilityInstance ability = ability(pet);
        if (ability != null && ability.grabbedEntity == owner) {
            ability.releaseEntity(false);
            Changed.PACKET_HANDLER.send(
                    PacketDistributor.TRACKING_ENTITY.with(() -> pet),
                    new GrabEntityPacket(pet, owner, GrabType.RELEASE));
            ChangedAddonCompat.syncFriendlySuitControl(pet, owner, false);
            if (playReleaseSound) {
                ChangedSounds.broadcastSound(
                        pet, ChangedSounds.LATEX_UNSUIT_ENTITY, 1.0F, 1.0F);
            }
        }
        ChangedAddonCompat.configureFriendlyGrab(ability, false);
        LatexSocialMemory.endFriendlySuit(pet, owner);
        pet.getPersistentData().remove(DROWNING_SUIT);
        pet.getPersistentData().remove(TRANSFUR_SUIT);
        clearTransfurRescueRequest(pet);
        syncOwnerSuitState(pet, owner, false);
        clearTemporarySuitVariant(owner);
    }

    /** Permanently restores the bonded creature's own form to a reverted owner. */
    public static boolean reassimilateOwner(ChangedEntity pet, ServerPlayer owner) {
        if (!pet.isAlive()
                || !LatexSocialMemory.isPetOwner(pet, owner)
                || ProcessTransfur.isPlayerTransfurred(owner)) {
            return false;
        }
        TransfurVariant<?> sourceVariant = pet.getSelfVariant();
        if (sourceVariant == null) {
            return false;
        }

        TransfurContext context = TransfurContext.npcLatexHazard(pet, TransfurCause.GRAB_REPLICATE);
        float progress = owner.level().getGameRules()
                .getBoolean(ChangedGameRules.RULE_DO_TRANSFUR_ANIMATION) ? 0.0F : 1.0F;
        TransfurVariantInstance<?> instance = ProcessTransfur.setPlayerTransfurVariant(
                owner, sourceVariant, context, progress);
        if (instance == null) {
            return false;
        }
        instance.transfurContext = context;
        instance.transfurProgressionO = progress;
        instance.transfurProgression = progress;
        instance.setTemporaryForSuit(false);
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> owner),
                SyncTransfurPacket.Builder.of(owner));
        if (LatexSocialMemory.isOrganic(pet)) {
            ChangedSounds.broadcastSound(
                    owner, ChangedSounds.TRANSFUR_BY_NOT_LATEX, 1.0F, 1.0F);
        } else {
            ChangedSounds.broadcastSound(owner, sourceVariant.sound, 1.0F, 1.0F);
        }
        LatexSocialMemory.addBond(pet, owner);
        LatexSocialEvents.calmTowards(pet, owner);
        NpcDialogue.trigger(pet, owner, Cue.BOND_REASSIMILATE);
        return true;
    }

    /** Permanently takes a reverted native pet owner into the pet's own form. */
    public static boolean reassimilateNativePet(ChangedEntity pet, ServerPlayer owner) {
        if (!pet.isAlive()
                || pet.level() != owner.level()
                || owner.distanceToSqr(pet) > 64.0D
                || !(pet instanceof TamableLatexEntity nativePet)
                || nativePet.getOwner() != owner
                || ProcessTransfur.isPlayerTransfurred(owner)) {
            return false;
        }
        TransfurVariant<?> sourceVariant = pet.getSelfVariant();
        if (sourceVariant == null) {
            return false;
        }

        TransfurContext context = TransfurContext.npcLatexHazard(pet, TransfurCause.GRAB_REPLICATE);
        float progress = owner.level().getGameRules()
                .getBoolean(ChangedGameRules.RULE_DO_TRANSFUR_ANIMATION) ? 0.0F : 1.0F;
        TransfurVariantInstance<?> instance = ProcessTransfur.setPlayerTransfurVariant(
                owner, sourceVariant, context, progress);
        if (instance == null) {
            return false;
        }
        instance.transfurContext = context;
        instance.transfurProgressionO = progress;
        instance.transfurProgression = progress;
        instance.setTemporaryForSuit(false);
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> owner),
                SyncTransfurPacket.Builder.of(owner));
        if (LatexSocialMemory.isOrganic(pet)) {
            ChangedSounds.broadcastSound(
                    owner, ChangedSounds.TRANSFUR_BY_NOT_LATEX, 1.0F, 1.0F);
        } else {
            ChangedSounds.broadcastSound(owner, sourceVariant.sound, 1.0F, 1.0F);
        }
        LatexSocialEvents.calmTowards(pet, owner);
        NpcDialogue.trigger(pet, owner, Cue.BOND_REASSIMILATE);
        return true;
    }

    /** Maintains healing and performs the automatic release after emergency recovery. */
    public static void tickFriendlySuit(ServerPlayer owner) {
        if (!ChangedSynergyGameRules.enabled(
                owner.level(), ChangedSynergyGameRules.BOND_SYSTEM)) {
            ChangedEntity wrapping = getWrappingPet(owner);
            if (wrapping != null
                    && LatexSocialMemory.isFriendlySuitActive(wrapping, owner)) {
                releaseOwner(wrapping, owner, ReleaseReason.MANUAL);
            }
            return;
        }
        ChangedEntity pet = getFriendlySuitPet(owner);
        if (pet == null) {
            return;
        }
        GrabEntityAbilityInstance ability = ability(pet);
        if (ability == null || ability.grabbedEntity != owner || !ability.suited) {
            LatexSocialMemory.endFriendlySuit(pet, owner);
            ChangedAddonCompat.configureFriendlyGrab(ability, false);
            pet.getPersistentData().remove(DROWNING_SUIT);
            pet.getPersistentData().remove(TRANSFUR_SUIT);
            clearTransfurRescueRequest(pet);
            clearTemporarySuitVariant(owner);
            syncOwnerSuitState(pet, owner, false);
            return;
        }

        TransfurVariantInstance<?> current = ProcessTransfur.getPlayerTransfurVariant(owner);
        if (LatexSocialMemory.isOrganic(pet)
                || current == null
                || !current.isTemporaryFromSuit()) {
            releaseOwner(pet, owner, ReleaseReason.MANUAL);
            return;
        }

        ability.grabbedHasControl = true;
        ability.attackDown = false;
        ability.useDown = false;
        ChangedAddonCompat.configureFriendlyGrab(ability, true);
        if (owner.tickCount % 20 == 0) {
            ChangedAddonCompat.syncFriendlySuitControl(pet, owner, true);
            syncOwnerSuitState(pet, owner, true);
        }
        LatexSocialEvents.calmTowards(pet, owner);

        if (owner.tickCount % 20 == 0 && owner.getHealth() < owner.getMaxHealth()) {
            float recovery = CreaturePersonality.bondedRecoveryFraction(pet, owner);
            owner.heal(Math.max(1.0F, owner.getMaxHealth() * recovery));
            owner.clearFire();
        }
        if (owner.tickCount % 20 == 0
                && LatexSocialMemory.isEmergencySuitActive(pet, owner)
                && pet.getHealth() < pet.getMaxHealth()) {
            float recovery = CreaturePersonality.bondedRecoveryFraction(pet, owner);
            pet.heal(Math.max(1.0F, pet.getMaxHealth() * recovery));
            pet.clearFire();
        }
        if (owner.tickCount % 20 == 0
                && pet.getPersistentData().getBoolean(TRANSFUR_SUIT)) {
            float progress = ProcessTransfur.getPlayerTransfurProgress(owner);
            float tolerance = (float)Math.max(
                    0.0001D,
                    ProcessTransfur.getEntityTransfurTolerance(owner));
            if (progress > 0.0F) {
                ProcessTransfur.setPlayerTransfurProgress(
                        owner,
                        Math.max(
                                0.0F,
                                progress
                                        - tolerance
                                                * TRANSFUR_RECOVERY_PER_SECOND));
            }
        }
        if (owner.getAirSupply() < owner.getMaxAirSupply()) {
            owner.setAirSupply(owner.getMaxAirSupply());
        }

        boolean releaseReady = protectiveReleaseConditionsMet(pet, owner);
        if (LatexSocialMemory.isEmergencySuitActive(pet, owner)
                && !LatexSocialMemory.isCombatSuitActive(pet, owner)) {
            if (!releaseReady) {
                return;
            }
            int required = ChangedSynergyConfig.COMMON
                    .bondProtectiveReleaseRequests.get()
                    && ChangedSynergyGameRules.enabled(
                            pet.level(), ChangedSynergyGameRules.PERSONALITY_SYSTEM)
                            ? LatexSocialMemory.friendlySuitReleaseRequestsRequired(
                                    pet, owner)
                            : 1;
            if (required <= 1) {
                releaseOwner(pet, owner, ReleaseReason.RECOVERED);
                return;
            }
            if (LatexSocialMemory.claimFriendlySuitHoldAnnouncement(pet, owner)) {
                NpcDialogue.triggerPersonality(
                        pet, owner, Cue.BOND_SAFETY_HOLD_READY);
            }
        }
    }

    private static boolean protectiveReleaseConditionsMet(
            ChangedEntity pet,
            ServerPlayer owner) {
        long heldTicks = pet.level().getGameTime()
                - LatexSocialMemory.friendlySuitStarted(pet);
        float transfurProgress = ProcessTransfur.getPlayerTransfurProgress(owner);
        float transfurTolerance = (float)Math.max(
                0.0001D,
                ProcessTransfur.getEntityTransfurTolerance(owner));
        boolean transfurSafe =
                !pet.getPersistentData().getBoolean(TRANSFUR_SUIT)
                        || transfurProgress
                                <= transfurTolerance * TRANSFUR_RELEASE_RATIO;
        boolean drowningSafe =
                !pet.getPersistentData().getBoolean(DROWNING_SUIT)
                        || !owner.isUnderWater();
        return heldTicks >= MINIMUM_EMERGENCY_SUIT_TICKS
                && owner.onGround()
                && owner.getHealth()
                        >= owner.getMaxHealth() * EMERGENCY_RELEASE_HEALTH
                && transfurSafe
                && drowningSafe;
    }

    /** Also repairs Changed/Addon native owner suits that were not started by Synergy. */
    public static void syncOwnerSuitState(
            ChangedEntity pet,
            ServerPlayer owner,
            boolean active) {
        FriendlySuitSyncPacket packet = new FriendlySuitSyncPacket(
                pet.getId(), owner.getId(), active);
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY.with(() -> pet), packet);
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> owner), packet);
    }

    private static void clearTemporarySuitVariant(ServerPlayer owner) {
        TransfurVariantInstance<?> current = ProcessTransfur.getPlayerTransfurVariant(owner);
        if (current != null && current.isTemporaryFromSuit()) {
            ProcessTransfur.removePlayerTransfurVariant(owner);
        }
    }

    public enum SuitReason {
        MANUAL,
        EMERGENCY,
        DROWNING,
        COMBAT,
        TRANSFUR
    }

    public enum ReleaseReason {
        MANUAL,
        RECOVERED,
        SAFETY_ACCEPTED
    }
}
