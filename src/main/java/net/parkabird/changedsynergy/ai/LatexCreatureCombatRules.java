package net.parkabird.changedsynergy.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.latex.LatexType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;

/**
 * Creature-to-creature combat rules. These deliberately do not participate in
 * player-form disposition, so a transformed player can still be a valid rival.
 */
public final class LatexCreatureCombatRules {
    public static final float BONDED_SAFETY_HEALTH_RATIO = 0.35F;
    private static final float HEALTH_EPSILON = 0.01F;
    private static final Map<CombatPair, PendingDisengagement>
            PENDING_DISENGAGEMENTS = new LinkedHashMap<>();

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
            return false;
        }
        if (isProtectedLowHealthBond(target)) {
            return true;
        }
        if (!(attacker instanceof ChangedEntity changed)) {
            return false;
        }
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

    /** Villagers are civilians, and iron golems no longer classify Changed creatures as threats. */
    public static boolean mustRejectVillageTarget(
            Mob attacker,
            LivingEntity target) {
        return attacker instanceof ChangedEntity
                        && target instanceof AbstractVillager
                || attacker instanceof IronGolem
                        && target instanceof ChangedEntity;
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
        return LatexSocialMemory.hasActiveBond(creature)
                || LatexSocialMemory.petOwnerUuid(creature).isPresent();
    }

    /** A player's bonded companion and trusted friends are always on one side. */
    private static boolean sharePlayerAlliance(
            ChangedEntity first,
            ChangedEntity second) {
        return companionLinksTo(first, second)
                || companionLinksTo(second, first);
    }

    private static boolean companionLinksTo(
            ChangedEntity companion,
            ChangedEntity other) {
        if (!LatexSocialMemory.hasActiveBond(companion)) {
            return false;
        }
        for (UUID playerId : LatexSocialMemory.bondedPlayerUuids(companion)) {
            if (isPersonalAlly(other, playerId)) {
                return true;
            }
        }
        return LatexSocialMemory.petOwnerUuid(companion)
                .map(playerId -> isPersonalAlly(other, playerId))
                .orElse(false);
    }

    private static boolean isPersonalAlly(
            ChangedEntity creature,
            UUID playerId) {
        return LatexSocialMemory.hasActiveBond(creature)
                        && LatexSocialMemory.bondedPlayerUuids(creature).contains(playerId)
                || LatexSocialMemory.petOwnerUuid(creature)
                        .filter(playerId::equals)
                        .isPresent()
                || CreaturePersonality.hasTrustedRelationship(creature, playerId);
    }

    private static void clearOneSide(LivingEntity entity, LivingEntity opponent) {
        if (entity.getLastHurtByMob() == opponent) {
            entity.setLastHurtByMob(null);
        }
        if (!(entity instanceof Mob mob)) {
            return;
        }
        if (mob.getTarget() == opponent) {
            mob.setTarget(null);
        }
        mob.setAggressive(false);
        mob.setSprinting(false);
        mob.getNavigation().stop();
        if (mob instanceof ChangedEntity changed) {
            HuntMemory.clear(changed);
            LatexSocialMemory.clearPetDefense(changed, opponent);
        }
    }

    private record CombatPair(UUID first, UUID second) {
    }

    private record PendingDisengagement(
            MinecraftServer server,
            LivingEntity first,
            LivingEntity second) {
    }
}
