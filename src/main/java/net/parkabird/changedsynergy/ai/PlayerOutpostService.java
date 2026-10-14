package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Member;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Outpost;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Role;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostMenu;

@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerOutpostService {
    private static final String OUTPOST_OWNER = "ChangedSynergyPlayerOutpostOwner";
    private static final java.util.Map<Goal, Integer> STROLL_PRIORITIES = new java.util.WeakHashMap<>();
    private PlayerOutpostService() {
    }

    public static boolean enabled(net.minecraft.world.level.Level level) {
        return ChangedSynergyConfig.COMMON.playerOutposts.get()
                && net.parkabird.changedsynergy.init.ChangedSynergyGameRules.enabled(level,
                        net.parkabird.changedsynergy.init.ChangedSynergyGameRules.CREATURE_LIFE);
    }

    /** Disabled relationship rules pause assignments instead of treating members as strangers. */
    public static boolean operational(ChangedEntity mob) {
        if (!enabled(mob.level())) return false;
        if (!mob.getPersistentData().hasUUID(OUTPOST_OWNER)) return true;
        java.util.UUID owner = mob.getPersistentData().getUUID(OUTPOST_OWNER);
        boolean bonded = LatexSocialMemory.bondedPlayerUuids(mob).contains(owner)
                || mob instanceof net.ltxprogrammer.changed.entity.TamableLatexEntity pet
                        && pet.isTame() && owner.equals(pet.getOwnerUUID());
        return net.parkabird.changedsynergy.init.ChangedSynergyGameRules.enabled(mob.level(), bonded
                ? net.parkabird.changedsynergy.init.ChangedSynergyGameRules.BOND_SYSTEM
                : net.parkabird.changedsynergy.init.ChangedSynergyGameRules.FRIENDSHIP_SYSTEM);
    }

    public static boolean assigned(ChangedEntity mob) {
        return operational(mob) && hasAssignment(mob);
    }

    /** Ownership persists while camp features are paused. */
    public static boolean hasAssignment(ChangedEntity mob) {
        return mob.getPersistentData().hasUUID(OUTPOST_OWNER);
    }

    public static java.util.Optional<java.util.UUID> assignedOwner(ChangedEntity mob) {
        return assigned(mob) ? java.util.Optional.of(mob.getPersistentData().getUUID(OUTPOST_OWNER))
                : java.util.Optional.empty();
    }

    /** Production and its saved delivery batches take priority over spontaneous social actions. */
    public static boolean isWorking(ChangedEntity mob) {
        if (!assigned(mob) || !(mob.level() instanceof ServerLevel level)) return false;
        Member member = PlayerOutpostData.get(level.getServer()).byMember(mob.getUUID())
                .map(home -> home.members.get(mob.getUUID())).orElse(null);
        return member != null && (member.role == Role.SUPPLY
                && (PlayerOutpostGoal.hasCargo(mob) || PlayerOutpostWorkGoal.hasCargo(mob)
                        || mob.goalSelector.getRunningGoals().anyMatch(wrapped ->
                                wrapped.getGoal() instanceof PlayerOutpostGoal
                                        || wrapped.getGoal() instanceof PlayerOutpostWorkGoal))
                || mob.goalSelector.getRunningGoals().anyMatch(wrapped ->
                        wrapped.getGoal() instanceof PlayerOutpostCrewGatherGoal));
    }

    /** Camp duties override companion attack policies; hunting remains part of the work goal. */
    public static boolean mayAttackTarget(ChangedEntity mob, LivingEntity target) {
        if (target == null || !assigned(mob) || !(mob.level() instanceof ServerLevel level)) return true;
        Member member = PlayerOutpostData.get(level.getServer()).byMember(mob.getUUID())
                .map(home -> home.members.get(mob.getUUID())).orElse(null);
        return member == null || member.role == Role.GUARD
                || member.role == Role.CREW && member.crewJob == PlayerOutpostData.CrewJob.COMBAT
                || mob.getLastHurtByMob() == target
                        && mob.tickCount - mob.getLastHurtByMobTimestamp() < 600;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCombatTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof ChangedEntity mob && !mob.level().isClientSide
                && !mayAttackTarget(mob, event.getNewTarget())) {
            // Reject a proposed fight without stopping an unrelated work/rest route.
            event.setCanceled(true);
        }
    }

    public static boolean authorized(ChangedEntity mob, ServerPlayer owner) {
        return CreatureSocialProfile.allowsPersonalRelationship(mob)
                && LatexSocialMemory.petOwnerUuid(mob).filter(id -> !id.equals(owner.getUUID())).isEmpty()
                && (!LatexSocialMemory.hasActiveBond(mob) || LatexSocialMemory.isBonded(mob, owner))
                && CreaturePersonality.socialPartnerUuid(mob)
                        .filter(id -> !id.equals(owner.getUUID())).isEmpty()
                && (LatexSocialMemory.isPetOwner(mob, owner)
                        || LatexSocialMemory.isBonded(mob, owner)
                        || CreaturePersonality.hasTrustedRelationship(mob, owner)
                                && CreaturePersonality.canFriendFollow(mob, owner));
    }

    public static boolean canInvite(ChangedEntity mob, ServerPlayer owner) {
        return enabled(owner.level()) && authorized(mob, owner)
                && CreaturePersonality.familiarity(mob, owner) >= 28;
    }

    public static void applyRole(ChangedEntity mob, ServerPlayer owner, Role role) {
        CreatureCacheGuardService.finish(mob, false);
        SocialAudienceGoal.end(mob, owner);
        // An interrupted wild delivery cannot run while this camp owns the MOVE task.
        // Keep its items recoverable rather than letting the wild-cargo gate block every order.
        transferWildCargo(mob);
        PlayerOutpostMealGoal.cleanup(mob);
        PlayerOutpostRestGoal.cleanup(mob);
        if (role == Role.CREW || role == Role.HOLD || role == Role.RETURN || role == Role.RETREAT) {
            if (mob.getPersistentData().hasUUID("ChangedSynergyRestOwner")) SharedRestGoal.cancel(mob);
            CreatureComfortGoal.endSeatedActivity(mob);
            if (mob.isSleeping()) mob.stopSleeping();
        }
        mob.getPersistentData().putUUID(OUTPOST_OWNER, owner.getUUID());
        if (LatexSocialMemory.isPetOwner(mob, owner)) {
            LatexSocialMemory.setFollowingOwner(mob, role == Role.CREW);
        } else {
            CreaturePersonality.setSocialFollowing(mob, owner, role == Role.CREW);
        }
        mob.getNavigation().stop();
        if (role != Role.CREW) mob.setTarget(null);
        ensureGoal(mob);
    }

    private static void transferWildCargo(ChangedEntity mob) {
        if (CreatureSettlementService.hasCargo(mob)) {
            ItemStack remainder = PlayerOutpostWorkGoal.acceptCargo(mob, CreatureSettlementService.takeCargo(mob));
            if (!remainder.isEmpty()) mob.spawnAtLocation(remainder);
        }
    }

    public static void clearAssignment(ChangedEntity mob) {
        restoreCompanionGoals(mob);
        PlayerOutpostMealGoal.cleanup(mob);
        PlayerOutpostRestGoal.cleanup(mob);
        if (mob.isSleeping()) mob.stopSleeping();
        boolean assigned = mob.getPersistentData().hasUUID(OUTPOST_OWNER);
        if (assigned) {
            java.util.UUID owner = mob.getPersistentData().getUUID(OUTPOST_OWNER);
            CreaturePersonality.clearSocialFollowing(mob, owner);
            if (LatexSocialMemory.petOwnerUuid(mob).filter(owner::equals).isPresent()) {
                LatexSocialMemory.setFollowingOwner(mob, false);
            }
            mob.getPersistentData().remove(OUTPOST_OWNER);
        }
        releaseCargo(mob);
        if (assigned) mob.getNavigation().stop();
    }

    public static void releaseCargo(ChangedEntity mob) {
        PlayerOutpostWorkGoal.releaseCargo(mob);
        ItemStack cargo = PlayerOutpostGoal.takeCargo(mob);
        if (!cargo.isEmpty() && mob.level() instanceof ServerLevel level) {
            level.addFreshEntity(new ItemEntity(level, mob.getX(), mob.getY(), mob.getZ(), cargo));
        }
    }

    public static void ensureGoal(ChangedEntity mob) {
        Member member = mob.level() instanceof ServerLevel level
                ? PlayerOutpostData.get(level.getServer()).byMember(mob.getUUID())
                        .map(home -> home.members.get(mob.getUUID())).orElse(null) : null;
        int gatherPriority = member == null || member.work == PlayerOutpostData.Work.GATHER ? 1 : 2;
        if (member != null && member.role == Role.CREW) gatherPriority = 2;
        if (member != null && (member.role == Role.RETURN || member.role == Role.RETREAT
                || member.role == Role.HOLD)) gatherPriority = 0;
        int workPriority = gatherPriority == 1 ? 2 : 1;
        if (member != null && operational(mob)) {
            for (var wrapped : java.util.List.copyOf(mob.goalSelector.getAvailableGoals())) {
                if (nativeCompanionTask(wrapped.getGoal())) {
                    // Addon's sleeping goals have no MOVE flag and otherwise overwrite camp navigation.
                    mob.goalSelector.removeGoal(wrapped.getGoal());
                    mob.goalSelector.addGoal(wrapped.getPriority(), new CampYieldGoal(mob, wrapped.getGoal()));
                }
                if (wrapped.getGoal() instanceof RandomStrollGoal && wrapped.getPriority() < 5) {
                    STROLL_PRIORITIES.putIfAbsent(wrapped.getGoal(), wrapped.getPriority());
                    mob.goalSelector.removeGoal(wrapped.getGoal());
                    mob.goalSelector.addGoal(5, wrapped.getGoal());
                }
            }
        }
        // Replace old priorities on already loaded residents as well as freshly loaded ones.
        for (var wrapped : java.util.List.copyOf(mob.goalSelector.getAvailableGoals())) {
            if (wrapped.getGoal() instanceof PlayerOutpostGoal && wrapped.getPriority() != gatherPriority
                    || wrapped.getGoal() instanceof PlayerOutpostWorkGoal && wrapped.getPriority() != workPriority
                    || wrapped.getGoal() instanceof PlayerOutpostRestGoal && wrapped.getPriority() != 3) {
                mob.goalSelector.removeGoal(wrapped.getGoal());
            }
        }
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof PlayerOutpostGoal.FireEscape)) {
            mob.goalSelector.addGoal(0, new PlayerOutpostGoal.FireEscape(mob));
        }
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof PlayerOutpostMealGoal)) {
            mob.goalSelector.addGoal(0, new PlayerOutpostMealGoal(mob));
        }
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof PlayerOutpostCrewGatherGoal)) {
            mob.goalSelector.addGoal(1, new PlayerOutpostCrewGatherGoal(mob));
        }
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof PlayerOutpostWorkGoal)) {
            mob.goalSelector.addGoal(workPriority, new PlayerOutpostWorkGoal(mob));
        }
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof PlayerOutpostGoal)) {
            mob.goalSelector.addGoal(gatherPriority, new PlayerOutpostGoal(mob));
        }
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof PlayerOutpostRestGoal)) {
            mob.goalSelector.addGoal(3, new PlayerOutpostRestGoal(mob));
        }
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof PlayerOutpostGoal.Idle)) {
            mob.goalSelector.addGoal(4, new PlayerOutpostGoal.Idle(mob));
        }
    }

    private static void restoreCompanionGoals(ChangedEntity mob) {
        for (var wrapped : java.util.List.copyOf(mob.goalSelector.getAvailableGoals())) {
            if (wrapped.getGoal() instanceof CampYieldGoal paused && !nativeCompanionWork(paused.original)) {
                mob.goalSelector.removeGoal(paused);
                mob.goalSelector.addGoal(wrapped.getPriority(), paused.original);
            }
            Integer original = STROLL_PRIORITIES.remove(wrapped.getGoal());
            if (original != null && mob.isAlive()) {
                mob.goalSelector.removeGoal(wrapped.getGoal());
                mob.goalSelector.addGoal(original, wrapped.getGoal());
            }
        }
    }

    private static boolean nativeCompanionTask(Goal goal) {
        for (Class<?> type = goal.getClass(); type != null; type = type.getSuperclass()) {
            if (!type.getName().startsWith("net.ltxprogrammer.changed.")
                    && !type.getName().startsWith("net.foxyas.changedaddon.")) continue;
            switch (type.getSimpleName()) {
                case "DarkLatexFishingGoal", "DarkLatexCaveHarvestGoal", "DarkLatexCaveTorchingGoal",
                     "DarkLatexSuitOwnerGoal", "LatexFollowOwnerGoal", "LatexFishingGoal", "LatexCaveHarvestGoal",
                     "LatexCaveTorchingGoal", "LatexSuitOwnerGoal", "SleepingWithOwnerGoal", "BipedSleepGoal",
                     "AlphaSleepGoal" -> { return true; }
            }
        }
        return false;
    }

    private static boolean nativeCompanionWork(Goal goal) {
        String name = goal.getClass().getName();
        return (name.startsWith("net.ltxprogrammer.changed.") || name.startsWith("net.foxyas.changedaddon."))
                && (name.endsWith("FishingGoal") || name.endsWith("CaveHarvestGoal") || name.endsWith("CaveTorchingGoal"));
    }

    public static Goal underlyingCompanionTask(Goal goal) {
        return goal instanceof CampYieldGoal wrapper ? wrapper.original : goal;
    }

    /** The same goal guard controls camp assignment and optional native companion work. */
    public static void ensureCompanionWorkControls(ChangedEntity mob) {
        for (var wrapped : java.util.List.copyOf(mob.goalSelector.getAvailableGoals())) {
            if (!nativeCompanionWork(wrapped.getGoal())) continue;
            mob.goalSelector.removeGoal(wrapped.getGoal());
            mob.goalSelector.addGoal(wrapped.getPriority(), new CampYieldGoal(mob, wrapped.getGoal()));
        }
    }

    /** Retain the original goal in the selector; leaving the camp/config-off restores its behavior. */
    private static final class CampYieldGoal extends Goal {
        private final ChangedEntity mob;
        private final Goal original;
        private CampYieldGoal(ChangedEntity mob, Goal original) {
            this.mob = mob;
            this.original = original;
            setFlags(original.getFlags());
        }
        private boolean enabled() {
            return !assigned(mob) && (!nativeCompanionWork(original)
                    || net.parkabird.changedsynergy.performance.SynergyPerformanceTracker.featureEnabled(
                            net.parkabird.changedsynergy.performance.SynergyPerformanceTracker.Feature.COMPANION_WORK)
                            && CreatureLifeMemory.enabled(mob)
                            && net.parkabird.changedsynergy.init.ChangedSynergyGameRules.enabled(mob.level(),
                                    net.parkabird.changedsynergy.init.ChangedSynergyGameRules.BOND_SYSTEM));
        }
        @Override public boolean canUse() { return enabled() && original.canUse(); }
        @Override public boolean canContinueToUse() { return enabled() && original.canContinueToUse(); }
        @Override public void start() { original.start(); }
        @Override public void tick() { original.tick(); }
        @Override public void stop() { original.stop(); }
        @Override public boolean isInterruptable() { return original.isInterruptable(); }
        @Override public boolean requiresUpdateEveryTick() { return original.requiresUpdateEveryTick(); }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity mob)
                || !(event.getLevel() instanceof ServerLevel level)) return;
        PlayerOutpostData data = PlayerOutpostData.get(level.getServer());
        PlayerOutpostMealGoal.cleanupStale(mob);
        PlayerOutpostRestGoal.cleanupStale(mob);
        Outpost outpost = data.byMember(mob.getUUID()).orElse(null);
        if (outpost == null) {
            clearAssignment(mob);
            return;
        }
        if (mob.isSleeping()) mob.stopSleeping();
        mob.getPersistentData().putUUID(OUTPOST_OWNER, outpost.owner);
        if (!operational(mob)) {
            releaseCargo(mob);
            suspendFollow(mob, outpost.owner);
            return;
        }
        ensureGoal(mob);
        CreatureCommunityData.detach(mob);
        transferWildCargo(mob);
        mob.setPersistenceRequired();
        mob.getPersistentData().putUUID(OUTPOST_OWNER, outpost.owner);
        data.updateSupplyType(outpost.members.get(mob.getUUID()), PlayerOutpostSupplyType.of(mob));
        // Saved travelling cargo belongs to the member; recall/rejoin must not drop it.
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(outpost.owner);
        if (owner != null) {
            if (!authorized(mob, owner)) {
                data.remove(outpost, mob.getUUID());
                clearAssignment(mob);
            } else applyRole(mob, owner, outpost.members.get(mob.getUUID()).role);
        }
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity mob)
                || !(mob.level() instanceof ServerLevel level)) return;
        if (!mayAttackTarget(mob, mob.getTarget())) {
            mob.setTarget(null);
            mob.setAggressive(false);
        }
        if (mob.getPersistentData().hasUUID(OUTPOST_OWNER) && operational(mob))
            CompanionOpenDoorGoal.slidePastOpenTrapdoor(mob);
        if (mob.tickCount % 100 != 0) return;
        PlayerOutpostRestGoal.cleanupStale(mob);
        PlayerOutpostMealGoal.cleanupStale(mob);
        if (!mob.getPersistentData().hasUUID(OUTPOST_OWNER)) return;
        PlayerOutpostData data = PlayerOutpostData.get(level.getServer());
        Outpost outpost = data.byMember(mob.getUUID()).orElse(null);
        if (outpost == null) { clearAssignment(mob); return; }
        transferWildCargo(mob);
        Member member = outpost.members.get(mob.getUUID());
        if (level.isDay() ? !ChangedSynergyConfig.COMMON.playerOutpostIdleRest.get()
                : !ChangedSynergyConfig.COMMON.playerOutpostRest.get()) PlayerOutpostRestGoal.cleanup(mob);
        data.updateLocation(member, level, mob.blockPosition(), mob.getDisplayName().getString());
        data.updateSupplyType(member, PlayerOutpostSupplyType.of(mob));
        if (!operational(mob)) {
            PlayerOutpostMealGoal.cleanup(mob);
            PlayerOutpostRestGoal.cleanup(mob);
            releaseCargo(mob);
            restoreCompanionGoals(mob);
            suspendFollow(mob, outpost.owner);
            return;
        }
        if (mob.isSleeping() && (level.isDay() || member.role == Role.CREW
                || member.role == Role.HOLD || member.role == Role.RETURN
                || member.role == Role.RETREAT || !outpost.active(level))) mob.stopSleeping();
        ensureGoal(mob);
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(outpost.owner);
        if (owner != null && owner.level() == mob.level()) {
            if (!authorized(mob, owner)) {
                data.remove(outpost, member.id);
                clearAssignment(mob);
                return;
            } else {
                if (!LatexSocialMemory.isBonded(mob, owner)
                        || LatexSocialMemory.isPetOwner(mob, owner)) {
                    boolean shouldFollow = member.role == Role.CREW;
                    boolean following = LatexSocialMemory.isPetOwner(mob, owner)
                            ? LatexSocialMemory.isFollowingOwner(mob)
                            : CreaturePersonality.isSocialFollowing(mob, owner);
                    if (shouldFollow != following) applyRole(mob, owner, member.role);
                }
            }
        }
        if (member.role == Role.GUARD && outpost.active(level)
                && !ChangedSynergyConfig.COMMON.companionMonsterAssistOnly.get()
                && outpost.contains(mob.blockPosition()) && mob.getTarget() == null) {
            level.getEntitiesOfClass(Monster.class,
                            new AABB(outpost.marker).inflate(12.0D, 6.0D, 12.0D),
                            monster -> !(monster instanceof ChangedEntity)
                                    && !(monster instanceof Creeper)
                                    && monster.isAlive() && mob.canAttack(monster)
                                    && BondedPetSettings.isHostileMonster(monster, owner))
                    .stream().min(java.util.Comparator.comparingDouble(mob::distanceToSqr))
                    .ifPresent(mob::setTarget);
        }
    }

    private static void suspendFollow(ChangedEntity mob, java.util.UUID owner) {
        CreaturePersonality.clearSocialFollowing(mob, owner);
        if (LatexSocialMemory.petOwnerUuid(mob).filter(owner::equals).isPresent()
                && LatexSocialMemory.isFollowingOwner(mob)) {
            LatexSocialMemory.setFollowingOwner(mob, false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ChangedEntity mob)
                || !(mob.level() instanceof ServerLevel level)) return;
        PlayerOutpostData data = PlayerOutpostData.get(level.getServer());
        data.byMember(mob.getUUID()).ifPresent(outpost -> data.remove(outpost, mob.getUUID()));
        clearAssignment(mob);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMealDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ChangedEntity mob && mob.level() instanceof ServerLevel)
            PlayerOutpostMealGoal.cleanup(mob);
    }

    @SubscribeEvent
    public static void onOwnerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer owner)
                || !(owner.level() instanceof ServerLevel level)
                || !enabled(level)
                || !(event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker)) return;
        Outpost outpost = PlayerOutpostData.get(level.getServer()).byOwner(owner.getUUID()).orElse(null);
        if (outpost == null || !outpost.active(level) || !outpost.contains(owner.blockPosition())
                || !outpost.contains(attacker.blockPosition())) return;
        if (attacker instanceof ServerPlayer other
                && (!ChangedSynergyConfig.COMMON.playerOutpostGuardPvp.get()
                        || !owner.canHarmPlayer(other))) return;
        if (!(attacker instanceof net.minecraft.world.entity.monster.Monster)
                && !(attacker instanceof ServerPlayer)) return;
        if (attacker instanceof Creeper || attacker instanceof Ghast || attacker.isAlliedTo(owner)) return;
        for (Member member : outpost.members.values()) {
            if (member.role != Role.GUARD) continue;
            Entity entity = level.getEntity(member.id);
            if (entity instanceof ChangedEntity guard && operational(guard) && guard.isAlive()
                    && outpost.contains(guard.blockPosition()) && guard.getTarget() == null
                    && guard.canAttack(attacker)
                    && (!LatexSocialMemory.isPetOwner(guard, owner)
                            || BondedOwnerDefenseGoal.allowsConfiguredDefense(guard, owner, attacker))
                    && (!(attacker instanceof ChangedEntity other)
                            || !LatexCreatureCombatRules.mustRejectTarget(guard, other))) {
                guard.setTarget(attacker);
            }
        }
    }

    @SubscribeEvent
    public static void onMarkerUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND
                || !player.isShiftKeyDown()
                || !enabled(player.level())) return;
        Outpost outpost = PlayerOutpostData.get(player.server).byOwner(player.getUUID()).orElse(null);
        if (outpost == null || !outpost.active(player.serverLevel())
                || !outpost.marker.equals(event.getPos())) return;
        PlayerOutpostMenu.open(player, 0);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        PlayerOutpostData.get(level.getServer()).removeDestroyedMarker(level, event.getPos());
    }
}
