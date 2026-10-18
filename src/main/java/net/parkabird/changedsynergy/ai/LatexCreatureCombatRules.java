package net.parkabird.changedsynergy.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.latex.LatexType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.tags.TagKey;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;

/**
 * Creature-to-creature combat rules. These deliberately do not participate in
 * player-form disposition, so a transformed player can still be a valid rival.
 */
public final class LatexCreatureCombatRules {
    public static final float BONDED_SAFETY_HEALTH_RATIO = 0.35F;
    private static final float HEALTH_EPSILON = 0.01F;
    private static final Map<CombatPair, PendingDisengagement>
            PENDING_DISENGAGEMENTS = new LinkedHashMap<>();
    private static final TagKey<EntityType<?>> VILLAGE_CIVILIANS = tag("village_civilians");
    private static final TagKey<EntityType<?>> VILLAGE_PROTECTORS = tag("village_protectors");

    private LatexCreatureCombatRules() {
    }

    public static boolean areCompatriots(ChangedEntity first, ChangedEntity second) {
        return first != second
                && LatexSocialMemory.isSocialLatex(first)
                && LatexSocialMemory.isSocialLatex(second)
                && (LatexSocialRelation.sameSpecies(first, second)
                        || LatexSocialRelation.sameCategory(first, second));
    }

    /** True only for an actual latex/faction rivalry, not merely two unlike species. */
    public static boolean areRivals(ChangedEntity first, ChangedEntity second) {
        if (first == second
                || !LatexSocialMemory.isSocialLatex(first)
                || !LatexSocialMemory.isSocialLatex(second)
                || areCompatriots(first, second)) {
            return false;
        }
        LatexType firstType = LatexType.getEntityLatexType(first);
        LatexType secondType = LatexType.getEntityLatexType(second);
        if (firstType != null && secondType != null
                && (firstType.isHostileTo(secondType)
                        || secondType.isHostileTo(firstType))) {
            return true;
        }
        // Some Addon entities expose their faction through tags but have no
        // useful LatexType. Preserve Changed's white/dark rivalry for them.
        HunterFaction firstFaction = HunterFaction.of(first);
        HunterFaction secondFaction = HunterFaction.of(second);
        return firstFaction == HunterFaction.WHITE && secondFaction == HunterFaction.DARK
                || firstFaction == HunterFaction.DARK && secondFaction == HunterFaction.WHITE;
    }

    public static boolean isProtectedLowHealthBond(ChangedEntity creature) {
        return LatexSocialMemory.isSocialLatex(creature)
                && LatexSocialMemory.hasActiveBond(creature)
                && creature.getHealth() <= safetyHealth(creature) + HEALTH_EPSILON;
    }

    public static boolean mustRejectTarget(Mob attacker, ChangedEntity target) {
        if (attacker == target) {
            return true;
        }
        if (isProtectedLowHealthBond(target)) {
            return true;
        }
        if (!(attacker instanceof ChangedEntity changed)) {
            return false;
        }
        if (isFriendlyPlayerBodyPair(changed, target)) return true;
        if (areCompatriots(changed, target)) {
            return true;
        }
        if (sharePlayerAlliance(changed, target)) {
            return true;
        }
        if (!isBondedCompanion(changed)) {
            return false;
        }

        ServerPlayer owner = LatexSocialMemory.getPetOwner(changed);
        return owner == null
                || !BondedOwnerDefenseGoal.allowsConfiguredDefense(
                        changed, owner, target);
    }

    /** An INSTINCT body retains its player's alliances without acquiring NPC memories. */
    public static boolean isFriendlyPlayerBodyPair(ChangedEntity first, ChangedEntity second) {
        ServerPlayer firstOwner = net.parkabird.changedsynergy.compat.TrueTransfurCompat.bodyOwner(first);
        ServerPlayer secondOwner = net.parkabird.changedsynergy.compat.TrueTransfurCompat.bodyOwner(second);
        if (firstOwner != null && secondOwner != null)
            return firstOwner == secondOwner || !firstOwner.server.isPvpAllowed() || !firstOwner.canHarmPlayer(secondOwner);
        ServerPlayer owner = firstOwner != null ? firstOwner : secondOwner;
        ChangedEntity npc = firstOwner != null ? second : first;
        return owner != null && (LatexSocialMemory.isPetOwner(npc, owner)
                || net.parkabird.changedsynergy.origin.NativeOriginService.protectedMember(npc, owner)
                || CreaturePersonality.hasTrustedRelationship(npc, owner) && !LatexSocialMemory.isProvoked(npc, owner));
    }

