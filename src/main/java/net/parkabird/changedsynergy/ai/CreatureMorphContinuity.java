package net.parkabird.changedsynergy.ai;

import java.util.Set;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TamableLatexEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Preserves one individual when Changed implements a form change by replacing its entity. */
public final class CreatureMorphContinuity {
    private static final String PERSONALITY = "ChangedSynergyPersonality";
    private static final String IDENTITY = "ChangedSynergyIdentity";
    private static final String SOCIAL = "ChangedSynergySocial";
    private static final String LIFE = "ChangedSynergyLife";
    private static final String COMMUNITY = "ChangedSynergyCommunityId";
    private static final String FACILITY_COMMUNITY_AFFINITY =
            "ChangedSynergyFacilityCommunityAffinity";
    private static final String CARGO = "ChangedSynergyCarriedResource";
    private static final String LAST_PROVISION_SOURCE =
            "ChangedSynergyLastProvisionSource";
    private static final String LAST_PROVISION_ITEM =
            "ChangedSynergyLastProvisionItem";
    private static final String NEXT_HUNT = "ChangedSynergyNextHunt";
    private static final String FACILITY_WORK_CODE =
            "ChangedSynergyFacilityWorkCode";
    private static final String FACILITY_WORK_SECTION =
            "ChangedSynergyFacilityWorkSection";
    private static final String FACILITY_ORANGE_ROOM =
            "ChangedSynergyFacilityOrangeRoom";
    private static final String FACILITY_STORAGE_POS =
            "ChangedSynergyFacilityStoragePos";

    private CreatureMorphContinuity() {
    }

    public static void transfer(
            ChangedEntity previous,
            ChangedEntity replacement) {
        if (previous == replacement
                || !(replacement.level() instanceof ServerLevel level)
                || !hasPersonalContinuity(previous)) {
            return;
        }

        transferInternal(previous, replacement, level);
    }

    /**
     * Preserves identity for a source whose first personal history is the
     * absorption currently replacing its body.  These sources intentionally
     * have no pre-existing social profile, so the ordinary continuity gate
     * cannot recognize them yet.
     */
    public static void transferForced(
            ChangedEntity previous,
            ChangedEntity replacement) {
        if (previous == replacement
                || !(replacement.level() instanceof ServerLevel level)) {
            return;
        }

        transferInternal(previous, replacement, level);
    }

    private static void transferInternal(
            ChangedEntity previous,
            ChangedEntity replacement,
            ServerLevel level) {

        UUID previousId = previous.getUUID();
        UUID replacementId = replacement.getUUID();
        Set<UUID> bondedPlayers = LatexSocialMemory.bondedPlayerUuids(previous);
        if (!bondedPlayers.isEmpty()) {
            BondedCreatureLifecycle.untrack(previous);
        }

        CompoundTag source = previous.getPersistentData();
        CompoundTag target = replacement.getPersistentData();
        copyCompound(source, target, PERSONALITY);
        copyCompound(source, target, IDENTITY);
        copyCompound(source, target, SOCIAL);
        copyCompound(source, target, LIFE);
        copyCompound(source, target, CARGO);
        copyTag(source, target, LAST_PROVISION_SOURCE);
        copyTag(source, target, LAST_PROVISION_ITEM);
        copyTag(source, target, NEXT_HUNT);
        copyTag(source, target, FACILITY_COMMUNITY_AFFINITY);
        copyTag(source, target, FACILITY_WORK_CODE);
        copyTag(source, target, FACILITY_WORK_SECTION);
        copyTag(source, target, FACILITY_ORANGE_ROOM);
        copyTag(source, target, FACILITY_STORAGE_POS);
        InvoluntaryTransfurNegotiation.copySourceMarker(previous, replacement);
        if (source.hasUUID(COMMUNITY)) {
            target.putUUID(COMMUNITY, source.getUUID(COMMUNITY));
        }

        if (previous.hasCustomName()) {
            replacement.setCustomName(previous.getCustomName());
            replacement.setCustomNameVisible(previous.isCustomNameVisible());
        }
        if (previous.isPersistenceRequired()
                || hasStoredRelationship(source)
                || !bondedPlayers.isEmpty()) {
            replacement.setPersistenceRequired();
        }

        CreatureMorphAliasData.get(level.getServer())
                .record(previousId, replacementId);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            boolean related = hasStoredRelationshipWith(source, player.getUUID())
                    || bondedPlayers.contains(player.getUUID())
                    || LatexSocialMemory.isPetOwner(replacement, player);
            PlayerRelationshipSettings.replaceContactReference(
                    player, previousId, replacement, related);
            LatexSocialMemory.replacePlayerBondReference(
                    player, previousId, replacementId);
            BondedCreatureLifecycle.replaceReference(
                    player, previousId, replacementId);
            InvoluntaryTransfurNegotiation.replaceSourceReference(
                    player, previousId, replacementId);
        }

        CreaturePersonality.ensure(replacement);
        CreatureIdentity.ensure(replacement);
        CreatureLifeMemory.ensure(replacement);
        CreatureCommunityData.bind(replacement);
        if (!bondedPlayers.isEmpty()) {
            BondedCreatureLifecycle.track(replacement);
        }
    }

    private static boolean hasPersonalContinuity(ChangedEntity creature) {
        CompoundTag data = creature.getPersistentData();
        return hasStoredRelationship(data)
                || data.contains(LIFE, Tag.TAG_COMPOUND)
                || data.contains(CARGO, Tag.TAG_COMPOUND)
                || data.hasUUID(COMMUNITY)
                || data.contains(
                        InvoluntaryTransfurNegotiation.SOURCE_ROOT,
                        Tag.TAG_COMPOUND)
                || data.contains(SOCIAL, Tag.TAG_COMPOUND)
                        && (!data.getCompound(SOCIAL).getCompound("BondedPlayers").isEmpty()
                                || data.getCompound(SOCIAL).hasUUID("PetOwner"))
                || creature instanceof TamableLatexEntity nativePet
                        && nativePet.isTame();
    }

    private static boolean hasStoredRelationship(CompoundTag data) {
        if (!data.contains(PERSONALITY, Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag memories = data.getCompound(PERSONALITY)
                .getCompound("PlayerMemories");
        for (String key : memories.getAllKeys()) {
            if (memories.contains(key, Tag.TAG_COMPOUND)
                    && memories.getCompound(key).getBoolean("Relationship")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasStoredRelationshipWith(
            CompoundTag data,
            UUID playerId) {
        if (!data.contains(PERSONALITY, Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag memories = data.getCompound(PERSONALITY)
                .getCompound("PlayerMemories");
        String key = playerId.toString();
        return memories.contains(key, Tag.TAG_COMPOUND)
                && memories.getCompound(key).getBoolean("Relationship");
    }

    private static void copyCompound(
            CompoundTag source,
            CompoundTag target,
            String key) {
        if (source.contains(key, Tag.TAG_COMPOUND)) {
            target.put(key, source.getCompound(key).copy());
        }
    }

    private static void copyTag(
            CompoundTag source,
            CompoundTag target,
            String key) {
        Tag value = source.get(key);
        if (value != null) {
            target.put(key, value.copy());
        }
    }
}
