package net.parkabird.changedsynergy.ai;

import java.util.Locale;
import java.util.Optional;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.parkabird.changedsynergy.dialogue.LatexTerritory;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/**
 * Stable, versioned life data used by role-driven community work.
 *
 * <p>Legacy anchor and schedule fields are still read so old worlds migrate
 * safely, but they no longer drive creature movement. Communities may own a
 * cache; individual creatures no longer own an activity centre or timetable.</p>
 */
public final class CreatureLifeMemory {
    private static final String ROOT = "ChangedSynergyLife";
    private static final String VERSION = "Version";
    private static final String ANCHOR_KIND = "AnchorKind";
    private static final String ANCHOR_DIMENSION = "AnchorDimension";
    private static final String ANCHOR_POS = "AnchorPos";
    private static final String ROLE = "GroupRole";
    private static final String SCHEDULE_OFFSET = "ScheduleOffset";
    private static final String ROUTINE = "Routine";
    private static final String LAST_ROUTINE = "LastRoutine";
    private static final String ROUTINE_SINCE = "RoutineSince";
    private static final String NEXT_DECISION = "NextDecision";
    private static final String ROLE_STAT_0 = "RoleStat0";
    private static final String ROLE_STAT_1 = "RoleStat1";
    private static final String ROLE_STAT_2 = "RoleStat2";
    private static final int CURRENT_VERSION = 2;
    private static final double CONSENSUS_REFRESH_DISTANCE_SQR = 48.0D * 48.0D;

    private CreatureLifeMemory() {
    }

    public enum AnchorKind {
        ACTIVITY_RANGE("activity_range"),
        PATROL_POST("patrol_post"),
        FACILITY_ROOM("facility_room"),
        WATER_ROUTE("water_route"),
        PERCH("perch"),
        CONSENSUS("consensus");

        private final String id;

        AnchorKind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static AnchorKind fromId(String id) {
            for (AnchorKind value : values()) {
                if (value.id.equalsIgnoreCase(id)) {
                    return value;
                }
            }
            return ACTIVITY_RANGE;
        }
    }

    public enum GroupRole {
        SCOUT("scout"),
        GUARD("guard"),
        PROVISIONER("provisioner"),
        YOUNGSTER("youngster");

        private final String id;

        GroupRole(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static GroupRole fromId(String id) {
            if (id == null) {
                return null;
            }
            String normalized = id.trim().toLowerCase(Locale.ROOT);
            for (GroupRole value : values()) {
                if (value.id.equals(normalized)) {
                    return value;
                }
            }
            // World-save migration from the former nine-role prototype.
            return switch (normalized) {
                case "lookout", "coordinator", "wanderer" -> SCOUT;
                case "forager", "caretaker", "courier" -> PROVISIONER;
                default -> null;
            };
        }
    }

    public enum RoutineState {
        IDLE("idle"),
        SCOUTING("scouting"),
        GUARDING("guarding"),
        GATHERING("gathering"),
        FISHING("fishing"),
        MINING("mining"),
        DELIVERING("delivering"),
        TENDING("tending"),
        PLAYING("playing");

        private final String id;

        RoutineState(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static RoutineState fromId(String id) {
            for (RoutineState value : values()) {
                if (value.id.equalsIgnoreCase(id)) {
                    return value;
                }
            }
            if (id == null) {
                return IDLE;
            }
            return switch (id.toLowerCase(Locale.ROOT)) {
                case "forage" -> GATHERING;
                case "return_center" -> DELIVERING;
                case "socialize" -> TENDING;
                case "patrol", "watch" -> GUARDING;
                case "roam", "consensus" -> SCOUTING;
                default -> IDLE;
            };
        }
    }

    public record RoleStats(int first, int second, int third) {
    }

    public record Snapshot(
            AnchorKind anchorKind,
            String dimension,
            BlockPos anchor,
            GroupRole role,
            RoutineState routine,
            RoutineState lastRoutine,
            long routineSince,
            long nextDecision) {
    }

    public static boolean enabled(ChangedEntity mob) {
        return CreatureSocialProfile.allowsSynergySystems(mob)
                && ChangedSynergyGameRules.enabled(
                        mob.level(), ChangedSynergyGameRules.NPC_AI)
                && ChangedSynergyGameRules.enabled(
                        mob.level(), ChangedSynergyGameRules.CREATURE_LIFE);
    }