    /** Keeps village civilians/protectors and Changed creatures out of mutual combat. */
    public static boolean mustRejectVillageTarget(
            Mob attacker,
            LivingEntity target) {
        if (!ChangedSynergyConfig.COMMON.peacefulVillageRelations.get()) {
            return false;
        }
        return attacker instanceof ChangedEntity && isVillageEntity(target)
                || target instanceof ChangedEntity && isVillageEntity(attacker);
    }

    public static boolean isVillageEntity(LivingEntity entity) {
        return entity instanceof AbstractVillager
                || entity instanceof IronGolem
                || entity.getType().is(VILLAGE_CIVILIANS)
                || entity.getType().is(VILLAGE_PROTECTORS);
    }

    /**
     * Keeps monster damage from taking a bonded creature below its safety
     * threshold. A hit that reaches that threshold also ends combat on both
     * sides.
     */
    public static float limitDamageToBond(
            ChangedEntity target,
            Mob attacker,
            float damage) {
        if (damage <= 0.0F
                || !LatexSocialMemory.hasActiveBond(target)) {
            return damage;
        }

        float allowed = Math.max(0.0F, target.getHealth() - safetyHealth(target));
        if (damage + HEALTH_EPSILON < allowed) {
            return damage;
        }
        disengage(attacker, target);
        return Math.min(damage, allowed);
    }

    /** Incidental fights between non-rival latexes end before either can die. */
    public static float limitNonRivalDamage(
            ChangedEntity target, ChangedEntity attacker, float damage) {
        if (!ChangedSynergyConfig.COMMON.nonRivalCombatProtection.get()
                || damage <= 0.0F || areRivals(attacker, target)
                || !LatexSocialMemory.isSocialLatex(attacker)
                || !LatexSocialMemory.isSocialLatex(target)) return damage;
        float minimumHealth = Math.max(1.0F, target.getMaxHealth() * 0.2F);
        float allowed = Math.max(0.0F, target.getHealth() - minimumHealth);
        if (damage + HEALTH_EPSILON >= allowed) disengage(attacker, target);
        return Math.min(damage, allowed);
    }

    public static void disengage(LivingEntity first, LivingEntity second) {
        clearOneSide(first, second);
        clearOneSide(second, first);
        if (first.level() instanceof ServerLevel firstLevel
                && second.level() == first.level()) {
            UUID firstId = first.getUUID();
            UUID secondId = second.getUUID();
            CombatPair key = firstId.compareTo(secondId) <= 0
                    ? new CombatPair(firstId, secondId)
                    : new CombatPair(secondId, firstId);
            synchronized (PENDING_DISENGAGEMENTS) {
                PENDING_DISENGAGEMENTS.put(
                        key,
                        new PendingDisengagement(firstLevel.getServer(), first, second));
            }
        }
    }

    /**
     * Repeats the cleanup after vanilla and other mods have finished their
     * damage bookkeeping for this tick.
     */
    public static void flushPendingDisengagements(MinecraftServer server) {
        List<PendingDisengagement> pending = new ArrayList<>();
        synchronized (PENDING_DISENGAGEMENTS) {
            PENDING_DISENGAGEMENTS.entrySet().removeIf(entry -> {
                if (entry.getValue().server() != server) {
                    return false;
                }
                pending.add(entry.getValue());
                return true;
            });
        }
        for (PendingDisengagement entry : pending) {
            if (entry.first().isRemoved() || entry.second().isRemoved()
                    || entry.first().level() != entry.second().level()) {
                continue;
            }
            clearOneSide(entry.first(), entry.second());
            clearOneSide(entry.second(), entry.first());
        }
    }

    private static float safetyHealth(ChangedEntity creature) {
        return creature.getMaxHealth() * BONDED_SAFETY_HEALTH_RATIO;
    }

