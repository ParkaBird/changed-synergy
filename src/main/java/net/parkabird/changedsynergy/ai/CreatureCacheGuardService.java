package net.parkabird.changedsynergy.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory.GroupRole;
import net.parkabird.changedsynergy.advancement.SynergyAdvancements;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/** Reputation-gated, temporary defence of a real community cache. */
public final class CreatureCacheGuardService {
    private static final String PLAYER_COOLDOWNS =
            "ChangedSynergyCacheAccessCooldowns";
    private static final String ACCESS_WINDOWS =
            "ChangedSynergyCacheAccessWindows";
    private static final long REPUTATION_COOLDOWN = 1200L;
    private static final long ACCESS_WINDOW_TICKS = 600L;
    private static final int TOLERATED_ACCESS_COUNT = 2;
    private static final int CACHE_DESTRUCTION_REPUTATION_COST = -10;
    private static final int MAX_GUARDS_PER_CACHE = 3;
    private static final double GUARD_SEARCH_RADIUS = 28.0D;
    private static final double GUARD_PATROL_RADIUS = 96.0D;
    private static final double DEFENSE_RADIUS_SQR = 18.0D * 18.0D;
    private static final Map<UUID, Assignment> ASSIGNMENTS = new HashMap<>();

    private CreatureCacheGuardService() {
    }

    public static boolean handleCacheAccess(
            ServerLevel level,
            ServerPlayer player,
            BlockPos position) {
        if (player.isCreative() || player.isSpectator()
                || !ChangedSynergyGameRules.enabled(
                        level, ChangedSynergyGameRules.FACTION_REPUTATION)) {
            return true;
        }
        Optional<CreatureCommunityData.Snapshot> community =
                CreatureCommunityData.snapshotAtCache(level, position);
        if (community.isEmpty()) {
            return true;
        }
        List<ChangedEntity> members = membersAtCache(level, player, position);
        if (members.isEmpty()) {
            FactionReputation.Standing standing =
                    FactionReputation.Standing.forScore(
                            FactionReputation.scoreAt(
                                    community.get().faction(), player,
                                    level, position));
            if (standing == FactionReputation.Standing.ALLIED) {
                return true;
            }
            player.displayClientMessage(Component.translatable(
                    "message.changed_synergy.cache.trade_required"), true);
            return false;
        }
        ChangedEntity witness = members.get(0);
        FactionReputation.Standing standing =
                FactionReputation.standing(witness, player);
        if (standing == FactionReputation.Standing.ALLIED) {
            if (recordAccess(witness, player, level.getGameTime()) == 1) {
                NpcDialogue.trigger(witness, player, Cue.CACHE_ALLIED_ACCESS);
            }
            return true;
        }
        if (standing == FactionReputation.Standing.RESPECTED) {
            int accessCount = recordAccess(
                    witness, player, level.getGameTime());
            if (accessCount == 1) {
                NpcDialogue.trigger(witness, player, Cue.CACHE_TOLERATED);
                return false;
            }
            if (accessCount == TOLERATED_ACCESS_COUNT) {
                NpcDialogue.trigger(witness, player, Cue.CACHE_FINAL_WARNING);
                return false;
            }
            if (accessCount == TOLERATED_ACCESS_COUNT + 1) {
                NpcDialogue.trigger(witness, player, Cue.CACHE_OVERUSED);
                applyReputationCost(witness, player, level.getGameTime(), -1);
            }
            attackIntruder(members, witness, player, position);
            return false;
        }

        SynergyAdvancements.grant(
                player, SynergyAdvancements.MYSTERIOUS_FORAGING_SPOT);
        applyReputationCost(witness, player, level.getGameTime(), -2);
        NpcDialogue.trigger(witness, player, Cue.CACHE_INTRUSION);
        attackIntruder(members, witness, player, position);
        return false;
    }

