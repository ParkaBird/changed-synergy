package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
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
    private PlayerOutpostService() {
    }

    public static boolean assigned(ChangedEntity mob) {
        return ChangedSynergyConfig.COMMON.playerOutposts.get()
                && mob.getPersistentData().hasUUID(OUTPOST_OWNER);
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

    public static void applyRole(ChangedEntity mob, ServerPlayer owner, Role role) {
        CreatureCacheGuardService.finish(mob, false);
        if (role == Role.CREW || role == Role.HOLD || role == Role.RETURN || role == Role.RETREAT) {
            PlayerOutpostRestGoal.cleanup(mob);
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
    }

    public static void clearAssignment(ChangedEntity mob) {
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
        ItemStack cargo = PlayerOutpostGoal.takeCargo(mob);
        if (!cargo.isEmpty() && mob.level() instanceof ServerLevel level) {
            level.addFreshEntity(new ItemEntity(level, mob.getX(), mob.getY(), mob.getZ(), cargo));
        }
    }

    public static void ensureGoal(ChangedEntity mob) {
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof PlayerOutpostGoal)) {
            mob.goalSelector.addGoal(0, new PlayerOutpostGoal(mob));
        }
        if (mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof PlayerOutpostRestGoal)) {
            mob.goalSelector.addGoal(1, new PlayerOutpostRestGoal(mob));
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity mob)
                || !(event.getLevel() instanceof ServerLevel level)) return;
        PlayerOutpostData data = PlayerOutpostData.get(level.getServer());
        Outpost outpost = data.byMember(mob.getUUID()).orElse(null);
        if (outpost == null) {
            clearAssignment(mob);
            return;
        }
        if (mob.isSleeping()) mob.stopSleeping();
        if (!ChangedSynergyConfig.COMMON.playerOutposts.get()) {
            releaseCargo(mob);
            suspendFollow(mob, outpost.owner);
            return;
        }
        ensureGoal(mob);
        CreatureCommunityData.detach(mob);
        mob.setPersistenceRequired();
        mob.getPersistentData().putUUID(OUTPOST_OWNER, outpost.owner);
        data.updateSupplyType(outpost.members.get(mob.getUUID()), PlayerOutpostSupplyType.of(mob));
        if (outpost.members.get(mob.getUUID()).role != Role.SUPPLY) releaseCargo(mob);
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
                || !(mob.level() instanceof ServerLevel level)
                || mob.tickCount % 100 != 0) return;
        PlayerOutpostRestGoal.cleanupStale(mob);
        if (!mob.getPersistentData().hasUUID(OUTPOST_OWNER)) return;
        PlayerOutpostData data = PlayerOutpostData.get(level.getServer());
        Outpost outpost = data.byMember(mob.getUUID()).orElse(null);
        if (outpost == null) return;
        Member member = outpost.members.get(mob.getUUID());
        data.updateLocation(member, level, mob.blockPosition(), mob.getDisplayName().getString());
        data.updateSupplyType(member, PlayerOutpostSupplyType.of(mob));
        if (mob.isSleeping() && (level.isDay() || member.role == Role.CREW
                || member.role == Role.HOLD || member.role == Role.RETURN
                || member.role == Role.RETREAT || !outpost.active(level))) mob.stopSleeping();
        if (!ChangedSynergyConfig.COMMON.playerOutposts.get()) {
            suspendFollow(mob, outpost.owner);
            return;
        }
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
                                    && monster.isAlive() && mob.canAttack(monster))
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

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity mob)
                || !(mob.level() instanceof ServerLevel level)) return;
        PlayerOutpostRestGoal.cleanup(mob);
        releaseCargo(mob);
    }

    @SubscribeEvent
    public static void onOwnerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer owner)
                || !(owner.level() instanceof ServerLevel level)
                || !ChangedSynergyConfig.COMMON.playerOutposts.get()
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
            if (entity instanceof ChangedEntity guard && guard.isAlive()
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
    public static void onBellUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND
                || !player.isShiftKeyDown()
                || !ChangedSynergyConfig.COMMON.playerOutposts.get()
                || !player.serverLevel().getBlockState(event.getPos()).is(Blocks.BELL)) return;
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