    private static boolean isBondedCompanion(ChangedEntity creature) {
        if (TrueTransfurCompat.isInstinctBody(creature)) return false;
        return LatexSocialMemory.hasActiveBond(creature)
                || LatexSocialMemory.petOwnerUuid(creature).isPresent();
    }

    /** A player's bonded companion and trusted friends are always on one side. */
    public static boolean sharePlayerAlliance(
            ChangedEntity first,
            ChangedEntity second) {
        return companionLinksTo(first, second)
                || companionLinksTo(second, first);
    }

    private static boolean companionLinksTo(
            ChangedEntity companion,
            ChangedEntity other) {
        java.util.Set<UUID> owners = companionOwners(companion);
        java.util.Set<UUID> otherOwners = companionOwners(other);
        if (!owners.isEmpty() && !otherOwners.isEmpty()
                && companion.getServer() != null && !companion.getServer().isPvpAllowed()) return true;
        for (UUID playerId : owners) {
            if (isPersonalAlly(other, playerId)) {
                return true;
            }
            ServerPlayer player = companion.getServer() == null ? null : companion.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) continue;
            for (UUID otherId : otherOwners) {
                ServerPlayer otherPlayer = player.server.getPlayerList().getPlayer(otherId);
                if (otherPlayer != null && (!player.server.isPvpAllowed() || player.isAlliedTo(otherPlayer) || PlayerFriendshipData.friends(player,otherPlayer))) return true;
            }
        }
        return false;
    }

    private static java.util.Set<UUID> companionOwners(ChangedEntity creature) {
        java.util.Set<UUID> owners = new java.util.HashSet<>();
        ServerPlayer bodyOwner = net.parkabird.changedsynergy.compat.TrueTransfurCompat.bodyOwner(creature);
        if (bodyOwner != null) owners.add(bodyOwner.getUUID());
        if (TrueTransfurCompat.isInstinctBody(creature)) return owners;
        if (LatexSocialMemory.hasActiveBond(creature)) owners.addAll(LatexSocialMemory.bondedPlayerUuids(creature));
        LatexSocialMemory.petOwnerUuid(creature).ifPresent(owners::add);
        CreaturePersonality.socialPartnerUuid(creature).ifPresent(owners::add);
        PlayerOutpostService.assignedOwner(creature).ifPresent(owners::add);
        return owners;
    }

    private static boolean isPersonalAlly(
            ChangedEntity creature,
            UUID playerId) {
        if (TrueTransfurCompat.isInstinctBody(creature)) {
            ServerPlayer owner = TrueTransfurCompat.bodyOwner(creature);
            return owner != null && playerId.equals(owner.getUUID());
        }
        return LatexSocialMemory.hasActiveBond(creature)
                        && LatexSocialMemory.bondedPlayerUuids(creature).contains(playerId)
                || LatexSocialMemory.petOwnerUuid(creature)
                        .filter(playerId::equals)
                        .isPresent()
                || CreaturePersonality.hasTrustedRelationship(creature, playerId);
    }

    private static void clearOneSide(LivingEntity entity, LivingEntity opponent) {
        boolean involved = entity.getLastHurtByMob() == opponent || entity.getLastHurtMob() == opponent;
        if (entity.getLastHurtByMob() == opponent) {
            entity.setLastHurtByMob(null);
        }
        if (entity.getLastHurtMob() == opponent) {
            entity.setLastHurtMob(null);
        }
        if (!(entity instanceof Mob mob)) {
            return;
        }
        if (mob.getTarget() == opponent) {
            involved = true;
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        if (involved && mob.getTarget() == null) {
            mob.setAggressive(false);
            mob.setSprinting(false);
        }
        if (mob instanceof ChangedEntity changed && !TrueTransfurCompat.isInstinctBody(changed)) {
            ServerPlayer player = opponent instanceof ServerPlayer direct ? direct
                    : opponent instanceof ChangedEntity body ? TrueTransfurCompat.bodyOwner(body) : null;
            if (player != null && HuntMemory.targets(changed, player)) HuntMemory.clear(changed);
            LatexSocialMemory.clearPetDefense(changed, opponent);
        }
    }

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath("changed_synergy", path));
    }

    private record CombatPair(UUID first, UUID second) {
    }

    private record PendingDisengagement(
            MinecraftServer server,
            LivingEntity first,
            LivingEntity second) {
    }
}
