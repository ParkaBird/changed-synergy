package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.init.ChangedSounds;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket.GrabType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.FriendlySocialHugSyncPacket;
import net.parkabird.changedsynergy.network.FriendlySocialHugState;
import net.parkabird.changedsynergy.network.RevivalGrabAbilityPacket;

/** Owns Synergy NPC holds; optional mods only adapt their private ability and safety APIs. */
@Mod.EventBusSubscriber(modid = "changed_synergy")
public final class CreatureGrabService {
    private static final String SYNCED_NATIVE_SUIT_OWNER = "ChangedSynergySyncedNativeSuitOwner";
    private static final String SYNCED_NATIVE_SUIT_WAS_BONDED = "ChangedSynergySyncedNativeSuitWasBonded";
    private static final String ANNOUNCED_COMBAT_GRAB_TARGET = "ChangedSynergyAnnouncedCombatGrabTarget";
    private CreatureGrabService() {}

    public static GrabEntityAbilityInstance ability(ChangedEntity creature) {
        var registered = IAbstractChangedEntity.forEntity(creature)
                .getAbilityInstanceSafe(ChangedAbilities.GRAB_ENTITY_ABILITY.get()).orElse(null);
        if (registered != null && registered.grabbedEntity != null) return registered;
        var optional = ChangedAddonCompat.grabAbility(creature);
        return optional != null && optional.grabbedEntity != null ? optional : registered != null ? registered : optional;
    }

    public static boolean isGrabberBusy(ChangedEntity creature) {
        var grab = ability(creature);
        return grab != null && grab.grabbedEntity != null;
    }

    public static boolean isSafeGrab(Object value) {
        if (!(value instanceof GrabEntityAbilityInstance grab)) return false;
        if (PlayerHugService.active(grab) || PlayerHugService.mode(grab)) return true;
        return isScriptedHold(grab) || ChangedAddonCompat.isSafeGrab(grab);
    }

    public static boolean isScriptedHold(GrabEntityAbilityInstance grab) {
        if (grab.grabbedEntity == null || !(grab.entity.getEntity() instanceof ChangedEntity carrier)
                || carrier.getUnderlyingPlayer() != null || TrueTransfurCompat.isInstinctBody(carrier)) return false;
        return grab.entity.getLevel().isClientSide()
                ? FriendlySocialHugState.isActive(carrier.getId(), grab.grabbedEntity.getId())
                : LatexSocialMemory.isFriendlyArmHoldTarget(carrier, grab.grabbedEntity);
    }

