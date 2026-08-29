package net.parkabird.changedsynergy.ai;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TamableLatexEntity;
import net.ltxprogrammer.changed.entity.beast.AbstractDarkLatexEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.init.ChangedTags;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.advancement.SynergyAdvancements;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.player.Player;

/** Persistent per-creature bonds, provocations and short pat truces. */
public final class LatexSocialMemory {
    private static final String ROOT = "ChangedSynergySocial";
    private static final String BONDS = "BondedPlayers";
    private static final String PROVOKED = "ProvokedPlayers";
    private static final String FRIENDLY_HITS = "FriendlyHitWarnings";
    private static final String PET_OWNER = "PetOwner";
    private static final String FOLLOW_OWNER = "FollowOwner";
    private static final String PET_DEFENSE_PLAYER = "PetDefensePlayer";
    private static final String PET_DEFENSE_UNTIL = "PetDefenseUntil";
    private static final String PET_DEFENSE_FORCED = "PetDefenseForced";
    private static final String GRAB_BLOCKED_PLAYER = "GrabBlockedPlayer";
    private static final String GRAB_BLOCKED_UNTIL = "GrabBlockedUntil";
    private static final String TRUCE_PLAYER = "PatTrucePlayer";
    private static final String TRUCE_UNTIL = "PatTruceUntil";
    private static final String TRUCE_FROM_PAT = "TruceFromPat";
    private static final String PAT_TRUCE_BETRAYALS = "PatTruceBetrayals";
    private static final String RELATIONSHIP_BETRAYALS = "RelationshipBetrayals";
    private static final String WARNING_PLAYER = "FriendlyWarningPlayer";
    private static final String WARNING_UNTIL = "FriendlyWarningUntil";
    private static final String SECONDARY_GRAB_PLAYER = "SecondaryGrabPlayer";
    private static final String SECONDARY_TRANSFUR_SETTLED =
            "SecondaryTransfurSettledPlayers";
    private static final String FRIENDLY_HUG_PLAYER = "FriendlyHugPlayer";
    private static final String FRIENDLY_HUG_UNTIL = "FriendlyHugUntil";
    private static final String ORGANIC_EVACUATION_PLAYER = "OrganicEvacuationPlayer";
    private static final String ORGANIC_EVACUATION_UNTIL = "OrganicEvacuationUntil";
    private static final String ORGANIC_RESCUE_MODE = "OrganicRescueMode";
    private static final String FRIENDLY_SUIT_PLAYER = "FriendlySuitPlayer";
    private static final String FRIENDLY_SUIT_EMERGENCY = "FriendlySuitEmergency";
    private static final String FRIENDLY_SUIT_COMBAT = "FriendlySuitCombat";
    private static final String FRIENDLY_SUIT_STARTED = "FriendlySuitStarted";
    private static final String EMERGENCY_RESCUE_COOLDOWN = "EmergencyRescueCooldown";
    private static final String JEALOUS_FORMS = "JealousOwnerForms";
    private static final String PLAYER_BONDS = "ChangedSynergyBondedCreatures";
    private static final String PLAYER_BONDS_SCANNED = "ChangedSynergyBondRegistryScanned";
    private static final long FRIENDLY_HIT_WINDOW = 600L;
    private static final long PROVOCATION_DURATION = 1200L;
    private static final double ESCORT_RADIUS = 16.0;
    private static final double FOLLOW_COMBAT_LEASH_SQR = 24.0D * 24.0D;
    private static final Map<ChangedEntity, List<SuppressedMovementGoal>>
            SUPPRESSED_MOVEMENT_GOALS = new WeakHashMap<>();
    private static final Map<ChangedEntity, GoalMutation>
            PENDING_GOAL_MUTATIONS = new WeakHashMap<>();
    private static final TagKey<EntityType<?>> ORGANIC_LATEX = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("changed", "organic_latex"));
    private static final ClassValue<Boolean> NATIVE_PET_MENU = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> current = type; current != null && current != ChangedEntity.class;
                    current = current.getSuperclass()) {
                for (Method method : current.getDeclaredMethods()) {
                    if (method.getName().equals("tamedInteract")
                            && method.getParameterCount() == 2) {
                        return true;
                    }
                }
            }
            return false;
        }
    };

    private LatexSocialMemory() {
    }

    public static boolean isSocialLatex(ChangedEntity mob) {
        return mob.getUnderlyingPlayer() == null
                && CreatureSocialProfile.allowsSynergySystems(mob)
                && (mob.getType().is(ChangedTags.EntityTypes.LATEX)
                        || mob.getType().is(ORGANIC_LATEX));
    }

    /** Organic Changed creatures use physical assimilation rather than latex wrapping. */
    public static boolean isOrganic(ChangedEntity mob) {
        return mob.getType().is(ORGANIC_LATEX);
    }

    /** Unprovoked humans are neutral to organic creatures and are not valid assimilation targets. */
    public static boolean isNeutralOrganicHumanContact(
            ChangedEntity mob,
            ServerPlayer player) {
        return isOrganic(mob)
                && !ProcessTransfur.isPlayerTransfurred(player)
                && !isProvoked(mob, player)
                && !FactionReputation.isHostile(mob, player)
                && !isPetDefenseAuthorized(mob, player);
    }

    public static void addBond(ChangedEntity mob, ServerPlayer player) {
        if (!CreatureSocialProfile.allowsPersonalRelationship(mob)) {
            return;
        }
        if (!bondEnabled(mob)) {
            // Keep the two relationship layers independent: with bonds disabled,
            // an assimilation may still become an ordinary friendship.
            CreaturePersonality.establishRelationship(mob, player);
            return;
        }
        CompoundTag social = data(mob);
        boolean newBond = !isBonded(mob, player);
        boolean newRelationship =
                CreaturePersonality.establishRelationship(mob, player);
        child(mob, BONDS).putBoolean(player.getUUID().toString(), true);
        if (!social.hasUUID(PET_OWNER)) {
            UUID nativeOwner = mob instanceof TamableLatexEntity nativePet
                    ? nativePet.getOwnerUUID() : null;
            social.putUUID(PET_OWNER, nativeOwner != null ? nativeOwner : player.getUUID());
            social.putBoolean(FOLLOW_OWNER, true);
        }
        child(mob, PROVOKED).remove(player.getUUID().toString());
        child(mob, FRIENDLY_HITS).remove(player.getUUID().toString());
        child(mob, RELATIONSHIP_BETRAYALS).remove(player.getUUID().toString());
        clearTruce(mob, player);
        clearHostilityToward(mob, player);
        suppressBondedAvoidanceGoals(mob);
        if (isPetOwner(mob, player)) {
            promoteNativePet(mob, player);
        }
        playerBondData(player).putBoolean(mob.getUUID().toString(), true);
        PlayerRelationshipSettings.rememberContact(player, mob, true);
        BondedCreatureDeathData.get(player.server)
                .clear(player.getUUID(), mob.getUUID());
        BondedCreatureReleaseData.get(player.server)
                .clear(player.getUUID(), mob.getUUID());
        CreatureIdentity.ensure(mob);
        BondedCreatureLifecycle.track(mob);
        if (newBond) {
            initializeBondedCombatCondition(mob);
            // Establishing a relationship already contributes 12 reputation.
            // Keep a new bond's total reward at the original value of 15.
            FactionReputation.adjustFromInteraction(
                    mob, player, newRelationship ? 3 : 15);
            SynergyAdvancements.grant(
                    player, SynergyAdvancements.FIRST_CONTACT);
            SynergyAdvancements.grant(
                    player, SynergyAdvancements.BONDED_COMPANION);
        }
    }

    private static void initializeBondedCombatCondition(ChangedEntity mob) {
        if (mob instanceof AbstractDarkLatexEntity darkLatex) {
            for (int index = 0;
                    index < 3 && darkLatex.getAttackCondition().ordinal() != 2;
                    index++) {
                darkLatex.setAttackCondition(
                        darkLatex.getAttackCondition().cycle());
            }
            darkLatex.setTarget(null);
            return;
        }
        if (BondedPetSettings.usesFallbackBackend(mob)) {
            BondedPetSettings.initialize(mob);
        } else {
            ChangedAddonCompat.initializeBondedCombatCondition(mob);
        }
    }

    public static boolean isBonded(ChangedEntity mob, ServerPlayer player) {
        return bondEnabled(mob)
                && child(mob, BONDS).getBoolean(player.getUUID().toString());
    }

    /** Active behavior check; the raw UUID set remains available for lifecycle cleanup. */
    public static boolean hasActiveBond(ChangedEntity mob) {
        return bondEnabled(mob) && !bondedPlayerUuids(mob).isEmpty();
    }

    public static boolean isPetOwner(ChangedEntity mob, ServerPlayer player) {
        if (!bondEnabled(mob)) {
            return false;
        }
        CompoundTag social = data(mob);
        if (social.hasUUID(PET_OWNER)) {
            return player.getUUID().equals(social.getUUID(PET_OWNER));
        }
        // A juvenile may form Changed's native pet relationship without being
        // eligible for Synergy's adult personal-relationship layer.
        return mob instanceof TamableLatexEntity nativePet
                && nativePet.isTame()
                && player.getUUID().equals(nativePet.getOwnerUUID());
    }

    public static Optional<UUID> petOwnerUuid(ChangedEntity mob) {
        if (!bondEnabled(mob)) {
            return Optional.empty();
        }
        CompoundTag social = data(mob);
        if (social.hasUUID(PET_OWNER)) {
            return Optional.of(social.getUUID(PET_OWNER));
        }
        if (mob instanceof TamableLatexEntity nativePet
                && nativePet.isTame()
                && nativePet.getOwnerUUID() != null) {
            return Optional.of(nativePet.getOwnerUUID());
        }
        return Optional.empty();
    }

    public static ServerPlayer getPetOwner(ChangedEntity mob) {
        if (!(mob.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return null;
        }
        return petOwnerUuid(mob)
                .map(uuid -> level.getServer().getPlayerList().getPlayer(uuid))
                .filter(player -> player.level() == mob.level())
                .orElse(null);
    }

    public static boolean isFollowingOwner(ChangedEntity mob) {
        CompoundTag social = data(mob);
        if (social.contains(FOLLOW_OWNER)) {
            boolean following = social.getBoolean(FOLLOW_OWNER);
            // The Synergy wheel is authoritative. Changed and Addon may rewrite
            // their own pet bit while loading inventories or reassessing goals.
            if (!mob.level().isClientSide
                    && mob instanceof TamableLatexEntity nativePet
                    && nativePet.isFollowingOwner() != following) {
                nativePet.setFollowOwner(following);
            }
            return following;
        }
        return mob instanceof TamableLatexEntity nativePet && nativePet.isTame()
                ? nativePet.isFollowingOwner()
                : true;
    }

    /**
     * Follow mode keeps ordinary wheel-authorized combat near the host. Forced
     * grab rescues remain exempt so a companion can always free its host.
     */
    public static boolean rejectsFollowingCombatTarget(
            ChangedEntity pet,
            LivingEntity target) {
        ServerPlayer owner = getPetOwner(pet);
        if (owner == null || !isFollowingOwner(pet) || target == null) {
            return false;
        }
        if (target == owner) {
            return true;
        }
        if (isPetDefenseForced(pet, target)) {
            return false;
        }
        return !target.isAlive()
                || target.isRemoved()
                || target.level() != owner.level()
                || target.distanceToSqr(owner) > FOLLOW_COMBAT_LEASH_SQR;
    }

    /** Drops only the stale/remote target, leaving unrelated combat untouched. */
    public static void clearRejectedFollowingTarget(
            ChangedEntity pet,
            LivingEntity target) {
        if (pet.getTarget() == target) {
            pet.setTarget(null);
        }
        if (pet.getLastHurtByMob() == target) {
            pet.setLastHurtByMob(null);
        }
        if (target instanceof ServerPlayer player
                && HuntMemory.targets(pet, player)) {
            HuntMemory.clear(pet);
        }
        clearPetDefense(pet, target);
        pet.setAggressive(false);
        pet.setSprinting(false);
        pet.getNavigation().stop();
    }

    public static void setFollowingOwner(ChangedEntity mob, boolean following) {
        data(mob).putBoolean(FOLLOW_OWNER, following);
        if (mob instanceof TamableLatexEntity nativePet) {
            nativePet.setFollowOwner(following);
        }
        mob.getNavigation().stop();
        if (!following) {
            mob.setTarget(null);
        }
    }

    public static boolean hasSamePetOwner(ChangedEntity first, ChangedEntity second) {
        Optional<UUID> owner = petOwnerUuid(first);
        return owner.isPresent() && owner.equals(petOwnerUuid(second));
    }

    public static void authorizePetDefense(
            ChangedEntity pet,
            LivingEntity target,
            long durationTicks) {
        authorizePetDefense(pet, target, durationTicks, false);
    }

    public static void authorizeForcedPetDefense(
            ChangedEntity pet,
            LivingEntity target,
            long durationTicks) {
        authorizePetDefense(pet, target, durationTicks, true);
    }

    private static void authorizePetDefense(
            ChangedEntity pet,
            LivingEntity target,
            long durationTicks,
            boolean forced) {
        CompoundTag social = data(pet);
        social.putUUID(PET_DEFENSE_PLAYER, target.getUUID());
        social.putLong(PET_DEFENSE_UNTIL,
                pet.level().getGameTime() + Math.max(1L, durationTicks));
        social.putBoolean(PET_DEFENSE_FORCED, forced);
    }

    public static boolean isPetDefenseAuthorized(ChangedEntity pet, LivingEntity target) {
        CompoundTag social = data(pet);
        if (!social.hasUUID(PET_DEFENSE_PLAYER)
                || !target.getUUID().equals(social.getUUID(PET_DEFENSE_PLAYER))) {
            return false;
        }
        if (social.getLong(PET_DEFENSE_UNTIL) > pet.level().getGameTime()) {
            return true;
        }
        social.remove(PET_DEFENSE_PLAYER);
        social.remove(PET_DEFENSE_UNTIL);
        social.remove(PET_DEFENSE_FORCED);
        return false;
    }

    public static boolean isPetDefenseForced(ChangedEntity pet, LivingEntity target) {
        return isPetDefenseAuthorized(pet, target)
                && data(pet).getBoolean(PET_DEFENSE_FORCED);
    }

    /** Returns the still-loaded threat currently authorized for owner defense. */
    public static LivingEntity getPetDefenseTarget(ChangedEntity pet) {
        CompoundTag social = data(pet);
        if (!social.hasUUID(PET_DEFENSE_PLAYER)) {
            return null;
        }
        if (social.getLong(PET_DEFENSE_UNTIL) <= pet.level().getGameTime()) {
            social.remove(PET_DEFENSE_PLAYER);
            social.remove(PET_DEFENSE_UNTIL);
            social.remove(PET_DEFENSE_FORCED);
            return null;
        }
        if (!(pet.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return null;
        }
        Entity entity = level.getEntity(social.getUUID(PET_DEFENSE_PLAYER));
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    public static void clearPetDefense(ChangedEntity pet, LivingEntity target) {
        CompoundTag social = data(pet);
        if (target == null || social.hasUUID(PET_DEFENSE_PLAYER)
                && target.getUUID().equals(social.getUUID(PET_DEFENSE_PLAYER))) {
            social.remove(PET_DEFENSE_PLAYER);
            social.remove(PET_DEFENSE_UNTIL);
            social.remove(PET_DEFENSE_FORCED);
        }
    }

    /** Promote Changed/Addon entities with a native taming API to a real native pet. */
    public static void promoteNativePet(ChangedEntity mob, ServerPlayer player) {
        if (!(mob instanceof TamableLatexEntity nativePet)) {
            return;
        }

        boolean followOwner = isFollowingOwner(mob);
        LivingEntity currentOwner = nativePet.getOwner();
        UUID currentOwnerUuid = nativePet.getOwnerUUID();
        if (currentOwnerUuid != null && !currentOwnerUuid.equals(player.getUUID())) {
            return;
        }
        clearHostilityToward(mob, player);
        suppressBondedAvoidanceGoals(mob);
        if (nativePet.isTame() && currentOwner == player) {
            nativePet.setFollowOwner(followOwner);
            return;
        }

        try {
            boolean tamed = invokePlayerMethod(mob, "tameEntityForPlayer", player)
                    || invokePlayerMethod(mob, "tame", player);
            if (!tamed) {
                invokeMethod(mob, "setOwnerUUID", new Class<?>[]{UUID.class}, player.getUUID());
                invokeMethod(mob, "setTame", new Class<?>[]{boolean.class}, true);
            }
            nativePet.setFollowOwner(followOwner);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ChangedSynergyMod.LOGGER.warn(
                    "Could not promote bonded {} to its native pet implementation",
                    mob.getType(), exception);
        }
    }

    /**
     * A reverted player is vouched for only while one of this creature's
     * same-category bonded escorts is physically nearby.
     */
    public static boolean hasBondedEscortFor(ChangedEntity mob, ServerPlayer player) {
        return player.level().getEntitiesOfClass(
                        ChangedEntity.class,
                        player.getBoundingBox().inflate(ESCORT_RADIUS),
                        candidate -> candidate.isAlive()
                                && candidate != mob
                                && isSocialLatex(candidate)
                                && isBonded(candidate, player))
                .stream()
                .anyMatch(escort -> LatexSocialRelation.sameCategory(mob, escort));
    }

    /**
     * A trusted friend can introduce a human to its own category while it is
     * physically present. Bonds and pets use the stronger escort rule above.
     */
    public static boolean hasTrustedEscortFor(
            ChangedEntity mob,
            ServerPlayer player) {
        return player.level().getEntitiesOfClass(
                        ChangedEntity.class,
                        player.getBoundingBox().inflate(ESCORT_RADIUS),
                        candidate -> candidate.isAlive()
                                && candidate != mob
                                && isSocialLatex(candidate)
                                && !isBonded(candidate, player)
                                && !isPetOwner(candidate, player)
                                && !(candidate instanceof TamableLatexEntity nativePet
                                        && nativePet.isTame()
                                        && player.getUUID().equals(
                                                nativePet.getOwnerUUID()))
                                && !isProvoked(candidate, player)
                                && CreaturePersonality.hasTrustedRelationship(
                                        candidate, player))
                .stream()
                .anyMatch(escort ->
                        LatexSocialRelation.sameCategory(mob, escort));
    }

    public static boolean hasNearbyBondedCreature(ServerPlayer player) {
        return player.level().getEntitiesOfClass(
                        ChangedEntity.class,
                        player.getBoundingBox().inflate(ESCORT_RADIUS),
                        candidate -> candidate.isAlive()
                                && isSocialLatex(candidate)
                                && isBonded(candidate, player))
                .stream()
                .findAny()
                .isPresent();
    }

    /**
     * Changed Addon mixes the taming interface into every Changed creature.  Only
     * classes that actually declare the native pet interaction method have the
     * corresponding native goals/menu; the rest use Synergy's complete bridge.
     */
    public static boolean usesNativePetMenu(ChangedEntity mob) {
        return NATIVE_PET_MENU.get(mob.getClass());
    }

    /** Returns every currently loaded creature that still carries this player's bond. */
    public static List<ChangedEntity> loadedBondedCreatures(ServerPlayer player) {
        List<ChangedEntity> bonded = new ArrayList<>();
        for (net.minecraft.server.level.ServerLevel level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ChangedEntity candidate
                        && candidate.isAlive()
                        && isSocialLatex(candidate)
                        && isBonded(candidate, player)) {
                    bonded.add(candidate);
                    playerBondData(player).putBoolean(candidate.getUUID().toString(), true);
                }
            }
        }
        playerPersistedData(player).putBoolean(PLAYER_BONDS_SCANNED, true);
        return bonded;
    }

    /** Returns the persistent UUID registry used by the player bond command. */
    public static Set<UUID> bondedCreatureUuids(ServerPlayer player) {
        applyPendingBondDeaths(player);
        if (!playerPersistedData(player).getBoolean(PLAYER_BONDS_SCANNED)) {
            loadedBondedCreatures(player);
        }

        CompoundTag bonds = playerBondData(player);
        Set<UUID> creatures = new LinkedHashSet<>();
        for (String key : Set.copyOf(bonds.getAllKeys())) {
            try {
                creatures.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {
                bonds.remove(key);
            }
        }
        return creatures;
    }

    /** Finds a registered creature in any currently loaded server dimension. */
    public static ChangedEntity findLoadedBondedCreature(
            ServerPlayer player,
            UUID creatureUuid) {
        for (net.minecraft.server.level.ServerLevel level : player.server.getAllLevels()) {
            Entity entity = level.getEntity(creatureUuid);
            if (entity instanceof ChangedEntity candidate
                    && candidate.isAlive()
                    && isSocialLatex(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Immediately removes the player-side reference and records the creature
     * cleanup for the next time its chunk is loaded.
     */
    public static void queueManualRelease(ServerPlayer player, UUID creatureUuid) {
        playerBondData(player).remove(creatureUuid.toString());
        BondedCreatureLifecycle.forget(player, creatureUuid);
        BondedCreatureDeathData.get(player.server)
                .clear(player.getUUID(), creatureUuid);
        BondedCreatureReleaseData.get(player.server)
                .markReleased(player.getUUID(), creatureUuid);
    }

    /**
     * The player-side UUID list also remembers bonded pets in unloaded chunks, so
     * a second creature cannot create a competing friendly faction just because
     * the original companion is momentarily outside simulation distance.
     */
    public static boolean hasOtherBondedCreature(ServerPlayer player, ChangedEntity excluded) {
        applyPendingBondDeaths(player);
        String excludedKey = excluded == null ? "" : excluded.getUUID().toString();
        if (playerBondData(player).getAllKeys().stream()
                .anyMatch(key -> !key.equals(excludedKey))) {
            return true;
        }
        if (!playerPersistedData(player).getBoolean(PLAYER_BONDS_SCANNED)) {
            loadedBondedCreatures(player);
        }
        return playerBondData(player).getAllKeys().stream()
                .anyMatch(key -> !key.equals(excludedKey));
    }

    public static void unregisterBond(ChangedEntity mob, ServerPlayer player) {
        removeCreatureBondState(mob, player.getUUID());
        playerBondData(player).remove(mob.getUUID().toString());
        BondedCreatureLifecycle.forget(player, mob.getUUID());
        BondedCreatureReleaseData.get(player.server)
                .clear(player.getUUID(), mob.getUUID());
        if (bondedPlayerUuids(mob).isEmpty()) {
            BondedCreatureLifecycle.untrack(mob);
        }
    }

    /**
     * Applies manual releases made while this creature was unloaded.  This must
     * run before native pet promotion in the entity-join event.
     */
    public static boolean applyPendingManualReleases(ChangedEntity mob) {
        if (!(mob.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return false;
        }
        Set<UUID> owners = BondedCreatureReleaseData.get(level.getServer())
                .consume(mob.getUUID());
        for (UUID ownerUuid : owners) {
            removeCreatureBondState(mob, ownerUuid);
            ServerPlayer online = level.getServer().getPlayerList().getPlayer(ownerUuid);
            if (online != null) {
                playerBondData(online).remove(mob.getUUID().toString());
                BondedCreatureLifecycle.forget(online, mob.getUUID());
            }
        }
        if (bondedPlayerUuids(mob).isEmpty()) {
            BondedCreatureLifecycle.untrack(mob);
        }
        return !owners.isEmpty();
    }

    /** Returns every player UUID whose bond is stored on this creature. */
    public static Set<UUID> bondedPlayerUuids(ChangedEntity mob) {
        Set<UUID> players = new LinkedHashSet<>();
        for (String key : child(mob, BONDS).getAllKeys()) {
            try {
                players.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {
                // Malformed old keys are discarded when the creature dies.
            }
        }
        return players;
    }

    /**
     * Permanently ends every bond held by a dying creature.  Online owners are
     * cleaned immediately; offline owners receive a persistent tombstone that
     * is consumed on their next login.
     */
    public static void breakBondsOnDeath(ChangedEntity mob) {
        if (!(mob.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        Set<UUID> bondedPlayers = bondedPlayerUuids(mob);
        if (bondedPlayers.isEmpty()) {
            return;
        }

        BondedCreatureLifecycle.untrack(mob);
        BondedCreatureDeathData deaths = BondedCreatureDeathData.get(level.getServer());
        for (UUID playerUuid : bondedPlayers) {
            deaths.markDead(playerUuid, mob.getUUID());
            ServerPlayer online = level.getServer().getPlayerList().getPlayer(playerUuid);
            if (online != null) {
                playerBondData(online).remove(mob.getUUID().toString());
                BondedCreatureLifecycle.forget(online, mob.getUUID());
                deaths.clear(playerUuid, mob.getUUID());
            }
        }

        CompoundTag social = data(mob);
        social.remove(BONDS);
        social.remove(PET_OWNER);
        social.remove(FOLLOW_OWNER);
        social.remove(PET_DEFENSE_PLAYER);
        social.remove(PET_DEFENSE_UNTIL);
        social.remove(PET_DEFENSE_FORCED);
        social.remove(FRIENDLY_SUIT_PLAYER);
        social.remove(FRIENDLY_SUIT_EMERGENCY);
        social.remove(FRIENDLY_SUIT_COMBAT);
        social.remove(FRIENDLY_SUIT_STARTED);
        social.remove(ORGANIC_EVACUATION_PLAYER);
        social.remove(ORGANIC_EVACUATION_UNTIL);
    }

    /** Applies death records that accumulated while this player was offline. */
    public static void applyPendingBondDeaths(ServerPlayer player) {
        for (UUID creature : BondedCreatureDeathData.get(player.server)
                .consume(player.getUUID())) {
            playerBondData(player).remove(creature.toString());
            BondedCreatureLifecycle.forget(player, creature);
        }
    }

    /** Removes a player-side bond after its forced chunk has repeatedly loaded without the entity. */
    public static void forgetMissingBond(ServerPlayer player, UUID creatureUuid) {
        playerBondData(player).remove(creatureUuid.toString());
        BondedCreatureDeathData.get(player.server).clear(player.getUUID(), creatureUuid);
        BondedCreatureReleaseData.get(player.server).clear(player.getUUID(), creatureUuid);
    }

    /** Explicitly preserves the bond registry when Minecraft replaces a player on death. */
    public static void copyPlayerBondData(ServerPlayer original, ServerPlayer clone) {
        CompoundTag source = playerPersistedData(original);
        CompoundTag target = playerPersistedData(clone);
        if (source.contains(PLAYER_BONDS, Tag.TAG_COMPOUND)) {
            target.put(PLAYER_BONDS, source.getCompound(PLAYER_BONDS).copy());
        }
        if (source.contains(PLAYER_BONDS_SCANNED, Tag.TAG_BYTE)) {
            target.putBoolean(PLAYER_BONDS_SCANNED, source.getBoolean(PLAYER_BONDS_SCANNED));
        }
    }

    /** Re-keys only an existing player-side bond; ordinary friend cards are handled separately. */
    public static void replacePlayerBondReference(
            ServerPlayer player,
            UUID previousId,
            UUID replacementId) {
        if (previousId.equals(replacementId)) {
            return;
        }
        CompoundTag bonds = playerBondData(player);
        String previousKey = previousId.toString();
        if (bonds.getBoolean(previousKey)) {
            bonds.remove(previousKey);
            bonds.putBoolean(replacementId.toString(), true);
        }
        BondedCreatureDeathData.get(player.server)
                .clear(player.getUUID(), previousId);
        BondedCreatureReleaseData.get(player.server)
                .clear(player.getUUID(), previousId);
    }

    private static void removeCreatureBondState(ChangedEntity mob, UUID ownerUuid) {
        CompoundTag social = data(mob);
        child(mob, BONDS).remove(ownerUuid.toString());
        child(mob, PROVOKED).remove(ownerUuid.toString());
        child(mob, FRIENDLY_HITS).remove(ownerUuid.toString());
        child(mob, JEALOUS_FORMS).remove(ownerUuid.toString());

        boolean wasPetOwner = social.hasUUID(PET_OWNER)
                && ownerUuid.equals(social.getUUID(PET_OWNER));
        if (wasPetOwner) {
            social.remove(PET_OWNER);
            social.remove(FOLLOW_OWNER);
            social.remove(PET_DEFENSE_PLAYER);
            social.remove(PET_DEFENSE_UNTIL);
        }
        if (social.hasUUID(FRIENDLY_SUIT_PLAYER)
                && ownerUuid.equals(social.getUUID(FRIENDLY_SUIT_PLAYER))) {
            social.remove(FRIENDLY_SUIT_PLAYER);
            social.remove(FRIENDLY_SUIT_EMERGENCY);
            social.remove(FRIENDLY_SUIT_COMBAT);
            social.remove(FRIENDLY_SUIT_STARTED);
        }
        if (social.hasUUID(SECONDARY_GRAB_PLAYER)
                && ownerUuid.equals(social.getUUID(SECONDARY_GRAB_PLAYER))) {
            social.remove(SECONDARY_GRAB_PLAYER);
        }
        if (social.hasUUID(ORGANIC_EVACUATION_PLAYER)
                && ownerUuid.equals(social.getUUID(ORGANIC_EVACUATION_PLAYER))) {
            social.remove(ORGANIC_EVACUATION_PLAYER);
            social.remove(ORGANIC_EVACUATION_UNTIL);
        }
        if (wasPetOwner || mob instanceof TamableLatexEntity nativePet
                && ownerUuid.equals(nativePet.getOwnerUUID())) {
            demoteNativePet(mob, ownerUuid);
        }
        if (child(mob, BONDS).getAllKeys().isEmpty() && !social.hasUUID(PET_OWNER)) {
            restoreSuppressedMovementGoals(mob);
        }
    }

    /** Removes Changed's or Changed Addon's native taming state as well. */
    private static void demoteNativePet(ChangedEntity mob, UUID ownerUuid) {
        if (!(mob instanceof TamableLatexEntity nativePet)
                || !ownerUuid.equals(nativePet.getOwnerUUID())) {
            return;
        }
        try {
            nativePet.setFollowOwner(false);
            invokeMethod(mob, "setTame", new Class<?>[]{boolean.class}, false);
            invokeMethod(mob, "setOwnerUUID", new Class<?>[]{UUID.class}, (Object) null);
            mob.getNavigation().stop();
            mob.setTarget(null);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ChangedSynergyMod.LOGGER.warn(
                    "Could not remove native pet state from released {}",
                    mob.getType(), exception);
        }
    }

    /** True when the bonded owner now wears a permanent form other than this pet's form. */
    public static boolean isBondedOwnerInOtherForm(ChangedEntity mob, ServerPlayer player) {
        if (!isBonded(mob, player) || mob.getSelfVariant() == null) {
            return false;
        }
        var playerVariant = ProcessTransfur.getPlayerTransfurVariant(player);
        return playerVariant != null
                && !playerVariant.isTemporaryFromSuit()
                && playerVariant.getParent() != mob.getSelfVariant();
    }

    /** Claims the one-time jealous greeting for each distinct new owner form. */
    public static boolean claimJealousFormGreeting(ChangedEntity mob, ServerPlayer player) {
        CompoundTag forms = child(mob, JEALOUS_FORMS);
        String playerKey = player.getUUID().toString();
        if (!isBondedOwnerInOtherForm(mob, player)) {
            forms.remove(playerKey);
            return false;
        }
        String form = ProcessTransfur.getPlayerTransfurVariant(player).getParent().getFormId().toString();
        if (form.equals(forms.getString(playerKey))) {
            return false;
        }
        forms.putString(playerKey, form);
        return true;
    }

    public static void beginFriendlySuit(
            ChangedEntity mob,
            ServerPlayer player,
            boolean emergency,
            boolean combat) {
        CompoundTag social = data(mob);
        social.putUUID(FRIENDLY_SUIT_PLAYER, player.getUUID());
        social.putBoolean(FRIENDLY_SUIT_EMERGENCY, emergency);
        social.putBoolean(FRIENDLY_SUIT_COMBAT, combat);
        social.putLong(FRIENDLY_SUIT_STARTED, mob.level().getGameTime());
    }

    public static boolean isFriendlySuitActive(ChangedEntity mob, ServerPlayer player) {
        CompoundTag social = data(mob);
        return social.hasUUID(FRIENDLY_SUIT_PLAYER)
                && player.getUUID().equals(social.getUUID(FRIENDLY_SUIT_PLAYER));
    }

    public static boolean isEmergencySuitActive(ChangedEntity mob, ServerPlayer player) {
        return isFriendlySuitActive(mob, player)
                && data(mob).getBoolean(FRIENDLY_SUIT_EMERGENCY);
    }

    public static boolean isCombatSuitActive(ChangedEntity mob, ServerPlayer player) {
        return isFriendlySuitActive(mob, player)
                && data(mob).getBoolean(FRIENDLY_SUIT_COMBAT);
    }

    public static long friendlySuitStarted(ChangedEntity mob) {
        return data(mob).getLong(FRIENDLY_SUIT_STARTED);
    }

    public static void endFriendlySuit(ChangedEntity mob, ServerPlayer player) {
        CompoundTag social = data(mob);
        if (social.hasUUID(FRIENDLY_SUIT_PLAYER)
                && player.getUUID().equals(social.getUUID(FRIENDLY_SUIT_PLAYER))) {
            social.remove(FRIENDLY_SUIT_PLAYER);
            social.remove(FRIENDLY_SUIT_EMERGENCY);
            social.remove(FRIENDLY_SUIT_COMBAT);
            social.remove(FRIENDLY_SUIT_STARTED);
        }
    }

    public static boolean canStartEmergencyRescue(ChangedEntity mob) {
        return data(mob).getLong(EMERGENCY_RESCUE_COOLDOWN) <= mob.level().getGameTime();
    }

    public static void delayEmergencyRescue(ChangedEntity mob, long ticks) {
        data(mob).putLong(EMERGENCY_RESCUE_COOLDOWN,
                mob.level().getGameTime() + Math.max(1L, ticks));
    }

    /** @return true only for the first attack remembered from this player. */
    public static boolean markProvoked(ChangedEntity mob, ServerPlayer player) {
        clearSecondaryTransfurSettlement(mob, player);
        if (isBonded(mob, player)) {
            return false;
        }
        CompoundTag provoked = child(mob, PROVOKED);
        String key = player.getUUID().toString();
        boolean first = !isProvoked(mob, player);
        provoked.putLong(key, mob.level().getGameTime() + PROVOCATION_DURATION);
        return first;
    }

    public static boolean isProvoked(ChangedEntity mob, ServerPlayer player) {
        if (hasRelationshipBetrayal(mob, player)) {
            return true;
        }
        CompoundTag provoked = child(mob, PROVOKED);
        String key = player.getUUID().toString();
        if (provoked.contains(key, Tag.TAG_BYTE)) {
            if (!provoked.getBoolean(key)) {
                provoked.remove(key);
                return false;
            }
            // Version 1 stored permanent booleans. Give an old active grudge a
            // short final combat window, then let social disposition take over.
            provoked.putLong(key, mob.level().getGameTime() + PROVOCATION_DURATION / 2L);
        }
        if (!provoked.contains(key, Tag.TAG_LONG)) {
            return false;
        }
        if (provoked.getLong(key) > mob.level().getGameTime()) {
            return true;
        }
        provoked.remove(key);
        child(mob, FRIENDLY_HITS).remove(key);
        return false;
    }

    public static void refreshProvocation(ChangedEntity mob, ServerPlayer player) {
        if (isProvoked(mob, player)) {
            child(mob, PROVOKED).putLong(
                    player.getUUID().toString(),
                    mob.level().getGameTime() + PROVOCATION_DURATION);
        }
    }

    public static void clearProvocation(ChangedEntity mob, ServerPlayer player) {
        if (hasRelationshipBetrayal(mob, player)) {
            return;
        }
        child(mob, PROVOKED).remove(player.getUUID().toString());
        child(mob, FRIENDLY_HITS).remove(player.getUUID().toString());
    }

    public static boolean hasHostilityToward(ChangedEntity mob, ServerPlayer player) {
        return mob.getTarget() == player
                || mob.getLastHurtByMob() == player
                || HuntMemory.targets(mob, player);
    }

    public static void clearHostilityToward(ChangedEntity mob, ServerPlayer player) {
        clearHostilityToward(mob, player, true);
    }

    /**
     * Clears a companion's stale owner target without destroying a valid
     * owner-follow path that another goal has already calculated this tick.
     */
    public static void clearOwnerHostility(ChangedEntity mob, ServerPlayer player) {
        clearHostilityToward(mob, player, false);
    }

    private static void clearHostilityToward(
            ChangedEntity mob,
            ServerPlayer player,
            boolean stopNavigation) {
        if (hasRelationshipBetrayal(mob, player)) {
            return;
        }
        if (mob.getTarget() == player) {
            mob.setTarget(null);
        }
        clearRevengeMemoryToward(mob, player);
        mob.setAggressive(false);
        mob.setSprinting(false);
        if (stopNavigation) {
            mob.getNavigation().stop();
        }
    }

    public static void clearRevengeMemoryToward(ChangedEntity mob, ServerPlayer player) {
        if (mob.getLastHurtByMob() == player) {
            mob.setLastHurtByMob(null);
            mob.setLastHurtByPlayer(null);
        }
        if (mob.getLastHurtMob() == player) {
            mob.setLastHurtMob(null);
        }
        if (HuntMemory.targets(mob, player)) {
            HuntMemory.clear(mob);
        }
    }

    /**
     * A bonded companion must not retain panic or avoidance goals that make it
     * deliberately flee its host. Ordinary wandering remains installed so the
     * wheel's wander mode still works; the high-priority Synergy follow goal
     * takes MOVE control whenever follow mode requires it.
     *
     * Goal selectors are iterated during the entity tick. Queueing the mutation
     * until the end of the server tick prevents ConcurrentModificationException
     * when a bond is added or audited from inside that tick.
     */
    public static void suppressBondedAvoidanceGoals(ChangedEntity mob) {
        queueGoalMutation(
                mob,
                bondEnabled(mob)
                        ? GoalMutation.SUPPRESS
                        : GoalMutation.RESTORE);
    }

    public static void flushPendingGoalMutations(MinecraftServer server) {
        List<Map.Entry<ChangedEntity, GoalMutation>> pending = new ArrayList<>();
        synchronized (PENDING_GOAL_MUTATIONS) {
            PENDING_GOAL_MUTATIONS.forEach((mob, mutation) ->
                    pending.add(Map.entry(mob, mutation)));
            PENDING_GOAL_MUTATIONS.clear();
        }

        for (Map.Entry<ChangedEntity, GoalMutation> entry : pending) {
            ChangedEntity mob = entry.getKey();
            if (mob.isRemoved()
                    || !(mob.level() instanceof ServerLevel level)
                    || level.getServer() != server) {
                continue;
            }
            boolean stillCompanion = bondEnabled(mob)
                    && (!bondedPlayerUuids(mob).isEmpty()
                            || petOwnerUuid(mob).isPresent());
            if (entry.getValue() == GoalMutation.SUPPRESS && stillCompanion) {
                applyBondedGoalSuppression(mob);
            } else if (entry.getValue() == GoalMutation.RESTORE && !stillCompanion) {
                applySuppressedGoalRestoration(mob);
            }
        }
    }

    private static void applyBondedGoalSuppression(ChangedEntity mob) {
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof BondedFollowGoal)) {
            // Some native/Add-on tame-state refreshes rebuild their goal list
            // after EntityJoinLevelEvent. Restore Synergy's authoritative
            // follow goal at the safe end-of-tick mutation point.
            mob.goalSelector.addGoal(
                    BondedFollowGoal.PRIORITY,
                    new BondedFollowGoal(mob));
        }
        List<SuppressedMovementGoal> incompatible = mob.goalSelector.getAvailableGoals().stream()
                .filter(wrapped -> isBondIncompatibleMovementGoal(wrapped.getGoal()))
                .map(wrapped -> new SuppressedMovementGoal(
                        wrapped.getPriority(), wrapped.getGoal()))
                .toList();
        if (incompatible.isEmpty()) {
            return;
        }
        SUPPRESSED_MOVEMENT_GOALS
                .computeIfAbsent(mob, ignored -> new ArrayList<>())
                .addAll(incompatible);
        incompatible.stream()
                .map(SuppressedMovementGoal::goal)
                .forEach(mob.goalSelector::removeGoal);
    }

    private static void restoreSuppressedMovementGoals(ChangedEntity mob) {
        queueGoalMutation(mob, GoalMutation.RESTORE);
    }

    private static void applySuppressedGoalRestoration(ChangedEntity mob) {
        List<SuppressedMovementGoal> suppressed = SUPPRESSED_MOVEMENT_GOALS.remove(mob);
        if (suppressed == null) {
            return;
        }
        suppressed.forEach(entry ->
                mob.goalSelector.addGoal(entry.priority(), entry.goal()));
    }

    private static void queueGoalMutation(ChangedEntity mob, GoalMutation mutation) {
        synchronized (PENDING_GOAL_MUTATIONS) {
            PENDING_GOAL_MUTATIONS.put(mob, mutation);
        }
    }

    private record SuppressedMovementGoal(int priority, Goal goal) {
    }

    private enum GoalMutation {
        SUPPRESS,
        RESTORE
    }

    private static boolean isBondIncompatibleMovementGoal(Goal goal) {
        if (goal instanceof AvoidEntityGoal<?> || goal instanceof PanicGoal) {
            return true;
        }
        String name = goal.getClass().getSimpleName();
        return name.equals("LatexFollowOwnerGoal")
                || name.equals("AvoidCatlikeOrCatGoal")
                || name.contains("Avoid")
                || name.contains("Flee")
                || name.contains("Retreat")
                || name.contains("Panic")
                || name.contains("RunAway")
                || name.contains("LowHealth") && name.endsWith("Goal");
    }

    /** Builds from confusion to a warning, then hostility after abuse or a major blow. */
    public static FriendlyHitStage recordFriendlyHit(
            ChangedEntity mob,
            ServerPlayer player,
            float damage) {
        if (isProvoked(mob, player)) {
            return FriendlyHitStage.HOSTILE;
        }

        CompoundTag warnings = child(mob, FRIENDLY_HITS);
        String key = player.getUUID().toString();
        CompoundTag warning = warnings.contains(key, Tag.TAG_COMPOUND)
                ? warnings.getCompound(key)
                : new CompoundTag();
        long now = mob.level().getGameTime();
        if (now - warning.getLong("LastHit") > FRIENDLY_HIT_WINDOW) {
            warning = new CompoundTag();
        }

        int count = warning.getInt("Count") + 1;
        float totalDamage = warning.getFloat("Damage") + Math.max(0.0F, damage);
        warning.putInt("Count", count);
        warning.putFloat("Damage", totalDamage);
        warning.putLong("LastHit", now);
        warnings.put(key, warning);

        double tolerance = CreaturePersonality.damageToleranceMultiplier(mob, player);
        float majorBlow = (float)(Math.max(6.0F, mob.getMaxHealth() * 0.28F) * tolerance);
        float accumulatedLimit =
                (float)(Math.max(10.0F, mob.getMaxHealth() * 0.5F) * tolerance);
        int hitLimit = CreaturePersonality.friendlyHitLimit(mob, player);
        if (count >= hitLimit || damage >= majorBlow || totalDamage >= accumulatedLimit) {
            markProvoked(mob, player);
            warnings.remove(key);
            return FriendlyHitStage.HOSTILE;
        }
        return count == 1 ? FriendlyHitStage.CONFUSED : FriendlyHitStage.WARNING;
    }

    /**
     * Keeps revenge/attack goals quiet long enough for the confusion or final-warning
     * reaction to finish. A later hit is still counted normally and refreshes this grace.
     */
    public static void beginWarningGrace(ChangedEntity mob, ServerPlayer player, long durationTicks) {
        CompoundTag social = data(mob);
        social.putUUID(WARNING_PLAYER, player.getUUID());
        social.putLong(WARNING_UNTIL, mob.level().getGameTime() + Math.max(1L, durationTicks));
    }

    public static boolean isWarningGraceActive(ChangedEntity mob, ServerPlayer player) {
        CompoundTag social = data(mob);
        if (!social.hasUUID(WARNING_PLAYER)
                || !player.getUUID().equals(social.getUUID(WARNING_PLAYER))) {
            return false;
        }
        if (social.getLong(WARNING_UNTIL) > mob.level().getGameTime()) {
            return true;
        }
        social.remove(WARNING_PLAYER);
        social.remove(WARNING_UNTIL);
        return false;
    }

    public static void clearWarningGrace(ChangedEntity mob, ServerPlayer player) {
        CompoundTag social = data(mob);
        if (social.hasUUID(WARNING_PLAYER)
                && player.getUUID().equals(social.getUUID(WARNING_PLAYER))) {
            social.remove(WARNING_PLAYER);
            social.remove(WARNING_UNTIL);
        }
    }

    /** Marks the target whose secondary-transfur hold must not be broken by combat AI. */
    public static void beginSecondaryGrab(ChangedEntity mob, ServerPlayer player) {
        data(mob).putUUID(SECONDARY_GRAB_PLAYER, player.getUUID());
    }

    public static boolean isSecondaryGrabActive(ChangedEntity mob, ServerPlayer player) {
        CompoundTag social = data(mob);
        return social.hasUUID(SECONDARY_GRAB_PLAYER)
                && player.getUUID().equals(social.getUUID(SECONDARY_GRAB_PLAYER));
    }

    public static void endSecondaryGrab(ChangedEntity mob, ServerPlayer player) {
        CompoundTag social = data(mob);
        if (social.hasUUID(SECONDARY_GRAB_PLAYER)
                && player.getUUID().equals(social.getUUID(SECONDARY_GRAB_PLAYER))) {
            social.remove(SECONDARY_GRAB_PLAYER);
        }
    }

    /**
     * Treats a completed punitive secondary transfur as the end of the current
     * fight. Long-term familiarity and faction reputation are intentionally
     * retained, but this individual cannot immediately reacquire or re-grab
     * the player until the player starts a new aggressive incident.
     */
    public static void settleAfterSecondaryTransfur(
            ChangedEntity mob,
            ServerPlayer player) {
        settleCurrentConflict(mob, player);
    }

    /**
     * A negotiated release is also a concluded incident.  Keep the same
     * durable cease-fire used after a punitive second transfur so native and
     * Addon target goals cannot immediately acquire the just-released player.
     * A later player attack clears this marker and starts a new incident.
     */
    public static void settleAfterNegotiatedRelease(
            ChangedEntity mob,
            ServerPlayer player) {
        settleCurrentConflict(mob, player);
    }

    private static void settleCurrentConflict(
            ChangedEntity mob,
            ServerPlayer player) {
        String key = player.getStringUUID();
        child(mob, SECONDARY_TRANSFUR_SETTLED).putBoolean(key, true);
        child(mob, PROVOKED).remove(key);
        child(mob, FRIENDLY_HITS).remove(key);
        clearWarningGrace(mob, player);
        clearTruce(mob, player);
        endSecondaryGrab(mob, player);

        if (mob.getTarget() == player) {
            mob.setTarget(null);
        }
        if (mob.getLastHurtByMob() == player) {
            mob.setLastHurtByMob(null);
            mob.setLastHurtByPlayer(null);
        }
        if (HuntMemory.targets(mob, player)) {
            HuntMemory.clear(mob);
        }
        if (mob instanceof NeutralMob neutralMob) {
            neutralMob.stopBeingAngry();
        }
        mob.setAggressive(false);
        mob.setSprinting(false);
        mob.getNavigation().stop();
    }

    public static boolean isSettledAfterSecondaryTransfur(
            ChangedEntity mob,
            ServerPlayer player) {
        return child(mob, SECONDARY_TRANSFUR_SETTLED)
                .getBoolean(player.getStringUUID());
    }

    public static void clearSecondaryTransfurSettlement(
            ChangedEntity mob,
            ServerPlayer player) {
        child(mob, SECONDARY_TRANSFUR_SETTLED)
                .remove(player.getStringUUID());
    }

    /**
     * Marks a short, consensual social-wheel hug. The optional Addon bridge
     * uses this marker to distinguish it from hostile arm-grabs before calling
     * Changed's otherwise shared grab entry point.
     */
    public static void beginFriendlySocialHug(
            ChangedEntity mob,
            ServerPlayer player,
            long durationTicks) {
        CompoundTag social = data(mob);
        social.putUUID(FRIENDLY_HUG_PLAYER, player.getUUID());
        social.putLong(
                FRIENDLY_HUG_UNTIL,
                mob.level().getGameTime() + Math.max(1L, durationTicks));
    }

    public static boolean hasFriendlySocialHug(ChangedEntity mob) {
        return data(mob).hasUUID(FRIENDLY_HUG_PLAYER);
    }

    /**
     * Checks ownership of the friendly-hug marker without applying its timer.
     * Release cleanup uses this form so hostile QTE and damage code cannot take
     * over during the single tick in which an expired hug is being released.
     */
    public static boolean isFriendlySocialHugTarget(
            ChangedEntity mob,
            LivingEntity target) {
        CompoundTag social = data(mob);
        return social.hasUUID(FRIENDLY_HUG_PLAYER)
                && target.getUUID().equals(social.getUUID(FRIENDLY_HUG_PLAYER));
    }

    public static boolean isFriendlySocialHugActive(
            ChangedEntity mob,
            LivingEntity target) {
        return isFriendlySocialHugTarget(mob, target)
                && data(mob).getLong(FRIENDLY_HUG_UNTIL)
                        > mob.level().getGameTime();
    }

    public static long friendlySocialHugUntil(ChangedEntity mob) {
        return data(mob).getLong(FRIENDLY_HUG_UNTIL);
    }

    public static void endFriendlySocialHug(ChangedEntity mob) {
        CompoundTag social = data(mob);
        social.remove(FRIENDLY_HUG_PLAYER);
        social.remove(FRIENDLY_HUG_UNTIL);
    }

    /** Configurable low-health extraction used only by organic bonded creatures. */
    public static OrganicRescueMode organicRescueMode(ChangedEntity mob) {
        if (!isOrganic(mob)) {
            return OrganicRescueMode.OFF;
        }
        CompoundTag social = data(mob);
        if (!social.contains(ORGANIC_RESCUE_MODE, Tag.TAG_INT)) {
            social.putInt(
                    ORGANIC_RESCUE_MODE,
                    OrganicRescueMode.OFF.ordinal());
        }
        int ordinal = social.getInt(ORGANIC_RESCUE_MODE);
        OrganicRescueMode[] values = OrganicRescueMode.values();
        return ordinal >= 0 && ordinal < values.length
                ? values[ordinal]
                : OrganicRescueMode.OFF;
    }

    public static OrganicRescueMode cycleOrganicRescueMode(ChangedEntity mob) {
        OrganicRescueMode[] values = OrganicRescueMode.values();
        OrganicRescueMode current = organicRescueMode(mob);
        OrganicRescueMode next = values[(current.ordinal() + 1) % values.length];
        data(mob).putInt(ORGANIC_RESCUE_MODE, next.ordinal());
        return next;
    }

    public static void beginOrganicEvacuation(
            ChangedEntity mob,
            ServerPlayer player,
            long durationTicks) {
        CompoundTag social = data(mob);
        social.putUUID(ORGANIC_EVACUATION_PLAYER, player.getUUID());
        social.putLong(
                ORGANIC_EVACUATION_UNTIL,
                mob.level().getGameTime() + Math.max(1L, durationTicks));
    }

    public static boolean hasOrganicEvacuation(ChangedEntity mob) {
        return data(mob).hasUUID(ORGANIC_EVACUATION_PLAYER);
    }

    public static boolean isOrganicEvacuationTarget(
            ChangedEntity mob,
            LivingEntity target) {
        CompoundTag social = data(mob);
        return social.hasUUID(ORGANIC_EVACUATION_PLAYER)
                && target.getUUID().equals(
                        social.getUUID(ORGANIC_EVACUATION_PLAYER));
    }

    public static boolean isOrganicEvacuationActive(
            ChangedEntity mob,
            LivingEntity target) {
        return isOrganicEvacuationTarget(mob, target)
                && data(mob).getLong(ORGANIC_EVACUATION_UNTIL)
                        > mob.level().getGameTime();
    }

    public static void endOrganicEvacuation(ChangedEntity mob) {
        CompoundTag social = data(mob);
        social.remove(ORGANIC_EVACUATION_PLAYER);
        social.remove(ORGANIC_EVACUATION_UNTIL);
    }

    /** Safe scripted holds bypass hostile QTE and transfur damage. */
    public static boolean isFriendlyArmHoldTarget(
            ChangedEntity mob,
            LivingEntity target) {
        return isFriendlySocialHugTarget(mob, target)
                || isOrganicEvacuationTarget(mob, target)
                || InvoluntaryTransfurNegotiation.isReleaseHoldTarget(
                        mob, target);
    }

    public static boolean isFriendlyArmHoldActive(
            ChangedEntity mob,
            LivingEntity target) {
        return isFriendlySocialHugActive(mob, target)
                || isOrganicEvacuationActive(mob, target)
                || InvoluntaryTransfurNegotiation.isReleaseHoldActive(
                        mob, target);
    }

    /** Prevents an attacker from immediately re-grabbing an owner after a rescue hit. */
    public static void blockGrabAgainst(
            ChangedEntity mob,
            ServerPlayer player,
            long durationTicks) {
        CompoundTag social = data(mob);
        social.putUUID(GRAB_BLOCKED_PLAYER, player.getUUID());
        social.putLong(
                GRAB_BLOCKED_UNTIL,
                mob.level().getGameTime() + Math.max(1L, durationTicks));
    }

    public static boolean isGrabTemporarilyBlocked(
            ChangedEntity mob,
            ServerPlayer player) {
        CompoundTag social = data(mob);
        if (!social.hasUUID(GRAB_BLOCKED_PLAYER)
                || !player.getUUID().equals(social.getUUID(GRAB_BLOCKED_PLAYER))) {
            return false;
        }
        if (social.getLong(GRAB_BLOCKED_UNTIL) > mob.level().getGameTime()) {
            return true;
        }
        social.remove(GRAB_BLOCKED_PLAYER);
        social.remove(GRAB_BLOCKED_UNTIL);
        return false;
    }

    /** Single authoritative gate for hostile NPC-to-player grab attempts. */
    public static boolean mayInitiateHostileGrab(
            ChangedEntity mob,
            ServerPlayer player) {
        return !CreatureSocialProfile.isGrabMechanicExcluded(mob)
                && player.isAlive()
                && !player.isCreative()
                && !player.isSpectator()
                && !isGrabTemporarilyBlocked(mob, player)
                && !shouldRemainNeutral(mob, player);
    }

    /**
     * Applies one start roll to an otherwise valid hostile grab. A miss creates
     * a short per-target pause so a ticking ability cannot reroll immediately.
     */
    public static boolean passHostileGrabAttemptRoll(
            ChangedEntity mob,
            ServerPlayer player) {
        double chance = isOrganic(mob)
                ? ChangedSynergyConfig.COMMON.organicHostileGrabAttemptChance.get()
                : ChangedSynergyConfig.COMMON.hostileGrabAttemptChance.get();
        if (chance >= 1.0D || mob.getRandom().nextDouble() < chance) {
            return true;
        }
        blockGrabAgainst(mob, player, 30L + mob.getRandom().nextInt(21));
        return false;
    }

    public static void beginTruce(ChangedEntity mob, ServerPlayer player, long durationTicks) {
        beginTruce(mob, player, durationTicks, false);
    }

    /** Starts the specific ceasefire granted by a player's pat. */
    public static void beginPatTruce(
            ChangedEntity mob,
            ServerPlayer player,
            long durationTicks) {
        beginTruce(mob, player, durationTicks, true);
    }

    private static void beginTruce(
            ChangedEntity mob,
            ServerPlayer player,
            long durationTicks,
            boolean fromPat) {
        if (hasRelationshipBetrayal(mob, player)) {
            return;
        }
        CompoundTag data = data(mob);
        data.putUUID(TRUCE_PLAYER, player.getUUID());
        data.putLong(TRUCE_UNTIL, mob.level().getGameTime() + Math.max(1L, durationTicks));
        data.putBoolean(TRUCE_FROM_PAT, fromPat);
    }

    public static boolean isPatTruced(ChangedEntity mob, ServerPlayer player) {
        return isTruced(mob, player) && data(mob).getBoolean(TRUCE_FROM_PAT);
    }

    public static boolean isTruced(ChangedEntity mob, ServerPlayer player) {
        CompoundTag data = data(mob);
        if (!data.hasUUID(TRUCE_PLAYER) || !player.getUUID().equals(data.getUUID(TRUCE_PLAYER))) {
            return false;
        }
        if (data.getLong(TRUCE_UNTIL) > mob.level().getGameTime()) {
            return true;
        }
        data.remove(TRUCE_PLAYER);
        data.remove(TRUCE_UNTIL);
        data.remove(TRUCE_FROM_PAT);
        return false;
    }

    public static boolean clearTruce(ChangedEntity mob, ServerPlayer player) {
        CompoundTag data = data(mob);
        if (!data.hasUUID(TRUCE_PLAYER) || !player.getUUID().equals(data.getUUID(TRUCE_PLAYER))) {
            return false;
        }
        data.remove(TRUCE_PLAYER);
        data.remove(TRUCE_UNTIL);
        data.remove(TRUCE_FROM_PAT);
        return true;
    }

    /** Remembers that this player attacked while a pat ceasefire was active. */
    public static void markPatTruceBetrayal(ChangedEntity mob, ServerPlayer player) {
        clearSecondaryTransfurSettlement(mob, player);
        child(mob, PAT_TRUCE_BETRAYALS).putBoolean(player.getStringUUID(), true);
    }

    public static boolean hasBetrayedPatTruce(
            ChangedEntity mob,
            ServerPlayer player) {
        return child(mob, PAT_TRUCE_BETRAYALS).getBoolean(player.getStringUUID());
    }

    public static void markRelationshipBetrayal(
            ChangedEntity mob,
            ServerPlayer player) {
        if (!ChangedSynergyGameRules.enabled(
                mob.level(), ChangedSynergyGameRules.FRIENDSHIP_SYSTEM)) {
            return;
        }
        clearSecondaryTransfurSettlement(mob, player);
        child(mob, RELATIONSHIP_BETRAYALS).putBoolean(player.getStringUUID(), true);
        child(mob, PROVOKED).remove(player.getStringUUID());
        child(mob, FRIENDLY_HITS).remove(player.getStringUUID());
        clearTruce(mob, player);
        clearWarningGrace(mob, player);
        CreaturePersonality.setSocialFollowing(mob, player, false);
    }

    public static boolean hasRelationshipBetrayal(
            ChangedEntity mob,
            ServerPlayer player) {
        return hasRelationshipBetrayal(mob, player.getUUID());
    }

    public static boolean hasRelationshipBetrayal(
            ChangedEntity mob,
            UUID playerId) {
        return ChangedSynergyGameRules.enabled(
                        mob.level(), ChangedSynergyGameRules.FRIENDSHIP_SYSTEM)
                && child(mob, RELATIONSHIP_BETRAYALS)
                .getBoolean(playerId.toString());
    }

    /** Operator repair hook for testing or resolving an intentionally broken friendship. */
    public static void reconcileRelationship(
            ChangedEntity mob,
            ServerPlayer player) {
        String key = player.getStringUUID();
        child(mob, RELATIONSHIP_BETRAYALS).remove(key);
        child(mob, PAT_TRUCE_BETRAYALS).remove(key);
        child(mob, PROVOKED).remove(key);
        child(mob, FRIENDLY_HITS).remove(key);
        child(mob, SECONDARY_TRANSFUR_SETTLED).remove(key);
        clearWarningGrace(mob, player);
        clearTruce(mob, player);
        clearHostilityToward(mob, player);
    }

    public static boolean shouldRemainNeutral(ChangedEntity mob, ServerPlayer player) {
        if (isBonded(mob, player) || isPetOwner(mob, player)
                || mob instanceof TamableLatexEntity nativePet
                        && nativePet.isTame()
                        && player.getUUID().equals(nativePet.getOwnerUUID())) {
            return true;
        }
        if (FactionHostilityGrace.active(mob, player)) {
            return true;
        }
        if (InvoluntaryTransfurNegotiation.canNegotiate(player, mob)) {
            // The responsible individual has agreed to hear the player out.
            // Reacquiring them here would make the negotiation impossible.
            return true;
        }
        if (isSettledAfterSecondaryTransfur(mob, player)) {
            return true;
        }
        if (hasRelationshipBetrayal(mob, player)) {
            return false;
        }
        if (isTruced(mob, player) || isWarningGraceActive(mob, player)) {
            return true;
        }
        if (isPetDefenseAuthorized(mob, player)) {
            return false;
        }
        LatexSocialRelation relation = LatexSocialRelation.between(mob, player);
        if (CreaturePersonality.hasTrustedRelationship(mob, player)
                && !isProvoked(mob, player)
                && relation != LatexSocialRelation.RIVAL) {
            return true;
        }
        if (FactionReputation.isHostile(mob, player)) {
            return false;
        }
        if (FactionReputation.isRecognized(mob, player)
                && !isProvoked(mob, player)) {
            return true;
        }
        if (PoliteHumanInteraction.shouldWithholdHostility(mob, player)) {
            return true;
        }
        if (isOrganic(mob)) {
            // A nearby same-category companion still vouches for its reverted
            // owner. Unprovoked human neutrality is handled by the dedicated
            // organic-human gate; transformed outsiders remain combat targets.
            return (relation == LatexSocialRelation.FORMER_RESPECTED
                    || relation == LatexSocialRelation.FRIEND_RESPECTED)
                    && !isProvoked(mob, player);
        }
        return relation.isNormallyNeutral() && !isProvoked(mob, player);
    }

    private static CompoundTag child(ChangedEntity mob, String key) {
        CompoundTag root = data(mob);
        if (!root.contains(key, Tag.TAG_COMPOUND)) {
            root.put(key, new CompoundTag());
        }
        return root.getCompound(key);
    }

    private static boolean bondEnabled(ChangedEntity mob) {
        return CreatureSocialProfile.allowsSynergySystems(mob)
                && ChangedSynergyGameRules.enabled(
                mob.level(), ChangedSynergyGameRules.BOND_SYSTEM);
    }

    private static CompoundTag data(ChangedEntity mob) {
        CompoundTag persistent = mob.getPersistentData();
        if (!persistent.contains(ROOT, Tag.TAG_COMPOUND)) {
            persistent.put(ROOT, new CompoundTag());
        }
        return persistent.getCompound(ROOT);
    }

    private static CompoundTag playerBondData(ServerPlayer player) {
        CompoundTag persisted = playerPersistedData(player);
        if (!persisted.contains(PLAYER_BONDS, Tag.TAG_COMPOUND)) {
            persisted.put(PLAYER_BONDS, new CompoundTag());
        }
        return persisted.getCompound(PLAYER_BONDS);
    }

    private static CompoundTag playerPersistedData(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static boolean invokePlayerMethod(ChangedEntity mob, String name, Player player)
            throws ReflectiveOperationException {
        try {
            Method method = mob.getClass().getMethod(name, Player.class);
            method.invoke(mob, player);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (InvocationTargetException exception) {
            throw new ReflectiveOperationException(exception.getCause());
        }
    }

    private static boolean invokeMethod(
            ChangedEntity mob,
            String name,
            Class<?>[] parameterTypes,
            Object... arguments) throws ReflectiveOperationException {
        try {
            Method method = mob.getClass().getMethod(name, parameterTypes);
            method.invoke(mob, arguments);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (InvocationTargetException exception) {
            throw new ReflectiveOperationException(exception.getCause());
        }
    }

    public enum FriendlyHitStage {
        CONFUSED,
        WARNING,
        HOSTILE
    }

    public enum OrganicRescueMode {
        OFF(0.0F),
        CRITICAL(0.22F),
        LOW_HEALTH(0.38F);

        private final float healthRatio;

        OrganicRescueMode(float healthRatio) {
            this.healthRatio = healthRatio;
        }

        public boolean shouldRescue(ServerPlayer player) {
            return this != OFF
                    && player.getHealth()
                            <= player.getMaxHealth() * healthRatio;
        }

        public String translationKey() {
            return "menu.changed_synergy.bonded_latex.organic_rescue."
                    + name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
