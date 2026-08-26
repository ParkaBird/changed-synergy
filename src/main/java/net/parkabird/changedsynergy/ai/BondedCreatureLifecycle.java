package net.parkabird.changedsynergy.ai;

import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.Optional;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.common.world.ForgeChunkManager;

/**
 * Keeps a bonded creature loaded, persistent and recoverable after its owner
 * respawns or changes dimension. The player-side location record also makes an
 * absent entity distinguishable from a merely unloaded one.
 */
public final class BondedCreatureLifecycle {
    private static final String PLAYER_LOCATIONS = "ChangedSynergyBondedCreatureLocations";
    private static final String DIMENSION = "Dimension";
    private static final String CHUNK_X = "ChunkX";
    private static final String CHUNK_Z = "ChunkZ";
    private static final String MISSING_CHECKS = "MissingChecks";
    private static final String TICKET_DIMENSION = "ChangedSynergyBondTicketDimension";
    private static final String TICKET_CHUNK_X = "ChangedSynergyBondTicketChunkX";
    private static final String TICKET_CHUNK_Z = "ChangedSynergyBondTicketChunkZ";
    private static final String RECOVERY_WELCOME_PREFIX = "ChangedSynergyRecoveryWelcome_";
    private static final int CONFIRMED_MISSING_CHECKS = 3;
    private static final int UNLOCATED_MISSING_CHECKS = 12;
    private static final double EMERGENCY_TELEPORT_DISTANCE_SQR = 64.0 * 64.0;
    private static final long RECOVERY_WELCOME_COOLDOWN = 6000L;

    private BondedCreatureLifecycle() {
    }