    public static void ensure(ChangedEntity mob) {
        if (mob.level().isClientSide
                || !LatexSocialMemory.isSocialLatex(mob)
                || !CreatureSocialProfile.allowsSynergySystems(mob)) {
            return;
        }
        CompoundTag life = data(mob);
        boolean fresh = !life.contains(VERSION, Tag.TAG_INT);
        HunterFaction faction = HunterFaction.of(mob);
        AnchorKind expected = initialAnchorKind(mob, faction);

        if (fresh || !life.contains(ROLE, Tag.TAG_STRING)) {
            life.putString(ROLE, assignRole(mob, faction).id());
        }
        // SCHEDULE_OFFSET is intentionally no longer created or consulted.
        if (fresh || !life.contains(ANCHOR_POS, Tag.TAG_LONG)
                || !life.contains(ANCHOR_DIMENSION, Tag.TAG_STRING)) {
            writeAnchor(mob, life, expected, initialAnchor(mob, expected));
        } else if (faction == HunterFaction.WHITE
                && AnchorKind.fromId(life.getString(ANCHOR_KIND)) != AnchorKind.CONSENSUS) {
            // Migration from an ordinary fixed home to a mobile local consensus.
            writeAnchor(mob, life, AnchorKind.CONSENSUS,
                    consensusFocus(mob.blockPosition()));
        }
        if (!life.contains(ROUTINE, Tag.TAG_STRING)) {
            life.putString(ROUTINE, RoutineState.IDLE.id());
        }
        if (!life.contains(LAST_ROUTINE, Tag.TAG_STRING)) {
            life.putString(LAST_ROUTINE, RoutineState.IDLE.id());
        }
        if (!life.contains(NEXT_DECISION, Tag.TAG_LONG)) {
            life.putLong(NEXT_DECISION,
                    mob.level().getGameTime() + 40L + mob.getRandom().nextInt(161));
        }
        life.putInt(VERSION, CURRENT_VERSION);
    }

    public static Snapshot snapshot(ChangedEntity mob) {
        ensure(mob);
        CompoundTag life = data(mob);
        return new Snapshot(
                AnchorKind.fromId(life.getString(ANCHOR_KIND)),
                life.getString(ANCHOR_DIMENSION),
                BlockPos.of(life.getLong(ANCHOR_POS)),
                role(mob),
                RoutineState.fromId(life.getString(ROUTINE)),
                RoutineState.fromId(life.getString(LAST_ROUTINE)),
                life.getLong(ROUTINE_SINCE),
                life.getLong(NEXT_DECISION));
    }

    public static GroupRole role(ChangedEntity mob) {
        ensure(mob);
        CompoundTag life = data(mob);
        GroupRole role = GroupRole.fromId(life.getString(ROLE));
        if (role == null) {
            role = assignRole(mob, HunterFaction.of(mob));
        }
        if (!role.id().equals(life.getString(ROLE))) {
            life.putString(ROLE, role.id());
        }
        return role;
    }

    public static void setRole(ChangedEntity mob, GroupRole role) {
        ensure(mob);
        CompoundTag life = data(mob);
        GroupRole previous = GroupRole.fromId(life.getString(ROLE));
        life.putString(ROLE, role.id());
        if (previous != role) {
            life.putInt(ROLE_STAT_0, 0);
            life.putInt(ROLE_STAT_1, 0);
            life.putInt(ROLE_STAT_2, 0);
        }
    }

    public static RoleStats roleStats(ChangedEntity mob) {
        ensure(mob);
        CompoundTag life = data(mob);
        return new RoleStats(
                life.getInt(ROLE_STAT_0),
                life.getInt(ROLE_STAT_1),
                life.getInt(ROLE_STAT_2));
    }

    public static void incrementRoleStat(ChangedEntity mob, int index) {
        if (index < 0 || index > 2) {
            return;
        }
        CompoundTag life = data(mob);
        String key = index == 0 ? ROLE_STAT_0
                : index == 1 ? ROLE_STAT_1 : ROLE_STAT_2;
        life.putInt(key, Math.min(Integer.MAX_VALUE - 1, life.getInt(key) + 1));
    }

    public static Optional<BlockPos> centerInCurrentDimension(ChangedEntity mob) {
        ensure(mob);
        CompoundTag life = data(mob);
        if (!life.contains(ANCHOR_POS, Tag.TAG_LONG)
                || !mob.level().dimension().location().toString()
                        .equals(life.getString(ANCHOR_DIMENSION))) {
            return Optional.empty();
        }
        return Optional.of(BlockPos.of(life.getLong(ANCHOR_POS)));
    }

    public static AnchorKind anchorKind(ChangedEntity mob) {
        ensure(mob);
        return AnchorKind.fromId(data(mob).getString(ANCHOR_KIND));
    }