    public static GrabEntityAbilityInstance ensureAbility(ChangedEntity creature) {
        var occupied = ability(creature);
        if (occupied != null && occupied.grabbedEntity != null) return occupied;
        if (BondedSuitService.ability(creature) == null) {
            BondedSuitService.ensureGrabAbility(creature);
            ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> creature),
                    new RevivalGrabAbilityPacket(creature.getId()));
        }
        return BondedSuitService.ability(creature);
    }

    private static void cooldown(ChangedEntity creature, GrabEntityAbilityInstance ability, int ticks) {
        if (ability != null) ability.getController().forceCooldown(ticks);
        ChangedAddonCompat.applyGrabCooldown(creature, ticks);
    }

    /** A late observer needs the native hold and its social marker in the same order. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if(event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof net.parkabird.changedsynergy.entity.LatexEagleOwl owl){
            var grab=ability(owl);
            if(grab!=null&&grab.grabbedEntity!=null&&!grab.suited&&!isScriptedHold(grab)){
                ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->observer),new RevivalGrabAbilityPacket(owl.getId()));
                Changed.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(()->observer),new GrabEntityPacket(owl,grab.grabbedEntity,GrabType.ARMS));
            }
        }
        if (!(event.getEntity() instanceof ServerPlayer observer)
                || !(event.getTarget() instanceof ChangedEntity carrier)
                || carrier.getUnderlyingPlayer() != null || TrueTransfurCompat.isInstinctBody(carrier)
                || !(LatexSocialMemory.hasFriendlySocialHug(carrier)
                    || InvoluntaryTransfurNegotiation.hasReleaseHold(carrier)
                    || LatexSocialMemory.hasOrganicEvacuation(carrier))) return;
        var grab = ability(carrier);
        if (grab == null || grab.grabbedEntity == null || !isScriptedHold(grab)) return;
        int duration = LatexSocialMemory.hasFriendlySocialHug(carrier)
                ? (int)Math.max(1L, LatexSocialMemory.friendlySocialHugUntil(carrier) - carrier.level().getGameTime()) : 200;
        ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> observer),
                new RevivalGrabAbilityPacket(carrier.getId()));
        ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> observer),
                new FriendlySocialHugSyncPacket(carrier.getId(), grab.grabbedEntity.getId(), true, duration + 10));
        Changed.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> observer),
                new GrabEntityPacket(carrier, grab.grabbedEntity, GrabType.ARMS));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity mob) || mob.level().isClientSide
                || mob.getUnderlyingPlayer() != null || TrueTransfurCompat.isInstinctBody(mob)
                || TakeoverService.carrying(mob)) return;
        var ability = ability(mob);
        if (LatexSocialMemory.hasFriendlySocialHug(mob)) {
            tickFriendlySocialHug(mob, ability);
            return;
        }
        if (InvoluntaryTransfurNegotiation.hasReleaseHold(mob)) {
            tickNegotiatedReleaseHold(mob, ability);
            return;
        }
        if (LatexSocialMemory.hasOrganicEvacuation(mob) && tickOrganicEvacuationHold(mob, ability)) return;
        announceBondedCombatGrab(mob, ability);
        clearStaleNativeSuitSync(mob, ability);
        if (ability != null && ability.grabbedEntity instanceof ServerPlayer player
                && BondedSuitService.isNativeOwnerSuit(mob, player)) {
            boolean firstSync = !mob.getPersistentData().hasUUID(SYNCED_NATIVE_SUIT_OWNER);
            boolean bonded = LatexSocialMemory.isFriendlySuitActive(mob, player);
            ability.grabbedHasControl = ability.suited = true;
            ability.attackDown = ability.useDown = false;
            if (bonded) ChangedAddonCompat.configureFriendlyGrab(ability, true);
            if (mob.tickCount % 20 == Math.floorMod(mob.getId(), 20)) BondedSuitService.syncOwnerSuitState(mob, player, true);
            mob.getPersistentData().putUUID(SYNCED_NATIVE_SUIT_OWNER, player.getUUID());
            mob.getPersistentData().putBoolean(SYNCED_NATIVE_SUIT_WAS_BONDED, bonded);
            if (firstSync && !bonded) NpcDialogue.trigger(mob, player, Cue.BOND_WRAP_REVERTED);
            LatexSocialEvents.calmTowards(mob, player);
        }
    }

    public static boolean tryStartSocialHug(
            ChangedEntity mob,
            ServerPlayer player,
            int durationTicks) {
        if (mob.level().isClientSide
                || !mob.isAlive()
                || !player.isAlive()
                || mob.level() != player.level()
                || mob.distanceToSqr(player) > 3.0D * 3.0D
                || HypnosisProfile.isHypnoticCreature(mob)
                || LatexSocialMemory.isProvoked(mob, player)
                || !(LatexSocialMemory.isPetOwner(mob, player)
                        || CreaturePersonality.hasTrustedRelationship(mob, player))
                || CreatureSocialProfile.isGrabMechanicExcluded(mob)
                || !CreatureSocialProfile.allowsSynergySystems(mob)
                || !Changed.config.server.isGrabEnabled.get()
                || TakeoverService.carrying(mob) || TakeoverService.active(player)
                || GrabEntityAbility.getGrabber(player) != null) {
            return false;
        }

        GrabEntityAbilityInstance ability = ensureAbility(mob);
        if (ability == null || ability.getController().isCoolingDown()
                || ability.grabbedEntity != null) {
            return false;
        }

        int safeDuration = Math.max(100, Math.min(200, durationTicks));
        LatexSocialMemory.beginFriendlySocialHug(mob, player, safeDuration);
        ChangedAddonCompat.configureFriendlyGrab(ability, true);
        ability.suited = false;
        ability.grabbedHasControl = false;
        ability.grabStrength = 1.0F;
        ability.attackDown = false;
        ability.useDown = false;
        if (!ability.grabEntity(player)) {
            LatexSocialMemory.endFriendlySocialHug(mob);
            ChangedAddonCompat.configureFriendlyGrab(ability, false);
            return false;
        }

        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> mob),
                new GrabEntityPacket(mob, player, GrabType.ARMS));
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> mob),
                new FriendlySocialHugSyncPacket(
                        mob.getId(),
                        player.getId(),
                        true,
                        safeDuration + 10));
        cooldown(mob, ability, 0);
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
        LatexSocialEvents.calmTowards(mob, player);
        ChangedSounds.broadcastSound(
                mob, ChangedSounds.LATEX_GRAB_ENTITY, 0.7F, 1.12F);
        return true;
    }

    public static boolean tryStartNegotiatedRelease(
            ChangedEntity mob,
            ServerPlayer player,
            int durationTicks) {
        if (mob.level().isClientSide
                || !mob.isAlive()
                || !player.isAlive()
                || mob.level() != player.level()
                || mob.distanceToSqr(player) > 4.0D * 4.0D
                || !InvoluntaryTransfurNegotiation.isReleaseHoldTarget(
                        mob, player)) {
            return false;
        }

        GrabEntityAbilityInstance ability = ensureAbility(mob);
        if (ability == null) {
            return false;
        }
        if (ability.grabbedEntity == player) {
            configureNegotiatedReleaseHold(mob, ability, player);
            return true;
        }
        if (ability.grabbedEntity != null
                || GrabEntityAbility.getGrabber(player) != null) {
            return false;
        }

        ChangedAddonCompat.configureFriendlyGrab(ability, true);
        ability.suited = false;
        ability.grabbedHasControl = false;
        ability.grabStrength = 1.0F;
        ability.attackDown = false;
        ability.useDown = false;
        if (!ability.grabEntity(player)) {
            ChangedAddonCompat.configureFriendlyGrab(ability, false);
            return false;
        }

        int safeDuration = Math.max(36, Math.min(80, durationTicks));
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> mob),
                new GrabEntityPacket(mob, player, GrabType.ARMS));
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> mob),
                new FriendlySocialHugSyncPacket(
                        mob.getId(), player.getId(), true, safeDuration + 10));
        configureNegotiatedReleaseHold(mob, ability, player);
        ChangedSounds.broadcastSound(
                mob, ChangedSounds.LATEX_GRAB_ENTITY, 0.7F, 0.94F);
        return true;
    }

    public static LivingEntity socialCombatGrabTarget(
            ChangedEntity pet,
            ServerPlayer owner) {
        boolean bonded = LatexSocialMemory.isBonded(pet, owner)
                || LatexSocialMemory.isPetOwner(pet, owner);
        boolean trustedFriend = CreaturePersonality.hasTrustedRelationship(
                        pet, owner)
                && !LatexSocialMemory.hasRelationshipBetrayal(pet, owner)
                && !LatexSocialMemory.isProvoked(pet, owner);
        if (!bonded && !trustedFriend) {
            return null;
        }
        GrabEntityAbilityInstance ability = ability(pet);
        if (bonded) {
            return bondedCombatGrabTarget(pet, owner, ability);
        }
        LivingEntity target = validCombatGrabTarget(
                pet, owner, ability);
        if (target == null) {
            return null;
        }
        return LatexSocialMemory.isPetDefenseAuthorized(pet, target)
                        || pet.getTarget() == target
                        || target.getLastHurtByMob() == pet
                ? target : null;
    }

    private static LivingEntity bondedCombatGrabTarget(
            ChangedEntity pet,
            ServerPlayer owner,
            GrabEntityAbilityInstance ability) {
        LivingEntity target = validCombatGrabTarget(
                pet, owner, ability);
        if (target == null) {
            return null;
        }
        return BondedOwnerDefenseGoal.allowsConfiguredDefense(
                pet, owner, target) ? target : null;
    }

    private static LivingEntity validCombatGrabTarget(
            ChangedEntity pet,
            ServerPlayer owner,
            GrabEntityAbilityInstance ability) {
        if (ability == null || ability.suited
                || ability.grabbedEntity == null) {
            return null;
        }

        LivingEntity target = ability.grabbedEntity;
        if (target == pet || target == owner || !target.isAlive()
                || target.isRemoved() || target.isAlliedTo(owner)
                || pet.isAlliedTo(target) || !pet.canAttack(target)) {
            return null;
        }
        if (target instanceof Player player
                && (player.isCreative() || player.isSpectator()
                        || !owner.canHarmPlayer(player))) {
            return null;
        }
        if (target instanceof ServerPlayer player
                && LatexSocialMemory.shouldRemainNeutral(pet, player)) {
            return null;
        }
        if (target instanceof ChangedEntity other
                && (LatexSocialMemory.hasSamePetOwner(pet, other)
                        || LatexSocialMemory.isBonded(other, owner)
                        || LatexSocialMemory.isPetOwner(other, owner)
                        || CreaturePersonality.hasTrustedRelationship(
                                other, owner)
                        || LatexCreatureCombatRules.mustRejectTarget(
                                pet, other))) {
            return null;
        }
        return target;
    }

    private static void announceBondedCombatGrab(
            ChangedEntity pet,
            GrabEntityAbilityInstance ability) {
        var data = pet.getPersistentData();
        ServerPlayer owner = LatexSocialMemory.getPetOwner(pet);
        LivingEntity target = owner == null
                ? null
                : bondedCombatGrabTarget(pet, owner, ability);
        if (target == null) {
            data.remove(ANNOUNCED_COMBAT_GRAB_TARGET);
            return;
        }
        if (data.hasUUID(ANNOUNCED_COMBAT_GRAB_TARGET)
                && data.getUUID(ANNOUNCED_COMBAT_GRAB_TARGET)
                        .equals(target.getUUID())) {
            return;
        }

        double range = ChangedSynergyConfig.COMMON.npcDialogueRange.get();
        if (owner.level() != pet.level()
                || owner.distanceToSqr(pet) > range * range) {
            return;
        }
        data.putUUID(ANNOUNCED_COMBAT_GRAB_TARGET, target.getUUID());
        NpcDialogue.trigger(pet, owner, Cue.BOND_COMBAT_GRAB_ASSIST);
    }

    private static void tickFriendlySocialHug(
            ChangedEntity mob,
            GrabEntityAbilityInstance ability) {
        ServerPlayer player = ability != null
                && ability.grabbedEntity instanceof ServerPlayer held
                && LatexSocialMemory.isFriendlySocialHugTarget(mob, held)
                        ? held : LatexSocialMemory.friendlySocialHugPlayer(mob);
        if (ability == null
                || player == null
                || ability.grabbedEntity != player
                || !LatexSocialMemory.isFriendlySocialHugActive(mob, player)
                || !mob.isAlive()
                || !player.isAlive()
                || mob.level() != player.level()) {
            if (player != null) {
                if (ability != null) ability.attackDown = ability.useDown = false;
                if (ability != null && ability.grabbedEntity == player) {
                    ability.releaseEntity(false);
                }
                Changed.PACKET_HANDLER.send(
                        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> mob),
                        new GrabEntityPacket(mob, player, GrabType.RELEASE));
                ChangedSynergyNetwork.CHANNEL.send(
                        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> mob),
                        new FriendlySocialHugSyncPacket(
                                mob.getId(), player.getId(), false, 0));
                if (mob.isAlive()
                        && player.isAlive()
                        && mob.level() == player.level()) {
                    NpcDialogue.trigger(
                            mob,
                            player,
                            LatexSocialMemory.isPetOwner(mob, player)
                                    ? NpcDialogue.Cue.SOCIAL_PLAY_HUG_RELEASE_BONDED
                                    : NpcDialogue.Cue.SOCIAL_PLAY_HUG_RELEASE_FRIEND);
                }
            }
            if (ability != null && (ability.grabbedEntity == null || player != null))
                ChangedAddonCompat.configureFriendlyGrab(ability, false);
            LatexSocialMemory.endFriendlySocialHug(mob);
            cooldown(mob, ability, 30);
            return;
        }

        ChangedAddonCompat.configureFriendlyGrab(ability, true);
        ability.suited = false;
        ability.grabbedHasControl = false;
        ability.attackDown = false;
        ability.useDown = false;
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
        LatexSocialEvents.calmTowards(mob, player);
        ((GrabAbilityTick)ability).changedSynergy$tickHoldIfNeeded();
    }

    private static void tickNegotiatedReleaseHold(
            ChangedEntity mob,
            GrabEntityAbilityInstance ability) {
        ServerPlayer player = InvoluntaryTransfurNegotiation
                .releaseHoldPlayer(mob);
        if (player == null
                || !mob.isAlive()
                || !player.isAlive()
                || mob.level() != player.level()) {
            releaseNegotiatedHold(mob, ability, player);
            InvoluntaryTransfurNegotiation.abortReleaseHold(mob, player);
            return;
        }

        // The Addon grab presentation can drop its target when Changed changes
        // the player's form, or when its own short hold bookkeeping expires.
        // Negotiation success is already committed at this point: losing the
        // animation must complete the promised reversal instead of restoring
        // the old negotiation progress and leaving the player transformed.
        if (ability == null
                || ability.grabbedEntity != player) {
            releaseNegotiatedHold(mob, ability, player);
            InvoluntaryTransfurNegotiation.finishReleaseHold(mob, player);
            return;
        }

        configureNegotiatedReleaseHold(mob, ability, player);
        ((GrabAbilityTick)ability).changedSynergy$tickHoldIfNeeded();
        InvoluntaryTransfurNegotiation.ReleaseHoldStep step =
                InvoluntaryTransfurNegotiation.advanceReleaseHold(mob, player);
        if (step == InvoluntaryTransfurNegotiation.ReleaseHoldStep.COMPLETE) {
            releaseNegotiatedHold(mob, ability, player);
            InvoluntaryTransfurNegotiation.finishReleaseHold(mob, player);
        } else if (step == InvoluntaryTransfurNegotiation.ReleaseHoldStep.ABORT) {
            releaseNegotiatedHold(mob, ability, player);
            InvoluntaryTransfurNegotiation.abortReleaseHold(mob, player);
        }
    }

    private static void configureNegotiatedReleaseHold(
            ChangedEntity mob,
            GrabEntityAbilityInstance ability,
            ServerPlayer player) {
        ChangedAddonCompat.configureFriendlyGrab(ability, true);
        ability.suited = false;
        ability.grabbedHasControl = false;
        ability.attackDown = false;
        ability.useDown = false;
        cooldown(mob, ability, 0);
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
        LatexSocialEvents.calmTowards(mob, player);
    }

    private static void releaseNegotiatedHold(
            ChangedEntity mob,
            GrabEntityAbilityInstance ability,
            ServerPlayer player) {
        LivingEntity held = ability == null ? null : ability.grabbedEntity;
        if (ability != null) {
            ability.attackDown = false;
            ability.useDown = false;
            if (held != null) {
                ability.releaseEntity(false);
                Changed.PACKET_HANDLER.send(
                        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> mob),
                        new GrabEntityPacket(mob, held, GrabType.RELEASE));
            }
            ChangedAddonCompat.configureFriendlyGrab(ability, false);
        }
        if (player != null) {
            ChangedSynergyNetwork.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> mob),
                    new FriendlySocialHugSyncPacket(
                            mob.getId(), player.getId(), false, 0));
        }
        cooldown(mob, ability, 30);
    }

    private static boolean tickOrganicEvacuationHold(
            ChangedEntity mob,
            GrabEntityAbilityInstance ability) {
        LivingEntity passenger = ability == null ? null : ability.grabbedEntity;
        ServerPlayer player = passenger instanceof ServerPlayer held ? held
                : passenger instanceof ChangedEntity body ? net.parkabird.changedsynergy.compat.TrueTransfurCompat.bodyOwner(body) : null;
        if (ability == null
                || player == null
                || !LatexSocialMemory.isOrganicEvacuationActive(mob, passenger)
                || !mob.isAlive()
                || !player.isAlive() || !passenger.isAlive() || passenger.isRemoved()
                || mob.level() != passenger.level()) {
            if (player != null && ability != null) {
                ability.attackDown = false;
                ability.useDown = false;
                ability.releaseEntity(false);
                Changed.PACKET_HANDLER.send(
                        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> mob),
                        new GrabEntityPacket(mob, passenger, GrabType.RELEASE));
                ChangedSynergyNetwork.CHANNEL.send(
                        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> mob),
                        new FriendlySocialHugSyncPacket(
                                mob.getId(), passenger.getId(), false, 0));
            }
            ChangedAddonCompat.configureFriendlyGrab(ability, false);
            LatexSocialMemory.endOrganicEvacuation(mob);
            return false;
        }

        ChangedAddonCompat.configureFriendlyGrab(ability, true);
        ability.suited = false;
        ability.grabbedHasControl = false;
        ability.attackDown = false;
        ability.useDown = false;
        mob.setTarget(null);
        mob.setAggressive(false);
        LatexSocialEvents.calmTowards(mob, player);
        ((GrabAbilityTick)ability).changedSynergy$tickHoldIfNeeded();
        return true;
    }

    private static void clearStaleNativeSuitSync(
            ChangedEntity mob,
            GrabEntityAbilityInstance ability) {
        var data = mob.getPersistentData();
        if (!data.hasUUID(SYNCED_NATIVE_SUIT_OWNER)) {
            return;
        }
        java.util.UUID ownerUuid = data.getUUID(SYNCED_NATIVE_SUIT_OWNER);
        boolean wasBonded = data.getBoolean(SYNCED_NATIVE_SUIT_WAS_BONDED);
        boolean stillActive = ability != null
                && ability.grabbedEntity instanceof ServerPlayer current
                && current.getUUID().equals(ownerUuid)
                && BondedSuitService.isNativeOwnerSuit(mob, current);
        if (stillActive) {
            return;
        }

        if (mob.level() instanceof net.minecraft.server.level.ServerLevel level) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
            if (owner != null) {
                BondedSuitService.syncOwnerSuitState(mob, owner, false);
                if (!wasBonded) {
                    NpcDialogue.trigger(mob, owner, Cue.BOND_RELEASE_REVERTED);
                }
            }
        }
        data.remove(SYNCED_NATIVE_SUIT_OWNER);
        data.remove(SYNCED_NATIVE_SUIT_WAS_BONDED);
    }
}