    /** Marks the creature persistent and moves its single non-ticking chunk ticket. */
    public static void track(ChangedEntity creature) {
        if (!lifecycleEnabled(creature)) {
            releaseTicketOnly(creature);
            return;
        }
        Set<UUID> owners = LatexSocialMemory.bondedPlayerUuids(creature);
        if (owners.isEmpty() || !(creature.level() instanceof ServerLevel level)) {
            return;
        }

        creature.setPersistenceRequired();
        CompoundTag persistent = creature.getPersistentData();
        ChunkPos current = creature.chunkPosition();
        String currentDimension = level.dimension().location().toString();

        if (persistent.contains(TICKET_DIMENSION, Tag.TAG_STRING)
                && persistent.contains(TICKET_CHUNK_X, Tag.TAG_INT)
                && persistent.contains(TICKET_CHUNK_Z, Tag.TAG_INT)) {
            String oldDimension = persistent.getString(TICKET_DIMENSION);
            int oldX = persistent.getInt(TICKET_CHUNK_X);
            int oldZ = persistent.getInt(TICKET_CHUNK_Z);
            if (!currentDimension.equals(oldDimension)
                    || current.x != oldX || current.z != oldZ) {
                ServerLevel oldLevel = level.getServer().getLevel(dimensionKey(oldDimension));
                if (oldLevel != null) {
                    ForgeChunkManager.forceChunk(
                            oldLevel, ChangedSynergyMod.MOD_ID, creature.getUUID(),
                            oldX, oldZ, false, false);
                }
            }
        }

        ForgeChunkManager.forceChunk(
                level, ChangedSynergyMod.MOD_ID, creature.getUUID(),
                current.x, current.z, true, false);
        persistent.putString(TICKET_DIMENSION, currentDimension);
        persistent.putInt(TICKET_CHUNK_X, current.x);
        persistent.putInt(TICKET_CHUNK_Z, current.z);

        for (UUID ownerUuid : owners) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
            if (owner != null) {
                rememberLocation(owner, creature.getUUID(), currentDimension, current.x, current.z);
            }
        }
    }

    /** Releases the ticket after the final bond ends or the creature dies. */
    public static void untrack(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel currentLevel)) {
            return;
        }
        CompoundTag persistent = creature.getPersistentData();
        String dimension = persistent.contains(TICKET_DIMENSION, Tag.TAG_STRING)
                ? persistent.getString(TICKET_DIMENSION)
                : currentLevel.dimension().location().toString();
        int chunkX = persistent.contains(TICKET_CHUNK_X, Tag.TAG_INT)
                ? persistent.getInt(TICKET_CHUNK_X) : creature.chunkPosition().x;
        int chunkZ = persistent.contains(TICKET_CHUNK_Z, Tag.TAG_INT)
                ? persistent.getInt(TICKET_CHUNK_Z) : creature.chunkPosition().z;
        ServerLevel ticketLevel = currentLevel.getServer().getLevel(dimensionKey(dimension));
        if (ticketLevel != null) {
            ForgeChunkManager.forceChunk(
                    ticketLevel, ChangedSynergyMod.MOD_ID, creature.getUUID(),
                    chunkX, chunkZ, false, false);
        }
        persistent.remove(TICKET_DIMENSION);
        persistent.remove(TICKET_CHUNK_X);
        persistent.remove(TICKET_CHUNK_Z);

        for (UUID ownerUuid : LatexSocialMemory.bondedPlayerUuids(creature)) {
            ServerPlayer owner = currentLevel.getServer().getPlayerList().getPlayer(ownerUuid);
            if (owner != null) {
                locations(owner).remove(creature.getUUID().toString());
            }
        }
    }

    /** Removes one stale player-side reference and its last known ticket. */
    public static void forget(ServerPlayer owner, UUID creatureUuid) {
        CompoundTag locations = locations(owner);
        String key = creatureUuid.toString();
        if (locations.contains(key, Tag.TAG_COMPOUND)) {
            CompoundTag location = locations.getCompound(key);
            releaseRememberedTicket(owner, creatureUuid, location);
            locations.remove(key);
        }
    }

    /** Re-keys the last-known location after Changed replaces a bonded entity. */
    public static void replaceReference(
            ServerPlayer owner,
            UUID previousId,
            UUID replacementId) {
        if (previousId.equals(replacementId)) {
            return;
        }
        CompoundTag stored = locations(owner);
        String previousKey = previousId.toString();
        String replacementKey = replacementId.toString();
        if (stored.contains(previousKey, Tag.TAG_COMPOUND)
                && !stored.contains(replacementKey, Tag.TAG_COMPOUND)) {
            stored.put(replacementKey, stored.getCompound(previousKey).copy());
        }
        stored.remove(previousKey);
    }

    /**
     * Loads the recorded chunk before declaring a creature missing. Three
     * confirmed checks are used so asynchronous entity loading cannot erase a
     * valid bond. Records without a known location get a longer grace period.
     */
    public static void audit(ServerPlayer owner) {
        if (!lifecycleEnabled(owner)) {
            return;
        }
        for (UUID creatureUuid : Set.copyOf(LatexSocialMemory.bondedCreatureUuids(owner))) {
            ChangedEntity creature = LatexSocialMemory.findLoadedBondedCreature(owner, creatureUuid);
            CompoundTag location = location(owner, creatureUuid);
            boolean hasLocation = hasCompleteLocation(location);

            if (creature == null && hasLocation) {
                ServerLevel recordedLevel = owner.server.getLevel(
                        dimensionKey(location.getString(DIMENSION)));
                if (recordedLevel != null) {
                    int chunkX = location.getInt(CHUNK_X);
                    int chunkZ = location.getInt(CHUNK_Z);
                    ForgeChunkManager.forceChunk(
                            recordedLevel, ChangedSynergyMod.MOD_ID, creatureUuid,
                            chunkX, chunkZ, true, false);
                    recordedLevel.getChunk(chunkX, chunkZ);
                    Entity loaded = recordedLevel.getEntity(creatureUuid);
                    if (loaded instanceof ChangedEntity changed && changed.isAlive()) {
                        creature = changed;
                    }
                }
            }

            if (creature == null || !LatexSocialMemory.isBonded(creature, owner)) {
                int missing = location.getInt(MISSING_CHECKS) + 1;
                location.putInt(MISSING_CHECKS, missing);
                int threshold = hasLocation
                        ? CONFIRMED_MISSING_CHECKS : UNLOCATED_MISSING_CHECKS;
                if (missing >= threshold) {
                    LatexSocialMemory.forgetMissingBond(owner, creatureUuid);
                    forget(owner, creatureUuid);
                }
                continue;
            }

            location.remove(MISSING_CHECKS);
            track(creature);
            bringToOwnerIfLost(creature, owner);
        }
    }

    /** Copies bond location data to the replacement player entity after death. */
    public static void copyPlayerData(ServerPlayer original, ServerPlayer clone) {
        CompoundTag source = playerPersistedData(original);
        CompoundTag target = playerPersistedData(clone);
        if (source.contains(PLAYER_LOCATIONS, Tag.TAG_COMPOUND)) {
            target.put(PLAYER_LOCATIONS, source.getCompound(PLAYER_LOCATIONS).copy());
        }
    }

    private static void bringToOwnerIfLost(ChangedEntity creature, ServerPlayer owner) {
        if (!LatexSocialMemory.isPetOwner(creature, owner)
                || !LatexSocialMemory.isFollowingOwner(creature)
                || !owner.isAlive() || owner.isSpectator()
                || BondedSuitService.isSuitingOwner(creature, owner)
                || creature.isPassenger() || creature.isLeashed()) {
            return;
        }
        GrabEntityAbilityInstance ability = BondedSuitService.ability(creature);
        if (ability != null && ability.grabbedEntity != null) {
            return;
        }

        boolean otherDimension = creature.level() != owner.level();
        if (!otherDimension
                && creature.distanceToSqr(owner) <= EMERGENCY_TELEPORT_DISTANCE_SQR) {
            return;
        }

        ServerLevel destination = owner.serverLevel();
        Optional<Vec3> safeLanding =
                BondedTeleportSafety.findSafeLanding(destination, creature, owner);
        if (safeLanding.isEmpty()) {
            // The owner may be high in the air or in a space too small for the
            // creature. Keep the companion where it is and retry after the
            // owner reaches a genuinely safe area.
            return;
        }
        Vec3 landing = safeLanding.get();
        ChangedEntity arrived = creature;
        if (otherDimension) {
            Entity moved = creature.changeDimension(destination, new ITeleporter() {
                @Override
                public PortalInfo getPortalInfo(
                        Entity entity,
                        ServerLevel destinationLevel,
                        Function<ServerLevel, PortalInfo> defaultPortalInfo) {
                    return new PortalInfo(
                            landing, Vec3.ZERO, entity.getYRot(), entity.getXRot());
                }
            });
            if (moved instanceof ChangedEntity changed) {
                arrived = changed;
            } else {
                return;
            }
        } else {
            creature.teleportTo(landing.x, landing.y, landing.z);
        }
        BondedTeleportSafety.settleAfterTeleport(arrived);
        arrived.setTarget(null);
        track(arrived);
        greetAfterRecoveryTeleport(arrived, owner);
    }

    /** Stops forced loading while retaining the remembered location for re-enable. */
    private static void releaseTicketOnly(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel currentLevel)) {
            return;
        }
        CompoundTag persistent = creature.getPersistentData();
        if (!persistent.contains(TICKET_DIMENSION, Tag.TAG_STRING)
                || !persistent.contains(TICKET_CHUNK_X, Tag.TAG_INT)
                || !persistent.contains(TICKET_CHUNK_Z, Tag.TAG_INT)) {
            return;
        }
        ServerLevel ticketLevel = currentLevel.getServer().getLevel(
                dimensionKey(persistent.getString(TICKET_DIMENSION)));
        if (ticketLevel != null) {
            ForgeChunkManager.forceChunk(
                    ticketLevel,
                    ChangedSynergyMod.MOD_ID,
                    creature.getUUID(),
                    persistent.getInt(TICKET_CHUNK_X),
                    persistent.getInt(TICKET_CHUNK_Z),
                    false,
                    false);
        }
        persistent.remove(TICKET_DIMENSION);
        persistent.remove(TICKET_CHUNK_X);
        persistent.remove(TICKET_CHUNK_Z);
    }

    private static boolean lifecycleEnabled(net.minecraft.world.entity.Entity entity) {
        return ChangedSynergyGameRules.enabled(
                entity.level(), ChangedSynergyGameRules.BOND_SYSTEM)
                && ChangedSynergyGameRules.enabled(
                        entity.level(), ChangedSynergyGameRules.CREATURE_LIFE);
    }

    /** A bonded welcome is reserved for a real reunion after recovery teleporting. */
    public static void greetAfterRecoveryTeleport(
            ChangedEntity creature,
            ServerPlayer owner) {
        if (!creature.isAlive() || !LatexSocialMemory.isBonded(creature, owner)) {
            return;
        }
        long now = creature.level().getGameTime();
        String cooldownKey = RECOVERY_WELCOME_PREFIX + owner.getStringUUID();
        if (creature.getPersistentData().getLong(cooldownKey) > now) {
            return;
        }
        creature.getPersistentData().putLong(
                cooldownKey, now + RECOVERY_WELCOME_COOLDOWN);
        if (!ProcessTransfur.isPlayerTransfurred(owner)) {
            NpcDialogue.trigger(creature, owner, Cue.FORMER_BOND_WELCOME);
        } else if (LatexSocialMemory.isBondedOwnerInOtherForm(creature, owner)) {
            NpcDialogue.trigger(creature, owner, Cue.BOND_NEW_FORM_WELCOME);
        }
    }

    private static void rememberLocation(
            ServerPlayer owner,
            UUID creatureUuid,
            String dimension,
            int chunkX,
            int chunkZ) {
        CompoundTag location = location(owner, creatureUuid);
        location.putString(DIMENSION, dimension);
        location.putInt(CHUNK_X, chunkX);
        location.putInt(CHUNK_Z, chunkZ);
        location.remove(MISSING_CHECKS);
    }

    private static void releaseRememberedTicket(
            ServerPlayer owner,
            UUID creatureUuid,
            CompoundTag location) {
        if (!hasCompleteLocation(location)) {
            return;
        }
        ServerLevel level = owner.server.getLevel(dimensionKey(location.getString(DIMENSION)));
        if (level != null) {
            ForgeChunkManager.forceChunk(
                    level, ChangedSynergyMod.MOD_ID, creatureUuid,
                    location.getInt(CHUNK_X), location.getInt(CHUNK_Z), false, false);
        }
    }

    private static boolean hasCompleteLocation(CompoundTag location) {
        return location.contains(DIMENSION, Tag.TAG_STRING)
                && location.contains(CHUNK_X, Tag.TAG_INT)
                && location.contains(CHUNK_Z, Tag.TAG_INT);
    }

    private static CompoundTag location(ServerPlayer owner, UUID creatureUuid) {
        CompoundTag locations = locations(owner);
        String key = creatureUuid.toString();
        if (!locations.contains(key, Tag.TAG_COMPOUND)) {
            locations.put(key, new CompoundTag());
        }
        return locations.getCompound(key);
    }

    private static CompoundTag locations(ServerPlayer owner) {
        CompoundTag persisted = playerPersistedData(owner);
        if (!persisted.contains(PLAYER_LOCATIONS, Tag.TAG_COMPOUND)) {
            persisted.put(PLAYER_LOCATIONS, new CompoundTag());
        }
        return persisted.getCompound(PLAYER_LOCATIONS);
    }

    private static CompoundTag playerPersistedData(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static ResourceKey<net.minecraft.world.level.Level> dimensionKey(String value) {
        ResourceLocation location = ResourceLocation.tryParse(value);
        if (location == null) {
            location = net.minecraft.world.level.Level.OVERWORLD.location();
        }
        return ResourceKey.create(Registries.DIMENSION, location);
    }
}