    public static void setCenterHere(ChangedEntity mob) {
        ensure(mob);
        HunterFaction faction = HunterFaction.of(mob);
        AnchorKind kind = initialAnchorKind(mob, faction);
        writeAnchor(mob, data(mob), kind, initialAnchor(mob, kind));
        scheduleNextDecision(mob, mob.level().getGameTime() + 20L);
    }

    /** Applies a shared community centre without turning white consensus into a home. */
    public static void applyCommunityCenter(ChangedEntity mob, BlockPos center) {
        ensure(mob);
        if (HunterFaction.of(mob) == HunterFaction.WHITE) {
            return;
        }
        CompoundTag life = data(mob);
        BlockPos current = life.contains(ANCHOR_POS, Tag.TAG_LONG)
                ? BlockPos.of(life.getLong(ANCHOR_POS)) : mob.blockPosition();
        if (current.equals(center)
                && mob.level().dimension().location().toString()
                        .equals(life.getString(ANCHOR_DIMENSION))) {
            return;
        }
        AnchorKind kind = AnchorKind.fromId(life.getString(ANCHOR_KIND));
        if (kind == AnchorKind.CONSENSUS) {
            kind = initialAnchorKind(mob, HunterFaction.of(mob));
        }
        writeAnchor(mob, life, kind, center);
        scheduleNextDecision(mob, Math.min(
                nextDecisionTick(mob), mob.level().getGameTime() + 20L));
    }

    public static void reset(ChangedEntity mob) {
        mob.getPersistentData().remove(ROOT);
        ensure(mob);
    }

    public static RoutineState routine(ChangedEntity mob) {
        ensure(mob);
        return RoutineState.fromId(data(mob).getString(ROUTINE));
    }

    public static void beginRoutine(
            ChangedEntity mob,
            RoutineState state,
            long now) {
        CompoundTag life = data(mob);
        life.putString(ROUTINE, state.id());
        life.putLong(ROUTINE_SINCE, now);
    }

    public static void finishRoutine(ChangedEntity mob, long nextDecision) {
        CompoundTag life = data(mob);
        RoutineState current = RoutineState.fromId(life.getString(ROUTINE));
        if (current != RoutineState.IDLE) {
            life.putString(LAST_ROUTINE, current.id());
        }
        life.putString(ROUTINE, RoutineState.IDLE.id());
        life.putLong(NEXT_DECISION, nextDecision);
    }

    public static long nextDecisionTick(ChangedEntity mob) {
        ensure(mob);
        return data(mob).getLong(NEXT_DECISION);
    }

    public static void scheduleNextDecision(ChangedEntity mob, long tick) {
        data(mob).putLong(NEXT_DECISION, tick);
    }

    /** Retained as a no-op compatibility API for pre-compression callers. */
    public static int scheduleOffset(ChangedEntity mob) {
        return 0;
    }

    /** Keeps a white-latex focus local without turning it into a return-home point. */
    public static void refreshConsensusFocus(ChangedEntity mob) {
        if (HunterFaction.of(mob) != HunterFaction.WHITE) {
            return;
        }
        CompoundTag life = data(mob);
        BlockPos old = life.contains(ANCHOR_POS, Tag.TAG_LONG)
                ? BlockPos.of(life.getLong(ANCHOR_POS)) : mob.blockPosition();
        boolean otherDimension = !mob.level().dimension().location().toString()
                .equals(life.getString(ANCHOR_DIMENSION));
        if (otherDimension || old.distSqr(mob.blockPosition())
                > CONSENSUS_REFRESH_DISTANCE_SQR) {
            writeAnchor(mob, life, AnchorKind.CONSENSUS,
                    consensusFocus(mob.blockPosition()));
        }
    }

    private static AnchorKind initialAnchorKind(
            ChangedEntity mob,
            HunterFaction faction) {
        if (faction == HunterFaction.WHITE) {
            return AnchorKind.CONSENSUS;
        }
        if (mob.level() instanceof ServerLevel level
                && LatexTerritory.facilityFactionAt(level, mob.blockPosition()) != null) {
            return AnchorKind.FACILITY_ROOM;
        }
        return switch (HunterArchetype.of(mob)) {
            case AQUATIC -> AnchorKind.WATER_ROUTE;
            case AVIAN -> AnchorKind.PERCH;
            case SOLDIER, ROYAL -> AnchorKind.PATROL_POST;
            default -> AnchorKind.ACTIVITY_RANGE;
        };
    }

    private static BlockPos initialAnchor(ChangedEntity mob, AnchorKind kind) {
        return kind == AnchorKind.CONSENSUS
                ? consensusFocus(mob.blockPosition()) : mob.blockPosition();
    }