    /** Breaking a community cache is an immediate offence at every standing. */
    public static void handleCacheDestroyed(
            ServerLevel level,
            ServerPlayer player,
            BlockPos position) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        List<ChangedEntity> members = membersAtCache(level, player, position);
        if (members.isEmpty()) {
            return;
        }
        ChangedEntity witness = members.get(0);
        FactionReputation.Standing standing =
                FactionReputation.standing(witness, player);
        FactionReputation.adjust(
                witness, player, CACHE_DESTRUCTION_REPUTATION_COST);
        boolean trusted = standing == FactionReputation.Standing.RESPECTED
                || standing == FactionReputation.Standing.ALLIED;
        NpcDialogue.trigger(
                witness,
                player,
                trusted ? Cue.CACHE_DESTROYED_TRUSTED : Cue.CACHE_DESTROYED);
        attackIntruder(members, witness, player, position);
    }

    private static List<ChangedEntity> membersAtCache(
            ServerLevel level,
            ServerPlayer player,
            BlockPos position) {
        return level.getEntitiesOfClass(
                        ChangedEntity.class,
                        new AABB(position).inflate(GUARD_SEARCH_RADIUS, 10.0D,
                                GUARD_SEARCH_RADIUS),
                        creature -> creature.isAlive()
                                && !creature.isNoAi()
                                && LatexSocialMemory.isSocialLatex(creature)
                                && (CreatureSettlementService.cachePosition(creature)
                                                .filter(position::equals).isPresent()
                                        || CreatureLifeMemory.role(creature)
                                                        == GroupRole.GUARD
                                                && patrolCache(creature)
                                                        .filter(position::equals)
                                                        .isPresent()))
                .stream()
                .sorted(Comparator.comparingDouble(player::distanceToSqr))
                .toList();
    }

    private static void attackIntruder(
            List<ChangedEntity> members,
            ChangedEntity witness,
            ServerPlayer player,
            BlockPos position) {
        CreatureCacheGuardService.ensureGuardPresence(witness, position);
        ServerLevel level = (ServerLevel) witness.level();
        List<ChangedEntity> stationed = stationedGuards(level, position);
        List<ChangedEntity> guards = stationed.stream()
                .filter(member -> assignment(member).isEmpty())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (guards.isEmpty() && stationed.size() < MAX_GUARDS_PER_CACHE) {
            members.stream()
                    .filter(member -> CreatureLifeMemory.role(member)
                            != GroupRole.YOUNGSTER)
                    .filter(member -> CreatureLifeMemory.role(member)
                            != GroupRole.GUARD)
                    .filter(member -> assignment(member).isEmpty())
                    .filter(member -> !LatexSocialMemory.hasActiveBond(member))
                    .filter(member -> LatexSocialMemory.petOwnerUuid(member).isEmpty())
                    .min(Comparator.comparingDouble(player::distanceToSqr))
                    .ifPresent(member -> {
                        CreatureLifeMemory.setRole(member, GroupRole.GUARD);
                        if (patrolCache(member)
                                .filter(position::equals).isPresent()) {
                            guards.add(member);
                        }
                    });
        }
        for (ChangedEntity guard : guards) {
            begin(guard, player, position);
        }
    }

    public static void ensureGuardPresence(
            ChangedEntity member,
            BlockPos cache) {
        if (!(member.level() instanceof ServerLevel level)) {
            return;
        }
        if (!stationedGuards(level, cache).isEmpty()) {
            return;
        }
        List<ChangedEntity> peers = level.getEntitiesOfClass(
                ChangedEntity.class,
                new AABB(cache).inflate(22.0D, 9.0D, 22.0D),
                candidate -> candidate.isAlive()
                        && !candidate.isNoAi()
                        && LatexSocialMemory.isSocialLatex(candidate)
                        && CreatureCommunityData.sameCommunity(member, candidate)
                        && CreatureLifeMemory.role(candidate) != GroupRole.YOUNGSTER
                        && !LatexSocialMemory.hasActiveBond(candidate)
                        && LatexSocialMemory.petOwnerUuid(candidate).isEmpty());
        peers.stream()
                .filter(candidate ->
                        CreatureLifeMemory.role(candidate) != GroupRole.GUARD)
                .filter(candidate -> candidate != member)
                .min(Comparator.comparingDouble(candidate ->
                        candidate.distanceToSqr(VecUtil.center(cache))))
                .ifPresent(candidate ->
                        CreatureLifeMemory.setRole(candidate, GroupRole.GUARD));
    }

    /** At most three stable guard assignments are allowed for one cache. */
    private static List<ChangedEntity> stationedGuards(
            ServerLevel level,
            BlockPos cache) {
        return level.getEntitiesOfClass(
                        ChangedEntity.class,
                        new AABB(cache).inflate(GUARD_PATROL_RADIUS),
                        candidate -> eligibleCacheGuard(candidate)
                                && patrolCache(candidate)
                                        .filter(cache::equals).isPresent())
                .stream()
                .sorted(Comparator.comparing(ChangedEntity::getUUID))
                .limit(MAX_GUARDS_PER_CACHE)
                .toList();
    }

    public static Optional<Assignment> assignment(ChangedEntity guard) {
        Assignment assignment = ASSIGNMENTS.get(guard.getUUID());
        if (assignment == null) {
            return Optional.empty();
        }
        if (!guard.isAlive() || guard.isRemoved()) {
            ASSIGNMENTS.remove(guard.getUUID());
            return Optional.empty();
        }
        return Optional.of(assignment);
    }

    /** The nearest loaded cache belonging to this guard's faction branch. */
    public static Optional<BlockPos> patrolCache(ChangedEntity guard) {
        if (!eligibleCacheGuard(guard)) {
            return Optional.empty();
        }
        Optional<BlockPos> nearest = uncappedPatrolCache(guard);
        if (nearest.isEmpty() || !(guard.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        BlockPos cache = nearest.get();
        List<ChangedEntity> candidates = level.getEntitiesOfClass(
                        ChangedEntity.class,
                        new AABB(cache).inflate(GUARD_PATROL_RADIUS),
                        candidate -> eligibleCacheGuard(candidate)
                                && uncappedPatrolCache(candidate)
                                        .filter(cache::equals).isPresent())
                .stream()
                .sorted(Comparator.comparing(ChangedEntity::getUUID))
                .limit(MAX_GUARDS_PER_CACHE)
                .toList();
        return candidates.contains(guard) ? nearest : Optional.empty();
    }

    private static Optional<BlockPos> uncappedPatrolCache(ChangedEntity guard) {
        return CreatureSettlementService.nearestCompatibleCache(
                guard, GUARD_PATROL_RADIUS);
    }

    private static boolean eligibleCacheGuard(ChangedEntity guard) {
        return guard.isAlive()
                && !guard.isNoAi()
                && CreatureLifeMemory.role(guard) == GroupRole.GUARD
                && LatexSocialMemory.isSocialLatex(guard)
                && !LatexSocialMemory.hasActiveBond(guard)
                && LatexSocialMemory.petOwnerUuid(guard).isEmpty();
    }

    public static void begin(
            ChangedEntity guard,
            ServerPlayer intruder,
            BlockPos cache) {
        BlockPos current = guard.blockPosition();
        BlockPos returnPost = current.distSqr(cache) <= 14.0D * 14.0D
                ? current.immutable() : cache.immutable();
        ASSIGNMENTS.put(guard.getUUID(), new Assignment(
                intruder.getUUID(), cache.immutable(),
                returnPost));
        HuntMemory.clear(guard);
        guard.getNavigation().stop();
        guard.setAggressive(true);
        guard.setTarget(intruder);
    }

    /**
     * Keeps the cache leash authoritative while Changed's own melee goal owns
     * pursuit, path speed, attack reach and attack timing.
     */
    public static void tick(ChangedEntity guard) {
        Assignment active = assignment(guard).orElse(null);
        if (active == null) {
            return;
        }
        ServerPlayer intruder = guard.level() instanceof ServerLevel level
                ? resolvePlayer(level, active)
                : null;
        if (active.defenseCompleted) {
            clearCombatState(guard, intruder);
            return;
        }
        if (!isValidIntruder(guard, intruder)
                || outsideDefenseRadius(intruder, active)) {
            active.markDefenseCompleted();
            clearCombatState(guard, intruder);
            guard.getNavigation().stop();
            return;
        }
        if (guard.getTarget() != intruder) {
            HuntMemory.clear(guard);
            guard.setTarget(intruder);
        }
        guard.setAggressive(true);
    }

    public static void finish(ChangedEntity guard, boolean defendedCache) {
        Assignment removed = ASSIGNMENTS.remove(guard.getUUID());
        clearCombatState(guard, resolveAssignedPlayer(guard, removed));
        guard.getNavigation().stop();
        if (removed != null && defendedCache) {
            CreatureLifeMemory.incrementRoleStat(guard, 1);
        }
    }

    public static boolean outsideDefenseRadius(
            ServerPlayer player,
            Assignment assignment) {
        return player.blockPosition().distSqr(assignment.cache) > DEFENSE_RADIUS_SQR;
    }

    private static boolean isValidIntruder(
            ChangedEntity guard,
            @Nullable ServerPlayer player) {
        return player != null
                && player.isAlive()
                && !player.isCreative()
                && !player.isSpectator()
                && player.level() == guard.level();
    }

    public static boolean isDefendingAgainst(
            ChangedEntity guard,
            ServerPlayer player) {
        return assignment(guard)
                .map(active -> !active.defenseCompleted
                        && active.player.equals(player.getUUID()))
                .orElse(false);
    }

    public static void clearCombatState(
            ChangedEntity guard,
            @Nullable ServerPlayer intruder) {
        if (intruder == null || guard.getTarget() == intruder) {
            guard.setTarget(null);
        }
        if (intruder == null || guard.getLastHurtByMob() == intruder) {
            guard.setLastHurtByMob(null);
            guard.setLastHurtByPlayer(null);
        }
        HuntMemory.clear(guard);
        guard.setAggressive(false);
        guard.setSprinting(false);
    }

    @Nullable
    public static ServerPlayer resolvePlayer(
            ServerLevel level,
            Assignment assignment) {
        return level.getServer().getPlayerList().getPlayer(assignment.player);
    }

    @Nullable
    private static ServerPlayer resolveAssignedPlayer(
            ChangedEntity guard,
            @Nullable Assignment assignment) {
        return assignment != null && guard.level() instanceof ServerLevel level
                ? resolvePlayer(level, assignment)
                : null;
    }

    private static boolean applyReputationCost(
            ChangedEntity witness,
            ServerPlayer player,
            long now,
            int amount) {
        CompoundTag root = player.getPersistentData();
        CompoundTag cooldowns = root.getCompound(PLAYER_COOLDOWNS);
        String key = communityKey(witness);
        if (cooldowns.getLong(key) > now) {
            return false;
        }
        FactionReputation.adjust(witness, player, amount);
        cooldowns.putLong(key, now + REPUTATION_COOLDOWN);
        root.put(PLAYER_COOLDOWNS, cooldowns);
        return true;
    }

    private static int recordAccess(
            ChangedEntity witness,
            ServerPlayer player,
            long now) {
        CompoundTag root = player.getPersistentData();
        CompoundTag windows = root.getCompound(ACCESS_WINDOWS);
        String key = communityKey(witness);
        CompoundTag window = windows.getCompound(key);
        long started = window.getLong("Started");
        int count = window.getInt("Count");
        if (started <= 0L || now < started
                || now - started > ACCESS_WINDOW_TICKS) {
            started = now;
            count = 0;
        }
        count++;
        window.putLong("Started", started);
        window.putInt("Count", count);
        windows.put(key, window);
        root.put(ACCESS_WINDOWS, windows);
        return count;
    }

    private static String communityKey(ChangedEntity witness) {
        return CreatureCommunityData.snapshot(witness)
                .map(snapshot -> snapshot.id().toString())
                .orElse("unknown");
    }

    public static final class Assignment {
        private final UUID player;
        private final BlockPos cache;
        private final BlockPos post;
        private boolean defenseCompleted;

        private Assignment(
                UUID player,
                BlockPos cache,
                BlockPos post) {
            this.player = player;
            this.cache = cache;
            this.post = post;
        }

        public BlockPos cache() {
            return cache;
        }

        public BlockPos post() {
            return post;
        }

        public boolean defenseCompleted() {
            return defenseCompleted;
        }

        public void markDefenseCompleted() {
            defenseCompleted = true;
        }
    }

    /** Avoids allocating a Vec3 helper at every comparator call site. */
    private static final class VecUtil {
        private static net.minecraft.world.phys.Vec3 center(BlockPos position) {
            return net.minecraft.world.phys.Vec3.atCenterOf(position);
        }
    }
}