    private static BlockPos consensusFocus(BlockPos position) {
        // A 32-block cell is a local meeting focus, not ownership or a nest.
        int x = Math.floorDiv(position.getX(), 32) * 32 + 16;
        int z = Math.floorDiv(position.getZ(), 32) * 32 + 16;
        return new BlockPos(x, position.getY(), z);
    }

    private static void writeAnchor(
            ChangedEntity mob,
            CompoundTag life,
            AnchorKind kind,
            BlockPos position) {
        life.putString(ANCHOR_KIND, kind.id());
        life.putString(ANCHOR_DIMENSION,
                mob.level().dimension().location().toString());
        life.putLong(ANCHOR_POS, position.asLong());
    }

    private static GroupRole assignRole(
            ChangedEntity mob,
            HunterFaction faction) {
        if (CreatureSocialProfile.isJuvenile(mob)) {
            return GroupRole.YOUNGSTER;
        }
        CreaturePersonality.Trait trait = CreaturePersonality.dominantTrait(mob);
        int variation = stableVariation(mob, 3);

        if (faction == HunterFaction.WHITE) {
            return switch (trait) {
                case PROTECTIVE -> choose(variation,
                        GroupRole.GUARD, GroupRole.PROVISIONER, GroupRole.SCOUT);
                case CALM, POLITE -> choose(variation,
                        GroupRole.PROVISIONER, GroupRole.SCOUT, GroupRole.GUARD);
                case CURIOUS, PLAYFUL -> choose(variation,
                        GroupRole.SCOUT, GroupRole.PROVISIONER, GroupRole.GUARD);
                case CAUTIOUS, SENSITIVE -> choose(variation,
                        GroupRole.SCOUT, GroupRole.PROVISIONER, GroupRole.GUARD);
                default -> choose(variation,
                        GroupRole.PROVISIONER, GroupRole.GUARD, GroupRole.SCOUT);
            };
        }

        HunterArchetype archetype = HunterArchetype.of(mob);
        if (archetype == HunterArchetype.SOLDIER
                || archetype == HunterArchetype.ROYAL) {
            return choose(variation,
                    GroupRole.GUARD, GroupRole.SCOUT, GroupRole.PROVISIONER);
        }
        if (archetype == HunterArchetype.AQUATIC) {
            return choose(variation,
                    GroupRole.PROVISIONER, GroupRole.SCOUT, GroupRole.GUARD);
        }
        if (archetype == HunterArchetype.AVIAN) {
            return choose(variation,
                    GroupRole.SCOUT, GroupRole.GUARD, GroupRole.PROVISIONER);
        }
        if (LatexSocialMemory.isOrganic(mob)) {
            return choose(variation,
                    GroupRole.PROVISIONER, GroupRole.SCOUT, GroupRole.GUARD);
        }
        return switch (trait) {
            case CURIOUS -> choose(variation,
                    GroupRole.SCOUT, GroupRole.PROVISIONER, GroupRole.GUARD);
            case CAUTIOUS, SENSITIVE -> choose(variation,
                    GroupRole.SCOUT, GroupRole.PROVISIONER, GroupRole.GUARD);
            case PROTECTIVE -> choose(variation,
                    GroupRole.GUARD, GroupRole.PROVISIONER, GroupRole.SCOUT);
            case CALM, POLITE -> choose(variation,
                    GroupRole.PROVISIONER, GroupRole.SCOUT, GroupRole.GUARD);
            case COMPETITIVE, SHOW_OFF -> choose(variation,
                    GroupRole.GUARD, GroupRole.SCOUT, GroupRole.PROVISIONER);
            case PLAYFUL -> choose(variation,
                    GroupRole.SCOUT, GroupRole.PROVISIONER, GroupRole.GUARD);
        };
    }

    private static GroupRole choose(
            int variation,
            GroupRole first,
            GroupRole second,
            GroupRole third) {
        return variation == 0 ? first : variation == 1 ? second : third;
    }

    private static int stableVariation(ChangedEntity mob, int bound) {
        long value = mob.getUUID().getMostSignificantBits()
                ^ Long.rotateLeft(mob.getUUID().getLeastSignificantBits(), 21);
        value ^= value >>> 33;
        return (int)Math.floorMod(value, bound);
    }

    private static CompoundTag data(ChangedEntity mob) {
        CompoundTag persistent = mob.getPersistentData();
        if (!persistent.contains(ROOT, Tag.TAG_COMPOUND)) {
            persistent.put(ROOT, new CompoundTag());
        }
        return persistent.getCompound(ROOT);
    }
}
